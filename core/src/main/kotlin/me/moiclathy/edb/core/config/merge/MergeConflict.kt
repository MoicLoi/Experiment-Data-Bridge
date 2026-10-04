package me.moiclathy.edb.core.config.merge

data class MergeConflict(

    val path: String,

    val type: MergeConflictType,

    val targetValue: Any?,

    val sourceValue: Any?,

    val message: String
)