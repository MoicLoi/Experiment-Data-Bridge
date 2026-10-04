package me.moiclathy.edb.core.task.workflow.ir

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonWorkflowIrCodecTest {
    private val codec = JsonWorkflowIrCodec()

    @Test
    fun `round trips the complete Workflow IR v1 contract`() {
        val workflow = WorkflowIr(
            id = "analysis-flow",
            version = "1.2.0",
            description = "Analyze a source file",
            inputs = listOf(WorkflowIrInput("source", "source.id", defaultValue = JsonNull)),
            outputs = listOf(WorkflowIrOutput("result", "result.id")),
            steps = listOf(
                WorkflowIrStep(
                    id = "set-result",
                    type = "context.set",
                    version = "1",
                    order = 10,
                    dependsOn = linkedSetOf("prepare"),
                    parameters = buildJsonObject { put("key", "result.id"); put("value", "42") },
                    contextBindings = listOf(WorkflowIrContextBinding("/value", "source.id")),
                    outputBindings = listOf(WorkflowIrOutputBinding("result.id", "published.id")),
                    policy = WorkflowIrStepPolicy(
                        timeoutMs = 5_000,
                        retry = WorkflowIrRetryPolicy(3, 10, 1.5, 100),
                        failureStrategy = WorkflowIrFailureStrategy.CONTINUE
                    )
                ),
                WorkflowIrStep("prepare", "context.set", "1", 0, parameters = buildJsonObject {
                    put("key", "source.id"); put("value", "1")
                })
            )
        )

        assertEquals(workflow, codec.deserialize(codec.serialize(workflow)))
    }

    @Test
    fun `rejects unknown fields and incorrect primitive types`() {
        val unknown = validDocument().replace("\"steps\":", "\"unexpected\":true,\"steps\":")
        val wrongType = validDocument().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")

        assertTrue(assertFailsWith<WorkflowIrJsonException> { codec.deserialize(unknown) }.message!!.contains("unknown property"))
        assertTrue(assertFailsWith<WorkflowIrJsonException> { codec.deserialize(wrongType) }.message!!.contains("must be an integer"))
    }

    private fun validDocument() = """
        {"schemaVersion":1,"id":"flow","version":"1","steps":[
          {"id":"set","type":"context.set","version":"1","order":0,"parameters":{"key":"x","value":"y"}}
        ]}
    """.trimIndent()
}
