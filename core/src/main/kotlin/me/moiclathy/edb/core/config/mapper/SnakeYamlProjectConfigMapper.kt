package me.moiclathy.edb.core.config.mapper

import me.moiclathy.edb.core.config.OutputConfig
import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.ProjectInfo
import me.moiclathy.edb.core.config.WorkflowConfig
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings

class SnakeYamlProjectConfigMapper :

    ProjectConfigMapper {

    override fun fromYaml(
        yaml: String
    ): ProjectConfig {

        val settings =
            LoadSettings.builder()
                .build()

        val loader =
            Load(settings)

        val root =
            loader.loadFromString(yaml)


        @Suppress("UNCHECKED_CAST")
        val map =
            root as? Map<String, Any?>
                ?: throw IllegalArgumentException(
                    "Configuration root must be yaml object"
                )


        fun section(name:String):Map<String,Any?> {

            @Suppress("UNCHECKED_CAST")
            return map[name]
                    as? Map<String,Any?>
                ?: emptyMap()

        }


        val project = section("project")
        val workflow = section("workflow")
        val output = section("output")


        return ProjectConfig(

            version =
                (map["version"] as? Number)
                    ?.toInt()
                    ?: 1,


            project =
                ProjectInfo(
                    name =
                        project["name"]
                                as? String
                            ?: "Unnamed Project",

                    description =
                        project["description"]
                                as? String,

                    species =
                        project["species"]
                                as? String
                            ?: "Unknown",

                    owner =
                        project["owner"]
                                as? String
                ),


            workflow =
                WorkflowConfig(
                    steps =
                        (workflow["steps"] as? List<*>)
                            ?.filterIsInstance<String>()
                            ?: emptyList()
                ),


            output =
                OutputConfig(
                    format =
                        output["format"]
                                as? String
                            ?: "json",

                    path =
                        output["path"]
                                as? String
                            ?: "./output"
                )
        )
    }

    override fun toYaml(
        config: ProjectConfig
    ): String {

        val map = linkedMapOf<String, Any?>()

        // version
        map["version"] = config.version

        // project
        map["project"] = linkedMapOf(
            "name" to config.project.name,
            "description" to config.project.description,
            "species" to config.project.species,
            "owner" to config.project.owner
        )

        // workflow
        map["workflow"] = linkedMapOf(
            "steps" to config.workflow.steps
        )

        // output
        map["output"] = linkedMapOf(
            "format" to config.output.format,
            "path" to config.output.path
        )

        val settings =
            DumpSettings.builder()
                .build()

        val dump =
            Dump(settings)

        return dump.dumpToString(map)
    }

}