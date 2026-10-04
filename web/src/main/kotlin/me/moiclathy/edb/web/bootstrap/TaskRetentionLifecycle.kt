package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import me.moiclathy.edb.core.task.retention.TaskRetentionManager
import org.koin.ktor.ext.get

fun Application.configureTaskRetentionLifecycle(
    manager: TaskRetentionManager = get()
) {
    monitor.subscribe(ApplicationStarted) { manager.start() }
    monitor.subscribe(ApplicationStopping) { manager.close() }
}
