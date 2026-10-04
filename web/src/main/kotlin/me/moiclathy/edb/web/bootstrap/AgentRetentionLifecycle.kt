package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import me.moiclathy.edb.agent.application.AgentRetentionManager
import org.koin.ktor.ext.get

fun Application.configureAgentRetentionLifecycle(manager: AgentRetentionManager = get()) {
    monitor.subscribe(ApplicationStarted) { manager.start() }
    monitor.subscribe(ApplicationStopping) { manager.close() }
}
