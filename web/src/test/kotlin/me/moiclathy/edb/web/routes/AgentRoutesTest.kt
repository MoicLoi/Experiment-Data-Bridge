package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import me.moiclathy.edb.agent.application.AgentPlanningApplicationService
import me.moiclathy.edb.agent.application.AgentAsyncPlanningService
import me.moiclathy.edb.agent.application.AgentProposalApprovalService
import me.moiclathy.edb.agent.application.AgentProposalSubmission
import me.moiclathy.edb.agent.application.AgentProposalSubmissionService
import me.moiclathy.edb.agent.application.AgentPlanningResult
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentDefinitionRegistry
import me.moiclathy.edb.agent.model.AgentProposalValidation
import me.moiclathy.edb.agent.model.AgentProposalDecision
import me.moiclathy.edb.agent.model.AgentProposalDecisionStatus
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentSessionStatus
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.model.AgentPolicyRegistry
import me.moiclathy.edb.agent.persistence.AgentAuditEvent
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.agent.persistence.AgentMessageRecord
import me.moiclathy.edb.agent.provider.AgentChatRole
import me.moiclathy.edb.agent.application.AgentTokenQuotaSnapshot
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaKey
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaScope
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaUsage
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.policy.AgentPolicy
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.workflow.definition.ProjectWorkflowDefinition
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRoutesTest {
    @Test
    fun `authenticated user creates session plans and queries persisted Agent resources`() = testApplication {
        val service = mockk<AgentPlanningApplicationService>()
        val asyncPlanning = mockk<AgentAsyncPlanningService>()
        val approvals = mockk<AgentProposalApprovalService>()
        val submissions = mockk<AgentProposalSubmissionService>()
        val definition = definition()
        val policy = AgentPolicy("read-only", setOf(AgentCapability.DISCOVER_STEPS, AgentCapability.VALIDATE_WORKFLOW))
        val session = session()
        val run = run()
        val proposal = proposal()
        val audit = AgentAuditEvent(1, session.id, run.id, USER_ID, PROJECT_ID, AgentAuditEventType.RUN_COMPLETED, run.status, createdAt = NOW)
        coEvery { service.createSession(definition, USER_ID, PROJECT_ID) } returns session
        every { service.getSession(session.id, USER_ID) } returns session
        coEvery { asyncPlanning.start(session.id, USER_ID, definition, policy, any(), 2) } returns run
        coEvery { asyncPlanning.cancel(run.id, USER_ID) } returns run
        every { service.getRun(run.id, USER_ID) } returns run
        every { service.getRunProposals(run.id, USER_ID) } returns listOf(proposal)
        every { service.getProposal(proposal.id, USER_ID) } returns proposal
        every { service.getRunAudit(run.id, USER_ID) } returns listOf(audit)
        every { service.getSessionMessages(session.id, USER_ID) } returns listOf(
            AgentMessageRecord(9, session.id, run.id, AgentChatRole.USER, "Plan an experiment", memoryEligible = true, createdAt = NOW)
        )
        every { service.getTokenQuota(PROJECT_ID, USER_ID) } returns AgentTokenQuotaSnapshot(
            true, LocalDate.parse("2026-08-11"), listOf(
                AgentTokenQuotaUsage(AgentTokenQuotaKey(AgentTokenQuotaScope.USER, USER_ID), LocalDate.parse("2026-08-11"), 120, 1000)
            )
        )
        coEvery { approvals.approve(proposal.id, USER_ID) } returns AgentProposalDecision(
            proposal.id, AgentProposalDecisionStatus.APPROVED, USER_ID, "a".repeat(64), decidedAt = NOW
        )
        val definitionRecord = ProjectWorkflowDefinition(
            3, PROJECT_ID, "flow", 1, "{}", "a".repeat(64), USER_ID, "agent-proposal:${proposal.id}", NOW
        )
        val submittedTask = Task.pending(PROJECT_ID, "workflow-definition", "{}", USER_ID, NOW).copy(id = 41)
        coEvery { submissions.submit(proposal.id, USER_ID, emptyMap()) } returns
            AgentProposalSubmission(proposal.id, definitionRecord, submittedTask)

        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") { verifier(JWT.require(ALGORITHM).build()); validate { JWTPrincipal(it.payload) } }
            }
            routing { agentRoutes(service, AgentDefinitionRegistry(listOf(definition)), AgentPolicyRegistry(listOf(policy)), approvals, submissions, asyncPlanning) }
        }

        val created = client.post("/api/v1/projects/$PROJECT_ID/agent-sessions") {
            bearerAuth(token()); contentType(ContentType.Application.Json); setBody("""{"agentId":"workflow-planner","agentVersion":"1"}""")
        }
        val planned = client.post("/api/v1/agent-sessions/${session.id}/messages") {
            bearerAuth(token()); contentType(ContentType.Application.Json); setBody("""{"message":"Plan an experiment"}""")
        }
        val runResponse = client.get("/api/v1/agent-runs/${run.id}") { bearerAuth(token()) }
        val cancelResponse = client.post("/api/v1/agent-runs/${run.id}/cancel") { bearerAuth(token()) }
        val proposals = client.get("/api/v1/agent-runs/${run.id}/proposals") { bearerAuth(token()) }
        val proposalResponse = client.get("/api/v1/agent-proposals/${proposal.id}") { bearerAuth(token()) }
        val approvalResponse = client.post("/api/v1/agent-proposals/${proposal.id}/approve") { bearerAuth(token()) }
        val submissionResponse = client.post("/api/v1/agent-proposals/${proposal.id}/submit") {
            bearerAuth(token()); contentType(ContentType.Application.Json); setBody("{}")
        }
        val timeline = client.get("/api/v1/agent-runs/${run.id}/audit") { bearerAuth(token()) }
        val messages = client.get("/api/v1/agent-sessions/${session.id}/messages") { bearerAuth(token()) }
        val quota = client.get("/api/v1/projects/$PROJECT_ID/agent-token-quota") { bearerAuth(token()) }
        val unauthorized = client.get("/api/v1/agent-runs/${run.id}")

        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals(HttpStatusCode.Accepted, planned.status)
        assertEquals(HttpStatusCode.OK, runResponse.status)
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        assertEquals(HttpStatusCode.OK, proposals.status)
        assertEquals(HttpStatusCode.OK, proposalResponse.status)
        assertEquals(HttpStatusCode.OK, approvalResponse.status)
        assertEquals(HttpStatusCode.Accepted, submissionResponse.status)
        assertEquals(HttpStatusCode.OK, timeline.status)
        assertEquals(HttpStatusCode.OK, messages.status)
        assertEquals(HttpStatusCode.OK, quota.status)
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertTrue(planned.bodyAsText().contains("\"run\""))
        assertTrue(timeline.bodyAsText().contains("RUN_COMPLETED"))
        assertTrue(messages.bodyAsText().contains("Plan an experiment"))
        assertTrue(quota.bodyAsText().contains("\"remainingTokens\":880"))
        assertTrue(approvalResponse.bodyAsText().contains("\"status\":\"APPROVED\""))
        assertTrue(submissionResponse.bodyAsText().contains("\"definitionId\":3"))
        coVerify { asyncPlanning.start(session.id, USER_ID, definition, policy, "Plan an experiment", 2) }
    }

    private fun token() = JWT.create().withSubject("scientist").withClaim("userId", USER_ID).sign(ALGORITHM)
    private fun definition() = AgentDefinition(
        "workflow-planner", "1", "Planner", "Plan Workflow IR", "fake", emptySet(), "read-only"
    )
    private fun session() = AgentSession("session-1", USER_ID, PROJECT_ID, "workflow-planner", "1", AgentSessionStatus.ACTIVE, NOW, NOW)
    private fun run() = AgentRun("run-1", "session-1", "correlation-1", AgentRunStatus.COMPLETED, "fake", NOW, NOW, NOW)
    private fun proposal() = AgentWorkflowProposal(
        "proposal-1", "run-1", "{}", "Validated proposal", validation = AgentProposalValidation(true), createdAt = NOW
    )

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private val NOW = Instant.parse("2026-08-11T16:00:00Z")
        private val ALGORITHM = Algorithm.HMAC256("agent-route-test-secret")
    }
}
