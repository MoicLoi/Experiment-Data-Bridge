package me.moiclathy.edb.core.plugin

import me.moiclathy.edb.plugin.api.EdbPlugin
import me.moiclathy.edb.plugin.api.PluginContext
import me.moiclathy.edb.plugin.api.PluginContributions
import me.moiclathy.edb.plugin.api.PluginDescriptor
import me.moiclathy.edb.plugin.api.PluginTaskHandler
import me.moiclathy.edb.plugin.api.PluginTaskRequest
import me.moiclathy.edb.plugin.api.PluginTaskResult
import me.moiclathy.edb.plugin.api.PluginExecutionContext
import me.moiclathy.edb.plugin.api.PluginWorkflowStepHandler
import me.moiclathy.edb.plugin.api.PluginWorkflowStepRequest
import me.moiclathy.edb.plugin.api.PluginWorkflowStepResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals

class DirectoryPluginLoaderTest {
    @TempDir
    lateinit var directory: Path

    @BeforeEach
    fun resetFixture() {
        ValidPlugin.initialized.set(0)
        ValidPlugin.closed.set(0)
    }

    @Test
    fun `loads one ServiceLoader plugin from a jar and closes its lifecycle`() {
        serviceJar("valid.jar", ValidPlugin::class.java)

        val result = DirectoryPluginLoader(context = PluginContext(mapOf("environment" to "test"))).load(directory)

        assertEquals(listOf("example.valid"), result.loaded.map { it.descriptor.id })
        assertEquals(emptyList(), result.failures)
        assertEquals(1, ValidPlugin.initialized.get())
        result.close()
        assertEquals(1, ValidPlugin.closed.get())
    }

    @Test
    fun `isolates missing provider incompatible API and duplicate plugin failures`() {
        emptyJar("00-empty.jar")
        serviceJar("10-valid.jar", ValidPlugin::class.java)
        serviceJar("20-duplicate.jar", ValidPlugin::class.java)
        serviceJar("30-incompatible.jar", IncompatiblePlugin::class.java)

        val result = DirectoryPluginLoader().load(directory)

        assertEquals(1, result.loaded.size)
        assertEquals(
            setOf(
                PluginLoadFailureCode.NO_PROVIDER,
                PluginLoadFailureCode.DUPLICATE_PLUGIN,
                PluginLoadFailureCode.API_INCOMPATIBLE
            ),
            result.failures.mapTo(mutableSetOf()) { it.code }
        )
        result.close()
    }

    private fun serviceJar(name: String, provider: Class<out EdbPlugin>) {
        JarOutputStream(Files.newOutputStream(directory.resolve(name))).use { jar ->
            jar.putNextEntry(JarEntry("META-INF/services/${EdbPlugin::class.java.name}"))
            jar.write("${provider.name}\n".toByteArray())
            jar.closeEntry()
        }
    }

    private fun emptyJar(name: String) {
        JarOutputStream(Files.newOutputStream(directory.resolve(name))).use { }
    }
}

class ValidPlugin : EdbPlugin {
    override val descriptor = PluginDescriptor(
        id = "example.valid",
        name = "Valid fixture",
        version = "1.0.0",
        apiVersion = "1.0"
    )

    override fun initialize(context: PluginContext) {
        check(context.properties["environment"] == "test" || context.properties.isEmpty())
        initialized.incrementAndGet()
    }

    override fun contributions() = PluginContributions(
        taskHandlers = listOf(FixtureTaskHandler()),
        workflowStepHandlers = listOf(FixtureWorkflowHandler())
    )

    override fun close() {
        closed.incrementAndGet()
    }

    companion object {
        val initialized = AtomicInteger()
        val closed = AtomicInteger()
    }
}

class IncompatiblePlugin : EdbPlugin {
    override val descriptor = PluginDescriptor(
        id = "example.future",
        name = "Future fixture",
        version = "1.0.0",
        apiVersion = "2.0"
    )
}

class FixtureTaskHandler : PluginTaskHandler {
    override val taskType = "example.task"

    override suspend fun execute(
        request: PluginTaskRequest,
        context: PluginExecutionContext
    ): PluginTaskResult {
        context.ensureNotCancelled()
        context.reportProgress(100, "complete")
        return PluginTaskResult(metadata = mapOf("projectId" to request.projectId.toString()))
    }
}

class FixtureWorkflowHandler : PluginWorkflowStepHandler {
    override val type = "example.step"

    override suspend fun execute(request: PluginWorkflowStepRequest): PluginWorkflowStepResult =
        PluginWorkflowStepResult.Success(mapOf("plugin.output" to request.config.getValue("value")))
}
