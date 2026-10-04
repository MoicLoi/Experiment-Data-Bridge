package me.moiclathy.edb.core.datafile.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalDataFileStorageTest {
    @Test
    fun `stages hashes and atomically commits content under a storage key`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        val content = "research-data".toByteArray()
        val staged = storage.stage(ByteArrayInputStream(content))

        assertEquals(content.size.toLong(), staged.sizeBytes)
        assertEquals(sha256(content), staged.sha256)
        assertEquals(1, stagingFileCount(root))

        val stored = staged.use { storage.commit(it, "projects/7/raw/data.csv") }

        assertEquals("local", stored.reference.provider)
        assertEquals("projects/7/raw/data.csv", stored.reference.key)
        assertEquals(sha256(content), stored.sha256)
        assertTrue(storage.exists(stored.reference.key))
        assertEquals("research-data", storage.open(stored.reference.key).bufferedReader().use { it.readText() })
        assertEquals("research-data", root.resolve("projects/7/raw/data.csv").readText())
        assertEquals(0, stagingFileCount(root))
    }

    @Test
    fun `rejects unsafe keys and overwrites and removes failed staging files`(@TempDir root: Path) {
        val storage = LocalDataFileStorage(root)
        storage.store("safe/file.txt", ByteArrayInputStream("first".toByteArray()))

        listOf("../escape.txt", "/absolute.txt", "safe\\windows.txt", ".staging/file.txt").forEach { key ->
            assertFailsWith<InvalidDataFileStorageKeyException> {
                storage.store(key, ByteArrayInputStream("bad".toByteArray()))
            }
        }
        assertFailsWith<DataFileAlreadyExistsException> {
            storage.store("safe/file.txt", ByteArrayInputStream("second".toByteArray()))
        }
        assertEquals("first", root.resolve("safe/file.txt").readText())

        assertFailsWith<DataFileStorageException> {
            storage.stage(object : InputStream() {
                override fun read(): Int = throw IOException("simulated upload failure")
            })
        }
        assertEquals(0, stagingFileCount(root))
        assertFalse(Files.exists(root.parent.resolve("escape.txt")))
    }

    private fun stagingFileCount(root: Path): Long = Files.list(
        root.resolve(LocalDataFileStorage.STAGING_DIRECTORY)
    ).use { it.count() }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
