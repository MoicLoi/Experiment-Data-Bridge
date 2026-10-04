package me.moiclathy.edb.core.task.workflow.definition

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.database.table.ProjectsTable
import me.moiclathy.edb.core.database.table.UsersTable
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.pipeline.PipelineExecutor
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
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
import kotlin.test.assertNotNull

class ProjectWorkflowDefinitionIntegrationTest {
    private val now = Instant.parse("2026-08-11T20:00:00Z")
    private lateinit var repository: ProjectWorkflowDefinitionRepository
    private lateinit var definitions: ProjectWorkflowDefinitionService
    private lateinit var codec: JsonWorkflowIrCodec
    private lateinit var compiler: WorkflowIrCompiler

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:workflow-definition-${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ProjectsTable, ProjectWorkflowDefinitionsTable)
            UsersTable.insert { row ->
                row[id] = USER_ID; row[username] = "owner"; row[passwordHash] = "hash"; row[fullName] = "Owner"
                row[role] = UserRole.MEMBER; row[createdAt] = now; row[updatedAt] = now
            }
            ProjectsTable.insert { row ->
                row[id] = PROJECT_ID; row[name] = "Experiment"; row[ownerId] = USER_ID; row[mode] = ProjectMode.PERSONAL
                row[createdAt] = now; row[updatedAt] = now
            }
        }
        repository = MySqlProjectWorkflowDefinitionRepository()
        val handlers = WorkflowStepHandlerRegistry(listOf(SetContextWorkflowStepHandler()))
        val schemas = WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all())
        codec = JsonWorkflowIrCodec()
        compiler = WorkflowIrCompiler(handlers, schemas = schemas)
        definitions = ProjectWorkflowDefinitionService(
            repository, WorkflowIrApplicationService(codec, compiler, PipelineRegistry()), codec,
            Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @Test
    fun `persists immutable versions and handler recompiles exact snapshot`() = runBlocking {
        val first = definitions.create(PROJECT_ID, WORKFLOW, USER_ID, "agent-proposal:proposal-1")
        val duplicate = definitions.create(PROJECT_ID, WORKFLOW, USER_ID, "agent-proposal:proposal-1")
        assertEquals(first, duplicate)
        assertEquals(1, first.version)
        assertEquals(64, first.workflowSha256.length)
        assertEquals(first, repository.findById(assertNotNull(first.id)))

        val task = Task.pending(
            projectId = PROJECT_ID,
            taskType = WorkflowDefinitionTaskHandler.TASK_TYPE,
            configSnapshot = WorkflowDefinitionTaskPayloadCodec.encode(WorkflowDefinitionTaskPayload(
                first.id!!, first.version, first.workflowSha256
            )),
            triggerUserId = USER_ID,
            createdAt = now
        ).copy(id = 41)
        val result = WorkflowDefinitionTaskHandler(repository, codec, compiler, SequentialPipelineExecutor())
            .execute(task, NoCancellation)
        assertEquals("ready", result.metadata["context.result"])
        assertEquals(first.workflowSha256, result.metadata["workflowSha256"])
    }

    private data object NoCancellation : TaskExecutionContext {
        override suspend fun ensureNotCancelled() = Unit
        override suspend fun reportProgress(percent: Int, message: String?): TaskProgress = error("Not used")
    }

    companion object {
        private const val USER_ID = 7
        private const val PROJECT_ID = 11
        private val WORKFLOW = """{"schemaVersion":1,"id":"flow","version":"1","steps":[{"id":"set","type":"context.set","version":"1","order":0,"parameters":{"key":"result","value":"ready"}}]}"""
    }
}
