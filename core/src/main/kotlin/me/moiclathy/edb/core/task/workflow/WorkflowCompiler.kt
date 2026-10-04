package me.moiclathy.edb.core.task.workflow

import me.moiclathy.edb.core.task.pipeline.Pipeline
import me.moiclathy.edb.core.task.workflow.ir.LegacyWorkflowIrAdapter
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler

/** Converts a validated declarative workflow into the existing executable Pipeline model. */
class WorkflowCompiler(
    private val handlers: WorkflowStepHandlerRegistry,
    validator: WorkflowValidator = WorkflowValidator(),
    irCompiler: WorkflowIrCompiler = WorkflowIrCompiler(handlers)
) {
    private val adapter = LegacyWorkflowIrAdapter(validator)
    private val irCompiler = irCompiler

    fun compile(definition: WorkflowDefinition): Pipeline {
        return irCompiler.compile(adapter.convert(definition))
    }
}

/** Small facade for callers that start with JSON rather than an in-memory definition. */
class JsonWorkflowDriver(
    private val parser: JsonWorkflowParser,
    private val compiler: WorkflowCompiler
) {
    fun compile(json: String): Pipeline = compiler.compile(parser.parse(json))
}
