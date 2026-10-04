package me.moiclathy.edb.core.datafile.format

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.io.InputStream
import kotlin.reflect.KClass

interface DataFileReader<T : Any> {
    val format: DataFileFormat
    val valueType: KClass<T>
    fun read(input: InputStream, options: Map<String, String> = emptyMap()): T
}
