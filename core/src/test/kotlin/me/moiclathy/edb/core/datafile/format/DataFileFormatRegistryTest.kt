package me.moiclathy.edb.core.datafile.format

import me.moiclathy.edb.core.datafile.model.DataFileFormat
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataFileFormatRegistryTest {
    @Test
    fun `registry resolves extension and media type while detector keeps extension precedence`() {
        val registry = DataFileFormatRegistry()
        val detector = DefaultDataFileFormatDetector(registry)

        assertEquals(DataFileFormat.HDF5, registry.findByExtension(".h5")?.format)
        assertEquals(DataFileFormat.JSON, registry.findByMediaType("application/json; charset=utf-8")?.format)
        assertNull(registry.findByExtension("unknown"))
        assertEquals(
            DetectedDataFileFormat(DataFileFormat.CSV, "text/csv"),
            detector.detect("observations.CSV", "application/json")
        )
        assertEquals(
            DetectedDataFileFormat(DataFileFormat.IMAGE, "image/x-scientific-camera"),
            detector.detect("capture", "image/x-scientific-camera")
        )
    }
}
