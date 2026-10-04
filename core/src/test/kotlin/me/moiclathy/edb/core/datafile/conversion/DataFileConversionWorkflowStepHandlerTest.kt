package me.moiclathy.edb.core.datafile.conversion

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.task.pipeline.PipelineContext
import me.moiclathy.edb.core.task.pipeline.PipelineStepResult
import me.moiclathy.edb.core.task.pipeline.PipelineSystemContextKeys
import me.moiclathy.edb.core.task.workflow.WorkflowStepDefinition
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataFileConversionWorkflowStepHandlerTest {
    @Test
    fun `workflow conversion resolves context input and exposes derived DataFile id`() = runBlocking {
        val service = mockk<DataFileConversionService>()
        val command = slot<ExecuteDataFileConversion>()
        coEvery { service.convert(capture(command)) } returns DataFileConversionResult(convertedDataFile(), provenanceReport())
        val handler = DataFileConversionWorkflowStepHandler(service)

        val result = handler.execute(
            WorkflowStepDefinition(
                id = "convert",
                type = DataFileConversionWorkflowStepHandler.TYPE,
                config = mapOf(
                    "sourceDataFileIdContext" to "uploadedDataFileId",
                    "targetFormat" to "xlsx",
                    "optionContext.sheetName" to "experimentName"
                )
            ),
            PipelineContext(
                mapOf(
                    PipelineSystemContextKeys.TASK_ID to 81,
                    PipelineSystemContextKeys.PROJECT_ID to 7,
                    PipelineSystemContextKeys.TRIGGER_USER_ID to 11,
                    "uploadedDataFileId" to "42",
                    "experimentName" to "Observations"
                )
            )
        )

        val success = assertIs<PipelineStepResult.Success>(result)
        assertEquals(43, success.context[DataFileConversionWorkflowStepHandler.DEFAULT_RESULT_CONTEXT_KEY])
        assertEquals(44, success.context["${DataFileConversionWorkflowStepHandler.DEFAULT_RESULT_CONTEXT_KEY}ProvenanceReportId"])
        assertEquals(42, command.captured.sourceDataFileId)
        assertEquals(DataFileFormat.XLSX, command.captured.targetFormat)
        assertEquals("Observations", command.captured.options["sheetName"])
        assertEquals(81, command.captured.producingTaskId)
        coVerify(exactly = 1) { service.convert(any()) }
    }

    private fun convertedDataFile() = DataFile(
        id = 43,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.DERIVED_DATA,
        format = DataFileFormat.XLSX,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "observations.xlsx",
        provenance = DataFileProvenance(DataFileOrigin.CONVERSION, setOf(42), 81),
        createdAt = Instant.parse("2026-08-10T12:01:00Z")
    )

    private fun provenanceReport() = DataFile(
        id = 44, projectId = 7, createdByUserId = 11, kind = DataFileKind.AUXILIARY,
        format = DataFileFormat.JSON, status = DataFileStatus.AVAILABLE,
        originalFileName = "format-conversion-81.provenance.json",
        provenance = DataFileProvenance(DataFileOrigin.CONVERSION, setOf(42, 43), 81),
        createdAt = Instant.parse("2026-08-10T12:01:00Z")
    )
}
