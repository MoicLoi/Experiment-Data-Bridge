package me.moiclathy.edb.core.config.packages.zip

import me.moiclathy.edb.core.config.packages.EdbPackage
import me.moiclathy.edb.core.config.packages.EdbPackagePaths
import me.moiclathy.edb.core.config.packages.EdbPackageReader
import me.moiclathy.edb.core.config.packages.serializer.ManifestDeserializer
import java.io.InputStream
import java.util.zip.ZipInputStream

class ZipEdbPackageReader(

    private val manifestDeserializer: ManifestDeserializer

) : EdbPackageReader {

    override fun read(

        input: InputStream

    ): EdbPackage {

        var manifestYaml: String? = null

        var configurationYaml: String? = null

        ZipInputStream(input).use { zip ->

            while (true) {

                val entry = zip.nextEntry ?: break

                when (entry.name) {

                    EdbPackagePaths.MANIFEST -> {

                        manifestYaml =
                            zip.readBytes()
                                .toString(Charsets.UTF_8)

                    }

                    EdbPackagePaths.CONFIGURATION -> {

                        configurationYaml =
                            zip.readBytes()
                                .toString(Charsets.UTF_8)

                    }

                }

                zip.closeEntry()

            }

        }

        if (manifestYaml == null) {

            throw IllegalStateException(

                "Missing entry: edb/manifest.yaml"

            )

        }

        if (configurationYaml == null) {

            throw IllegalStateException(

                "Missing entry: edb/configuration.yaml"

            )

        }

        val manifest =

            manifestDeserializer.deserialize(

                manifestYaml

            )

        return EdbPackage(

            manifest = manifest,

            configuration = configurationYaml

        )

    }

}