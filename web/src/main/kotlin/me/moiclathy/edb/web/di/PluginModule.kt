package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.plugin.DirectoryPluginLoader
import me.moiclathy.edb.core.plugin.PluginRegistryAdapter
import me.moiclathy.edb.core.plugin.declarative.DeclarativeHttpPluginLoader
import me.moiclathy.edb.core.config.AppConfig
import org.koin.dsl.module

val pluginModule = module {
    single { DirectoryPluginLoader() }
    single { DeclarativeHttpPluginLoader(get<AppConfig>().plugins.declarativeAllowedHosts.toSet()) }
    single { PluginRegistryAdapter(taskHandlers = get(), workflowStepHandlers = get(), phrasePatterns = get()) }
}
