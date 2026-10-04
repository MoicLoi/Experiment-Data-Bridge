package me.moiclathy.edb.agent.application

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentRun
import me.moiclathy.edb.agent.policy.AgentPolicy
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class AgentAsyncPlanningService(
    private val planning: AgentPlanningApplicationService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : Closeable {
    private val jobs = ConcurrentHashMap<String, Job>()

    suspend fun start(
        sessionId: String,
        requestedBy: Int,
        definition: AgentDefinition,
        policy: AgentPolicy,
        userIntent: String,
        maxRepairAttempts: Int = 2
    ): AgentRun {
        planning.getSession(sessionId, requestedBy)
        val started = CompletableDeferred<AgentRun>()
        val job = scope.launch {
            try {
                planning.plan(sessionId, requestedBy, definition, policy, userIntent, maxRepairAttempts) { run ->
                    jobs[run.id] = currentCoroutineContext()[Job]!!
                    started.complete(run)
                }
            } catch (failure: Throwable) {
                if (!started.isCompleted) started.completeExceptionally(failure)
            } finally {
                if (started.isCompleted && !started.isCancelled) runCatching { started.await().id }.getOrNull()?.let(jobs::remove)
            }
        }
        job.invokeOnCompletion { failure ->
            if (!started.isCompleted && failure != null) started.completeExceptionally(failure)
        }
        return started.await()
    }

    suspend fun cancel(runId: String, requestedBy: Int): AgentRun {
        val cancelled = planning.cancelRun(runId, requestedBy)
        jobs.remove(runId)?.cancel(CancellationException("Agent run cancellation requested by user $requestedBy"))
        return cancelled
    }

    override fun close() = scope.cancel("Agent async planning service is closing")
}
