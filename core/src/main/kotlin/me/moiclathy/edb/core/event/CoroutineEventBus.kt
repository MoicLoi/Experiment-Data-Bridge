package me.moiclathy.edb.core.event

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

class CoroutineEventBus(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    private val bufferStrategy: EventBufferStrategy = EventBufferStrategy.SUSPEND,
    private val subscriberErrorHandler: EventSubscriberErrorHandler = LoggingSubscriberErrorHandler
) : EventBus {
    private val closed = AtomicBoolean(false)
    private val nextSubscriptionId = AtomicLong(0)
    private val lifecycleLock = Any()
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + dispatcher)
    private val subscribers = ConcurrentHashMap<Long, Subscriber>()

    init {
        require(bufferCapacity > 0) { "Event buffer capacity must be greater than zero." }
    }

    override val isClosed: Boolean
        get() = closed.get()

    override fun <E : Event> subscribe(
        eventType: KClass<E>,
        handler: suspend (E) -> Unit
    ): EventSubscription = synchronized(lifecycleLock) {
        check(!closed.get()) { "Event bus is closed." }

        val id = nextSubscriptionId.incrementAndGet()
        val channel = Channel<Event>(
            capacity = bufferCapacity,
            onBufferOverflow = bufferStrategy.toBufferOverflow()
        )
        val job = scope.launch {
            for (event in channel) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    handler(event as E)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    subscriberErrorHandler.handle(id, event, error)
                }
            }
        }
        subscribers[id] = Subscriber(eventType, channel, job)
        Subscription(id)
    }

    override suspend fun publish(event: Event): EventPublishResult {
        check(!closed.get()) { "Event bus is closed." }
        val matching = subscribers.values.filter { it.eventType.isInstance(event) }
        val accepted = when (bufferStrategy) {
            EventBufferStrategy.SUSPEND -> coroutineScope {
                matching.map { subscriber ->
                    async {
                        try {
                            subscriber.channel.send(event)
                            true
                        } catch (_: ClosedSendChannelException) {
                            false
                        }
                    }
                }.awaitAll().count { it }
            }
            EventBufferStrategy.DROP_OLDEST,
            EventBufferStrategy.DROP_LATEST ->
                matching.count { it.channel.trySend(event).isSuccess }
        }

        return EventPublishResult(
            matchedSubscribers = matching.size,
            acceptedSubscribers = accepted
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        synchronized(lifecycleLock) {
            subscribers.keys.toList().forEach(::cancelSubscription)
            supervisor.cancel()
        }
    }

    private fun cancelSubscription(id: Long) {
        subscribers.remove(id)?.let { subscriber ->
            subscriber.channel.cancel()
            subscriber.job.cancel()
        }
    }

    private fun EventBufferStrategy.toBufferOverflow(): BufferOverflow = when (this) {
        EventBufferStrategy.SUSPEND -> BufferOverflow.SUSPEND
        EventBufferStrategy.DROP_OLDEST -> BufferOverflow.DROP_OLDEST
        EventBufferStrategy.DROP_LATEST -> BufferOverflow.DROP_LATEST
    }

    private inner class Subscription(
        override val id: Long
    ) : EventSubscription {
        override val isActive: Boolean
            get() = !closed.get() && subscribers[id]?.job?.isActive == true

        override fun cancel() {
            cancelSubscription(id)
        }
    }

    private data class Subscriber(
        val eventType: KClass<out Event>,
        val channel: Channel<Event>,
        val job: Job
    )

    companion object {
        const val DEFAULT_BUFFER_CAPACITY = 64
    }
}

private object LoggingSubscriberErrorHandler : EventSubscriberErrorHandler {
    private val logger = LogManager.getLogger(CoroutineEventBus::class.java)

    override fun handle(subscriptionId: Long, event: Event, error: Throwable) {
        logger.error(
            "Event subscriber {} failed while processing {}",
            subscriptionId,
            event::class.simpleName,
            error
        )
    }
}
