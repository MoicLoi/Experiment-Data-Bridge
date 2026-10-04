package me.moiclathy.edb.core.datafile.conversion

import me.moiclathy.edb.core.datafile.model.DataFile
import me.moiclathy.edb.core.datafile.model.DataFileFormat
import me.moiclathy.edb.core.datafile.model.DataFileOrigin
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
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

data class SubmitDataFileConversionCommand(
    val projectId: Int,
    val sourceDataFileId: Int,
    val targetFormat: DataFileFormat,
    val requestedBy: Int,
    val options: Map<String, String> = emptyMap(),
    val priority: TaskPriority = TaskPriority.NORMAL,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val timeout: Duration? = null
) {
    init {
        require(projectId > 0 && sourceDataFileId > 0 && requestedBy > 0) {
            "Project, source DataFile and requester ids must be positive."
        }
        require(targetFormat in DataFileConversionRegistry.SUPPORTED_SCIENTIFIC_FORMATS) {
            "Unsupported DataFile conversion target: $targetFormat"
        }
        require(options.keys.none { it.isBlank() }) { "Conversion option keys must not be blank." }
        require(timeout == null || timeout.toMillis() >= 1) {
            "Task timeout must be at least one millisecond."
        }
    }
}

class DataFileConversionApplicationService(
    private val runtime: TaskRuntime,
    private val repository: DataFileRepository,
    private val registry: DataFileConversionRegistry,
    private val projectRepository: ProjectRepository,
    private val permissionChecker: PermissionChecker,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun submit(command: SubmitDataFileConversionCommand): Task {
        val project = projectRepository.findById(command.projectId)
            ?: throw ProjectNotFoundException(command.projectId)
        if (!permissionChecker.requireWritable(command.requestedBy, project)) {
            throw PermissionDeniedException()
        }
        val source = repository.findById(command.sourceDataFileId)
            ?.takeIf { it.projectId == command.projectId }
            ?: throw DataFileConversionSourceNotFoundException(command.sourceDataFileId)
        registry.require(source.format, command.targetFormat)
        val now = clock.instant()
        return runtime.submit(
            Task.pending(
                projectId = command.projectId,
                taskType = DataFileConversionTaskHandler.TASK_TYPE,
                configSnapshot = DataFileConversionTaskPayloadCodec.encode(
                    DataFileConversionTaskPayload(
                        command.sourceDataFileId,
                        command.targetFormat,
                        command.options
                    )
                ),
                triggerUserId = command.requestedBy,
                createdAt = now,
                priority = command.priority,
                retryPolicy = command.retryPolicy,
                timeout = command.timeout
            )
        )
    }

    fun listDerived(
        projectId: Int,
        sourceDataFileId: Int,
        requestedBy: Int
    ): List<DataFile> {
        require(projectId > 0 && sourceDataFileId > 0 && requestedBy > 0) {
            "Project, source DataFile and requester ids must be positive."
        }
        val project = projectRepository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)
        if (!permissionChecker.requireReadable(requestedBy, project)) {
            throw PermissionDeniedException()
        }
        repository.findById(sourceDataFileId)
            ?.takeIf { it.projectId == projectId }
            ?: throw DataFileConversionSourceNotFoundException(sourceDataFileId)

        return repository.findByProjectId(projectId)
            .asSequence()
            .filter { it.provenance.origin == DataFileOrigin.CONVERSION }
            .filter { sourceDataFileId in it.provenance.parentDataFileIds }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    fun capabilities(
        projectId: Int,
        sourceDataFileId: Int,
        requestedBy: Int
    ): List<DataFileConversionCapability> {
        require(projectId > 0 && sourceDataFileId > 0 && requestedBy > 0) {
            "Project, source DataFile and requester ids must be positive."
        }
        val project = projectRepository.findById(projectId)
            ?: throw ProjectNotFoundException(projectId)
        if (!permissionChecker.requireReadable(requestedBy, project)) {
            throw PermissionDeniedException()
        }
        val source = repository.findById(sourceDataFileId)
            ?.takeIf { it.projectId == projectId }
            ?: throw DataFileConversionSourceNotFoundException(sourceDataFileId)
        return registry.capabilitiesFor(source.format)
    }
}
