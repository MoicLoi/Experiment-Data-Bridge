package me.moiclathy.edb.core.config.packages

import java.io.OutputStream

interface EdbPackageWriter{

    fun write(

        pkg:EdbPackage,

        output: OutputStream

    )

}