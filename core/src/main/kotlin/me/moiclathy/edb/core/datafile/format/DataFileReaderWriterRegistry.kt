package me.moiclathy.edb.core.datafile.format

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class DataFileReaderRegistry(readers: Iterable<DataFileReader<*>>) {
    private val entries = ConcurrentHashMap<Pair<DataFileFormat, KClass<*>>, DataFileReader<*>>()

    init {
        readers.forEach(::register)
    }

    fun register(reader: DataFileReader<*>) {
        val key = reader.format to reader.valueType
        require(entries.putIfAbsent(key, reader) == null) {
            "DataFile reader already registered for ${reader.format} -> ${reader.valueType.simpleName}."
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> require(format: DataFileFormat, valueType: KClass<T>): DataFileReader<T> =
        entries[format to valueType] as? DataFileReader<T>
            ?: throw IllegalArgumentException("No DataFile reader registered for $format -> ${valueType.simpleName}.")
}

class DataFileWriterRegistry(writers: Iterable<DataFileWriter<*>>) {
    private val entries = ConcurrentHashMap<Pair<DataFileFormat, KClass<*>>, DataFileWriter<*>>()

    init {
        writers.forEach(::register)
    }

    fun register(writer: DataFileWriter<*>) {
        val key = writer.format to writer.valueType
        require(entries.putIfAbsent(key, writer) == null) {
            "DataFile writer already registered for ${writer.format} <- ${writer.valueType.simpleName}."
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> require(format: DataFileFormat, valueType: KClass<T>): DataFileWriter<T> =
        entries[format to valueType] as? DataFileWriter<T>
            ?: throw IllegalArgumentException("No DataFile writer registered for $format <- ${valueType.simpleName}.")
}
