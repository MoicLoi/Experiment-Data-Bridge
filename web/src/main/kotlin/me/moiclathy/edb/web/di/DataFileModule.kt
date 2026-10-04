package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.datafile.persistence.DataFileMapper
import me.moiclathy.edb.core.datafile.repository.DataFileRepository
import me.moiclathy.edb.core.datafile.repository.DataFileStorageReferenceIndex
import me.moiclathy.edb.core.datafile.repository.MySqlDataFileRepository
import me.moiclathy.edb.core.datafile.storage.DataFileStorage
import me.moiclathy.edb.core.datafile.storage.DataFileStorageInventory
import me.moiclathy.edb.core.datafile.storage.LocalDataFileStorage
import me.moiclathy.edb.core.datafile.upload.DataFileUploadPolicy
import me.moiclathy.edb.core.datafile.upload.DataFileUploadService
import me.moiclathy.edb.core.datafile.application.DataFileApplicationService
import me.moiclathy.edb.core.datafile.analysis.AnalysisArtifactService
import me.moiclathy.edb.core.datafile.analysis.AnalysisExecutionService
import me.moiclathy.edb.core.datafile.analysis.AnalysisHandlerRegistry
import me.moiclathy.edb.core.datafile.analysis.AnalysisTaskApplicationService
import me.moiclathy.edb.core.datafile.analysis.AnalysisTaskHandler
import me.moiclathy.edb.core.datafile.analysis.AnalysisWorkflowStepHandler
import me.moiclathy.edb.core.datafile.analysis.PythonHttpAnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.PythonHttpAnalysisHandlerConfig
import me.moiclathy.edb.core.datafile.analysis.PythonStatisticsAnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.DataFileTabularProfileService
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandler
import me.moiclathy.edb.core.datafile.analysis.ExternalHttpAnalysisHandlerConfig
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionApplicationService
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionRegistry
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionService
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionTaskHandler
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionWorkflowStepHandler
import me.moiclathy.edb.core.datafile.conversion.CsvToJsonDataFileConverter
import me.moiclathy.edb.core.datafile.conversion.CsvToXlsxDataFileConverter
import me.moiclathy.edb.core.datafile.conversion.JsonToCsvDataFileConverter
import me.moiclathy.edb.core.datafile.conversion.XlsxToCsvDataFileConverter
import me.moiclathy.edb.core.datafile.format.DataFileFormatDetector
import me.moiclathy.edb.core.datafile.format.DataFileFormatRegistry
import me.moiclathy.edb.core.datafile.format.DefaultDataFileFormatDetector
import me.moiclathy.edb.core.datafile.format.DataFileReaderRegistry
import me.moiclathy.edb.core.datafile.format.DataFileWriterRegistry
import me.moiclathy.edb.core.datafile.format.defaultTabularDataFileReaderRegistry
import me.moiclathy.edb.core.datafile.format.defaultTabularDataFileWriterRegistry
import me.moiclathy.edb.core.datafile.lifecycle.DataFileDeletionService
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupManager
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupAdminService
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupPolicy
import me.moiclathy.edb.core.datafile.lifecycle.DataFileOrphanCleanupService
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionAdminService
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionManager
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionPolicy
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionRepository
import me.moiclathy.edb.core.datafile.retention.DataFileRetentionService
import me.moiclathy.edb.core.datafile.retention.MySqlDataFileRetentionRepository
import me.moiclathy.edb.core.datafile.report.ReportArtifactService
import me.moiclathy.edb.core.datafile.report.ReportGeneratorRegistry
import me.moiclathy.edb.core.datafile.report.ReportDefinitionRegistry
import me.moiclathy.edb.core.datafile.report.ReportTaskApplicationService
import me.moiclathy.edb.core.datafile.report.ReportTaskHandler
import me.moiclathy.edb.core.datafile.report.ReportWorkflowStepHandler
import me.moiclathy.edb.core.datafile.report.DirectoryReportDefinitionLoader
import me.moiclathy.edb.core.datafile.report.JsonReportDefinitionParser
import me.moiclathy.edb.core.datafile.report.ReportDefinitionParser
import me.moiclathy.edb.core.datafile.report.TabularSummaryReportGenerator
import me.moiclathy.edb.core.datafile.report.RHttpReportGenerator
import me.moiclathy.edb.core.datafile.report.RHttpReportGeneratorConfig
import me.moiclathy.edb.core.datafile.upload.DataFileNameSanitizer
import me.moiclathy.edb.core.datafile.upload.DataFileStorageKeyGenerator
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileNameSanitizer
import me.moiclathy.edb.core.datafile.upload.DefaultDataFileStorageKeyGenerator
import me.moiclathy.edb.core.datafile.upload.ResumableUploadSessionRepository
import me.moiclathy.edb.core.datafile.upload.MySqlResumableUploadSessionRepository
import me.moiclathy.edb.core.datafile.upload.ResumableUploadStore
import me.moiclathy.edb.core.datafile.upload.LocalResumableUploadStore
import me.moiclathy.edb.core.datafile.upload.ResumableUploadService
import org.koin.dsl.module
import java.net.URI
import java.nio.file.Path
import java.time.Clock

val dataFileModule = module {
    single { DataFileMapper() }

    single { MySqlDataFileRepository(mapper = get()) }
    single<DataFileRepository> { get<MySqlDataFileRepository>() }
    single<DataFileStorageReferenceIndex> { get<MySqlDataFileRepository>() }

    single<DataFileStorage> {
        val root = Path.of(get<AppConfig>().storage.dataPath).resolve("files")
        LocalDataFileStorage(root)
    }
    single<DataFileStorageInventory> { get<DataFileStorage>() as DataFileStorageInventory }
    single<ResumableUploadSessionRepository> { MySqlResumableUploadSessionRepository() }
    single<ResumableUploadStore> { LocalResumableUploadStore(Path.of(get<AppConfig>().storage.dataPath).resolve("files")) }

    single {
        val cleanup = get<AppConfig>().storage.orphanCleanup
        DataFileOrphanCleanupPolicy(
            enabled = cleanup.enabled,
            minimumAge = java.time.Duration.ofHours(cleanup.minimumAgeHours),
            cleanupInterval = java.time.Duration.ofMinutes(cleanup.cleanupIntervalMinutes),
            maxDeletesPerRun = cleanup.maxDeletesPerRun
        )
    }
    single {
        DataFileOrphanCleanupService(
            storage = get(),
            inventory = get(),
            referenceIndex = get(),
            policy = get(),
            clock = Clock.systemUTC()
        )
    }
    single {
        DataFileOrphanCleanupManager(
            service = get(),
            policy = get()
        )
    }
    single {
        DataFileOrphanCleanupAdminService(
            policy = get(),
            service = get(),
            manager = get()
        )
    }

    single<DataFileRetentionRepository> { MySqlDataFileRetentionRepository() }
    single {
        val retention = get<AppConfig>().storage.retention
        DataFileRetentionPolicy(
            enabled = retention.enabled,
            retentionPeriod = java.time.Duration.ofDays(retention.retentionDays),
            cleanupInterval = java.time.Duration.ofMinutes(retention.cleanupIntervalMinutes),
            batchSize = retention.batchSize,
            maxBatchesPerRun = retention.maxBatchesPerRun
        )
    }
    single {
        DataFileRetentionService(
            repository = get(),
            policy = get(),
            clock = Clock.systemUTC()
        )
    }
    single { DataFileRetentionManager(service = get(), policy = get()) }
    single {
        DataFileRetentionAdminService(
            policy = get(),
            service = get(),
            manager = get()
        )
    }

    single { DataFileUploadPolicy() }

    single { DataFileFormatRegistry() }

    single<DataFileReaderRegistry> { defaultTabularDataFileReaderRegistry() }
    single<DataFileWriterRegistry> { defaultTabularDataFileWriterRegistry() }

    single<DataFileFormatDetector> { DefaultDataFileFormatDetector(get()) }

    single {
        DataFileDeletionService(
            repository = get(),
            storage = get(),
            clock = Clock.systemUTC()
        )
    }

    single {
        AnalysisArtifactService(
            repository = get(),
            storage = get(),
            formatRegistry = get(),
            fileNameSanitizer = get(),
            storageKeyGenerator = get(),
            clock = Clock.systemUTC()
        )
    }

    single {
        val python = get<AppConfig>().task.analysis.pythonHttp
        val handlers = buildList {
            if (python.enabled) add(
                PythonHttpAnalysisHandler(
                    PythonHttpAnalysisHandlerConfig(
                        id = python.handlerId,
                        analysisType = python.analysisType,
                        endpoint = URI.create(python.endpoint),
                        outputFormat = me.moiclathy.edb.core.datafile.model.DataFileFormat.valueOf(
                            python.outputFormat.uppercase()
                        ),
                        bearerToken = python.bearerToken,
                        connectTimeout = java.time.Duration.ofMillis(python.connectTimeoutMs),
                        requestTimeout = java.time.Duration.ofMillis(python.requestTimeoutMs)
                    )
                )
            )
            get<AppConfig>().task.analysis.externalHttpHandlers
                .filter { it.enabled }
                .forEach { external ->
                    add(if (external.analysisType == "tabular-statistics") PythonStatisticsAnalysisHandler(
                        id = external.handlerId,
                        analysisType = external.analysisType,
                        endpoint = URI.create(external.endpoint),
                        outputFormat = me.moiclathy.edb.core.datafile.model.DataFileFormat.valueOf(external.outputFormat.uppercase()),
                        bearerToken = external.bearerToken,
                        connectTimeout = java.time.Duration.ofMillis(external.connectTimeoutMs),
                        requestTimeout = java.time.Duration.ofMillis(external.requestTimeoutMs)
                    ) else ExternalHttpAnalysisHandler(
                        ExternalHttpAnalysisHandlerConfig(
                            id = external.handlerId,
                            analysisType = external.analysisType,
                            tool = external.tool,
                            endpoint = URI.create(external.endpoint),
                            outputFormat = me.moiclathy.edb.core.datafile.model.DataFileFormat.valueOf(
                                external.outputFormat.uppercase()
                            ),
                            bearerToken = external.bearerToken,
                            connectTimeout = java.time.Duration.ofMillis(external.connectTimeoutMs),
                            requestTimeout = java.time.Duration.ofMillis(external.requestTimeoutMs)
                        )
                    ))
                }
        }
        AnalysisHandlerRegistry(handlers)
    }

    single { DataFileTabularProfileService(files = get(), readers = get()) }

    single {
        AnalysisExecutionService(
            handlers = get(),
            repository = get(),
            storage = get(),
            artifactService = get()
        )
    }

    single { AnalysisTaskHandler(executionService = get()) }

    single { AnalysisWorkflowStepHandler(executionService = get()) }

    single {
        AnalysisTaskApplicationService(
            runtime = get(),
            handlers = get(),
            dataFiles = get(),
            projects = get(),
            permissionChecker = get(),
            clock = Clock.systemUTC()
        )
    }

    single {
        val generators = buildList {
            add(TabularSummaryReportGenerator(get()))
            val rHttp = get<AppConfig>().task.report.rHttp
            if (rHttp.enabled) add(RHttpReportGenerator(RHttpReportGeneratorConfig(
                id = rHttp.generatorId,
                endpoint = URI.create(rHttp.endpoint),
                supportedFormats = rHttp.outputFormats.mapTo(linkedSetOf()) { name ->
                    me.moiclathy.edb.core.datafile.model.DataFileFormat.valueOf(name.uppercase())
                },
                bearerToken = rHttp.bearerToken,
                connectTimeout = java.time.Duration.ofMillis(rHttp.connectTimeoutMs),
                requestTimeout = java.time.Duration.ofMillis(rHttp.requestTimeoutMs)
            )))
        }
        ReportGeneratorRegistry(generators)
    }

    single { ReportDefinitionRegistry() }

    single<ReportDefinitionParser> { JsonReportDefinitionParser() }

    single {
        DirectoryReportDefinitionLoader(
            parser = get(),
            definitions = get(),
            generators = get()
        )
    }

    single {
        ReportArtifactService(
            registry = get(),
            repository = get(),
            storage = get(),
            formatRegistry = get(),
            fileNameSanitizer = get(),
            storageKeyGenerator = get(),
            clock = Clock.systemUTC()
        )
    }

    single { ReportTaskHandler(definitions = get(), artifactService = get()) }

    single { ReportWorkflowStepHandler(definitions = get(), artifactService = get()) }

    single {
        ReportTaskApplicationService(
            runtime = get(),
            definitions = get(),
            dataFiles = get(),
            projects = get(),
            permissionChecker = get(),
            clock = Clock.systemUTC()
        )
    }

    single<DataFileNameSanitizer> { DefaultDataFileNameSanitizer() }

    single<DataFileStorageKeyGenerator> { DefaultDataFileStorageKeyGenerator() }

    single {
        DataFileUploadService(
            storage = get(),
            repository = get(),
            policy = get(),
            fileNameSanitizer = get(),
            formatDetector = get(),
            storageKeyGenerator = get(),
            clock = Clock.systemUTC()
        )
    }
    single { ResumableUploadService(sessions=get(),store=get(),uploads=get(),projects=get(),permissions=get(),policy=get(),clock=Clock.systemUTC()) }

    single {
        DataFileApplicationService(
            uploadService = get(),
            repository = get(),
            storage = get(),
            projectRepository = get(),
            permissionChecker = get(),
            deletionService = get()
        )
    }

    single {
        DataFileConversionRegistry(
            listOf(
                CsvToJsonDataFileConverter(get(), get()),
                JsonToCsvDataFileConverter(get(), get()),
                CsvToXlsxDataFileConverter(get(), get()),
                XlsxToCsvDataFileConverter(get(), get())
            )
        )
    }

    single {
        DataFileConversionService(
            registry = get(),
            repository = get(),
            storage = get(),
            fileNameSanitizer = get(),
            storageKeyGenerator = get(),
            clock = Clock.systemUTC()
        )
    }

    single { DataFileConversionTaskHandler(conversionService = get()) }

    single { DataFileConversionWorkflowStepHandler(conversionService = get()) }

    single {
        DataFileConversionApplicationService(
            runtime = get(),
            repository = get(),
            registry = get(),
            projectRepository = get(),
            permissionChecker = get(),
            clock = Clock.systemUTC()
        )
    }
}
