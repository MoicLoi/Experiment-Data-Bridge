package me.moiclathy.edb.core.datafile.serialization

import me.moiclathy.edb.core.datafile.model.DataFile

interface DataFileDescriptorSerializer {
    fun serialize(descriptor: DataFileDescriptor): String

    fun serialize(dataFile: DataFile): String = serialize(DataFileDescriptor.from(dataFile))
}
