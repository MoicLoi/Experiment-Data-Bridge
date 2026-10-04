package me.moiclathy.edb.core.git

import me.moiclathy.edb.core.git.exception.GitRepositoryInitException
import org.eclipse.jgit.api.Git
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class GitRepositoryService(
    private val customRootDirectory: Path? = null
) {

     fun getProjectDirectory(projectId: Int): Path =
        customRootDirectory
            ?.resolve("project_$projectId")
            ?: GitPaths.projectDirectory(projectId)

    private fun getGitDirectory(projectId: Int): Path =
        getProjectDirectory(projectId)
            .resolve(".git")

    fun exists(projectId: Int): Boolean =
        Files.exists(getGitDirectory(projectId))

    fun initialize(projectId: Int): Git {
        if (exists(projectId)) {
            return Git.open(getProjectDirectory(projectId).toFile())
        }

        val directory = getProjectDirectory(projectId)
        Files.createDirectories(directory)

        return try {
            Git.init()
                .setDirectory(directory.toFile())
                .call()
        } catch (ex: Exception) {
            throw GitRepositoryInitException(projectId, ex)
        }
    }

    fun open(projectId: Int): Git {
        if (!exists(projectId)) {
            initialize(projectId)
        }

        return Git.open(
            getProjectDirectory(projectId).toFile()
        )
    }

    fun delete(projectId: Int) {
        val directory = getProjectDirectory(projectId)
        if (!directory.exists()) {
            return
        }
        directory.toFile().deleteRecursively()
    }
}