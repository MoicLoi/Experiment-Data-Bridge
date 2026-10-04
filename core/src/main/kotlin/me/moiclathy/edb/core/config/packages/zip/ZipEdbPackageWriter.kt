package me.moiclathy.edb.core.config.packages.zip

import me.moiclathy.edb.core.config.packages.EdbPackage
import me.moiclathy.edb.core.config.packages.EdbPackageWriter
import me.moiclathy.edb.core.config.packages.serializer.ManifestSerializer
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipEdbPackageWriter (private val manifestSerializer: ManifestSerializer): EdbPackageWriter {
    override fun write(

        pkg: EdbPackage,

        output: OutputStream

    ) {

        ZipOutputStream(output).use { zip ->

            val manifestYaml =

                manifestSerializer.serialize(

                    pkg.manifest

                )

            writeManifest(

                zip,

                manifestYaml

            )

            writeConfiguration(

                zip,

                pkg.configuration

            )

            zip.finish()

        }

    }

    private fun writeManifest(
        zip: ZipOutputStream,
        manifestYaml: String
    ) {
        writeEntry(
            zip,
            "edb/manifest.yaml",
            manifestYaml
        )
    }

    private fun writeConfiguration(
        zip: ZipOutputStream,
        yaml: String
    ) {
        writeEntry(
            zip,
            "edb/configuration.yaml",
            yaml
        )
    }

    private fun writeEntry(

        zip: ZipOutputStream,

        path: String,

        content: String

    ) {

        zip.putNextEntry(

            ZipEntry(path)

        )

        zip.write(

            content.toByteArray(

                Charsets.UTF_8

            )

        )

        zip.closeEntry()

    }


}