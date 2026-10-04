package me.moiclathy.edb.core.config

object DefaultProjectConfig {

    val DEFAULT =

        ProjectConfig(

            version = 1,

            project =

                ProjectInfo(

                    name = "Untitled",

                    description = null,

                    species = "mouse",

                    owner = null

                ),

            workflow =

                WorkflowConfig(

                    steps = emptyList()

                ),

            output =

                OutputConfig(

                    format = "excel",

                    path = "./output"

                )

        )

}