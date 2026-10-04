package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionApplicationService
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionCapability
import me.moiclathy.edb.core.datafile.conversion.DataFileConverterOption
import me.moiclathy.edb.core.datafile.conversion.DataFileConverterOptionType
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionTaskHandler
import me.moiclathy.edb.core.datafile.conversion.SubmitDataFileConversionCommand
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataFileConversionRoutesTest {
    @Test
    fun `submits conversion and lists derived DataFiles`() = testApplication {
        val service = mockk<DataFileConversionApplicationService>()
        val command = slot<SubmitDataFileConversionCommand>()
        coEvery { service.submit(capture(command)) } returns queuedTask()
        every { service.listDerived(7, 42, 11) } returns listOf(derivedDataFile())
        every { service.capabilities(7, 42, 11) } returns listOf(
            DataFileConversionCapability(
                sourceFormat = DataFileFormat.CSV,
                targetFormat = DataFileFormat.XLSX,
                options = listOf(
                    DataFileConverterOption(
                        name = "sheetName",
                        type = DataFileConverterOptionType.STRING,
                        defaultValue = "Data"
                    )
                )
            )
        )

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { dataFileConversionRoutes(service) }
        }

        val submitted = client.post("/api/v1/projects/7/data-files/42/conversions") {
            bearerAuth(token())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """{"targetFormat":"xlsx","options":{"sheetName":"Observations"},"priority":75}"""
            )
        }
        val derived = client.get("/api/v1/projects/7/data-files/42/conversions") {
            bearerAuth(token())
        }
        val targets = client.get("/api/v1/projects/7/data-files/42/conversion-targets") {
            bearerAuth(token())
        }

        assertEquals(HttpStatusCode.Accepted, submitted.status)
        assertEquals(HttpStatusCode.OK, derived.status)
        assertEquals(HttpStatusCode.OK, targets.status)
        assertTrue(targets.bodyAsText().contains("sheetName"))
        assertEquals(7, command.captured.projectId)
        assertEquals(42, command.captured.sourceDataFileId)
        assertEquals(11, command.captured.requestedBy)
        assertEquals(DataFileFormat.XLSX, command.captured.targetFormat)
        assertEquals("Observations", command.captured.options["sheetName"])
        assertEquals(75, command.captured.priority.value)
    }

    private fun queuedTask() = Task.pending(
        projectId = 7,
        taskType = DataFileConversionTaskHandler.TASK_TYPE,
        configSnapshot = null,
        triggerUserId = 11,
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    ).copy(id = 81, status = TaskStatus.QUEUED)

    private fun derivedDataFile() = DataFile(
        id = 43,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.DERIVED_DATA,
        format = DataFileFormat.XLSX,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "lab.xlsx",
        provenance = DataFileProvenance(
            origin = DataFileOrigin.CONVERSION,
            parentDataFileIds = setOf(42),
            producingTaskId = 81
        ),
        createdAt = Instant.parse("2026-08-10T12:01:00Z")
    )

    private fun token(): String = JWT.create()
        .withSubject("researcher")
        .withClaim("userId", 11)
        .sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("data-file-conversion-routes-test-secret")
    }
}
