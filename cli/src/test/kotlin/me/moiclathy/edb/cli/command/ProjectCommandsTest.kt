package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.testing.test
import me.moiclathy.edb.cli.createCli
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectCommandsTest {
    @Test
    fun `project deletion requires explicit confirmation`() {
        val result = createCli().test("project delete 7")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("Project deletion is permanent"))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `configuration update requires explicit confirmation before reading file`() {
        val result = createCli().test("project config update 7 missing.yaml")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("configuration", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `configuration import requires explicit confirmation before reading file`() {
        val result = createCli().test("project config import 7 missing.edb")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("import", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `configuration restore requires explicit confirmation before contacting server`() {
        val result = createCli().test("project config restore 7 abc1234")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("restore", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `deop requires explicit confirmation`() {
        val result = createCli().test("deop alice")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("operator", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `project update requires at least one changed field`() {
        val result = createCli().test("--token test-token project update 7")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("--name"))
        assertTrue(result.output.contains("--description"))
        assertTrue(result.output.contains("--mode"))
    }

    @Test
    fun `metadata deletion requires explicit confirmation`() {
        val result = createCli().test("project metadata delete 7")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("metadata", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `snapshot deletion requires explicit confirmation`() {
        val result = createCli().test("project snapshot delete 42")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("snapshot", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }

    @Test
    fun `template deletion requires explicit confirmation`() {
        val result = createCli().test("template delete 5")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("template", ignoreCase = true))
        assertTrue(result.output.contains("--confirm"))
    }
}
