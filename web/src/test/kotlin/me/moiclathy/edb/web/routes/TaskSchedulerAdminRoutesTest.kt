package me.moiclathy.edb.web.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
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
import kotlinx.serialization.json.Json
import me.moiclathy.edb.core.security.UserRole
import me.moiclathy.edb.core.task.application.TaskSchedulerApplicationService
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerState
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TaskSchedulerAdminRoutesTest {
    @Test
    fun `admin reads starts resizes and stops scheduler while member is forbidden`() = testApplication {
        val service = mockk<TaskSchedulerApplicationService>()
        val stopped = TaskSchedulerState(TaskSchedulerStatus.STOPPED, 1, 0)
        val running = TaskSchedulerState(TaskSchedulerStatus.RUNNING, 1, 1)
        val resized = TaskSchedulerState(TaskSchedulerStatus.RUNNING, 4, 4)
        every { service.state() } returns stopped
        every { service.start() } returns running
        every { service.updateWorkerCount(4) } returns resized
        coEvery { service.stop() } returns stopped

        application {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(ALGORITHM).build())
                    validate { JWTPrincipal(it.payload) }
                }
            }
            routing { taskSchedulerAdminRoutes(service) }
        }

        val state = client.get(PATH) { bearerAuth(token(UserRole.ADMIN)) }
        val start = client.post("$PATH/start") { bearerAuth(token(UserRole.ADMIN)) }
        val resize = client.put("$PATH/workers") {
            bearerAuth(token(UserRole.ADMIN))
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"workerCount":4}""")
        }
        val stop = client.post("$PATH/stop") { bearerAuth(token(UserRole.ADMIN)) }
        val forbidden = client.get(PATH) { bearerAuth(token(UserRole.MEMBER)) }

        assertEquals(HttpStatusCode.OK, state.status)
        assertEquals(HttpStatusCode.OK, start.status)
        assertEquals(HttpStatusCode.OK, resize.status)
        assertEquals(HttpStatusCode.OK, stop.status)
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
    }

    private fun token(role: UserRole): String = JWT.create()
        .withSubject("operator")
        .withClaim("userId", 11)
        .withClaim("role", role.name)
        .sign(ALGORITHM)

    companion object {
        private const val PATH = "/api/v1/admin/task-scheduler"
        private val ALGORITHM = Algorithm.HMAC256("scheduler-admin-test-secret")
    }
}
