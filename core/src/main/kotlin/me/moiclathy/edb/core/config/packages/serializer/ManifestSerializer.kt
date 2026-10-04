package me.moiclathy.edb.core.config.packages.serializer

import me.moiclathy.edb.core.config.packages.EdbManifest

interface ManifestSerializer {

    fun serialize(
        manifest: EdbManifest
    ): String

}