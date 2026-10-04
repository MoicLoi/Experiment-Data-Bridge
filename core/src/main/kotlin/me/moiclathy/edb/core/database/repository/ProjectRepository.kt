package me.moiclathy.edb.core.database.repository

import me.moiclathy.edb.core.database.model.CreateProjectCommand
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.database.table.ProjectsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ProjectRepository{
    private fun toProject(row: ResultRow): Project {
        return Project(
            id = row[ProjectsTable.id],
            name = row[ProjectsTable.name],
            description = row[ProjectsTable.description],
            ownerId = row[ProjectsTable.ownerId],
            mode = row[ProjectsTable.mode],
            configYaml = row[ProjectsTable.configYaml]
        )
    }



    /*fun createProject(command: CreateProjectCommand) {

        transaction {

            ProjectsTable.insert {

                it[name] = command.name

                it[description] = command.description

                it[ownerId] = command.ownerId

                it[mode] = command.mode

                it[configYaml] = command.configYaml

            }

        }

    }*/

    fun findAll(): List<Project> {

        return transaction {

            ProjectsTable
                .selectAll()
                .map(::toProject)

        }

    }

    fun findById(
        id: Int
    ): Project? {

        return transaction {

            ProjectsTable
                .selectAll()
                .where {
                    ProjectsTable.id eq id
                }
                .singleOrNull()
                ?.let(::toProject)

        }

    }

    fun findByOwner(
        ownerId: Int
    ): List<Project> {

        return transaction {

            ProjectsTable
                .selectAll()
                .where {
                    ProjectsTable.ownerId eq ownerId
                }
                .map(::toProject)

        }

    }

    fun existsById(
        id: Int
    ): Boolean {

        return transaction {

            ProjectsTable
                .selectAll()
                .where {
                    ProjectsTable.id eq id
                }
                .limit(1)
                .any()

        }

    }

    fun create(project: Project): Int {

        return transaction {

            val statement = ProjectsTable.insert {

                it[name] = project.name
                it[description] = project.description
                it[ownerId] = project.ownerId
                it[mode] = project.mode
                it[configYaml] = project.configYaml

            }

            statement[ProjectsTable.id]
        }
    }

    fun delete(
        id: Int
    ): Boolean {

        return transaction {

            ProjectsTable.deleteWhere {

                ProjectsTable.id eq id

            } > 0

        }

    }

    fun deleteById(
        id:Int
    ):Boolean {


        return transaction {


            ProjectsTable.deleteWhere {

                ProjectsTable.id eq id

            } > 0


        }

    }

    fun update(
        id:Int,
        project:Project
    ):Boolean {


        return transaction {


            ProjectsTable.update(
                {
                    ProjectsTable.id eq id
                }
            ){

                it[name]=project.name

                it[description]=project.description

                it[mode]=project.mode

                it[configYaml]=project.configYaml

            }>0


        }

    }

    fun findConfigYaml(
        projectId: Int
    ): String? {

        return transaction {

            ProjectsTable
                .selectAll()
                .where {

                    ProjectsTable.id eq projectId

                }
                .singleOrNull()
                ?.get(
                    ProjectsTable.configYaml
                )

        }

    }
    //测试用的方法
    fun updateConfigYaml(
        projectId: Int,
        yaml: String
    ) {
        transaction {
            ProjectsTable.update({
                ProjectsTable.id eq projectId
            }) {
                it[configYaml] = yaml
            }
        }
    }

    fun updateConfiguration(

        projectId:Int,

        yaml:String

    ){
        transaction {

            ProjectsTable.update({

                ProjectsTable.id eq projectId

            }){

                it[configYaml]=yaml

            }

        }
    }


}

