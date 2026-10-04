package me.moiclathy.edb.core.task.pipeline

import kotlinx.coroutines.CancellationException

enum class PipelineExecutionStatus {
    COMPLETED,
    FAILED
}

data class PipelineStepFailure(
    val stepId: String,
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val cause: Throwable? = null
)

data class PipelineExecutionResult(
    val pipelineId: String,
    val status: PipelineExecutionStatus,
    val context: PipelineContext,
    val completedStepIds: List<String>,
    val failure: PipelineStepFailure? = null
)

interface PipelineExecutor {
    suspend fun execute(
        pipeline: Pipeline,
        initialContext: PipelineContext = PipelineContext.EMPTY
    ): PipelineExecutionResult
}

/** Executes each step strictly in declaration order and stops at the first failure. */
class SequentialPipelineExecutor : PipelineExecutor {
    override suspend fun execute(
        pipeline: Pipeline,
        initialContext: PipelineContext
    ): PipelineExecutionResult {
        var context = initialContext
        val completedStepIds = mutableListOf<String>()

        for (step in pipeline.steps) {
            val result = try {
                step.execute(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                return failedResult(
                    pipeline = pipeline,
                    context = context,
                    completedStepIds = completedStepIds,
                    failure = PipelineStepFailure(
                        stepId = step.id,
                        code = "PIPELINE_STEP_EXCEPTION",
                        message = failure.message ?: "Pipeline step failed unexpectedly.",
                        cause = failure
                    )
                )
            }

            when (result) {
                is PipelineStepResult.Success -> {
                    context = result.context
                    completedStepIds += step.id
                }

                is PipelineStepResult.Failure -> return failedResult(
                    pipeline = pipeline,
                    context = context,
                    completedStepIds = completedStepIds,
                    failure = PipelineStepFailure(
                        stepId = step.id,
                        code = result.code,
                        message = result.message,
                        retryable = result.retryable
                    )
                )
            }
        }

        return PipelineExecutionResult(
            pipelineId = pipeline.id,
            status = PipelineExecutionStatus.COMPLETED,
            context = context,
            completedStepIds = completedStepIds.toList()
        )
    }

    private fun failedResult(
        pipeline: Pipeline,
        context: PipelineContext,
        completedStepIds: List<String>,
        failure: PipelineStepFailure
    ) = PipelineExecutionResult(
        pipelineId = pipeline.id,
        status = PipelineExecutionStatus.FAILED,
        context = context,
        completedStepIds = completedStepIds.toList(),
        failure = failure
    )
}
