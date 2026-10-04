package me.moiclathy.edb.core.git

import me.moiclathy.edb.core.git.exception.GitCommitNotFoundException
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.nio.charset.StandardCharsets

class GitFileLoader(
    private val repositoryService: GitRepositoryService
) {
    /**
     * 通用的历史文件加载方法，支持未来扩展读取 README.md、schema.yaml、workflow.yaml 等任意文件
     */
    fun loadFile(
        projectId: Int,
        commitId: String,
        path: String
    ): String {
        return repositoryService.open(projectId).use { git ->
            val repository = git.repository

            val objectId = repository.resolve(commitId)
                ?: throw GitCommitNotFoundException(commitId)

            val revCommit = RevWalk(repository).use { revWalk ->
                revWalk.parseCommit(objectId)
            }

            TreeWalk.forPath(repository, path, revCommit.tree)?.use { treeWalk ->
                val objectLoader = repository.open(treeWalk.getObjectId(0))
                String(objectLoader.bytes, StandardCharsets.UTF_8)
            } ?: throw IllegalStateException("File '$path' not found in commit $commitId")
        }
    }

    /**
     * 快捷方法：专门用于加载默认的配置文件
     */
    fun loadConfig(
        projectId: Int,
        commitId: String
    ): String {
        return loadFile(
            projectId,
            commitId,
            GitConstants.CONFIG_FILE // 如果你的常量名是 CONFIG_FILE_NAME，可按实际调整
        )
    }
}