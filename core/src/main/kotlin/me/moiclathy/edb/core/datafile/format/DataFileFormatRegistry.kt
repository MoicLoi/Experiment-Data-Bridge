package me.moiclathy.edb.core.datafile.format

import me.moiclathy.edb.core.datafile.model.DataFileFormat

data class DataFileFormatDefinition(
    val format: DataFileFormat,
    val extensions: Map<String, String>,
    val mediaTypes: Set<String>,
    val defaultMediaType: String
) {
    init {
        require(defaultMediaType in mediaTypes) { "Default media type must be registered for $format." }
        require(extensions.keys.none { it.isBlank() || it.startsWith('.') }) {
            "DataFile extensions must be non-blank and omit the leading dot."
        }
        require(extensions.values.all { it in mediaTypes }) {
            "Every extension media type must be registered for $format."
        }
    }
}

data class RegisteredDataFileFormat(
    val format: DataFileFormat,
    val mediaType: String
)

class DataFileFormatRegistry(
    definitions: Iterable<DataFileFormatDefinition> = defaultDefinitions()
) {
    private val definitionsByFormat: Map<DataFileFormat, DataFileFormatDefinition>
    private val formatsByExtension: Map<String, RegisteredDataFileFormat>
    private val formatsByMediaType: Map<String, RegisteredDataFileFormat>

    init {
        val normalized = definitions.map { definition ->
            definition.copy(
                extensions = definition.extensions.mapKeys { it.key.lowercase() }
                    .mapValues { it.value.lowercase() },
                mediaTypes = definition.mediaTypes.mapTo(linkedSetOf()) { it.lowercase() },
                defaultMediaType = definition.defaultMediaType.lowercase()
            )
        }
        require(normalized.map { it.format }.distinct().size == normalized.size) {
            "A DataFile format may only be registered once."
        }
        definitionsByFormat = normalized.associateBy { it.format }
        formatsByExtension = uniqueIndex(
            normalized.flatMap { definition ->
                definition.extensions.map { (extension, mediaType) ->
                    extension to RegisteredDataFileFormat(definition.format, mediaType)
                }
            },
            "extension"
        )
        formatsByMediaType = uniqueIndex(
            normalized.flatMap { definition ->
                definition.mediaTypes.map { mediaType ->
                    mediaType to RegisteredDataFileFormat(definition.format, mediaType)
                }
            },
            "media type"
        )
    }

    fun definition(format: DataFileFormat): DataFileFormatDefinition? = definitionsByFormat[format]

    fun findByExtension(extension: String): RegisteredDataFileFormat? =
        formatsByExtension[extension.removePrefix(".").lowercase()]

    fun findByMediaType(mediaType: String): RegisteredDataFileFormat? =
        formatsByMediaType[mediaType.substringBefore(';').trim().lowercase()]

    fun definitions(): List<DataFileFormatDefinition> =
        definitionsByFormat.values.sortedBy { it.format.name }

    companion object {
        fun defaultDefinitions(): List<DataFileFormatDefinition> = listOf(
            definition(DataFileFormat.CSV, "text/csv", "csv" to "text/csv"),
            definition(DataFileFormat.TSV, "text/tab-separated-values", "tsv" to "text/tab-separated-values"),
            definition(
                DataFileFormat.XLSX,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ),
            definition(DataFileFormat.JSON, "application/json", "json" to "application/json"),
            definition(
                DataFileFormat.HDF5,
                "application/x-hdf5",
                "h5" to "application/x-hdf5",
                "hdf" to "application/x-hdf5",
                "hdf5" to "application/x-hdf5"
            ),
            definition(DataFileFormat.MAT, "application/x-matlab-data", "mat" to "application/x-matlab-data"),
            definition(DataFileFormat.PDF, "application/pdf", "pdf" to "application/pdf"),
            definition(
                DataFileFormat.IMAGE,
                "image/png",
                "png" to "image/png",
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "tif" to "image/tiff",
                "tiff" to "image/tiff"
            ),
            definition(
                DataFileFormat.VIDEO,
                "video/mp4",
                "mp4" to "video/mp4",
                "avi" to "video/x-msvideo",
                "mov" to "video/quicktime"
            ),
            definition(DataFileFormat.BINARY, "application/octet-stream")
        )

        private fun definition(
            format: DataFileFormat,
            defaultMediaType: String,
            vararg extensions: Pair<String, String>
        ) = DataFileFormatDefinition(
            format = format,
            extensions = extensions.toMap(),
            mediaTypes = (extensions.map { it.second } + defaultMediaType).toSet(),
            defaultMediaType = defaultMediaType
        )

        private fun <T> uniqueIndex(entries: List<Pair<String, T>>, label: String): Map<String, T> {
            val duplicates = entries.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
            require(duplicates.isEmpty()) {
                "Duplicate DataFile $label registrations: ${duplicates.sorted().joinToString()}"
            }
            return entries.toMap()
        }
    }
}
