package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.task.repository.MySqlTaskRepository
import me.moiclathy.edb.core.task.repository.TaskRepository
import me.moiclathy.edb.core.task.checkpoint.MySqlTaskCheckpointRepository
import me.moiclathy.edb.core.task.checkpoint.TaskCheckpointRepository
import me.moiclathy.edb.core.task.execution.DefaultTaskExecutor
import me.moiclathy.edb.core.task.execution.TaskExecutor
import me.moiclathy.edb.core.task.execution.TaskHandlerRegistry
import me.moiclathy.edb.core.task.queue.PersistentTaskQueue
import me.moiclathy.edb.core.task.queue.TaskQueue
import me.moiclathy.edb.core.task.worker.TaskWorker
import me.moiclathy.edb.core.task.cancellation.TaskCancellationService
import me.moiclathy.edb.core.task.progress.TaskProgressService
import me.moiclathy.edb.core.task.error.DefaultTaskErrorClassifier
import me.moiclathy.edb.core.task.error.TaskErrorClassifier
import me.moiclathy.edb.core.task.audit.MySqlTaskAuditRepository
import me.moiclathy.edb.core.task.audit.TaskAuditRepository
import me.moiclathy.edb.core.task.audit.TaskAuditService
import me.moiclathy.edb.core.task.runtime.TaskLifecycleEventPublisher
import me.moiclathy.edb.core.task.runtime.TaskRunner
import me.moiclathy.edb.core.task.runtime.TaskRuntime
import me.moiclathy.edb.core.task.scheduler.CoroutineTaskScheduler
import me.moiclathy.edb.core.task.scheduler.TaskScheduler
import me.moiclathy.edb.core.task.scheduler.TaskSchedulerConfig
import me.moiclathy.edb.core.task.pipeline.PipelineExecutor
import me.moiclathy.edb.core.task.pipeline.PipelineRegistry
import me.moiclathy.edb.core.task.pipeline.PipelineTaskHandler
import me.moiclathy.edb.core.task.pipeline.SequentialPipelineExecutor
import me.moiclathy.edb.core.task.application.TaskApplicationService
import me.moiclathy.edb.core.task.api.event.TaskEventBusApi
import me.moiclathy.edb.core.task.api.event.TaskEventBusClient
import me.moiclathy.edb.core.task.application.TaskSchedulerApplicationService
import me.moiclathy.edb.core.task.metrics.MySqlTaskMetricsRepository
import me.moiclathy.edb.core.task.metrics.TaskMetricsApplicationService
import me.moiclathy.edb.core.task.metrics.TaskMetricsRepository
import me.moiclathy.edb.core.task.application.TaskRetryAdminService
import me.moiclathy.edb.core.task.retention.MySqlTaskRetentionRepository
import me.moiclathy.edb.core.task.retention.TaskRetentionManager
import me.moiclathy.edb.core.task.retention.TaskRetentionPolicy
import me.moiclathy.edb.core.task.retention.TaskRetentionRepository
import me.moiclathy.edb.core.task.retention.TaskRetentionService
import me.moiclathy.edb.core.task.retention.TaskRetentionAdminService
import me.moiclathy.edb.core.task.workflow.CopyContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.DirectoryWorkflowLoader
import me.moiclathy.edb.core.task.workflow.JsonWorkflowDriver
import me.moiclathy.edb.core.task.workflow.JsonWorkflowParser
import me.moiclathy.edb.core.task.workflow.KotlinxJsonWorkflowParser
import me.moiclathy.edb.core.task.workflow.SetContextWorkflowStepHandler
import me.moiclathy.edb.core.task.workflow.WorkflowCompiler
import me.moiclathy.edb.core.task.workflow.WorkflowStepHandlerRegistry
import me.moiclathy.edb.core.task.workflow.ir.LegacyWorkflowIrAdapter
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCompiler
import me.moiclathy.edb.core.task.workflow.ir.JsonWorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrValidator
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.schema.BuiltInWorkflowStepSchemas
import me.moiclathy.edb.core.task.workflow.schema.WorkflowJsonSchemaValidator
import me.moiclathy.edb.core.task.workflow.schema.WorkflowStepSchemaRegistry
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.core.task.workflow.phrase.WorkflowPhrasePatternRegistry
import me.moiclathy.edb.core.task.workflow.template.DirectoryWorkflowTemplateLoader
import me.moiclathy.edb.core.task.workflow.template.JsonWorkflowTemplateCodec
import me.moiclathy.edb.core.task.workflow.template.WorkflowTemplateRegistry
import me.moiclathy.edb.core.task.workflow.definition.MySqlProjectWorkflowDefinitionRepository
import me.moiclathy.edb.core.task.workflow.definition.ProjectWorkflowDefinitionRepository
import me.moiclathy.edb.core.task.workflow.definition.ProjectWorkflowDefinitionService
import me.moiclathy.edb.core.task.workflow.definition.WorkflowDefinitionTaskApplicationService
import me.moiclathy.edb.core.task.workflow.definition.WorkflowDefinitionTaskHandler
import me.moiclathy.edb.core.task.workflow.phrase.MySqlProjectPhraseDefinitionRepository
import me.moiclathy.edb.core.task.workflow.phrase.ProjectPhraseDefinitionRepository
import me.moiclathy.edb.core.task.workflow.phrase.ProjectPhraseDefinitionService
import me.moiclathy.edb.core.task.workflow.phrase.PhraseDefinitionTaskService
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionTaskHandler
import me.moiclathy.edb.core.datafile.conversion.DataFileConversionWorkflowStepHandler
import me.moiclathy.edb.core.datafile.report.ReportTaskHandler
import me.moiclathy.edb.core.datafile.report.ReportWorkflowStepHandler
import me.moiclathy.edb.core.datafile.analysis.AnalysisTaskHandler
import me.moiclathy.edb.core.datafile.analysis.AnalysisWorkflowStepHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import java.time.Clock

val taskModule = module {
    singleOf(::MySqlTaskAuditRepository) {
        bind<TaskAuditRepository>()
    }

    singleOf(::MySqlTaskRepository) {
        bind<TaskRepository>()
    }

    singleOf(::MySqlTaskCheckpointRepository) {
        bind<TaskCheckpointRepository>()
    }

    singleOf(::MySqlTaskMetricsRepository) {
        bind<TaskMetricsRepository>()
    }

    singleOf(::MySqlTaskRetentionRepository) {
        bind<TaskRetentionRepository>()
    }

    single {
        val retention = get<AppConfig>().task.retention
        TaskRetentionPolicy(
            enabled = retention.enabled,
            retentionPeriod = java.time.Duration.ofDays(retention.retentionDays),
            cleanupInterval = java.time.Duration.ofMinutes(retention.cleanupIntervalMinutes),
            batchSize = retention.batchSize,
            maxBatchesPerRun = retention.maxBatchesPerRun
        )
    }

    single {
        TaskRetentionService(
            repository = get(),
            policy = get(),
            clock = Clock.systemUTC()
        )
    }

    single {
        TaskRetentionManager(
            service = get(),
            policy = get()
        )
    } withOptions {
        onClose { manager -> manager?.close() }
    }

    single {
        TaskRetentionAdminService(
            policy = get(),
            retentionService = get(),
            manager = get()
        )
    }

    singleOf(::PersistentTaskQueue) {
        bind<TaskQueue>()
    }

    single { PipelineRegistry() }

    single {
        WorkflowStepHandlerRegistry(
            listOf(
                SetContextWorkflowStepHandler(),
                CopyContextWorkflowStepHandler(),
                get<DataFileConversionWorkflowStepHandler>(),
                get<AnalysisWorkflowStepHandler>(),
                get<ReportWorkflowStepHandler>()
            )
        )
    }

    single<JsonWorkflowParser> { KotlinxJsonWorkflowParser() }

    single { WorkflowIrValidator() }
    single<WorkflowIrCodec> { JsonWorkflowIrCodec(validator = get()) }
    single { WorkflowJsonSchemaValidator() }
    single { WorkflowStepSchemaRegistry(BuiltInWorkflowStepSchemas.all(), get()) }
    single { WorkflowPhrasePatternRegistry.builtIns() }
    single { WorkflowStepCatalogService(handlers = get(), schemas = get(), analysisHandlers = get(), phrasePatterns = get()) }
    single { WorkflowIrCompiler(handlers = get(), validator = get(), schemas = get(), parameterValidator = get()) }
    single { WorkflowIrApplicationService(codec = get(), compiler = get(), pipelineRegistry = get()) }
    single { WorkflowTemplateRegistry() }
    single { JsonWorkflowTemplateCodec(workflowCodec = get()) }
    single { DirectoryWorkflowTemplateLoader(codec = get(), registry = get()) }
    single<ProjectWorkflowDefinitionRepository> { MySqlProjectWorkflowDefinitionRepository() }
    single<ProjectPhraseDefinitionRepository> { MySqlProjectPhraseDefinitionRepository() }
    single { ProjectPhraseDefinitionService(repository=get(),projects=get(),permissions=get(),audit=get()) }
    single { PhraseDefinitionTaskService(phrases=get(),workflowDefinitions=get(),tasks=get(),handlers=get()) }
    single { ProjectWorkflowDefinitionService(repository = get(), workflowService = get(), codec = get()) }
    single { LegacyWorkflowIrAdapter() }

    single { WorkflowCompiler(handlers = get(), irCompiler = get()) }

    single { JsonWorkflowDriver(parser = get(), compiler = get()) }

    single { DirectoryWorkflowLoader(driver = get(), pipelineRegistry = get()) }

    singleOf(::SequentialPipelineExecutor) {
        bind<PipelineExecutor>()
    }

    singleOf(::PipelineTaskHandler)
    singleOf(::WorkflowDefinitionTaskHandler)

    single {
        TaskHandlerRegistry(
            listOf(
                get<PipelineTaskHandler>(),
                get<WorkflowDefinitionTaskHandler>(),
                get<DataFileConversionTaskHandler>(),
                get<AnalysisTaskHandler>(),
                get<ReportTaskHandler>()
            )
        )
    }

    single<TaskErrorClassifier> { DefaultTaskErrorClassifier() }

    single<TaskExecutor> {
        DefaultTaskExecutor(
            registry = get(),
            repository = get(),
            clock = Clock.systemUTC(),
            errorClassifier = get(),
            eventPublisher = get(),
            checkpointRepository = get()
        )
    }

    single {
        TaskCancellationService(
            repository = get(),
            clock = Clock.systemUTC()
        )
    }

    single {
        TaskProgressService(
            repository = get(),
            clock = Clock.systemUTC()
        )
    }

    singleOf(::TaskAuditService)

    singleOf(::TaskLifecycleEventPublisher)

    single {
        TaskRuntime(
            queue = get(),
            executor = get(),
            repository = get(),
            cancellationService = get(),
            eventPublisher = get(),
            clock = Clock.systemUTC()
        )
    }

    single<TaskRunner> { get<TaskRuntime>() }

    single {
        TaskApplicationService(
            runtime = get(),
            taskRepository = get(),
            pipelineRegistry = get(),
            auditService = get(),
            projectRepository = get(),
            permissionChecker = get(),
            clock = Clock.systemUTC()
        )
    }
    single {
        WorkflowDefinitionTaskApplicationService(
            definitions = get(), runtime = get(), projects = get(), permissions = get()
        )
    }

    single {
        TaskEventBusApi(
            eventBus = get(),
            service = get(),
            clock = Clock.systemUTC()
        )
    } withOptions {
        onClose { api -> api?.close() }
    }

    single {
        TaskEventBusClient(eventBus = get())
    } withOptions {
        onClose { client -> client?.close() }
    }

    single {
        TaskWorker(runner = get())
    }

    single {
        CoroutineTaskScheduler(
            worker = get(),
            config = TaskSchedulerConfig(),
            clock = Clock.systemUTC()
        )
    } withOptions {
        onClose { scheduler -> scheduler?.close() }
    }

    single<TaskScheduler> { get<CoroutineTaskScheduler>() }

    singleOf(::TaskSchedulerApplicationService)

    single {
        TaskMetricsApplicationService(
            repository = get(),
            clock = Clock.systemUTC()
        )
    }

    single {
        TaskRetryAdminService(
            repository = get(),
            eventPublisher = get(),
            clock = Clock.systemUTC()
        )
    }
}
