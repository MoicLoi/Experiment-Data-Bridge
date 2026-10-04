package me.moiclathy.edb.core.git

import me.moiclathy.edb.core.config.runtime.ConfigRefreshService
import me.moiclathy.edb.core.config.schema.ConfigSchema
import me.moiclathy.edb.core.config.schema.YamlValidationService
import me.moiclathy.edb.core.config.migration.ConfigMigrationManager
import me.moiclathy.edb.core.config.migration.ConfigVersionReader
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.git.exception.GitCommitNotFoundException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.nio.charset.StandardCharsets

class GitRestoreService(
    private val repositoryService: GitRepositoryService,
    private val commitService: GitCommitService,
    private val projectRepository: ProjectRepository,
    private val configRefreshService: ConfigRefreshService,
    private val yamlValidationService: YamlValidationService,
    private val versionReader: ConfigVersionReader,
    private val migrationManager: ConfigMigrationManager
) {

    fun restore(
        projectId: Int,
        commitId: String,
        author: String
    ): GitCommitInfo {
        return repositoryService.open(projectId).use { git ->
            // 1. 从历史 Commit 中读取 yaml 内容
            val rawYaml = loadYamlFromCommit(git, commitId)

            // 2. 版本读取与自动迁移逻辑
            var normalizedYaml = rawYaml
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

            // 3. 执行 Schema 校验（对迁移后的规范化 YAML 进行校验）
            val validation = yamlValidationService.validate(normalizedYaml)

            // 4. 更新数据库状态（使用迁移和校验通过后的 normalizedYaml）
            projectRepository.updateConfiguration(projectId, normalizedYaml)

            // 5. 调用 commitService：它内部会自动写工作区、add、并生成一条新的还原 Commit
            val commitMessage = "Restore configuration from commit $commitId"
            val commitInfo = commitService.commit(projectId, normalizedYaml, commitMessage, author)

            // 6. 触发运行时配置热加载刷新，使修改立即生效
            configRefreshService.refresh(projectId)

            commitInfo
        }
    }

    /**
     * 封装私有方法：从指定的 Commit 中读取 config.yaml
     * 以后 History Preview、Diff、Restore 都可以复用此方法
     */
    private fun loadYamlFromCommit(git: Git, commitId: String): String {
        val repository = git.repository

        val objectId = repository.resolve(commitId)
            ?: throw GitCommitNotFoundException(commitId)

        val revCommit = RevWalk(repository).use { revWalk ->
            revWalk.parseCommit(objectId)
        }

        val configFileName = GitConstants.CONFIG_FILE
        return TreeWalk.forPath(repository, configFileName, revCommit.tree)?.use { treeWalk ->
            val objectLoader = repository.open(treeWalk.getObjectId(0))
            String(objectLoader.bytes, StandardCharsets.UTF_8)
        } ?: throw IllegalStateException("Configuration file '$configFileName' not found in commit $commitId")
    }
}