package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.audit.repository.AuditLogRepository
import me.moiclathy.edb.core.audit.repository.MySqlAuditLogRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val auditModule =
    module {

        singleOf(::MySqlAuditLogRepository) {
            bind<AuditLogRepository>()
        }
    }