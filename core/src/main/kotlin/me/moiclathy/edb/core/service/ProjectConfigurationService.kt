package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.config.migration.ConfigMigrationManager
import me.moiclathy.edb.core.config.migration.ConfigVersionReader
import me.moiclathy.edb.core.config.preview.ConfigurationPreview
import me.moiclathy.edb.core.config.schema.ConfigSchema
import me.moiclathy.edb.core.config.schema.YamlValidationService
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.git.GitCommitInfo
import me.moiclathy.edb.core.git.GitCommitMessages
import me.moiclathy.edb.core.git.GitCommitService
import me.moiclathy.edb.core.git.GitDiffResult
import me.moiclathy.edb.core.git.GitHistoryService // 1. 导入 GitHistoryService
import me.moiclathy.edb.core.git.GitDiffService
import me.moiclathy.edb.core.git.GitPreviewService
import me.moiclathy.edb.core.git.GitRestoreService
import me.moiclathy.edb.core.security.PermissionChecker
import org.eclipse.jgit.api.errors.InvalidConfigurationException

class ProjectConfigurationService(

    private val repository: ProjectRepository,

    private val gitCommitService: GitCommitService,

    private val gitHistoryService: GitHistoryService, // 2. 注入 GitHistoryService

    private val gitDiffService: GitDiffService,

    private val permissionChecker: PermissionChecker,

    private val gitRestoreService: GitRestoreService,

    private val yamlValidationService: YamlValidationService,

    private val gitPreviewService: GitPreviewService,

    private val validationService: ProjectConfigurationValidationService,

    private val migrationManager: ConfigMigrationManager,

    private val versionReader: ConfigVersionReader

){
    fun saveConfiguration(
        projectId: Int,
        userId: Int, // 1. 引入调用者的 userId
        yaml: String,
        author: String,
        messageBuilder: (String) -> String
    ): GitCommitInfo {
        // 2. 检查项目是否存在
        val project = repository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)

        // 3. 严格的写权限校验（拦截越权修改）
        if (!permissionChecker.requireWritable(userId, project)) {
            throw PermissionDeniedException()
        }

        // 4. 版本读取与自动迁移逻辑
        var normalizedYaml = yaml
        val version = versionReader.readVersion(normalizedYaml)
        if (version < ConfigSchema.CURRENT_VERSION) {

            // 📍 推荐加在这里：在执行迁移前记录日志，方便排查是哪个项目、从哪个旧版本升级上来的
            println("Migrating configuration for project $projectId from v$version to v${ConfigSchema.CURRENT_VERSION}")

            // 后续统一换成 Slf4j Logger 的写法：
            // logger.info("Migrating project {} configuration from v{} to v{}", projectId, version, ConfigSchema.CURRENT_VERSION)
            normalizedYaml = migrationManager.migrate(
                normalizedYaml,
                version
            )
        }

        // 5. 执行 Schema 校验（对迁移后的规范化 YAML 进行校验）
        yamlValidationService.validate(normalizedYaml)

        // 6. 执行数据库更新与 Git 提交（使用迁移和校验通过后的 normalizedYaml）
        repository.updateConfiguration(
            projectId,
            normalizedYaml
        )

        val commitMessage = messageBuilder(project.name)

        return gitCommitService.commit(
            projectId,
            normalizedYaml,
            commitMessage,
            author
        ).also {
            println("GIT COMMIT CREATED: $it")
        }
    }

    // 3. 业务层的 history 方法：不写底层 Git 细节，直接委托给 GitHistoryService
    fun history(
        projectId: Int,
        userId: Int, // <-- 加上这个参数
        limit: Int = 50,

    ): List<GitCommitInfo> {
// 1. 检查项目是否存在
        val project = repository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)

        // 2. 统一鉴权
        if (!permissionChecker.requireReadable(userId, project)) {
            throw PermissionDeniedException()
        }

        // 3. 委托给底层 GitHistoryService
        return gitHistoryService.history(projectId, limit)
    }

    fun diff(

        projectId: Int,

        userId: Int,

        oldCommit: String,

        newCommit: String

    ): GitDiffResult {

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

        return gitDiffService.diff(
            projectId,
            oldCommit,
            newCommit
        )
    }

    fun restore(

        projectId: Int,

        userId: Int,

        commitId: String,

        author: String

    ): GitCommitInfo {


        val project =
            repository.findById(projectId)
                ?: throw ProjectNotFoundException(
                    projectId
                )


        if (
            !permissionChecker.requireWritable(
                userId,
                project
            )
        ) {

            throw PermissionDeniedException()

        }


        return gitRestoreService.restore(

            projectId,

            commitId,

            author

        )

    }

    fun preview(
        projectId: Int,
        userId: Int,
        commitId: String? = null // 设置默认值为 null
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

        return if (commitId == null) {
            val yaml = project.configYaml
                ?: throw IllegalStateException("Configuration for project $projectId is empty or not initialized.")
            ConfigurationPreview(
                yaml = yaml
            )
        } else {
            gitPreviewService.preview(
                projectId,
                commitId
            )
        }
    }


}