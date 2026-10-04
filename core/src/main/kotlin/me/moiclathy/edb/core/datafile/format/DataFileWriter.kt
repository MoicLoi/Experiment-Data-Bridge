package me.moiclathy.edb.core.datafile.format

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.io.OutputStream
import kotlin.reflect.KClass

interface DataFileWriter<T : Any> {
    val format: DataFileFormat
    val valueType: KClass<T>
    val mediaType: String
    fun write(value: T, output: OutputStream, options: Map<String, String> = emptyMap())
}
