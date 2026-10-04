package me.moiclathy.edb.web.dto

import me.moiclathy.edb.core.database.model.Project

fun Project.toResponse(): ProjectResponse {

    return ProjectResponse(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        mode = mode.name,
        configYaml = configYaml
    )
}