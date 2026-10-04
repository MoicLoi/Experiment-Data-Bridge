package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import kotlinx.coroutines.runBlocking
import me.moiclathy.edb.core.task.scheduler.TaskScheduler
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

/** Connects the task scheduler to the Ktor application's process lifecycle. */
fun Application.configureTaskSchedulerLifecycle(
    scheduler: TaskScheduler = get()
) {
    val logger = LoggerFactory.getLogger("TaskSchedulerLifecycle")

    monitor.subscribe(ApplicationStarted) {
        if (scheduler.start()) {
            logger.info(
                "Task scheduler started with {} worker(s).",
                scheduler.state.value.desiredWorkerCount
            )
        }
    }

    monitor.subscribe(ApplicationStopping) {
        logger.info("Stopping task scheduler gracefully...")
        runBlocking {
            scheduler.stop()
        }
        logger.info("Task scheduler stopped.")
    }
}
