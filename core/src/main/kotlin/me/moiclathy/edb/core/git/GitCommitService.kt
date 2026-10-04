package me.moiclathy.edb.core.git

import java.nio.file.Files
import java.nio.file.Path

class GitCommitService(
    private val repositoryService: GitRepositoryService
) {

    private fun configFile(projectId: Int): Path {
        return repositoryService
            .getProjectDirectory(projectId)
            .resolve(
                GitConstants.CONFIG_FILE
            )
    }

    private fun writeConfig(
        projectId: Int,
        yaml: String
    ) {
        val file = configFile(projectId)

        Files.createDirectories(
            file.parent
        )

        Files.writeString(
            file,
            yaml
        )
    }

    fun commit(
        projectId: Int,
        yaml: String,
        message: String,
        author: String
    ): GitCommitInfo {
        // 使用 use 块包裹，确保方法结束或异常时自动调用 git.close()
        return repositoryService.open(projectId).use { git ->

            // 第二步：写 yaml（现在会正确写入到自定义的 tempDir 沙箱路径中）
            writeConfig(projectId, yaml)

            // 第三步：git add
            git.add()
                .addFilepattern(GitConstants.CONFIG_FILE)
                .call()

            // 构造 PersonIdent（既设置了名字，也包含了 email）
            val authorIdent = org.eclipse.jgit.lib.PersonIdent(author, "$author@edb.local")

            // 第四步：commit
            val commit = git.commit()
                .setMessage(message)
                .setAuthor(author, "$author@edb.local")
                .call()

            // 返回 GitCommitInfo
            GitCommitInfo(
                commitId = commit.id.name(),
                shortCommitId = commit.id.name().substring(0, 7),
                message = commit.fullMessage,
                author = commit.authorIdent.name,
                email = authorIdent.emailAddress,
                time = commit.authorIdent.`when`.toInstant()
            )
        }
    }
}