package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.testing.test
import me.moiclathy.edb.cli.createCli
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerCommandsTest {
    @Test
    fun `worker command exposes minecraft style subcommands`() {
        val result = createCli().test("worker --help")

        assertEquals(0, result.statusCode)
        listOf("list", "status", "start", "stop", "restart", "logs").forEach {
            assertTrue(result.output.contains(it), result.output)
        }
    }

    @Test
    fun `follow overlap emits only appended content`() {
        assertEquals("line-3\n", appendedLogContent("line-1\nline-2\n", "line-1\nline-2\nline-3\n"))
        assertEquals("line-3\n", appendedLogContent("line-1\nline-2\n", "line-2\nline-3\n"))
        assertEquals("replacement", appendedLogContent("old", "replacement"))
    }

    @Test
    fun `logs rejects an unsafe tail before contacting server`() {
        val result = createCli().test("worker logs r --tail 0")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("between 1 and 10000"), result.output)
    }
}
