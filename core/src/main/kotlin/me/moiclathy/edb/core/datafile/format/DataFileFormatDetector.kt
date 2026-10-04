package me.moiclathy.edb.core.datafile.format

import me.moiclathy.edb.core.datafile.model.DataFileFormat

data class DetectedDataFileFormat(val format: DataFileFormat, val mediaType: String)

fun interface DataFileFormatDetector {
    fun detect(fileName: String, declaredMediaType: String?): DetectedDataFileFormat
}

/** Extension-first detection; a declared media type is only used when the extension is unknown. */
class DefaultDataFileFormatDetector(
    private val registry: DataFileFormatRegistry = DataFileFormatRegistry()
) : DataFileFormatDetector {
    override fun detect(fileName: String, declaredMediaType: String?): DetectedDataFileFormat {
        val extension = fileName.substringAfterLast('.', "")
        registry.findByExtension(extension)?.let {
            return DetectedDataFileFormat(it.format, it.mediaType)
        }
        val normalizedMediaType = declaredMediaType
            ?.substringBefore(';')?.trim()?.lowercase()?.takeIf { MEDIA_TYPE.matches(it) }
        normalizedMediaType?.let { mediaType ->
            registry.findByMediaType(mediaType)?.let {
                return DetectedDataFileFormat(it.format, it.mediaType)
            }
            if (mediaType.startsWith("image/")) return DetectedDataFileFormat(DataFileFormat.IMAGE, mediaType)
            if (mediaType.startsWith("video/")) return DetectedDataFileFormat(DataFileFormat.VIDEO, mediaType)
            return DetectedDataFileFormat(DataFileFormat.BINARY, mediaType)
        }
        return DetectedDataFileFormat(DataFileFormat.BINARY, "application/octet-stream")
    }

    companion object {
        private val MEDIA_TYPE = Regex("^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$")
    }
}
