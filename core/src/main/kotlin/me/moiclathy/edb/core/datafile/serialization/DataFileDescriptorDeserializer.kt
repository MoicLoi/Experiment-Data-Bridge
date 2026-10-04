package me.moiclathy.edb.core.datafile.serialization

import me.moiclathy.edb.core.datafile.model.DataFile

interface DataFileDescriptorDeserializer {
    fun deserialize(value: String): DataFileDescriptor

    fun deserializeDataFile(value: String): DataFile = deserialize(value).toDataFile()
}
