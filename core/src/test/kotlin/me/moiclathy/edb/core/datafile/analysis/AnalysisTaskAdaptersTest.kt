package me.moiclathy.edb.core.datafile.analysis

import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.format.DataFileFormatRegistry
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileNameSanitizer
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileStorageKeyGenerator
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import me.moiclathy.edb.core.task.execution.TaskExecutionContext
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineSystemContextKeys
import me.moiclathy.edb.core.task.progress.TaskProgress
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnalysisTaskAdaptersTest {
    @Test
    fun `registered external handler runs through task and workflow adapters`(@TempDir root: Path) = runBlocking {
        val storage = LocalDataFileStorage(root)
        val stored = storage.store("projects/7/data/source.csv", ByteArrayInputStream("x,y\n1,2".toByteArray()))
        val repository = InMemoryRepository(source(stored.reference))
        val handler = object : AnalysisHandler {
            override val id = "python-summary"
            override val analysisType = "statistical-summary"
            override val tool = "Python"
            override val outputFormat = DataFileFormat.JSON

            override suspend fun analyze(input: AnalysisHandlerInput): AnalysisHandlerOutput {
                assertEquals("x,y\n1,2", input.sources.single().content.bufferedReader().readText())
                return AnalysisHandlerOutput(
                    fileName = "summary.json",
                    content = ByteArrayInputStream("{\"rows\":1}".toByteArray()),
                    mediaType = "application/json"
                )
            }
        }
        val artifactService = AnalysisArtifactService(
            repository,
            storage,
            DataFileFormatRegistry(),
            DefaultDataFileNameSanitizer(),
            DefaultDataFileStorageKeyGenerator { "analysis-${repository.nextId()}" },
            Clock.fixed(Instant.parse("2026-08-10T15:00:00Z"), ZoneOffset.UTC)
        )
        val executionService = AnalysisExecutionService(
            AnalysisHandlerRegistry(listOf(handler)), repository, storage, artifactService
        )
        val progress = mutableListOf<Int>()
        val task = Task.pending(
            projectId = 7,
            taskType = AnalysisTaskHandler.TASK_TYPE,
            configSnapshot = AnalysisTaskPayloadCodec.encode(
                AnalysisTaskPayload(handler.id, setOf(1), mapOf("method" to "mean"))
            ),
            triggerUserId = 11,
            createdAt = Instant.parse("2026-08-10T14:00:00Z")
        ).copy(id = 81, status = TaskStatus.RUNNING)

        val taskResult = AnalysisTaskHandler(executionService).execute(task, executionContext(progress))
        assertEquals(listOf(10, 100), progress)
        assertEquals("2", taskResult.metadata["dataFileId"])
        assertEquals("3", taskResult.metadata["provenanceReportDataFileId"])
        assertEquals(DataFileOrigin.ANALYSIS, repository.findById(2)?.provenance?.origin)
        assertEquals(DataFileKind.AUXILIARY, repository.findById(3)?.kind)

        val workflowResult = AnalysisWorkflowStepHandler(executionService).execute(
            WorkflowStepDefinition(
                id = "analyze",
                type = AnalysisWorkflowStepHandler.TYPE,
                config = mapOf(
                    "handlerId" to handler.id,
                    "sourceDataFileIdsContext" to "convertedDataFileId",
                    "parameter.method" to "median"
                )
            ),
            PipelineContext(
                mapOf(
                    PipelineSystemContextKeys.TASK_ID to 82,
                    PipelineSystemContextKeys.PROJECT_ID to 7,
                    PipelineSystemContextKeys.TRIGGER_USER_ID to 11,
                    "convertedDataFileId" to "1"
                )
            )
        )
        val success = assertIs<PipelineStepResult.Success>(workflowResult)
        assertEquals(4, success.context[AnalysisWorkflowStepHandler.DEFAULT_RESULT_CONTEXT_KEY])
        assertEquals(5, success.context["${AnalysisWorkflowStepHandler.DEFAULT_RESULT_CONTEXT_KEY}ProvenanceReportId"])
        assertEquals(82, repository.findById(4)?.provenance?.producingTaskId)
    }

    private fun executionContext(progress: MutableList<Int>) = object : TaskExecutionContext {
        override suspend fun ensureNotCancelled() = Unit
        override suspend fun reportProgress(percent: Int, message: String?): TaskProgress {
            progress += percent
            return TaskProgress(percent, message, Instant.parse("2026-08-10T15:00:00Z"))
        }
    }

    private fun source(reference: me.moiclathy.edb.core.datafile.model.DataFileStorageReference) = DataFile(
        id = 1,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.DERIVED_DATA,
        format = DataFileFormat.CSV,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "source.csv",
        storage = reference,
        provenance = DataFileProvenance(DataFileOrigin.CONVERSION),
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    )

    private class InMemoryRepository(source: DataFile) : DataFileRepository {
        private val files = linkedMapOf(requireNotNull(source.id) to source)
        fun nextId(): Int = (files.keys.maxOrNull() ?: 0) + 1
        override fun save(dataFile: DataFile): DataFile = dataFile.copy(id = nextId()).also { files[requireNotNull(it.id)] = it }
        override fun findById(id: Int): DataFile? = files[id]
        override fun findByProjectId(projectId: Int): List<DataFile> = files.values.filter { it.projectId == projectId }
        override fun update(dataFile: DataFile): DataFile = dataFile.also { files[requireNotNull(it.id)] = it }
    }
}
