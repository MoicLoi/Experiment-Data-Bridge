package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.config.importer.ImportReport
import me.moiclathy.edb.core.config.migration.ConfigMigrationManager
import me.moiclathy.edb.core.config.migration.ConfigVersionReader
import me.moiclathy.edb.core.config.schema.ConfigSchema
import me.moiclathy.edb.core.config.schema.ConfigSchemaValidator
import me.moiclathy.edb.core.git.GitCommitInfo
import me.moiclathy.edb.core.git.GitCommitMessages

class ProjectConfigurationImportService(

    private val validator: ConfigSchemaValidator,

    private val migrationManager: ConfigMigrationManager,

    private val configurationService: ProjectConfigurationService,

    private val versionReader: ConfigVersionReader

){
    fun importConfiguration(

        projectId: Int,

        userId: Int,

        yaml: String,

        author: String

    ): ImportReport {

        // 1. 读取版本
        val version =
            versionReader.readVersion(yaml)

        // 2. 是否需要迁移
        val finalYaml =
            if (
                version < ConfigSchema.CURRENT_VERSION
            ) {
                migrationManager.migrate(
                    yaml,
                    version
                )
            } else {
                yaml
            }

        // 3. 统一走保存流程
        /*return configurationService.saveConfiguration(
            projectId = projectId,
            userId = userId,
            yaml = finalYaml,
            author = author,
            messageBuilder =
                GitCommitMessages::configurationImported
        )*/

        val commit =
            configurationService.saveConfiguration(
                projectId,
                userId,
                finalYaml,
                author,
                messageBuilder = GitCommitMessages::configurationImported
            )

        return ImportReport(

            originalVersion = version,

            migratedVersion = ConfigSchema.CURRENT_VERSION,

            migrationApplied = version != ConfigSchema.CURRENT_VERSION,

            commit = commit

        )
    }
}