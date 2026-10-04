package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionManager
import org.koin.ktor.ext.get

fun Application.configureDataFileRetentionLifecycle(manager: DataFileRetentionManager = get()) {
    monitor.subscribe(ApplicationStarted) { manager.start() }
    monitor.subscribe(ApplicationStopping) { manager.close() }
}
