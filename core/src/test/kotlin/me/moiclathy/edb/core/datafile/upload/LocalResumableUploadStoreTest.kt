package me.moiclathy.edb.core.datafile.upload

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalResumableUploadStoreTest {
    @Test
    fun `appends chunks at exact offsets and survives store recreation`(@TempDir root:Path){
        val id="123e4567-e89b-12d3-a456-426614174000"
        LocalResumableUploadStore(root).also{store->store.create(id);assertEquals(3,store.append(id,0,ByteArrayInputStream("abc".toByteArray()),6));assertFailsWith<UploadOffsetMismatchException>{store.append(id,0,ByteArrayInputStream("x".toByteArray()),3)}}
        val reopened=LocalResumableUploadStore(root)
        assertEquals(6,reopened.append(id,3,ByteArrayInputStream("def".toByteArray()),3))
        assertEquals("abcdef",reopened.open(id).bufferedReader().use{it.readText()})
        assertEquals(6,reopened.size(id));assertTrue(reopened.delete(id))
    }

    @Test
    fun `rejects chunks larger than remaining upload size`(@TempDir root:Path){
        val id="123e4567-e89b-12d3-a456-426614174001";val store=LocalResumableUploadStore(root);store.create(id)
        assertFailsWith<IllegalArgumentException>{store.append(id,0,ByteArrayInputStream("too-large".toByteArray()),3)}
    }
}
