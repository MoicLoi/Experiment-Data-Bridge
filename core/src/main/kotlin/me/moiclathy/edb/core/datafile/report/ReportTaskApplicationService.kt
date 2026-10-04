package me.moiclathy.edb.core.datafile.report

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

data class SubmitReportTaskCommand(
    val projectId: Int,
    val definitionId: String,
    val sourceDataFileIds: Set<Int> = emptySet(),
    val parameters: Map<String, String> = emptyMap(),
    val requestedBy: Int,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val timeout: Duration? = null
) {
    init {
        require(projectId > 0 && requestedBy > 0) { "Project and requester ids must be positive." }
        require(definitionId.isNotBlank()) { "Report definition id must not be blank." }
        require(sourceDataFileIds.all { it > 0 }) { "Report source DataFile ids must be positive." }
        require(parameters.keys.none { it.isBlank() }) { "Report parameter names must not be blank." }
        require(timeout == null || timeout.toMillis() >= 1) { "Task timeout must be at least one millisecond." }
    }
}

class ReportTaskApplicationService(
    private val runtime: TaskRuntime,
    private val definitions: ReportDefinitionRegistry,
    private val dataFiles: DataFileRepository,
    private val projects: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun submit(command: SubmitReportTaskCommand): Task {
        val project = projects.findById(command.projectId) ?: throw ProjectNotFoundException(command.projectId)
        if (!permissionChecker.requireWritable(command.requestedBy, project)) throw PermissionDeniedException()
        definitions.require(command.definitionId)
        command.sourceDataFileIds.forEach { id ->
            val source = dataFiles.findById(id)
            if (source == null || source.projectId != command.projectId) throw ReportSourceNotFoundException(id)
        }
        return runtime.submit(
            Task.pending(
                projectId = command.projectId,
                taskType = ReportTaskHandler.TASK_TYPE,
                configSnapshot = ReportTaskPayloadCodec.encode(
                    ReportTaskPayload(command.definitionId, command.sourceDataFileIds, command.parameters)
                ),
                triggerUserId = command.requestedBy,
                createdAt = clock.instant(),
                priority = command.priority,
                retryPolicy = command.retryPolicy,
                timeout = command.timeout
            )
        )
    }

    fun listDefinitions(projectId: Int, requestedBy: Int): List<ReportDefinition> {
        requireReadableProject(projectId, requestedBy)
        return definitions.definitions()
    }

    fun listArtifactsBySource(
        projectId: Int,
        sourceDataFileId: Int,
        requestedBy: Int
    ): List<DataFile> {
        requireReadableProject(projectId, requestedBy)
        val source = dataFiles.findById(sourceDataFileId)
        if (source == null || source.projectId != projectId) {
            throw ReportSourceNotFoundException(sourceDataFileId)
        }
        return dataFiles.findByProjectId(projectId)
            .asSequence()
            .filter { it.kind == DataFileKind.REPORT }
            .filter { it.provenance.origin == DataFileOrigin.REPORT_GENERATION }
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
