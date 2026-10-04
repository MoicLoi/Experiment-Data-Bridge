package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.loadConfig
import me.moiclathy.edb.core.config.mapper.ProjectConfigMapper
import me.moiclathy.edb.core.config.mapper.SnakeYamlProjectConfigMapper
import me.moiclathy.edb.core.config.migration.ConfigMigrationManager
import me.moiclathy.edb.core.config.migration.ConfigVersionReader
import me.moiclathy.edb.core.config.migration.MigrationV1ToV2
import me.moiclathy.edb.core.config.runtime.CachedConfigurationCenter
import me.moiclathy.edb.core.config.runtime.ConfigRefreshService
import me.moiclathy.edb.core.config.runtime.ConfigurationCenter
import me.moiclathy.edb.core.config.runtime.DatabaseProjectConfigLoader
import me.moiclathy.edb.core.config.runtime.ProjectConfigLoader
import me.moiclathy.edb.core.config.schema.ConfigSchemaRegistry
import me.moiclathy.edb.core.config.schema.ConfigSchemaValidator
import me.moiclathy.edb.core.config.schema.SchemaV1
import me.moiclathy.edb.core.config.schema.SchemaV2
import me.moiclathy.edb.core.config.schema.YamlValidationService
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val configModule = module {

    single {
        loadConfig()
    }

    single {
        ConfigSchemaRegistry(
            listOf(
                SchemaV1(),
                SchemaV2()
            )
        )
    }

    singleOf(::ConfigSchemaValidator)

    singleOf(::ConfigVersionReader)

    single {
        ConfigMigrationManager(
            listOf(
                MigrationV1ToV2()
            )
        )
    }

    singleOf(::SnakeYamlProjectConfigMapper) {
        bind<ProjectConfigMapper>()
    }

    singleOf(::DatabaseProjectConfigLoader) {
        bind<ProjectConfigLoader>()
    }

    singleOf(::CachedConfigurationCenter) {
        bind<ConfigurationCenter>()
    }

    singleOf(::YamlValidationService)

    singleOf(::ConfigRefreshService)
}