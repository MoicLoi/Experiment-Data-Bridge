package me.moiclathy.edb.core.datafile.upload

import java.text.Normalizer

fun interface DataFileNameSanitizer {
    fun sanitize(originalFileName: String): String
}

class DefaultDataFileNameSanitizer(
    private val maxLength: Int = DEFAULT_MAX_LENGTH
) : DataFileNameSanitizer {
    init {
        require(maxLength >= 32) { "Sanitized file name maximum length must be at least 32." }
    }

    override fun sanitize(originalFileName: String): String {
        val leaf = originalFileName.replace('\\', '/').substringAfterLast('/')
        val normalized = Normalizer.normalize(leaf, Normalizer.Form.NFKC)
        val cleaned = buildString {
            normalized.forEach { character ->
                append(
                    when {
                        character.isLetterOrDigit() -> character
                        character in SAFE_PUNCTUATION -> character
                        character.isWhitespace() -> '_'
                        else -> '_'
                    }
                )
            }
        }.replace(MULTIPLE_UNDERSCORES, "_")
            .trim { it == '_' || it == '.' || it.isWhitespace() }
            .ifBlank { DEFAULT_FILE_NAME }

        val safeReservedName = if (cleaned.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES) {
            "_$cleaned"
        } else {
            cleaned
        }
        return shorten(safeReservedName)
    }

    private fun shorten(fileName: String): String {
        if (fileName.length <= maxLength) return fileName
        val extension = fileName.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= MAX_PRESERVED_EXTENSION_LENGTH }
        if (extension == null) return fileName.take(maxLength)
        val stemLength = maxLength - extension.length - 1
        return "${fileName.substringBeforeLast('.').take(stemLength)}.$extension"
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 255
        private const val DEFAULT_FILE_NAME = "upload.bin"
        private const val MAX_PRESERVED_EXTENSION_LENGTH = 20
        private val SAFE_PUNCTUATION = setOf('.', '_', '-', '(', ')')
        private val MULTIPLE_UNDERSCORES = Regex("_+")
        private val WINDOWS_RESERVED_NAMES = buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            (1..9).forEach {
                add("COM$it")
                add("LPT$it")
            }
        }
    }
}

