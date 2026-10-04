package me.moiclathy.edb.core.config.merge

data class MergeRequest(

    val target: Map<String, Any?>,

    val source: Map<String, Any?>,

    val strategy: MergeStrategy = MergeStrategy.RECURSIVE
)