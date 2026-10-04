package me.moiclathy.edb.agent.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.event.AgentEventPublisher
import me.moiclathy.edb.agent.model.*
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.runtime.TaskSubmissionOutcome
import me.moiclathy.edb.core.task.workflow.definition.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class AgentProposalSubmissionServiceTest {
    @Test
    fun `approved proposal creates one idempotent definition task and lifecycle audit`() = runBlocking {
        val planning = mockk<AgentPlanningApplicationService>()
        val approvals = mockk<AgentProposalApprovalService>()
        val definitionService = mockk<ProjectWorkflowDefinitionService>()
        val definitions = mockk<ProjectWorkflowDefinitionRepository>()
        val taskService = mockk<WorkflowDefinitionTaskApplicationService>()
        val tasks = mockk<TaskRepository>()
        val audit = mockk<AgentAuditService>(relaxed = true)
        val events = mockk<AgentEventPublisher>(relaxed = true)
        val proposal = AgentWorkflowProposal("proposal", "run", "{}", "Plan", validation = AgentProposalValidation(true), createdAt = NOW)
        val run = AgentRun("run", "session", "correlation", AgentRunStatus.COMPLETED, "fake", NOW, NOW, NOW)
        val session = AgentSession("session", USER_ID, PROJECT_ID, "planner", "1", AgentSessionStatus.ACTIVE, NOW, NOW)
        val decision = AgentProposalDecision("proposal", AgentProposalDecisionStatus.APPROVED, USER_ID, HASH, decidedAt = NOW)
        val definition = ProjectWorkflowDefinition(3, PROJECT_ID, "flow", 1, "{}", HASH, USER_ID, "agent-proposal:proposal", NOW)
        val payload = WorkflowDefinitionTaskPayloadCodec.encode(WorkflowDefinitionTaskPayload(3, 1, HASH))
        val task = Task.pending(PROJECT_ID, WorkflowDefinitionTaskHandler.TASK_TYPE, payload, USER_ID, NOW,
            idempotencyKey = "agent-proposal:proposal").copy(id = 41)

        every { planning.getProposal("proposal", USER_ID) } returns proposal
        every { planning.getRun("run", USER_ID) } returns run
        every { planning.getSession("session", USER_ID) } returns session
        every { approvals.getDecision("proposal", USER_ID) } returns decision
        every { definitionService.create(PROJECT_ID, "{}", USER_ID, "agent-proposal:proposal") } returns definition
        every { tasks.findByIdempotencyKey("agent-proposal:proposal") } returnsMany listOf(null, task)
        every { definitions.findById(3) } returns definition
        coEvery { taskService.submitWithOutcome(any()) } returns TaskSubmissionOutcome(task, true)

        val service = AgentProposalSubmissionService(
            planning, approvals, definitionService, definitions, taskService, tasks, audit, events
        )
        val first = service.submit("proposal", USER_ID)
        val repeated = service.submit("proposal", USER_ID)

        assertEquals(41, first.task.id)
        assertEquals(first, repeated)
        coVerify(exactly = 1) { taskService.submitWithOutcome(match { it.idempotencyKey == "agent-proposal:proposal" }) }
        verify(exactly = 2) { audit.record(any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { events.publish(any()) }
    }

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private val NOW = Instant.parse("2026-08-11T21:00:00Z")
    }
}
