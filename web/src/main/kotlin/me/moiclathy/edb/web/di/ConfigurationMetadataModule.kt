package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.metadata.repository.ConfigurationMetadataRepository
import me.moiclathy.edb.core.config.metadata.repository.MySqlConfigurationMetadataRepository
import me.moiclathy.edb.core.config.metadata.service.ConfigurationMetadataService
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val configurationMetadataModule =
    module {

        singleOf(
            ::MySqlConfigurationMetadataRepository
        ) {
            bind<ConfigurationMetadataRepository>()
        }

        singleOf(
            ::ConfigurationMetadataService
        )
    }