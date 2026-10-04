package me.moiclathy.edb.core.datafile.report

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineSystemContextKeys
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReportTaskAdaptersTest {
    @Test
    fun `task and workflow adapters generate a registered report artifact`() = runBlocking {
        val definition = ReportDefinition(
            id = "summary",
            title = "Summary",
            generatorId = "summary-generator",
            outputFormat = DataFileFormat.PDF,
            outputFileName = "summary.pdf"
        )
        val definitions = ReportDefinitionRegistry(listOf(definition))
        val artifactService = mockk<ReportArtifactService>()
        coEvery { artifactService.generate(any()) } returns reportArtifact()
        val progress = mutableListOf<Int>()
        val taskHandler = ReportTaskHandler(definitions, artifactService)
        val task = Task.pending(
            projectId = 7,
            taskType = ReportTaskHandler.TASK_TYPE,
            configSnapshot = ReportTaskPayloadCodec.encode(
                ReportTaskPayload("summary", setOf(1), mapOf("title" to "Experiment"))
            ),
            triggerUserId = 11,
            createdAt = Instant.parse("2026-08-10T12:00:00Z")
        ).copy(id = 81, status = TaskStatus.RUNNING)

        val result = taskHandler.execute(task, executionContext(progress))
        assertEquals(listOf(10, 100), progress)
        assertEquals("2", result.metadata["dataFileId"])

        val workflowHandler = ReportWorkflowStepHandler(definitions, artifactService)
        val workflowResult = workflowHandler.execute(
            WorkflowStepDefinition(
                id = "report",
                type = ReportWorkflowStepHandler.TYPE,
                config = mapOf(
                    "definitionId" to "summary",
                    "sourceDataFileIdsContext" to "sourceId",
                    "parameter.title" to "Experiment"
                )
            ),
            PipelineContext(
                mapOf(
                    PipelineSystemContextKeys.TASK_ID to 82,
                    PipelineSystemContextKeys.PROJECT_ID to 7,
                    PipelineSystemContextKeys.TRIGGER_USER_ID to 11,
                    "sourceId" to "1"
                )
            )
        )
        val success = assertIs<PipelineStepResult.Success>(workflowResult)
        assertEquals(2, success.context[ReportWorkflowStepHandler.DEFAULT_RESULT_CONTEXT_KEY])
    }

    private fun executionContext(progress: MutableList<Int>) = object : TaskExecutionContext {
        override suspend fun ensureNotCancelled() = Unit
        override suspend fun reportProgress(percent: Int, message: String?): TaskProgress {
            progress += percent
            return TaskProgress(percent, message, Instant.parse("2026-08-10T12:00:00Z"))
        }
    }

    private fun reportArtifact() = DataFile(
        id = 2,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.REPORT,
        format = DataFileFormat.PDF,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "summary.pdf",
        mediaType = "application/pdf",
        storage = DataFileStorageReference("local", "projects/7/data/report/summary.pdf"),
        provenance = DataFileProvenance(DataFileOrigin.REPORT_GENERATION, setOf(1), 81),
        createdAt = Instant.parse("2026-08-10T12:01:00Z")
    )
}
