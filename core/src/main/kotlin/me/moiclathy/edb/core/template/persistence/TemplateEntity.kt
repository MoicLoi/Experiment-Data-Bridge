package me.moiclathy.edb.core.template.persistence

import java.time.Instant

data class TemplateEntity(

    val id: Long?,

    val name: String,

    val description: String,

    val type: String,

    val visibility: String,

    val version: Int,

    val author: String,

    val createdAt: Instant,

    val updatedAt: Instant,

    val yaml: String

)