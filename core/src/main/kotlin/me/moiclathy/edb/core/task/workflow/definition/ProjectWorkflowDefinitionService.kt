package me.moiclathy.edb.core.task.workflow.definition

import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

class ProjectWorkflowDefinitionService(
    private val repository: ProjectWorkflowDefinitionRepository,
    private val workflowService: WorkflowIrApplicationService,
    private val codec: WorkflowIrCodec,
    private val clock: Clock = Clock.systemUTC()
) {
    fun create(projectId: Int, document: String, createdBy: Int, sourceReference: String? = null): ProjectWorkflowDefinition {
        require(projectId > 0 && createdBy > 0)
        val compilation = workflowService.validate(document)
        val canonical = codec.serialize(codec.deserialize(document))
        val hash = canonical.sha256()
        var lastFailure: Exception? = null
        repeat(MAX_VERSION_ATTEMPTS) {
            val latest = repository.findLatest(projectId, compilation.workflowId)
            if (latest?.workflowSha256 == hash) return latest
            val candidate = ProjectWorkflowDefinition(
                projectId = projectId,
                workflowId = compilation.workflowId,
                version = (latest?.version ?: 0) + 1,
                workflowIrJson = canonical,
                workflowSha256 = hash,
                createdBy = createdBy,
                sourceReference = sourceReference,
                createdAt = Instant.now(clock)
            )
            try {
                return repository.save(candidate)
            } catch (failure: Exception) {
                lastFailure = failure
                repository.findLatest(projectId, compilation.workflowId)
                    ?.takeIf { it.workflowSha256 == hash }
                    ?.let { return it }
            }
        }
        throw checkNotNull(lastFailure)
    }

    private companion object { const val MAX_VERSION_ATTEMPTS = 3 }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
