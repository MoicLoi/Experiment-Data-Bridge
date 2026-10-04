package me.moiclathy.edb.core.config.packages.serializer

import me.moiclathy.edb.core.config.packages.EdbManifest
import me.moiclathy.edb.core.config.packages.EdbManifestMetadata
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.time.Instant

class SnakeYamlManifestDeserializer :

    ManifestDeserializer {

    private val loader =

        Load(

            LoadSettings.builder().build()

        )

    override fun deserialize(

        yaml: String

    ): EdbManifest {

        @Suppress("UNCHECKED_CAST")

        val map =

            loader.loadFromString(yaml)

                    as? Map<String, Any?>

                ?: emptyMap()

        return EdbManifest(

            packageVersion =
                (map["packageVersion"] as Number).toInt(),

            edbVersion =
                map["edbVersion"] as String,

            projectName =
                map["projectName"] as String,

            projectId =
                (map["projectId"] as Number?)?.toInt(),

            exportedAt =
                Instant.parse(
                    map["exportedAt"] as String
                ),

            author =
                map["author"] as String?,

            metadata =
                deserializeMetadata(map["metadata"])

        )

    }

    private fun deserializeMetadata(
        value: Any?
    ): EdbManifestMetadata? {
        if (value == null) {
            return null
        }

        val metadata = value as? Map<*, *>
            ?: throw IllegalArgumentException("Manifest metadata must be a mapping")

        val tags = (metadata["tags"] as? List<*>)
            ?.map { tag ->
                tag as? String
                    ?: throw IllegalArgumentException("Manifest metadata tags must be strings")
            }
            ?: emptyList()

        val updatedAt = metadata["updatedAt"] as? String
            ?: throw IllegalArgumentException("Manifest metadata updatedAt is required")

        return EdbManifestMetadata(
            description = metadata["description"] as? String,
            category = metadata["category"] as? String,
            tags = tags,
            updatedAt = Instant.parse(updatedAt),
            dataSource = metadata["dataSource"] as? String
        )
    }

}
