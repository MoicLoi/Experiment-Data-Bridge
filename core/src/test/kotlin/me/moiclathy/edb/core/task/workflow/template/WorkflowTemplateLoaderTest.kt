package me.moiclathy.edb.core.task.workflow.template

import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrValidator
import java.nio.file.Path
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkflowTemplateLoaderTest {
    @Test
    fun `loads strict versioned workflow templates from directory`() {
        val registry = WorkflowTemplateRegistry()
        val loader = DirectoryWorkflowTemplateLoader(JsonWorkflowTemplateCodec(JsonWorkflowIrCodec(WorkflowIrValidator())), registry)

        val result = loader.load(Path.of("../packaging/demo-v1/examples/workflow-templates"))

        assertEquals(listOf("context-starter@1.0.0", "data-preparation@1.0.0"), result.templateIds)
        assertEquals(2, registry.list().size)
        assertEquals("datafile.convert", registry.require("data-preparation", "1.0.0").workflow.steps.single().type)
        assertFailsWith<IllegalArgumentException> { registry.register(registry.require("context-starter", "1.0.0")) }
    }
}
