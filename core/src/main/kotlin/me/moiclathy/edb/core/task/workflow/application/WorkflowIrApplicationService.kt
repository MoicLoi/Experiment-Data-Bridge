package me.moiclathy.edb.core.task.workflow.application

import me.moiclathy.edb.core.exception.EDBException
import me.moiclathy.edb.core.task.pipeline.PipelineAlreadyRegisteredException
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIr
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler

data class WorkflowIrCompilation(
    val schemaVersion: Int,
    val workflowId: String,
    val workflowVersion: String,
    val description: String?,
    val stepIds: List<String>,
    val executionOrder: List<String>,
    val registered: Boolean
)

class WorkflowIrApplicationService(
    private val codec: WorkflowIrCodec,
    private val compiler: WorkflowIrCompiler,
    private val pipelineRegistry: PipelineRegistry
) {
    fun validate(document: String): WorkflowIrCompilation = compile(document, register = false)

    fun compileAndRegister(document: String): WorkflowIrCompilation = compile(document, register = true)

    private fun compile(document: String, register: Boolean): WorkflowIrCompilation {
        val workflow = codec.deserialize(document)
        val pipeline = compiler.compile(workflow)
        if (register) {
            try {
                pipelineRegistry.register(pipeline)
            } catch (failure: PipelineAlreadyRegisteredException) {
                throw WorkflowPipelineAlreadyRegisteredException(failure.pipelineId)
            }
        }
        return workflow.result(pipeline.steps.map { it.id }, register)
    }
}

class WorkflowPipelineAlreadyRegisteredException(workflowId: String) : EDBException(
    "WORKFLOW_ALREADY_REGISTERED",
    "Workflow pipeline is already registered: $workflowId"
)

private fun WorkflowIr.result(executionOrder: List<String>, registered: Boolean) = WorkflowIrCompilation(
    schemaVersion = schemaVersion,
    workflowId = id,
    workflowVersion = version,
    description = description,
    stepIds = steps.map { it.id },
    executionOrder = executionOrder,
    registered = registered
)
