package me.moiclathy.edb.web.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import me.moiclathy.edb.core.config.schema.ConfigValidationException
import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.web.dto.ConfigValidationResponse
import me.moiclathy.edb.web.dto.ErrorResponse
import me.moiclathy.edb.web.dto.ValidationItem
import me.moiclathy.edb.web.exception.ExceptionStatusMapper
import me.moiclathy.edb.core.datafile.upload.DataFileUploadTooLargeException
import me.moiclathy.edb.core.worker.docker.DockerWorkerException
import me.moiclathy.edb.core.worker.docker.DockerWorkerFailureCategory
import me.moiclathy.edb.core.worker.docker.DockerWorkerFleetException
import org.slf4j.LoggerFactory

fun Application.configureExceptionHandling() {
    val logger = LoggerFactory.getLogger("ExceptionHandling")

    install(StatusPages) {

        exception<DockerWorkerFleetException> { call, cause ->
            logger.error("Docker worker fleet operation failed: {}", cause.operation, cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("DOCKER_WORKER_FLEET_UNAVAILABLE", cause.message ?: "Docker worker fleet operation failed")
            )
        }

        exception<DockerWorkerException> { call, cause ->
            val status = when (cause.category) {
                DockerWorkerFailureCategory.CONFIGURATION -> HttpStatusCode.BadRequest
                DockerWorkerFailureCategory.AUTHORIZATION -> HttpStatusCode.Forbidden
                DockerWorkerFailureCategory.CONFLICT -> HttpStatusCode.Conflict
                DockerWorkerFailureCategory.NOT_FOUND -> HttpStatusCode.NotFound
                DockerWorkerFailureCategory.CONNECTION,
                DockerWorkerFailureCategory.ENGINE,
                DockerWorkerFailureCategory.HEALTH,
                DockerWorkerFailureCategory.TIMEOUT -> HttpStatusCode.ServiceUnavailable
                DockerWorkerFailureCategory.UNKNOWN -> HttpStatusCode.InternalServerError
            }
            call.respond(
                status,
                ErrorResponse("DOCKER_WORKER_${cause.category.name}", cause.message ?: "Docker worker operation failed")
            )
        }

        exception<DataFileUploadTooLargeException> { call, cause ->
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse(
                    code = "DATA_FILE_TOO_LARGE",
                    message = cause.message ?: "Uploaded DataFile is too large"
                )
            )
        }

        /*
         * ------------------------------------------
         * 1. EDB 通用业务异常 (通过 Mapper 自动适配状态码)
         * ------------------------------------------
         */
        exception<EDBException> { call, cause ->
            val status = ExceptionStatusMapper.status(cause)

            logger.warn(
                "Business exception: code={}, status={}, message={}",
                cause.code,
                status.value,
                cause.message
            )

            call.respond(
                status,
                ErrorResponse(
                    code = cause.code,
                    message = cause.message
                )
            )
        }

        /*
         * ------------------------------------------
         * 2. 配置校验专属异常 (带结构化错误列表)
         * ------------------------------------------
         */
        exception<ConfigValidationException> { call, cause ->
            logger.warn("Configuration validation failed: {}", cause.message)

            call.respond(
                HttpStatusCode.BadRequest,
                ConfigValidationResponse(
                    code = "CONFIG_VALIDATION_FAILED",
                    message = "Configuration validation failed",
                    errors = cause.errors.map {
                        ValidationItem(
                            field = it.field,
                            message = it.message
                        )
                    }
                )
            )
        }

        /*
         * ------------------------------------------
         * 3. Ktor 参数 / 非法请求异常
         * ------------------------------------------
         */
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Invalid request: {}", cause.message)

            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    code = "INVALID_REQUEST",
                    message = cause.message ?: "Invalid request"
                )
            )
        }

        /*
         * ------------------------------------------
         * 4. 兜底未捕获异常 (500)
         * ------------------------------------------
         */
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)

            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = "Internal server error"
                )
            )
        }
    }
}
