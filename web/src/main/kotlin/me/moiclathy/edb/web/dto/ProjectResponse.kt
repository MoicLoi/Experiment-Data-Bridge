package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProjectResponse(

    val id: Int,

    val name: String,

    val description: String?,

    val ownerId: Int,

    val mode: String,

    val configYaml: String?

)