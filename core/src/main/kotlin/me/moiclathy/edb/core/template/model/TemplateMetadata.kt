package me.moiclathy.edb.core.template.model

import java.time.Instant

data class TemplateMetadata(

    val createdAt: Instant,

    val updatedAt: Instant,

    val author: String,

    val version: Int

)