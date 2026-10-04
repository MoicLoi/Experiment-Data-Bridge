package me.moiclathy.edb.cli.command

import com.github.ajalt.clikt.testing.test
import me.moiclathy.edb.cli.createCli
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetentionCommandsTest {
    @Test
    fun `cleanup refuses to call the server without explicit confirmation`() {
        val result = createCli().test("retention cleanup")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("Re-run with --confirm"))
    }

    @Test
    fun `datafile destructive commands require explicit confirmation`() {
        val invocations = listOf(
            "datafile delete 7 81" to "DataFile deletion",
            "datafile orphan cleanup" to "Orphan cleanup",
            "datafile retention cleanup" to "DataFile retention"
        )

        invocations.forEach { (arguments, expected) ->
            val result = createCli().test(arguments)
            assertEquals(2, result.statusCode)
            assertTrue(result.output.contains(expected), result.output)
            assertTrue(result.output.contains("--confirm"), result.output)
        }
    }

    @Test
    fun `Agent retention cleanup requires explicit confirmation`() {
        val result = createCli().test("agent admin retention cleanup")

        assertEquals(2, result.statusCode)
        assertTrue(result.output.contains("Agent retention cleanup"), result.output)
        assertTrue(result.output.contains("--confirm"), result.output)
    }
}
