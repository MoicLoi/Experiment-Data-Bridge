package me.moiclathy.edb.core.config

data class ProjectConfig(

    val version: Int,

    val project: ProjectInfo,

    val workflow: WorkflowConfig,

    val output: OutputConfig

)