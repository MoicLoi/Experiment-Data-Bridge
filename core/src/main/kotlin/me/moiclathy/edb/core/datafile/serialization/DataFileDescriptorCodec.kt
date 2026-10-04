package me.moiclathy.edb.core.datafile.serialization

/** Backwards-compatible combined contract for components that need both directions. */
interface DataFileDescriptorCodec :
    DataFileDescriptorSerializer,
    DataFileDescriptorDeserializer
