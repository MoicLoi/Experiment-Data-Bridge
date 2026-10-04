package me.moiclathy.edb.web.di

import me.moiclathy.edb.agent.application.AgentAuthorization
import me.moiclathy.edb.agent.application.AgentChatMemory
import me.moiclathy.edb.agent.application.AgentAsyncPlanningService
import me.moiclathy.edb.agent.application.AgentPlanningApplicationService
import me.moiclathy.edb.agent.application.AgentProposalApprovalService
import me.moiclathy.edb.agent.application.AgentProposalSubmissionService
import me.moiclathy.edb.agent.application.AgentOperationsService
import me.moiclathy.edb.agent.application.AgentRetentionManager
import me.moiclathy.edb.agent.application.AgentRetentionPolicy
import me.moiclathy.edb.agent.application.AgentTokenQuotaPolicy
import me.moiclathy.edb.agent.application.AgentTokenQuotaService
import me.moiclathy.edb.agent.application.CoreAgentAuthorization
import me.moiclathy.edb.agent.audit.AgentAuditService
import me.moiclathy.edb.agent.event.AgentEventPublisher
import me.moiclathy.edb.agent.event.EventBusAgentEventPublisher
import me.moiclathy.edb.agent.event.EventPublishingWorkflowPlanningObserver
import me.moiclathy.edb.agent.model.AgentDefinition
import me.moiclathy.edb.agent.model.AgentDefinitionRegistry
import me.moiclathy.edb.agent.model.AgentPolicyRegistry
import me.moiclathy.edb.agent.persistence.AgentRepository
import me.moiclathy.edb.agent.persistence.MySqlAgentRepository
import me.moiclathy.edb.agent.persistence.AgentOperationsRepository
import me.moiclathy.edb.agent.persistence.MySqlAgentOperationsRepository
import me.moiclathy.edb.agent.persistence.AgentTokenQuotaRepository
import me.moiclathy.edb.agent.persistence.MySqlAgentTokenQuotaRepository
import me.moiclathy.edb.agent.persistence.PersistentWorkflowPlanningObserver
import me.moiclathy.edb.agent.planning.CompositeWorkflowPlanningObserver
import me.moiclathy.edb.agent.planning.WorkflowPlanningAgent
import me.moiclathy.edb.agent.policy.AgentCapability
import me.moiclathy.edb.agent.policy.AgentPolicy
import me.moiclathy.edb.agent.provider.AgentChatModel
import me.moiclathy.edb.agent.provider.AgentChatModelRegistry
import me.moiclathy.edb.agent.provider.AgentModelProfileCatalogService
import me.moiclathy.edb.agent.provider.AgentModelProfile
import me.moiclathy.edb.agent.provider.AgentModelProvider
import me.moiclathy.edb.agent.provider.AgentModelProviderRegistry
import me.moiclathy.edb.agent.provider.AgentSecretResolver
import me.moiclathy.edb.agent.provider.EnvironmentAgentSecretResolver
import me.moiclathy.edb.agent.provider.FakeWorkflowPlanningChatModel
import me.moiclathy.edb.agent.provider.LangChain4jOpenAiModelProvider
import me.moiclathy.edb.agent.provider.RoutingAgentChatModel
import me.moiclathy.edb.agent.tools.WorkflowPlanningTools
import me.moiclathy.edb.core.event.EventBus
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.task.workflow.application.WorkflowIrApplicationService
import me.moiclathy.edb.core.task.workflow.catalog.WorkflowStepCatalogService
import me.moiclathy.edb.core.task.workflow.ir.WorkflowIrCodec
import org.koin.dsl.module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import java.net.URI
import java.time.Duration

val agentModule = module {
    single<AgentRepository> { MySqlAgentRepository() }
    single<AgentOperationsRepository> { MySqlAgentOperationsRepository() }
    single<AgentTokenQuotaRepository> { MySqlAgentTokenQuotaRepository() }
    single {
        val retention = get<AppConfig>().agent.retention
        AgentRetentionPolicy(
            retentionPeriod = Duration.ofDays(retention.retentionDays),
            batchSize = retention.batchSize,
            maxBatches = retention.maxBatchesPerRun,
            enabled = retention.enabled,
            cleanupInterval = Duration.ofMinutes(retention.cleanupIntervalMinutes),
            initialDelay = Duration.ofMinutes(retention.initialDelayMinutes)
        )
    }
    single { AgentOperationsService(get(), get()) }
    single { AgentRetentionManager(get(), get()) } withOptions { onClose { it?.close() } }
    single {
        val quota = get<AppConfig>().agent.model.quota
        AgentTokenQuotaPolicy(quota.enabled, quota.dailyUserTokens, quota.dailyProjectTokens)
    }
    single { AgentTokenQuotaService(get(), get()) }
    single { AgentAuditService(get()) }
    single { AgentChatMemory(get()) }
    single<AgentAuthorization> { CoreAgentAuthorization(get(), get()) }
    single<AgentEventPublisher> { EventBusAgentEventPublisher(get<EventBus>()) }

    single {
        AgentDefinition(
            id = "workflow-planner",
            version = "1",
            description = "Plans and strictly validates Workflow IR without executing it.",
            systemPrompt = "You are the EDB Workflow planning agent. Use only the provided read-only tools and return a strict Workflow proposal.",
            modelProfileId = get<AppConfig>().agent.model.let {
                if (it.enabled) it.defaultProfile else FAKE_WORKFLOW_PROFILE
            },
            enabledTools = setOf(
                WorkflowPlanningTools.LIST_STEPS,
                WorkflowPlanningTools.GET_STEP_SCHEMA,
                WorkflowPlanningTools.VALIDATE_WORKFLOW
            ),
            policyId = "workflow-planning-read-only"
        )
    }
    single {
        AgentPolicy(
            id = "workflow-planning-read-only",
            allowedCapabilities = setOf(AgentCapability.DISCOVER_STEPS, AgentCapability.VALIDATE_WORKFLOW),
            allowedToolNames = get<AgentDefinition>().enabledTools
        )
    }
    single { AgentDefinitionRegistry(listOf(get())) }
    single { AgentPolicyRegistry(listOf(get())) }
    single<AgentSecretResolver> { EnvironmentAgentSecretResolver() }
    single<List<AgentModelProvider>> {
        listOf(
            LangChain4jOpenAiModelProvider(LangChain4jOpenAiModelProvider.OPENAI, get()),
            LangChain4jOpenAiModelProvider(LangChain4jOpenAiModelProvider.OPENAI_COMPATIBLE, get())
        )
    }
    single { AgentModelProviderRegistry(get<List<AgentModelProvider>>()) }
    single { FakeWorkflowPlanningChatModel() }
    single {
        val runtime = get<AppConfig>().agent.model
        val profiles = runtime.profiles.map { configured ->
            AgentModelProfile(
                id = configured.id, providerId = configured.provider, modelName = configured.modelName,
                baseUrl = configured.baseUrl?.let(::URI), apiKeySecretRef = configured.apiKeyEnv,
                temperature = configured.temperature, maxOutputTokens = configured.maxOutputTokens,
                timeout = Duration.ofMillis(configured.timeoutMs), toolCallingEnabled = configured.toolCallingEnabled,
                structuredOutputEnabled = configured.structuredOutputEnabled, maxConcurrency = configured.maxConcurrency,
                requestsPerMinute = configured.requestsPerMinute,
                circuitBreakerFailures = configured.circuitBreakerFailures,
                circuitBreakerOpenDuration = Duration.ofMillis(configured.circuitBreakerOpenMs)
            )
        }
        AgentChatModelRegistry(
            profiles = profiles,
            providers = get(),
            builtInModels = mapOf(FAKE_WORKFLOW_PROFILE to get<FakeWorkflowPlanningChatModel>()),
            fallback = get<FakeWorkflowPlanningChatModel>().takeIf { runtime.fallbackToFake }
        )
    }
    single<AgentChatModel> { RoutingAgentChatModel(get(), get()) }
    single {
        val runtime = get<AppConfig>().agent.model
        AgentModelProfileCatalogService(
            registry = get(),
            defaultProfileId = if (runtime.enabled) runtime.defaultProfile else FAKE_WORKFLOW_PROFILE
        )
    }
    single {
        WorkflowPlanningTools(
            catalog = get<WorkflowStepCatalogService>(),
            workflowService = get<WorkflowIrApplicationService>(),
            codec = get<WorkflowIrCodec>()
        )
    }
    single {
        CompositeWorkflowPlanningObserver(
            listOf(
                PersistentWorkflowPlanningObserver(get(), get()),
                EventPublishingWorkflowPlanningObserver(get())
            )
        )
    }
    single {
        WorkflowPlanningAgent(
            model = get(),
            tools = get(),
            observer = get<CompositeWorkflowPlanningObserver>()
        )
    }
    single {
        AgentPlanningApplicationService(
            planner = get(),
            repository = get(),
            audit = get(),
            authorization = get(),
            eventPublisher = get(),
            chatMemory = get(),
            tokenQuota = get()
        )
    }
    single { AgentAsyncPlanningService(get()) } withOptions { onClose { it?.close() } }
    single {
        AgentProposalApprovalService(
            repository = get(),
            authorization = get(),
            tools = get(),
            audit = get(),
            eventPublisher = get()
        )
    }
    single {
        AgentProposalSubmissionService(
            planning = get(), approvals = get(), definitionService = get(), definitions = get(),
            taskService = get(), tasks = get(), audit = get(), eventPublisher = get()
        )
    }
}

private const val FAKE_WORKFLOW_PROFILE = "fake-workflow-planner"
