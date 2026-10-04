package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.serialization.json.jsonPrimitive
import me.moiclathy.edb.core.task.workflow.WorkflowDefinition
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LegacyWorkflowIrAdapterTest {
    @Test
    fun `converts legacy string workflow into ordered IR v1`() {
        val ir = LegacyWorkflowIrAdapter().convert(
            WorkflowDefinition(
                schemaVersion = 1,
                id = "legacy-flow",
                name = "Legacy workflow",
                steps = listOf(
                    WorkflowStepDefinition("load", "context.set", mapOf("value" to "raw.csv")),
                    WorkflowStepDefinition("report", "report.generate", mapOf("definitionId" to "summary"))
                )
            )
        )

        assertEquals(WorkflowIr.CURRENT_SCHEMA_VERSION, ir.schemaVersion)
        assertEquals("legacy-1", ir.version)
        assertEquals("Legacy workflow", ir.description)
        assertEquals(listOf(0, 1), ir.steps.map { it.order })
        assertEquals(emptySet(), ir.steps[0].dependsOn)
        assertEquals(setOf("load"), ir.steps[1].dependsOn)
        assertEquals("summary", ir.steps[1].parameters.getValue("definitionId").jsonPrimitive.content)
    }
}
