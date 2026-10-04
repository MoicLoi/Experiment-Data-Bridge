package me.moiclathy.edb.agent.model

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AgentLifecycleTest {
    @Test
    fun `factory creates scoped session and run with controlled lifecycle`() {
        val instant = Instant.parse("2026-08-11T12:00:00Z")
        val ids = ArrayDeque(listOf("session-1", "run-1"))
        val factory = AgentLifecycleFactory(Clock.fixed(instant, ZoneOffset.UTC), ids::removeFirst)
        val definition = AgentDefinition(
            id = "workflow-planner",
            version = "1",
            description = "Plans Workflow IR",
            systemPrompt = "Create a valid Workflow IR proposal.",
            modelProfileId = "planning-model",
            enabledTools = setOf("searchWorkflowSteps", "validateWorkflowIr"),
            policyId = "planner-read-only"
        )

        val session = factory.createSession(definition, userId = 7, projectId = 9)
        val run = factory.createRun(session, definition.modelProfileId, "correlation-1")
            .transitionTo(AgentRunStatus.PLANNING, instant)
            .transitionTo(AgentRunStatus.VALIDATING, instant)
            .transitionTo(AgentRunStatus.COMPLETED, instant)

        assertEquals("session-1", session.id)
        assertEquals(AgentRunStatus.COMPLETED, run.status)
        assertNotNull(run.completedAt)
        assertFailsWith<IllegalArgumentException> { run.transitionTo(AgentRunStatus.PLANNING, instant) }
        assertFailsWith<IllegalArgumentException> { factory.createRun(session.close(instant), "planning-model", "c-2") }
    }

    @Test
    fun `failed transition requires structured failure`() {
        val instant = Instant.parse("2026-08-11T12:00:00Z")
        val run = AgentRun("run", "session", "correlation", AgentRunStatus.RECEIVED, "model", instant)

        assertFailsWith<IllegalArgumentException> { run.transitionTo(AgentRunStatus.FAILED, instant) }
        val failed = run.transitionTo(
            AgentRunStatus.FAILED,
            instant,
            AgentRunFailure("MODEL_UNAVAILABLE", "Model is unavailable", retryable = true)
        )
        assertEquals("MODEL_UNAVAILABLE", failed.failure?.code)
    }
}
