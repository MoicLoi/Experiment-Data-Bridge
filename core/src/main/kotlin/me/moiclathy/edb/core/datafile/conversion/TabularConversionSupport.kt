package me.moiclathy.edb.core.datafile.conversion

import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal fun temporaryConversionInput(
    suffix: String,
    write: (Path) -> Unit
): InputStream {
    val temporary = Files.createTempFile("edb-conversion-", suffix)
    try {
        write(temporary)
        return DeletingFileInputStream(temporary)
    } catch (failure: Exception) {
        runCatching { Files.deleteIfExists(temporary) }
            .exceptionOrNull()
            ?.let(failure::addSuppressed)
        throw failure
    }
}

private class DeletingFileInputStream(
    private val path: Path
) : FilterInputStream(Files.newInputStream(path)) {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        var closeFailure: Exception? = null
        try {
            super.close()
        } catch (failure: Exception) {
            closeFailure = failure
        }
        try {
            Files.deleteIfExists(path)
        } catch (failure: Exception) {
            closeFailure?.addSuppressed(failure) ?: throw failure
        }
        closeFailure?.let { throw it }
    }
}

internal fun convertedFileName(sourceName: String, extension: String): String {
    val stem = sourceName.substringBeforeLast('.', sourceName).ifBlank { "converted" }
    return "$stem.$extension"
}
