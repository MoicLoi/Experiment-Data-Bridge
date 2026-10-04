package me.moiclathy.edb.core.config.packages

import java.io.OutputStream

class EdbPackageExporter(

    private val writer: EdbPackageWriter

) {

    fun export(

        pkg: EdbPackage,

        output: OutputStream

    ) {

        writer.write(

            pkg,

            output

        )

    }

}