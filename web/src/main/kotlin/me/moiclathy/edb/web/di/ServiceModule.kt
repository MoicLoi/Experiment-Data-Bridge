package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.merge.service.ConfigurationMergeService
import me.moiclathy.edb.core.service.*
import me.moiclathy.edb.core.template.service.TemplatePreviewService
import me.moiclathy.edb.core.template.service.TemplateService
import me.moiclathy.edb.core.template.service.TemplateValidationService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val serviceModule = module {
    singleOf(::UserService)
    singleOf(::UserAdministrationService)
    singleOf(::AuditService)
    singleOf(::ProjectService)
    singleOf(::ProjectConfigurationValidationService)
    singleOf(::ProjectConfigurationService)
    singleOf(::ProjectConfigurationExportService)
    singleOf(::ProjectConfigurationImportService)
    singleOf(::ProjectConfigurationPackageImportService)
    singleOf(::TemplateValidationService)
    singleOf(::TemplatePreviewService)
    singleOf(::TemplateService)
    singleOf(::ConfigurationMergeService)
}
