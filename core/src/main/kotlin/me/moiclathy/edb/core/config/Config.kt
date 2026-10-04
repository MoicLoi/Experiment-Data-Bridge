package me.moiclathy.edb.core.config


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File




data class AppConfig(

    val server: ServerConfig = ServerConfig(),

    val database: DatabaseConfig,

    val storage: StorageConfig = StorageConfig(),

    val security: SecurityConfig = SecurityConfig(),

    val agent: AgentConfig = AgentConfig(),

    val logging: LoggingConfig = LoggingConfig(),

    val ktor: KtorConfig = KtorConfig(),

    val task: TaskConfig = TaskConfig(),

    val plugins: PluginConfig = PluginConfig(),

    val api: ApiConfig = ApiConfig(), // 对应 YAML 中的 api 节点
    val pool: PoolConfig = PoolConfig(), // 对应 YAML 中的顶级 pool 节点
    val initialAdmin: InitialAdminConfig = InitialAdminConfig() // 对应 YAML 中的顶级 initial-admin 节点
)

data class PluginConfig(
    val enabled: Boolean = true,
    val directory: String = "./plugins",
    val declarativeDirectory: String = "./plugins/adapters",
    val declarativeAllowedHosts: List<String> = listOf("127.0.0.1", "localhost"),
    val failOnError: Boolean = true
) {
    init {
        require(directory.isNotBlank()) { "Plugin directory must not be blank." }
        require(declarativeDirectory.isNotBlank()) { "Declarative plugin directory must not be blank." }
        require(declarativeAllowedHosts.isNotEmpty() && declarativeAllowedHosts.all(String::isNotBlank)) { "Declarative plugin allowed hosts must not be empty." }
    }
}

data class TaskConfig(
    val retention: TaskRetentionConfig = TaskRetentionConfig(),
    val workflow: TaskWorkflowConfig = TaskWorkflowConfig(),
    val report: TaskReportConfig = TaskReportConfig(),
    val analysis: TaskAnalysisConfig = TaskAnalysisConfig()
)

data class TaskAnalysisConfig(
    val workerMode: AnalysisWorkerMode = AnalysisWorkerMode.EXTERNAL,
    val pythonHttp: PythonHttpAnalysisConfig = PythonHttpAnalysisConfig(),
    val externalHttpHandlers: List<ExternalHttpAnalysisConfig> = emptyList(),
    val dockerWorker: DockerWorkerConfig = DockerWorkerConfig(),
    val managedDockerWorkers: List<DockerWorkerConfig> = emptyList()
) {
    init {
        val workers = enabledDockerWorkers()
        require(workers.map { it.id }.distinct().size == workers.size) {
            "Managed Docker worker ids must be unique."
        }
    }

    fun enabledDockerWorkers(): List<DockerWorkerConfig> = buildList {
        if (workerMode == AnalysisWorkerMode.MANAGED_DOCKER && dockerWorker.enabled) add(dockerWorker)
        addAll(managedDockerWorkers.filter { it.enabled })
    }
}

data class ExternalHttpAnalysisConfig(
    val enabled: Boolean = true,
    val handlerId: String,
    val analysisType: String,
    val tool: String,
    val endpoint: String,
    val outputFormat: String = "JSON",
    val bearerToken: String? = null,
    val connectTimeoutMs: Long = 5_000,
    val requestTimeoutMs: Long = 300_000
) {
    init {
        require(handlerId.isNotBlank() && analysisType.isNotBlank() && tool.isNotBlank()) {
            "External HTTP handler id, analysis type and tool must not be blank."
        }
        require(endpoint.isNotBlank()) { "External HTTP handler endpoint must not be blank." }
        require(connectTimeoutMs > 0 && requestTimeoutMs > 0) { "External HTTP handler timeouts must be positive." }
    }
}

enum class AnalysisWorkerMode {
    EXTERNAL,
    MANAGED_DOCKER
}

data class DockerWorkerConfig(
    val id: String = "python",
    val enabled: Boolean = true,
    val image: String = "edb/python-worker:dev",
    val workerKind: String = "python-http",
    val containerName: String = "edb-python-worker",
    val host: String = "127.0.0.1",
    val bindAddress: String = "127.0.0.1",
    val hostPort: Int = 8000,
    val containerPort: Int = 8000,
    val healthPath: String = "/health",
    val bearerToken: String? = System.getenv("EDB_PYTHON_WORKER_TOKEN"),
    val environment: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val maxUploadBytes: Long = 10L * 1024 * 1024 * 1024,
    val cpuLimit: Double = 2.0,
    val memoryLimitBytes: Long = 4L * 1024 * 1024 * 1024,
    val temporaryStorageBytes: Long = 2L * 1024 * 1024 * 1024,
    val healthRequestTimeoutMs: Long = 3_000,
    val readinessTimeoutMs: Long = 60_000,
    val readinessPollIntervalMs: Long = 500,
    val stopTimeoutMs: Long = 20_000
)

data class PythonHttpAnalysisConfig(
    val enabled: Boolean = false,
    val handlerId: String = "python-http",
    val analysisType: String = "python",
    val endpoint: String = "http://127.0.0.1:8000/analyze",
    val outputFormat: String = "JSON",
    val bearerToken: String? = System.getenv("EDB_PYTHON_WORKER_TOKEN"),
    val connectTimeoutMs: Long = 5_000,
    val requestTimeoutMs: Long = 300_000
)

data class TaskReportConfig(
    val enabled: Boolean = false,
    val directory: String = "./config/reports",
    val rHttp: RHttpReportGeneratorSettings = RHttpReportGeneratorSettings()
) {
    init {
        require(directory.isNotBlank()) { "Report definition directory must not be blank." }
    }
}

data class RHttpReportGeneratorSettings(
    val enabled: Boolean = false,
    val generatorId: String = "r-tabular-summary-report",
    val endpoint: String = "http://127.0.0.1:8001/analyze",
    val outputFormats: List<String> = listOf("PDF"),
    val bearerToken: String? = System.getenv("EDB_R_WORKER_TOKEN"),
    val connectTimeoutMs: Long = 5_000,
    val requestTimeoutMs: Long = 300_000
) {
    init {
        require(generatorId.isNotBlank() && endpoint.isNotBlank()) {
            "R HTTP report generator id and endpoint must not be blank."
        }
        require(outputFormats.isNotEmpty() && outputFormats.none { it.isBlank() }) {
            "R HTTP report generator output formats must not be empty."
        }
        require(connectTimeoutMs > 0 && requestTimeoutMs > 0) {
            "R HTTP report generator timeouts must be positive."
        }
    }
}

data class TaskWorkflowConfig(
    val enabled: Boolean = false,
    val directory: String = "./config/workflows",
    val templateDirectory: String = "./config/workflow-templates"
) {
    init {
        require(directory.isNotBlank()) { "Workflow directory must not be blank." }
        require(templateDirectory.isNotBlank()) { "Workflow template directory must not be blank." }
    }
}

data class TaskRetentionConfig(
    val enabled: Boolean = false,
    val retentionDays: Long = 90,
    val cleanupIntervalMinutes: Long = 60,
    val batchSize: Int = 500,
    val maxBatchesPerRun: Int = 10
)


data class ServerConfig(

    val port:Int = 25560,

    val host:String = "0.0.0.0",

    val contextPath:String="/api/v1"

)

data class ApiConfig(
    val contextPath: String = "/api/v1"
)



data class DatabaseConfig(

    val mysql:MySQLConfig

)



data class MySQLConfig(

    val host:String,

    val port:Int,

    val database:String,

    val username:String,

    val password:String,

    val pool:PoolConfig = PoolConfig()

)



data class PoolConfig(
    val maximumPoolSize: Int = 20,
    val minimumIdle: Int = 5,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000
)



data class StorageConfig(

    val dataPath:String="./data",

    val resultsPath:String="./data/results",

    val reportsPath:String="./data/reports",

    val orphanCleanup: DataFileOrphanCleanupConfig = DataFileOrphanCleanupConfig(),

    val retention: DataFileRetentionConfig = DataFileRetentionConfig()

)

data class DataFileOrphanCleanupConfig(
    val enabled: Boolean = true,
    val minimumAgeHours: Long = 24,
    val cleanupIntervalMinutes: Long = 360,
    val maxDeletesPerRun: Int = 1_000
)

data class DataFileRetentionConfig(
    val enabled: Boolean = true,
    val retentionDays: Long = 90,
    val cleanupIntervalMinutes: Long = 1440,
    val batchSize: Int = 500,
    val maxBatchesPerRun: Int = 10
)



data class SecurityConfig(

    val initialAdmin: InitialAdminConfig = InitialAdminConfig(),

    val jwt: JwtConfig = JwtConfig()

)


data class JwtConfig(
    val secret: String = System.getenv("JWT_SECRET") ?: "this-is-a-secure-default-secret-key-with-more-than-32-bytes",
    val issuer: String = "experiment-data-bridge",
    val audience: String = "edb-client",
    val expiresIn: Long = 86400000
)



data class InitialAdminConfig(

    val enabled:Boolean=true,

    val username:String="admin",

    val password:String="admin123",

    val email:String="admin@experiment-bridge.com",

    val fullName:String="系统管理员"

)



data class AgentConfig(

    val defaultProvider:String="local",

    val local:LocalAgentConfig=LocalAgentConfig(),

    val model: AgentModelRuntimeConfig = AgentModelRuntimeConfig(),

    val retention: AgentRetentionConfig = AgentRetentionConfig()

)

data class AgentRetentionConfig(
    val enabled: Boolean = false,
    val retentionDays: Long = 90,
    val cleanupIntervalMinutes: Long = 60,
    val initialDelayMinutes: Long = 5,
    val batchSize: Int = 100,
    val maxBatchesPerRun: Int = 10
) {
    init {
        require(retentionDays > 0 && cleanupIntervalMinutes > 0 && initialDelayMinutes >= 0)
        require(batchSize > 0 && maxBatchesPerRun > 0)
    }
}

data class AgentModelRuntimeConfig(
    val enabled: Boolean = false,
    val defaultProfile: String = "fake-workflow-planner",
    val fallbackToFake: Boolean = false,
    val profiles: List<AgentModelProfileConfig> = emptyList(),
    val quota: AgentTokenQuotaConfig = AgentTokenQuotaConfig()
) {
    init {
        require(defaultProfile.isNotBlank()) { "Default Agent model profile must not be blank." }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Agent model profile ids must be unique." }
        require(!enabled || defaultProfile == "fake-workflow-planner" || profiles.any { it.id == defaultProfile }) {
            "Enabled Agent default model profile must be configured."
        }
    }
}

data class AgentTokenQuotaConfig(
    val enabled: Boolean = false,
    val dailyUserTokens: Long = 100_000,
    val dailyProjectTokens: Long = 1_000_000
) {
    init {
        require(dailyUserTokens > 0 && dailyProjectTokens > 0) {
            "Agent Token quotas must be greater than zero."
        }
    }
}

data class AgentModelProfileConfig(
    val id: String,
    val provider: String,
    val modelName: String,
    val baseUrl: String? = null,
    val apiKeyEnv: String? = null,
    val temperature: Double = 0.0,
    val maxOutputTokens: Int = 4096,
    val timeoutMs: Long = 60_000,
    val toolCallingEnabled: Boolean = true,
    val structuredOutputEnabled: Boolean = true,
    val maxConcurrency: Int = 4,
    val requestsPerMinute: Int = 60,
    val circuitBreakerFailures: Int = 5,
    val circuitBreakerOpenMs: Long = 30_000
)



data class LocalAgentConfig(

    val url:String="http://localhost:11434/api/generate",

    val model:String="llama2:70b"

)



data class LoggingConfig(

    val level:Map<String,String> = emptyMap(),

    val debugMode:Boolean = false

)

data class KtorConfig(

    val deployment: KtorDeploymentConfig = KtorDeploymentConfig(),

    val application: KtorApplicationConfig = KtorApplicationConfig()

)


data class KtorDeploymentConfig(

    val port:Int? = null,

    val host:String? = null

)


data class KtorApplicationConfig(

    val modules:List<String> = emptyList()

)



fun loadConfig():AppConfig {


    val mapper =
        ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            .findAndRegisterModules()


    mapper.propertyNamingStrategy =
        PropertyNamingStrategies.KEBAB_CASE



    val external =
        File("config/application.yml")


    return if(external.exists()){

        mapper.readValue(external)

    }else{
        val stream =
            Thread.currentThread()
                .contextClassLoader
                .getResourceAsStream(
                    "application.yml"
                )
                ?: error("application.yml not found")


        mapper.readValue<AppConfig>(stream)
    }

}
