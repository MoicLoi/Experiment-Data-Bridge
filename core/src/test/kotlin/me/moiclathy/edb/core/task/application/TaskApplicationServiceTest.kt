package me.moiclathy.edb.core.task.application

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.project.ProjectMode
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.audit.TaskAuditService
import me.moiclathy.edb.core.task.audit.TaskAuditEvent
import me.moiclathy.edb.core.task.audit.TaskAuditEventType
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.pipeline.Pipeline
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineStep
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineTaskHandler
import me.moiclathy.edb.core.task.pipeline.PipelineTaskPayloadCodec
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.runtime.TaskRuntime
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class TaskApplicationServiceTest {
    private val runtime = mockk<TaskRuntime>()
    private val taskRepository = mockk<TaskRepository>()
    private val projectRepository = mockk<ProjectRepository>()
    private val auditService = mockk<TaskAuditService>()
    private val project = Project(
        id = 7,
        name = "Research",
        description = null,
        ownerId = 11,
        mode = ProjectMode.PERSONAL,
        configYaml = null
    )
    private val pipeline = Pipeline(
        id = "import-data",
        steps = listOf(
            PipelineStep("load") { context -> PipelineStepResult.Success(context) }
        )
    )
    private val service = TaskApplicationService(
        runtime = runtime,
        taskRepository = taskRepository,
        pipelineRegistry = PipelineRegistry(listOf(pipeline)),
        auditService = auditService,
        projectRepository = projectRepository,
        permissionChecker = PermissionChecker(),
        clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC)
    )

    @Test
    fun `submits registered pipeline as queued task through runtime`() = runBlocking {
        val captured = slot<Task>()
        every { projectRepository.findById(project.id) } returns project
        coEvery { runtime.submit(capture(captured)) } answers {
            captured.captured.copy(id = 31)
        }

        val submitted = service.submitPipelineTask(
            SubmitPipelineTaskCommand(
                projectId = project.id,
                pipelineId = pipeline.id,
                context = mapOf("source" to "lab.csv"),
                requestedBy = project.ownerId,
                priority = TaskPriority.HIGH
            )
        )

        assertEquals(31, submitted.id)
        assertEquals(PipelineTaskHandler.TASK_TYPE, captured.captured.taskType)
        assertEquals(TaskPriority.HIGH, captured.captured.priority)
        assertEquals(
            "lab.csv",
            PipelineTaskPayloadCodec.decode(captured.captured.configSnapshot).context["source"]
        )
    }

    @Test
    fun `queries and cancels owned task through one boundary`() = runBlocking {
        val task = Task.pending(
            projectId = project.id,
            taskType = PipelineTaskHandler.TASK_TYPE,
            configSnapshot = null,
            triggerUserId = project.ownerId,
            createdAt = Instant.parse("2026-08-10T12:00:00Z")
        ).copy(id = 41)
        every { taskRepository.findById(41) } returns task
        every { projectRepository.findById(project.id) } returns project
        coEvery { runtime.requestCancellation(41, project.ownerId) } returns task

        assertEquals(task, service.getTask(41, project.ownerId))
        assertEquals(task, service.requestCancellation(41, project.ownerId))

        coVerify(exactly = 1) { runtime.requestCancellation(41, project.ownerId) }
    }

    @Test
    fun `returns authorized task page and complete audit timeline`() {
        val task = Task.pending(
            projectId = project.id,
            taskType = PipelineTaskHandler.TASK_TYPE,
            configSnapshot = null,
            triggerUserId = project.ownerId,
            createdAt = Instant.parse("2026-08-10T12:00:00Z")
        ).copy(id = 42)
        val auditEvent = TaskAuditEvent(
            id = 1,
            taskId = 42,
            type = TaskAuditEventType.CREATED,
            fromStatus = null,
            toStatus = TaskStatus.PENDING,
            actorUserId = project.ownerId,
            createdAt = task.createdAt
        )
        every { projectRepository.findById(project.id) } returns project
        every { taskRepository.count(project.id, TaskStatus.PENDING) } returns 21
        every { taskRepository.findPage(project.id, TaskStatus.PENDING, 20, 20) } returns listOf(task)
        every { taskRepository.findById(42) } returns task
        every { auditService.history(42) } returns listOf(auditEvent)

        val page = service.listTasks(
            TaskListQuery(
                projectId = project.id,
                status = TaskStatus.PENDING,
                page = 1,
                size = 20,
                requestedBy = project.ownerId
            )
        )
        val timeline = service.getTaskTimeline(42, project.ownerId)

        assertEquals(21, page.totalElements)
        assertEquals(2, page.totalPages)
        assertEquals(listOf(task), page.items)
        assertEquals(listOf(auditEvent), timeline)
    }
}
