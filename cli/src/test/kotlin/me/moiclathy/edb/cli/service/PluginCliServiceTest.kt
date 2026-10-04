package me.moiclathy.edb.cli.service

import me.moiclathy.edb.plugin.api.EdbPlugin
import me.moiclathy.edb.plugin.api.PluginContributions
import me.moiclathy.edb.plugin.api.PluginDescriptor
import me.moiclathy.edb.plugin.api.PluginTaskHandler
import me.moiclathy.edb.plugin.api.PluginTaskRequest
import me.moiclathy.edb.plugin.api.PluginTaskResult
import me.moiclathy.edb.plugin.api.PluginExecutionContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginCliServiceTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `lists and validates a ServiceLoader plugin jar`() {
        val jar = serviceJar("fixture.jar", CliFixturePlugin::class.java)
        val service = PluginCliService()

        val listed = service.list(directory)
        val validated = service.validate(jar)

        assertTrue(listed.failures.isEmpty())
        assertEquals(listOf("cli.fixture"), listed.plugins.map { it.id })
        assertEquals(listOf("cli.fixture.task"), listed.plugins.single().taskTypes)
        assertEquals(listed.plugins.single().id, validated.plugins.single().id)
    }

    private fun serviceJar(name: String, provider: Class<out EdbPlugin>): Path {
        val jarPath = directory.resolve(name)
        JarOutputStream(Files.newOutputStream(jarPath)).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/services/${EdbPlugin::class.java.name}"))
            jar.write("${provider.name}\n".toByteArray())
            jar.closeEntry()
        }
        return jarPath
    }
}

class CliFixturePlugin : EdbPlugin {
    override val descriptor = PluginDescriptor(
        id = "cli.fixture",
        name = "CLI fixture",
        version = "1.0.0",
        apiVersion = "1.0"
    )

    override fun contributions() = PluginContributions(
        taskHandlers = listOf(CliFixtureTaskHandler())
    )
}

class CliFixtureTaskHandler : PluginTaskHandler {
    override val taskType: String = "cli.fixture.task"

    override suspend fun execute(
        request: PluginTaskRequest,
        context: PluginExecutionContext
    ): PluginTaskResult = PluginTaskResult(
        metadata = mapOf("projectId" to request.projectId.toString())
    )
}
