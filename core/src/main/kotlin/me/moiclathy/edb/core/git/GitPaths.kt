package me.moiclathy.edb.core.git

import java.nio.file.Path
import java.nio.file.Paths

object GitPaths {

    private val root: Path =

        Paths.get(
            GitConstants.PROJECT_DIRECTORY
        )

    fun projectDirectory(

        projectId:Int

    ):Path{

        return root.resolve(
            projectId.toString()
        )

    }

    fun gitDirectory(

        projectId:Int

    ):Path{

        return projectDirectory(
            projectId
        ).resolve(".git")

    }

    fun configFile(

        projectId:Int

    ):Path{

        return projectDirectory(
            projectId
        ).resolve(
            GitConstants.CONFIG_FILE
        )

    }

}