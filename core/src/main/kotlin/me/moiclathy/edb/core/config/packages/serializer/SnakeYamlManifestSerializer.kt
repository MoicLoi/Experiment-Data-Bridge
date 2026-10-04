package me.moiclathy.edb.core.config.packages.serializer

import me.moiclathy.edb.core.config.packages.EdbManifest
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings

class SnakeYamlManifestSerializer : ManifestSerializer {

    private val dump = Dump(
        DumpSettings.builder().build()
    )

    override fun serialize(
        manifest: EdbManifest
    ): String {

        val map = linkedMapOf<String, Any?>(
            "packageVersion" to manifest.packageVersion,
            "edbVersion" to manifest.edbVersion,
            "projectName" to manifest.projectName,
            "projectId" to manifest.projectId,
            "author" to manifest.author,
            "exportedAt" to manifest.exportedAt.toString()
        )

        manifest.metadata?.let { metadata ->
            map["metadata"] = linkedMapOf(
                "description" to metadata.description,
                "category" to metadata.category,
                "tags" to metadata.tags,
                "updatedAt" to metadata.updatedAt.toString(),
                "dataSource" to metadata.dataSource
            )
        }

        return dump.dumpToString(map)
    }
}
