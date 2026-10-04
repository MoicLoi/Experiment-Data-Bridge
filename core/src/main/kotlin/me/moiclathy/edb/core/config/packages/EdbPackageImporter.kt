package me.moiclathy.edb.core.config.packages

import java.io.InputStream

class EdbPackageImporter(

    private val reader:EdbPackageReader

){
    fun import(

        input: InputStream

    ): EdbPackage {

        return reader.read(

            input

        )

    }
}