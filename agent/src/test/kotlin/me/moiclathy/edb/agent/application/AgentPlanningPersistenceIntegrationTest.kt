package me.moiclathy.edb.agent.application

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentLifecycleFactory
import me.moiclathy.edb.agent.model.AgentRunStatus
import me.moiclathy.edb.agent.model.AgentTokenUsage
import me.moiclathy.edb.agent.persistence.AgentAuditEventType
import me.moiclathy.edb.agent.persistence.AgentAuditEventsTable
import me.moiclathy.edb.agent.persistence.AgentMessagesTable
import me.moiclathy.edb.agent.persistence.AgentRepository
import me.moiclathy.edb.agent.persistence.AgentRunsTable
import me.moiclathy.edb.agent.persistence.AgentSessionsTable
import me.moiclathy.edb.agent.persistence.AgentToolCallsTable
import me.moiclathy.edb.agent.persistence.AgentWorkflowProposalsTable
import me.moiclathy.edb.agent.persistence.MySqlAgentRepository
import me.moiclathy.edb.agent.persistence.PersistentWorkflowPlanningObserver
import me.moiclathy.edb.agent.planning.WorkflowPlanningAgent
import me.moiclathy.edb.agent.planning.WorkflowPlanningException
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.policy.AgentPolicy
import me.moiclathy.edb.agent.provider.AgentModelResponse
import me.moiclathy.edb.agent.provider.AgentRequestedToolCall
import me.moiclathy.edb.agent.provider.AgentChatModel
import me.moiclathy.edb.agent.support.ScriptedAgentChatModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPlanningPersistenceIntegrationTest {
    private val now = Instant.parse("2026-08-11T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var repository: AgentRepository

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:agent-${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(
                UsersTable,
                ProjectsTable,
                AgentSessionsTable,
                AgentRunsTable,
                AgentMessagesTable,
                AgentToolCallsTable,
                AgentWorkflowProposalsTable,
                AgentAuditEventsTable
            )
            UsersTable.insert { row ->
                row[id] = USER_ID; row[username] = "scientist"; row[passwordHash] = "hash"; row[fullName] = "Scientist"
                row[role] = UserRole.MEMBER; row[createdAt] = now; row[updatedAt] = now
            }
            ProjectsTable.insert { row ->
                row[id] = PROJECT_ID; row[name] = "Experiment"; row[ownerId] = USER_ID; row[mode] = ProjectMode.PERSONAL
                row[createdAt] = now; row[updatedAt] = now
            }
        }
        repository = MySqlAgentRepository()
    }

    @Test
    fun `persists delegated user planning lifecycle messages tools proposal and audit`() = runBlocking {
        val model = ScriptedAgentChatModel(
            listOf(
                AgentModelResponse(
                    toolCalls = listOf(AgentRequestedToolCall("catalog", WorkflowPlanningTools.LIST_STEPS, JsonObject(emptyMap()))),
                    tokenUsage = AgentTokenUsage(10, 2)
                ),
                AgentModelResponse(
                    toolCalls = listOf(AgentRequestedToolCall("schema", WorkflowPlanningTools.GET_STEP_SCHEMA, buildJsonObject {
                        put("type", "context.set"); put("version", "1")
                    })),
                    tokenUsage = AgentTokenUsage(8, 2)
                ),
                AgentModelResponse(structuredOutput = proposal(INVALID_WORKFLOW, "Initial"), tokenUsage = AgentTokenUsage(12, 4)),
                AgentModelResponse(structuredOutput = proposal(VALID_WORKFLOW, "Corrected"), tokenUsage = AgentTokenUsage(10, 3))
            )
        )
        val service = service(model, ArrayDeque(listOf("session-1", "run-1")))
        val session = service.createSession(DEFINITION, USER_ID, PROJECT_ID)
        val result = service.plan(session.id, USER_ID, DEFINITION, POLICY, "Set result to ready")

        val persistedRun = assertNotNull(repository.findRun(result.run.id))
        assertEquals(AgentRunStatus.COMPLETED, persistedRun.status)
        assertEquals(51, persistedRun.tokenUsage?.totalTokens)
        assertEquals(9, repository.findMessagesByRun(result.run.id).size)
        assertEquals(2, repository.findToolCallsByRun(result.run.id).size)
        assertEquals(result.proposal, repository.findProposal(result.proposal.id))
        val audit = repository.findAuditByRun(result.run.id)
        assertTrue(audit.any { it.type == AgentAuditEventType.VALIDATION_FAILED })
        assertTrue(audit.any { it.type == AgentAuditEventType.REPAIR_REQUESTED })
        assertTrue(audit.any { it.type == AgentAuditEventType.PROPOSAL_CREATED })
        assertTrue(audit.all { it.actorUserId == USER_ID && it.projectId == PROJECT_ID })
        assertEquals(AgentAuditEventType.SESSION_CREATED, repository.findAuditBySession(session.id).first().type)
        assertFailsWith<AgentSessionAccessDeniedException> {
            service.plan(session.id, USER_ID + 1, DEFINITION, POLICY, "Try another user session")
        }
    }

    @Test
    fun `persists structured run failure when repair is exhausted`() = runBlocking {
        val model = ScriptedAgentChatModel(listOf(AgentModelResponse(structuredOutput = proposal(INVALID_WORKFLOW, "Invalid"))))
        val service = service(model, ArrayDeque(listOf("session-failed", "run-failed")))
        val session = service.createSession(DEFINITION, USER_ID, PROJECT_ID)

        assertFailsWith<WorkflowPlanningException> {
            service.plan(session.id, USER_ID, DEFINITION, POLICY, "Create an invalid workflow", maxRepairAttempts = 0)
        }

        val run = assertNotNull(repository.findRun("run-failed"))
        assertEquals(AgentRunStatus.FAILED, run.status)
        assertEquals("AGENT_WORKFLOW_REPAIR_EXHAUSTED", run.failure?.code)
        assertTrue(repository.findProposalsByRun(run.id).isEmpty())
        assertEquals(AgentAuditEventType.RUN_FAILED, repository.findAuditByRun(run.id).last().type)
    }

    @Test
    fun `records model failure separately from terminal run failure`() = runBlocking {
        val service = service(AgentChatModel { error("Provider offline") }, ArrayDeque(listOf("session-model", "run-model")))
        val session = service.createSession(DEFINITION, USER_ID, PROJECT_ID)

        val failure = assertFailsWith<WorkflowPlanningException> {
            service.plan(session.id, USER_ID, DEFINITION, POLICY, "Plan while provider is offline")
        }

        assertEquals("AGENT_MODEL_FAILED", failure.code)
        assertEquals("AGENT_MODEL_FAILED", repository.findRun("run-model")?.failure?.code)
        val types = repository.findAuditByRun("run-model").map { it.type }
        assertTrue(AgentAuditEventType.MODEL_FAILED in types)
        assertEquals(AgentAuditEventType.RUN_FAILED, types.last())
    }

    private fun service(model: AgentChatModel, ids: ArrayDeque<String>): AgentPlanningApplicationService {
        val handlers = WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler()))
        val schemas = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())
        val pipelines = PipelineRegistry()
        val codec = JsonWorkflowIrCodec()
        val tools = WorkflowPlanningTools(
            WorkflowStepCatalogService(handlers, schemas),
            WorkflowIrApplicationService(codec, WorkflowIrCompiler(handlers, schemas = schemas), pipelines),
            codec
        )
        val audit = AgentAuditService(repository, clock)
        val planner = WorkflowPlanningAgent(
            model,
            tools,
            observer = PersistentWorkflowPlanningObserver(repository, audit),
            clock = clock,
            proposalIdSupplier = { "proposal-1" }
        )
        return AgentPlanningApplicationService(
            planner,
            repository,
            audit,
            authorization = AllowExpectedUserAuthorization,
            lifecycleFactory = AgentLifecycleFactory(clock, ids::removeFirst),
            clock = clock,
            correlationIdSupplier = { "correlation-${ids.firstOrNull() ?: "done"}" }
        )
    }

    private fun proposal(workflow: String, explanation: String) = buildJsonObject {
        put("workflow", Json.parseToJsonElement(workflow)); put("explanation", explanation)
    }

    private data object AllowExpectedUserAuthorization : AgentAuthorization {
        override fun requireReadable(userId: Int, projectId: Int) {
            require(userId == USER_ID && projectId == PROJECT_ID)
        }
        override fun requireWritable(userId: Int, projectId: Int) = requireReadable(userId, projectId)
    }

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private val DEFINITION = AgentDefinition(
            "workflow-planner", "1", "Plans Workflow IR", "Plan a strict Workflow IR.", "fake", setOf(
                WorkflowPlanningTools.LIST_STEPS, WorkflowPlanningTools.GET_STEP_SCHEMA, WorkflowPlanningTools.VALIDATE_WORKFLOW
            ), "read-only"
        )
        private val POLICY = AgentPolicy(
            "read-only",
            setOf(AgentCapability.DISCOVER_STEPS, AgentCapability.VALIDATE_WORKFLOW),
            allowedToolNames = DEFINITION.enabledTools,
            maxToolCallsPerRun = 10
        )
        private val INVALID_WORKFLOW = """{"schemaVersion":1,"id":"flow","version":"1","steps":[{"id":"set","type":"context.set","version":"1","order":0,"parameters":{"key":"result"}}]}"""
        private val VALID_WORKFLOW = """{"schemaVersion":1,"id":"flow","version":"1","steps":[{"id":"set","type":"context.set","version":"1","order":0,"parameters":{"key":"result","value":"ready"}}]}"""
    }
}
