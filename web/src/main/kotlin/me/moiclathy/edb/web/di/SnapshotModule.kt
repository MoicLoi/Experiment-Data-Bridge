package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.compare.service.ConfigurationCompareService
import me.moiclathy.edb.core.config.snapshot.repository.ConfigurationSnapshotRepository
import me.moiclathy.edb.core.config.snapshot.repository.MySqlConfigurationSnapshotRepository
import me.moiclathy.edb.core.config.snapshot.service.ConfigurationSnapshotService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val snapshotModule = module {

   /* singleOf<MySqlConfigurationSnapshotRepository> {
        MySqlConfigurationSnapshotRepository()
    }

    single<ConfigurationSnapshotRepository> {
        get<MySqlConfigurationSnapshotRepository>()
    }*/

    // 1. 注册具体实现
    single<ConfigurationSnapshotRepository> {
        MySqlConfigurationSnapshotRepository()
    }

    singleOf(::ConfigurationSnapshotService)

    singleOf(::ConfigurationCompareService)
}