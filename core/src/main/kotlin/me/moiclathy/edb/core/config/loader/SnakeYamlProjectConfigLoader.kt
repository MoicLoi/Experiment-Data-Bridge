package me.moiclathy.edb.core.config.loader

import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.mapper.SnakeYamlProjectConfigMapper
import java.nio.file.Path
import me.moiclathy.edb.core.config.validation.*

class SnakeYamlProjectConfigLoader :

    ProjectConfigLoader {

    private val mapper =
        SnakeYamlProjectConfigMapper()
    private val validator = DefaultConfigValidator()

    override fun load(path: Path): ProjectConfig {

        val yaml =
            path.toFile().readText()
        val config = mapper.fromYaml(yaml)

        val result = validator.validate(config)
        if (!result.valid) {

            throw IllegalArgumentException(
                result.errors.joinToString("\n")
            )

        }
        return config
        //return mapper.fromYaml(yaml)
    }

    override fun save(
        config: ProjectConfig,
        path: Path
    ) {

        val yaml =
            mapper.toYaml(config)

        path.toFile().writeText(yaml)
    }

}