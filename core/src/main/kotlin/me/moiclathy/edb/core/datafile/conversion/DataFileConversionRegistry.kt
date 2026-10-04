package me.moiclathy.edb.core.datafile.conversion

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import java.util.concurrent.ConcurrentHashMap

data class DataFileConversionPath(
    val source: DataFileFormat,
    val target: DataFileFormat
)

data class DataFileConversionCapability(
    val sourceFormat: DataFileFormat,
    val targetFormat: DataFileFormat,
    val options: List<DataFileConverterOption>
)

class DataFileConverterNotFoundException(source: DataFileFormat, target: DataFileFormat) :
    IllegalArgumentException("DataFile converter is not registered: $source -> $target")

class DataFileConversionRegistry(
    converters: Iterable<DataFileConverter> = emptyList()
) {
    private val convertersByPath = ConcurrentHashMap<DataFileConversionPath, DataFileConverter>()

    init {
        converters.forEach(::register)
    }

    fun register(converter: DataFileConverter) {
        require(converter.sourceFormat in SUPPORTED_SCIENTIFIC_FORMATS) {
            "Unsupported source DataFile format: ${converter.sourceFormat}"
        }
        require(converter.targetFormat in SUPPORTED_SCIENTIFIC_FORMATS) {
            "Unsupported target DataFile format: ${converter.targetFormat}"
        }
        val path = DataFileConversionPath(converter.sourceFormat, converter.targetFormat)
        val previous = convertersByPath.putIfAbsent(path, converter)
        require(previous == null) { "DataFile converter already registered: ${path.source} -> ${path.target}" }
    }

    fun find(source: DataFileFormat, target: DataFileFormat): DataFileConverter? =
        convertersByPath[DataFileConversionPath(source, target)]

    fun require(source: DataFileFormat, target: DataFileFormat): DataFileConverter =
        find(source, target) ?: throw DataFileConverterNotFoundException(source, target)

    fun paths(): Set<DataFileConversionPath> = convertersByPath.keys.toSet()

    fun targetsFor(source: DataFileFormat): Set<DataFileFormat> =
        convertersByPath.keys.filter { it.source == source }.mapTo(mutableSetOf()) { it.target }

    fun capabilitiesFor(source: DataFileFormat): List<DataFileConversionCapability> =
        convertersByPath.values
            .asSequence()
            .filter { it.sourceFormat == source }
            .map {
                DataFileConversionCapability(it.sourceFormat, it.targetFormat, it.options)
            }
            .sortedBy { it.targetFormat.name }
            .toList()

    companion object {
        val SUPPORTED_SCIENTIFIC_FORMATS = setOf(
            DataFileFormat.CSV,
            DataFileFormat.XLSX,
            DataFileFormat.JSON,
            DataFileFormat.HDF5,
            DataFileFormat.MAT
        )
    }
}
