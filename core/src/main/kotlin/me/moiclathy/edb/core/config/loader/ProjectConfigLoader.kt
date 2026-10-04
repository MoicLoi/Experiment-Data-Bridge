package me.moiclathy.edb.core.config.loader

import me.moiclathy.edb.core.config.ProjectConfig
import java.nio.file.Path

interface ProjectConfigLoader {

    fun load(path: Path): ProjectConfig

    fun save(
        config: ProjectConfig,
        path: Path
    )

}