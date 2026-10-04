package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.datafile.application.DataFileApplicationService
import me.moiclathy.edb.core.datafile.application.DataFileDownload
import me.moiclathy.edb.core.datafile.application.UploadDataFileCommand
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.model.DataFileProvenance
import me.moiclathy.edb.core.datafile.model.DataFileStatus
import me.moiclathy.edb.core.datafile.model.DataFileStorageReference
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.test.assertEquals

class DataFileRoutesTest {
    @Test
    fun `uploads lists describes and streams a project DataFile`() = testApplication {
        val service = mockk<DataFileApplicationService>()
        val command = slot<UploadDataFileCommand>()
        var uploadedContent = ""
        val file = dataFile()
        every { service.upload(capture(command)) } answers {
            uploadedContent = command.captured.input.bufferedReader().readText()
            file
        }
        every { service.list(7, 11) } returns listOf(file)
        every { service.get(7, 42, 11) } returns file
        every { service.delete(7, 42, 11) } returns file.copy(status = DataFileStatus.DELETED)
        every { service.openDownload(7, 42, 11) } returns DataFileDownload(
            file,
            ByteArrayInputStream("a,b\n1,2".toByteArray())
        )

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { dataFileRoutes(service) }
        }

        val uploaded = client.submitFormWithBinaryData(
            url = "/api/v1/projects/7/data-files",
            formData = formData {
                append(
                    "file",
                    "a,b\n1,2".toByteArray(),
                    Headers.build {
                        append(HttpHeaders.ContentType, "text/csv")
                        append(HttpHeaders.ContentDisposition, "filename=\"lab.csv\"")
                    }
                )
            }
        ) { bearerAuth(token()) }
        val listed = client.get("/api/v1/projects/7/data-files") { bearerAuth(token()) }
        val detail = client.get("/api/v1/projects/7/data-files/42") { bearerAuth(token()) }
        val deleted = client.delete("/api/v1/projects/7/data-files/42") { bearerAuth(token()) }
        val download = client.get("/api/v1/projects/7/data-files/42/download") { bearerAuth(token()) }

        assertEquals(HttpStatusCode.Created, uploaded.status)
        assertEquals(HttpStatusCode.OK, listed.status)
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals(HttpStatusCode.OK, download.status)
        assertEquals("a,b\n1,2", uploadedContent)
        assertEquals("lab.csv", command.captured.originalFileName)
        assertEquals("a,b\n1,2", download.bodyAsText())
    }

    private fun dataFile() = DataFile(
        id = 42,
        projectId = 7,
        createdByUserId = 11,
        kind = DataFileKind.RAW_DATA,
        format = DataFileFormat.CSV,
        status = DataFileStatus.AVAILABLE,
        originalFileName = "lab.csv",
        mediaType = "text/csv",
        sizeBytes = 7,
        sha256 = "d".repeat(64),
        storage = DataFileStorageReference("local", "projects/7/data/lab.csv"),
        provenance = DataFileProvenance(DataFileOrigin.UPLOAD),
        createdAt = Instant.parse("2026-08-10T12:00:00Z")
    )

    private fun token(): String = JWT.create()
        .withSubject("researcher")
        .withClaim("userId", 11)
        .sign(ALGORITHM)

    companion object {
        private val ALGORITHM = Algorithm.HMAC256("data-file-routes-test-secret")
    }
}
