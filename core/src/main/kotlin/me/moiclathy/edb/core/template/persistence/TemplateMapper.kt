package me.moiclathy.edb.core.template.persistence

import me.moiclathy.edb.core.template.model.*

object TemplateMapper {

    fun toEntity(

        template: Template

    ): TemplateEntity {

        return TemplateEntity(

            id = template.id,

            name = template.name,

            description = template.description,

            type = template.type.name,

            visibility = template.visibility.name,

            version = template.metadata.version,

            author = template.metadata.author,

            createdAt = template.metadata.createdAt,

            updatedAt = template.metadata.updatedAt,

            yaml = template.yaml

        )

    }

    fun toModel(

        entity: TemplateEntity

    ): Template {

        return Template(

            id = entity.id,

            name = entity.name,

            description = entity.description,

            type = TemplateType.valueOf(entity.type),

            visibility = TemplateVisibility.valueOf(entity.visibility),

            metadata =

                TemplateMetadata(

                    createdAt = entity.createdAt,

                    updatedAt = entity.updatedAt,

                    author = entity.author,

                    version = entity.version

                ),

            yaml = entity.yaml

        )

    }

}