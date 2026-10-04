package me.moiclathy.edb.web.exception

import io.ktor.http.HttpStatusCode
import me.moiclathy.edb.core.exception.EDBException

object ExceptionStatusMapper {

    fun status(exception: EDBException): HttpStatusCode {
        return when (exception.code) {

            // -----------------------------
            // 400 Bad Request
            // -----------------------------

            "TEMPLATE_INVALID",
            "TEMPLATE_VALIDATION_FAILED",
            "INVALID_TEMPLATE_REQUEST",
            "INVALID_TEMPLATE_ID" ->
                HttpStatusCode.BadRequest

            // -----------------------------
            // 404 Not Found
            // -----------------------------

            "TEMPLATE_NOT_FOUND",
            "TASK_NOT_FOUND",
            "PIPELINE_NOT_FOUND",
            "DATA_FILE_NOT_FOUND",
            "USER_NOT_FOUND",
            "PROJECT_NOT_FOUND" ->
                HttpStatusCode.NotFound

            "DOCKER_WORKER_NOT_FOUND" ->
                HttpStatusCode.NotFound

            "WORKFLOW_STEP_NOT_FOUND",
            "WORKFLOW_STEP_SCHEMA_NOT_FOUND" ->
                HttpStatusCode.NotFound

            "AGENT_SESSION_NOT_FOUND",
            "AGENT_RUN_NOT_FOUND",
            "AGENT_PROPOSAL_NOT_FOUND" ->
                HttpStatusCode.NotFound

            "PERMISSION_DENIED" ->
                HttpStatusCode.Forbidden

            "AGENT_TOKEN_QUOTA_EXCEEDED" ->
                HttpStatusCode.TooManyRequests

            "AGENT_PROMPT_INJECTION_REJECTED" ->
                HttpStatusCode.BadRequest

            "DATA_FILE_CLEANUP_FAILED" ->
                HttpStatusCode.InternalServerError

            // -----------------------------
            // 409 Conflict
            // -----------------------------

            "TEMPLATE_ALREADY_EXISTS",
            "TEMPLATE_NAME_ALREADY_EXISTS",
            "TEMPLATE_CONFLICT",
            "TASK_NOT_RETRYABLE",
            "TASK_RETENTION_ALREADY_RUNNING",
            "DATA_FILE_ORPHAN_CLEANUP_ALREADY_RUNNING",
            "DATA_FILE_RETENTION_ALREADY_RUNNING" ->
                HttpStatusCode.Conflict

            "WORKFLOW_ALREADY_REGISTERED" ->
                HttpStatusCode.Conflict

            "AGENT_PROPOSAL_DECIDED",
            "AGENT_PROPOSAL_NOT_APPROVED",
            "AGENT_PROPOSAL_CONTENT_CHANGED" ->
                HttpStatusCode.Conflict

            "USER_SELF_DEMOTION",
            "LAST_ACTIVE_ADMIN" ->
                HttpStatusCode.Conflict

            // -----------------------------
            // 默认业务异常
            // -----------------------------

            else ->
                HttpStatusCode.BadRequest
        }
    }
}
