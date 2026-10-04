package me.moiclathy.edb.web.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectRequest(

    val name: String,

    val description: String? = null,

    val mode: String,

    val configYaml: String? = null

)