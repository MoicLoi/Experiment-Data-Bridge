package me.moiclathy.edb.agent.application

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.model.AgentProposalDecisionStatus
import me.moiclathy.edb.agent.model.AgentProposalValidation
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentSession
import me.moiclathy.edb.agent.model.AgentSessionStatus
import me.moiclathy.edb.agent.model.AgentWorkflowProposal
import me.moiclathy.edb.agent.persistence.*
import me.moiclathy.edb.agent.tools.WorkflowPlanningTools
import me.moiclathy.edb.core.database.table.ProjectsTable
import me.moiclathy.edb.core.database.table.UsersTable
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AgentProposalApprovalServiceTest {
    private val now = Instant.parse("2026-08-11T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var repository: AgentRepository
    private lateinit var service: AgentProposalApprovalService

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:approval-${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ProjectsTable, AgentSessionsTable, AgentRunsTable,
                AgentWorkflowProposalsTable, AgentProposalDecisionsTable, AgentAuditEventsTable)
            UsersTable.insert { row ->
                row[id] = USER_ID; row[username] = "owner"; row[passwordHash] = "hash"; row[fullName] = "Owner"
                row[role] = UserRole.MEMBER; row[createdAt] = now; row[updatedAt] = now
            }
            ProjectsTable.insert { row ->
                row[id] = PROJECT_ID; row[name] = "Experiment"; row[ownerId] = USER_ID; row[mode] = ProjectMode.PERSONAL
                row[createdAt] = now; row[updatedAt] = now
            }
        }
        repository = MySqlAgentRepository()
        repository.saveSession(AgentSession("session", USER_ID, PROJECT_ID, "workflow-planner", "1", AgentSessionStatus.ACTIVE, now, now))
        repository.saveRun(AgentRun("run", "session", "correlation", AgentRunStatus.COMPLETED, "fake", now, now, now))
        repository.saveProposal(AgentWorkflowProposal(
            "proposal", "run", VALID_WORKFLOW, "Set result", validation = AgentProposalValidation(true, listOf("set")), createdAt = now
        ))
        val handlers = WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler()))
        val schemas = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())
        val codec = JsonWorkflowIrCodec()
        val tools = WorkflowPlanningTools(
            WorkflowStepCatalogService(handlers, schemas),
            WorkflowIrApplicationService(codec, WorkflowIrCompiler(handlers, schemas = schemas), PipelineRegistry()),
            codec
        )
        service = AgentProposalApprovalService(repository, ExpectedAuthorization, tools, AgentAuditService(repository, clock), clock = clock)
    }

    @Test
    fun `approval is revalidated hashed persisted audited and idempotent`() = runBlocking {
        val first = service.approve("proposal", USER_ID)
        val second = service.approve("proposal", USER_ID)

        assertEquals(first, second)
        assertEquals(AgentProposalDecisionStatus.APPROVED, first.status)
        assertEquals(64, first.workflowSha256.length)
        assertEquals(first, repository.findProposalDecision("proposal"))
        assertEquals(1, repository.findAuditByRun("run").count { it.type == AgentAuditEventType.PROPOSAL_APPROVED })
    }

    @Test
    fun `opposite decision is rejected and rejection requires a reason`() = runBlocking {
        val rejected = service.reject("proposal", USER_ID, "Parameters require review")
        assertEquals(AgentProposalDecisionStatus.REJECTED, rejected.status)
        assertEquals("Parameters require review", rejected.reason)
        assertFailsWith<AgentProposalDecisionConflictException> { service.approve("proposal", USER_ID) }
        assertTrue(repository.findAuditByRun("run").any { it.type == AgentAuditEventType.PROPOSAL_REJECTED })
    }

    private data object ExpectedAuthorization : AgentAuthorization {
        override fun requireReadable(userId: Int, projectId: Int) = require(userId == USER_ID && projectId == PROJECT_ID)
        override fun requireWritable(userId: Int, projectId: Int) = requireReadable(userId, projectId)
    }

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private val VALID_WORKFLOW = """{"schemaVersion":1,"id":"flow","version":"1","steps":[{"id":"set","type":"context.set","version":"1","order":0,"parameters":{"key":"result","value":"ready"}}]}"""
    }
}
