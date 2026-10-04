package me.moiclathy.edb.core.service // 或者根据你的包路径调整

import me.moiclathy.edb.core.config.export.ConfigurationExportResult
import me.moiclathy.edb.core.config.metadata.model.ConfigurationMetadata
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import me.moiclathy.edb.core.config.packages.EdbManifest
import me.moiclathy.edb.core.config.packages.EdbManifestMetadata
import me.moiclathy.edb.core.config.packages.EdbPackage
import me.moiclathy.edb.core.config.packages.EdbPackageExporter
import me.moiclathy.edb.core.config.packages.EdbVersion
import me.moiclathy.edb.core.config.packages.PackageVersion
import me.moiclathy.edb.core.config.preview.ConfigurationPreview
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.service.AuditService // 引入审计服务
import java.io.ByteArrayOutputStream
import java.time.Instant

class ProjectConfigurationExportService(
    private val repository: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val auditService: AuditService,
    private val packageExporter: EdbPackageExporter,
    private val metadataService: ConfigurationMetadataService
) {
    fun export(
        projectId: Int,
        userId: Int
    ): ConfigurationExportResult {
        // 1. 检查项目是否存在
        val project = repository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)

        // 2. 严格的读权限校验
        if (!permissionChecker.requireReadable(userId, project)) {
            throw PermissionDeniedException()
        }

        // 3. 记录配置导出的合规审计日志
        auditService.recordConfigurationExport(
            projectId,
            userId
        )

        // 4. 校验配置内容是否存在
        val yaml = project.configYaml
            ?: throw IllegalStateException("Configuration for project $projectId is empty or not initialized.")

        // 5. 返回结构化结果，方便 Web 层处理文件名和 Content-Type
        return ConfigurationExportResult(

            fileName = "${project.name}.yaml",

            yaml = yaml,

            projectId = project.id,

            projectName = project.name,

            author = null
        )
    }

    fun preview(

        projectId: Int,

        userId: Int

    ): ConfigurationPreview {

        val project =
            repository.findById(projectId)
                ?: throw ProjectNotFoundException(projectId)

        if (
            !permissionChecker.requireReadable(
                userId,
                project
            )
        ) {
            throw PermissionDeniedException()
        }

        // 校验配置内容是否存在
        val yaml = project.configYaml
            ?: throw IllegalStateException("Configuration for project $projectId is empty or not initialized.")

        return ConfigurationPreview(

            yaml = yaml

        )

    }

    fun exportPackage(

        projectId: Int,

        userId: Int

    ): ByteArray {

        val result = export(

            projectId,

            userId

        )

        val manifest =

            createManifest(

                result,

                metadataService.get(projectId)

            )

        val pkg =

            createPackage(

                manifest,

                result.yaml

            )

        val output = ByteArrayOutputStream()

        packageExporter.export(

            pkg,

            output

        )

        return output.toByteArray()

    }

    private fun createManifest(

        export: ConfigurationExportResult,

        metadata: ConfigurationMetadata?

    ): EdbManifest {

        return EdbManifest(

            packageVersion = PackageVersion.CURRENT,

            edbVersion = EdbVersion.VERSION,

            projectName = export.projectName,

            projectId = export.projectId,

            exportedAt = Instant.now(),

            author = export.author,

            metadata = metadata?.let {
                EdbManifestMetadata(
                    description = it.description,
                    category = it.category,
                    tags = it.tags,
                    updatedAt = it.updatedAt,
                    dataSource = it.dataSource
                )
            }

        )

    }

    private fun createPackage(

        manifest: EdbManifest,

        yaml: String

    ): EdbPackage {

        return EdbPackage(

            manifest = manifest,

            configuration = yaml

        )

    }
}
