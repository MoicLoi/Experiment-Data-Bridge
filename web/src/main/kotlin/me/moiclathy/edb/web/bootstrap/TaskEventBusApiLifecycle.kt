package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import me.moiclathy.edb.core.task.api.event.TaskEventBusApi
import org.koin.ktor.ext.get

fun Application.configureTaskEventBusApiLifecycle(
    api: TaskEventBusApi = get()
) {
    monitor.subscribe(ApplicationStarted) { api.start() }
    monitor.subscribe(ApplicationStopping) { api.close() }
}
