package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupManager
import org.koin.ktor.ext.get

fun Application.configureDataFileOrphanCleanupLifecycle(
    manager: DataFileOrphanCleanupManager = get()
) {
    monitor.subscribe(ApplicationStarted) { manager.start() }
    monitor.subscribe(ApplicationStopping) { manager.close() }
}
