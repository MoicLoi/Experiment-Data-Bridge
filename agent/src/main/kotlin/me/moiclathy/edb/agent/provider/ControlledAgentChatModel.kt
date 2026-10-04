package me.moiclathy.edb.agent.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

enum class AgentProviderHealth { UNINITIALIZED, HEALTHY, DEGRADED, CIRCUIT_OPEN, HALF_OPEN }

data class AgentModelCallMetrics(
    val requests: Long,
    val successes: Long,
    val failures: Long,
    val fallbackUses: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val inFlight: Long,
    val totalLatencyMillis: Long,
    val lastCalledAt: Instant?
) {
    val averageLatencyMillis: Double? get() = requests.takeIf { it > 0 }?.let { totalLatencyMillis.toDouble() / it }
}

data class ControlledAgentModelSnapshot(
    val health: AgentProviderHealth,
    val consecutiveFailures: Int,
    val circuitOpenUntil: Instant?,
    val metrics: AgentModelCallMetrics
)

class ControlledAgentChatModel(
    private val primary: AgentChatModel,
    profile: AgentModelProfile,
    private val fallback: AgentChatModel? = null,
    private val clock: Clock = Clock.systemUTC()
) : AgentChatModel {
    private val concurrency = Semaphore(profile.maxConcurrency)
    private val limiter = PerMinuteRateLimiter(profile.requestsPerMinute, clock)
    private val circuit = FailureCircuit(profile.circuitBreakerFailures, profile.circuitBreakerOpenDuration.toMillis(), clock)
    private val requests = AtomicLong()
    private val successes = AtomicLong()
    private val failures = AtomicLong()
    private val fallbackUses = AtomicLong()
    private val inputTokens = AtomicLong()
    private val outputTokens = AtomicLong()
    private val inFlight = AtomicLong()
    private val totalLatencyMillis = AtomicLong()
    @Volatile private var lastCalledAt: Instant? = null

    override suspend fun generate(request: AgentModelRequest): AgentModelResponse = concurrency.withPermit {
        execute(request) { model -> model.generate(request) }
    }

    override suspend fun generateStreaming(
        request: AgentModelRequest,
        onTextDelta: suspend (String) -> Unit
    ): AgentModelResponse = concurrency.withPermit {
        var emitted = false
        execute(request, fallbackAllowed = { !emitted }) { model ->
            model.generateStreaming(request) { delta ->
                emitted = true
                onTextDelta(delta)
            }
        }
    }

    private suspend fun execute(
        request: AgentModelRequest,
        fallbackAllowed: () -> Boolean = { true },
        invocation: suspend (AgentChatModel) -> AgentModelResponse
    ): AgentModelResponse {
        limiter.acquire()
        requests.incrementAndGet()
        inFlight.incrementAndGet()
        val startedAt = Instant.now(clock).also { lastCalledAt = it }
        return try {
            circuit.beforeCall()
            invocation(primary).also { response ->
                circuit.succeeded()
                recordSuccess(response, startedAt)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            circuit.failed()
            val fallbackModel = fallback?.takeIf { fallbackAllowed() }
            if (fallbackModel == null) {
                failures.incrementAndGet()
                recordLatency(startedAt)
                throw failure
            }
            try {
                invocation(fallbackModel).also { response ->
                    fallbackUses.incrementAndGet()
                    recordSuccess(response, startedAt)
                }
            } catch (fallbackFailure: Throwable) {
                failures.incrementAndGet()
                recordLatency(startedAt)
                throw fallbackFailure
            }
        } finally {
            inFlight.decrementAndGet()
        }
    }

    fun snapshot(): ControlledAgentModelSnapshot {
        val circuitSnapshot = circuit.snapshot()
        return ControlledAgentModelSnapshot(
            circuitSnapshot.health,
            circuitSnapshot.failures,
            circuitSnapshot.openUntil,
            AgentModelCallMetrics(
                requests.get(), successes.get(), failures.get(), fallbackUses.get(),
                inputTokens.get(), outputTokens.get(), inFlight.get(), totalLatencyMillis.get(), lastCalledAt
            )
        )
    }

    private fun recordSuccess(response: AgentModelResponse, startedAt: Instant) {
        successes.incrementAndGet()
        response.tokenUsage?.let { usage ->
            inputTokens.addAndGet(usage.inputTokens)
            outputTokens.addAndGet(usage.outputTokens)
        }
        recordLatency(startedAt)
    }

    private fun recordLatency(startedAt: Instant) {
        totalLatencyMillis.addAndGet(java.time.Duration.between(startedAt, Instant.now(clock)).toMillis().coerceAtLeast(0))
    }
}

private class PerMinuteRateLimiter(private val limit: Int, private val clock: Clock) {
    private val calls = ArrayDeque<Instant>()
    suspend fun acquire() {
        while (true) {
            val waitMs = synchronized(this) {
                val now = Instant.now(clock)
                while (calls.isNotEmpty() && calls.first().plusSeconds(60) <= now) calls.removeFirst()
                if (calls.size < limit) { calls.addLast(now); 0L }
                else java.time.Duration.between(now, calls.first().plusSeconds(60)).toMillis().coerceAtLeast(1)
            }
            if (waitMs == 0L) return
            delay(waitMs)
        }
    }
}

private class FailureCircuit(private val threshold: Int, private val openMs: Long, private val clock: Clock) {
    private var failures = 0
    private var openUntil: Instant? = null
    @Synchronized fun beforeCall() {
        val until = openUntil ?: return
        if (Instant.now(clock) < until) throw AgentModelCircuitOpenException(until)
        openUntil = null; failures = 0
    }
    @Synchronized fun succeeded() { failures = 0; openUntil = null }
    @Synchronized fun failed() {
        failures++
        if (failures >= threshold) openUntil = Instant.now(clock).plusMillis(openMs)
    }
    @Synchronized fun snapshot(): CircuitSnapshot {
        val until = openUntil
        val health = when {
            until != null && Instant.now(clock) < until -> AgentProviderHealth.CIRCUIT_OPEN
            until != null -> AgentProviderHealth.HALF_OPEN
            failures > 0 -> AgentProviderHealth.DEGRADED
            else -> AgentProviderHealth.HEALTHY
        }
        return CircuitSnapshot(health, failures, until)
    }
}

private data class CircuitSnapshot(val health: AgentProviderHealth, val failures: Int, val openUntil: Instant?)

class AgentModelCircuitOpenException(until: Instant) :
    IllegalStateException("Agent model circuit is open until $until")
