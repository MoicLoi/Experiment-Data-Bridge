package me.moiclathy.edb.core.config.packages.serializer

import me.moiclathy.edb.core.config.packages.EdbManifest

interface ManifestDeserializer {

    fun deserialize(
        yaml: String
    ): EdbManifest

}