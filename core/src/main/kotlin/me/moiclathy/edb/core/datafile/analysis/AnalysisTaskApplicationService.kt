package me.moiclathy.edb.core.datafile.analysis

import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileKind
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.exception.PermissionDeniedException
import me.moiclathy.edb.core.exception.ProjectNotFoundException
import me.moiclathy.edb.core.security.PermissionChecker
import me.moiclathy.edb.core.task.Task
import me.moiclathy.edb.core.task.TaskPriority
import me.moiclathy.edb.core.task.retry.RetryPolicy
import me.moiclathy.edb.core.task.runtime.TaskRuntime
import java.time.Clock
import java.time.Duration
import kotlinx.serialization.json.JsonObject

data class SubmitAnalysisTaskCommand(
    val projectId: Int,
    val handlerId: String,
    val sourceDataFileIds: Set<Int>,
    val parameters: Map<String, String> = emptyMap(),
    val parameterValues: JsonObject? = null,
    val requestedBy: Int,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val timeout: Duration? = null
) {
    init {
        require(projectId > 0 && requestedBy > 0) { "Project and requester ids must be positive." }
        require(handlerId.isNotBlank()) { "Analysis handler id must not be blank." }
        require(sourceDataFileIds.isNotEmpty() && sourceDataFileIds.all { it > 0 }) {
            "Analysis requires at least one positive source DataFile id."
        }
        require(parameters.keys.none { it.isBlank() }) { "Analysis parameter names must not be blank." }
        require(parameters.isEmpty() || parameterValues == null) {
            "Legacy parameters and structured parameter values cannot be submitted together."
        }
        require(timeout == null || timeout.toMillis() >= 1) { "Task timeout must be at least one millisecond." }
    }
}

class AnalysisTaskApplicationService(
    private val runtime: TaskRuntime,
    private val handlers: AnalysisHandlerRegistry,
    private val dataFiles: DataFileRepository,
    private val projects: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun submit(command: SubmitAnalysisTaskCommand): Task {
        val project = projects.findById(command.projectId) ?: throw ProjectNotFoundException(command.projectId)
        if (!permissionChecker.requireWritable(command.requestedBy, project)) throw PermissionDeniedException()
        val handler = handlers.require(command.handlerId)
        val encodedParameters = command.parameterValues
            ?.let(handler::encodeParameterValues)
            ?: command.parameters
        command.sourceDataFileIds.forEach { id ->
            val source = dataFiles.findById(id)
            if (source == null || source.projectId != command.projectId) throw AnalysisSourceNotFoundException(id)
        }
        return runtime.submit(
            Task.pending(
                projectId = command.projectId,
                taskType = AnalysisTaskHandler.TASK_TYPE,
                configSnapshot = AnalysisTaskPayloadCodec.encode(
                    AnalysisTaskPayload(command.handlerId, command.sourceDataFileIds, encodedParameters)
                ),
                triggerUserId = command.requestedBy,
                createdAt = clock.instant(),
                priority = command.priority,
                retryPolicy = command.retryPolicy,
                timeout = command.timeout
            )
        )
    }

    fun listHandlers(projectId: Int, requestedBy: Int): List<AnalysisHandler> {
        requireReadableProject(projectId, requestedBy)
        return handlers.handlers()
    }

    fun listArtifactsBySource(
        projectId: Int,
        sourceDataFileId: Int,
        requestedBy: Int
    ): List<DataFile> {
        requireReadableProject(projectId, requestedBy)
        val source = dataFiles.findById(sourceDataFileId)
        if (source == null || source.projectId != projectId) throw AnalysisSourceNotFoundException(sourceDataFileId)
        return dataFiles.findByProjectId(projectId)
            .asSequence()
            .filter { it.kind == DataFileKind.ANALYSIS_RESULT }
            .filter { it.provenance.origin == DataFileOrigin.ANALYSIS }
            .filter { sourceDataFileId in it.provenance.parentDataFileIds }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    private fun requireReadableProject(projectId: Int, requestedBy: Int) {
        require(projectId > 0 && requestedBy > 0) { "Project and requester ids must be positive." }
        val project = projects.findById(projectId) ?: throw ProjectNotFoundException(projectId)
        if (!permissionChecker.requireReadable(requestedBy, project)) throw PermissionDeniedException()
    }
}
