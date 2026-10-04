package me.moiclathy.edb.core.service

import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.runtime.ConfigRefreshService
import me.moiclathy.edb.core.config.runtime.ConfigurationCenter
import me.moiclathy.edb.core.database.model.CreateProjectCommand
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.service.AuditService

class ProjectService(
    private val repository: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val auditService: AuditService,
    private val configurationCenter: ConfigurationCenter,
    private val configRefreshService: ConfigRefreshService
) {
    fun findAll(): List<Project> {
        return repository.findAll()
    }

    fun findById(
        id: Int
    ): Project? {

        return repository.findById(id)

    }

    fun findByOwner(
        ownerId: Int
    ): List<Project> {

        return repository.findByOwner(ownerId)

    }

    fun findOwnedProjects(
        userId:Int
    ):List<Project>{

        return repository.findByOwner(userId)

    }

    fun exists(
        id: Int
    ): Boolean {

        return repository.existsById(id)

    }

    fun delete(
        id: Int
    ) {

        val project =
            repository.findById(id)
                ?: throw ProjectNotFoundException(
                    id
                )


        repository.delete(
            project.id
        )

    }

    fun create(
        command: CreateProjectCommand
    ): Int {




        val project = Project(

            id = 0,

            name = command.name,

            description = command.description,

            ownerId = command.ownerId,

            mode = command.mode,

            configYaml = command.configYaml

        )


        val id =
            repository.create(project)

        configRefreshService.refresh(id)


        auditService.record(

            userId = command.ownerId,

            action = "CREATE_PROJECT",

            resourceType = "PROJECT",

            resourceId = id,

            details = project.name

        )


        return id

    }

    fun update(
        id:Int,
        command:CreateProjectCommand
    ):Boolean {


        val project =
            Project(

                id=id,

                name=command.name,

                description=command.description,

                ownerId=command.ownerId,

                mode=command.mode,

                configYaml=command.configYaml
            )


        return repository.update(
            id,
            project
        )



    }

    fun getProject(

        projectId: Int,

        userId: Int

    ): Project {


        val project =
            repository.findById(projectId)
                ?: throw ProjectNotFoundException(
                    projectId
                )


        if(
            !permissionChecker.requireReadable(
                userId,
                project
            )
        ){

            throw PermissionDeniedException()

        }


        return project

    }

    fun requireWritableProject(projectId: Int, userId: Int): Project {
        val project = repository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)
        if (!permissionChecker.requireWritable(userId, project)) {
            throw PermissionDeniedException()
        }
        return project
    }

    fun deleteProject(

        projectId:Int,

        userId:Int

    ) {


        val project =
            repository.findById(projectId)
                ?: throw ProjectNotFoundException(
                    projectId
                )


        if(
            !permissionChecker.requireWritable(
                userId,
                project
            )
        ){

            throw PermissionDeniedException()

        }


        repository.delete(
            project.id
        )

        // 2. 新增：从配置中心缓存中失效/移除该项目的缓存
        configRefreshService.remove(
            project.id
        )

        auditService.record(

            userId=userId,

            action="DELETE_PROJECT",

            resourceType="PROJECT",

            resourceId=project.id,

            details=project.name

        )

    }

    fun updateProject(

        projectId:Int,

        userId:Int,

        command:CreateProjectCommand

    ):Project {


        val old =
            repository.findById(projectId)
                ?: throw ProjectNotFoundException(
                    projectId
                )


        if(
            !permissionChecker.requireWritable(
                userId,
                old
            )
        ){

            throw PermissionDeniedException()

        }



        val project =
            Project(

                id = projectId,

                name = command.name,

                description = command.description,

                ownerId = old.ownerId,

                mode = command.mode,

                configYaml = command.configYaml

            )


        repository.update(
            projectId,
            project
        )

        configRefreshService.refresh(
            projectId
        )

        return project

    }

    fun getProjectConfig(

        projectId:Int,

        userId:Int

    ): ProjectConfig {

        getProject(

            projectId,

            userId

        )

        return configurationCenter.get(

            projectId

        )

    }

    fun refreshProject(

        projectId: Int,

        userId: Int

    ) {

        val project =
            repository.findById(projectId)
                ?: throw ProjectNotFoundException(
                    projectId
                )

        if (
            !permissionChecker.requireReadable(
                userId,
                project
            )
        ) {
            throw PermissionDeniedException()
        }

        configRefreshService.refresh(
            projectId
        )

    }

    /*fun invalidateProject(
        projectId: Int,
        userId: Int
    )*/

}
