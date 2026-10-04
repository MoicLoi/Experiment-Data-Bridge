package me.moiclathy.edb.core.config.packages

import java.io.InputStream

interface EdbPackageReader{

    fun read(

        input: InputStream

    ):EdbPackage

}