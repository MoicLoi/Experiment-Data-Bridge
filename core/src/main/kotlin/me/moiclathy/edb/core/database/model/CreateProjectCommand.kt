package me.moiclathy.edb.core.database.model

import me.moiclathy.edb.core.project.ProjectMode

data class CreateProjectCommand(

    val name: String,

    val description: String?,

    val ownerId: Int,

    val mode: ProjectMode,

    val configYaml: String?

)