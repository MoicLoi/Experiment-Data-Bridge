package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.database.DatabaseFactory

import me.moiclathy.edb.core.database.repository.AuditRepository
import me.moiclathy.edb.core.database.repository.ProjectRepository
import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.web.plugin.DatabaseInitializer
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


val databaseModule = module {
    singleOf(::UserRepository)
    singleOf(::AuditRepository)
    singleOf(::ProjectRepository)
    singleOf(::DatabaseInitializer)
    single { DatabaseFactory }

}