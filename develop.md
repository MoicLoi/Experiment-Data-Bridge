# EDB Demo v1 开发手册

本文面向 EDB 核心模块、HTTP 客户端和第三方插件开发者。内容以当前源码为准，覆盖模块边界、关键类依赖关系、重要 HTTP 接口、Plugin SPI、字段演进规则以及发行前检查。

## 1. 技术基线

- Kotlin/JVM，运行时 Java 21。
- Gradle 多模块工程：`plugin-api`、`core`、`agent`、`web`、`cli`。
- Ktor 提供 HTTP、JWT 与 SSE 接口，Koin 负责装配。
- MySQL + Exposed + Flyway 负责持久化。
- Kotlin Coroutines 驱动 Task、EventBus、Worker 和 SSE。
- Workflow IR 与多数结构化载荷使用 `kotlinx.serialization` JSON。
- 项目配置 YAML 由 SnakeYAML/Jackson YAML 既有实现处理；不要在插件中假定 YAML 是 Workflow IR。
- 前端为 TypeScript/Vite，构建产物由 Web 服务作为静态资源发布。

## 2. 模块依赖关系树

```text
web (Ktor 应用、路由、生命周期、DI)
├── agent (受控 Workflow 规划器)
│   └── core
│       └── plugin-api
└── core (领域模型、服务、持久化、Task/DataFile/Workflow)
    └── plugin-api (稳定且最小的第三方 SPI)

cli (独立 HTTP 客户端)
├── core
└── plugin-api

第三方 plugin.jar
└── compileOnly/implementation plugin-api
    └── 运行时由 core.DirectoryPluginLoader 装载
```

依赖规则：

1. `plugin-api` 不得依赖 `core`、`web` 或 `agent`。
2. 插件只能依赖 `plugin-api` 中公开的 DTO/SPI，不应导入 `core` 内部类型。
3. `core` 通过 Adapter 把插件契约转换成内部 Task/Workflow 契约。
4. `web` 只负责鉴权、参数转换、状态码和生命周期；业务规则应留在 Application Service。
5. `agent` 只规划和提交已批准的 Workflow，不成为新的执行器。

## 3. 关键运行链路与类依赖树

### 3.1 应用启动

```text
Application.module
├── 读取 AppConfig
├── 安装 Koin / JWT / JSON / StatusPages
├── bootstrapPlugins
│   ├── DirectoryPluginLoader
│   └── PluginRegistryAdapter
├── 加载 Workflow / Report Definition
├── 启动 TaskScheduler 生命周期
│   └── TaskRuntime
└── 注册 HTTP Routes 与 SSE
```

插件必须在 Workflow 编译和 Worker 启动前注册。因此 v1 不支持热更新；替换 JAR 后必须重启应用。

### 3.2 Task 执行

```text
TaskRoutes / TaskEventBusApi / CLI
└── TaskApplicationService
    ├── TaskRepository
    ├── TaskQueue
    └── TaskAuditService
        ↓
TaskScheduler
└── TaskRuntime
    ├── TaskQueue.claim
    ├── TaskWorker
    │   └── TaskExecutor
    │       ├── TaskHandlerRegistry
    │       ├── Retry / Timeout / Cancellation / Progress
    │       └── TaskResult / TaskError
    ├── TaskAuditService
    └── EventBus<TaskLifecycleEvent>
```

Task Handler 抛出的异常由执行层分类并持久化为失败；插件不要绕过 `PluginExecutionContext`，长任务应周期性调用 `ensureNotCancelled()` 并报告进度。

### 3.3 Workflow

```text
JSON Workflow / Workflow IR HTTP / Agent Proposal
└── WorkflowIrApplicationService
    ├── JsonWorkflowIrCodec
    ├── WorkflowIrValidator
    ├── WorkflowStepSchemaRegistry
    └── WorkflowIrCompiler
        └── WorkflowStepHandlerRegistry
            ├── Built-in Handler
            └── PluginWorkflowStepHandlerAdapter
                └── PluginWorkflowStepHandler
                    ↓
PipelineRegistry → PipelineTaskHandler → TaskExecutor
```

Step 参数 Schema 由 Catalog 暴露。Plugin API v1 可以贡献 Step Handler，但尚不能直接贡献 JSON Schema；需要可视化表单的插件步骤应同时在宿主侧注册 Schema，或等待 Catalog/Plugin Contract v2。

### 3.4 DataFile / Conversion / Analysis / Report

```text
DataFileRoutes
└── DataFileUploadService
    ├── DataFileStorage (LocalDataFileStorage)
    ├── DataFileFormatDetector
    └── DataFileRepository

ConversionRoutes → DataFileConversionService
├── DataFileConversionRegistry
├── DataFileReaderRegistry / DataFileWriterRegistry
└── TaskApplicationService

AnalysisRoutes → AnalysisApplicationService
├── AnalysisHandlerRegistry
├── External HTTP / Docker Worker Handler
└── Report/DataFile Artifact 出口

ReportRoutes → ReportArtifactService
├── ReportDefinitionRegistry
├── ReportGenerator
├── DataFileStorage
└── DataFileRepository
```

存储提交后若数据库写入失败，服务层负责补偿删除。任何新制品生成器都必须通过 Storage + Repository 出口持久化，不能只返回本地临时路径。

### 3.5 Agent

```text
AgentRoutes / Agent SSE
└── AgentPlanningApplicationService
    ├── ChatMemory
    ├── ModelProfileRouter
    │   ├── Fake Provider
    │   └── LangChain4j OpenAI-compatible Provider
    ├── WorkflowPlanningTools
    │   ├── WorkflowStepCatalogService
    │   ├── Schema 查询
    │   └── Workflow IR validate
    ├── AgentRepository / Audit / TokenQuota
    └── WorkflowProposal
        ↓ approve
AgentProposalApprovalService
        ↓ submit
AgentProposalSubmissionService
└── versioned WorkflowDefinition → Task Queue
```

Agent 权限等同于发起用户权限；Proposal 的批准与执行使用 `definitionId + version + SHA-256` 固定内容，禁止提交时重新解释模型文本。

## 4. 重要 HTTP API

基础地址默认以部署配置为准，所有业务接口统一位于 `/api/v1`。除健康检查和登录外，接口应发送：

```http
Authorization: Bearer <token>
Content-Type: application/json
```

### 4.1 基础与认证

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/health` | 无鉴权存活检查 |
| POST | `/api/v1/auth/login` | 登录并获取 Token |
| GET | `/api/v1/user/me` | 当前用户信息 |
| PUT | `/api/v1/admin/users/{username}/role` | 管理员修改角色，包含最后管理员保护 |

### 4.2 Project 与配置

| 方法 | 路径 | 用途 |
|---|---|---|
| GET/POST | `/api/v1/projects` | 项目列表/创建 |
| GET/PUT/DELETE | `/api/v1/projects/{id}` | 项目详情/更新/删除 |
| POST | `/api/v1/projects/{id}/reload` | 重载项目 |
| GET | `/api/v1/projects/{id}/configuration/export` | 导出 YAML/EDB |
| POST | `/api/v1/projects/{id}/configuration/import` | 导入配置 |
| POST | `/api/v1/projects/{id}/configuration/validate` | 校验配置 |
| GET/PUT/DELETE | `/api/v1/projects/{projectId}/metadata` | Metadata 管理 |
| GET/POST/DELETE | `/api/v1/projects/{projectId}/metadata/tags` | Tag 管理 |
| GET/POST | `/api/v1/projects/{projectId}/config/snapshots` | 快照列表/创建 |
| GET | `/api/v1/projects/{projectId}/config/snapshots/compare` | 快照比较 |

### 4.3 Task、Scheduler 与 SSE

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/v1/tasks/pipeline` | 提交 Pipeline Task |
| GET | `/api/v1/tasks/{taskId}` | 查询任务 |
| POST | `/api/v1/tasks/{taskId}/cancel` | 请求取消 |
| GET | `/api/v1/tasks/{taskId}/timeline` | 生命周期审计时间线 |
| GET | `/api/v1/projects/{projectId}/tasks` | 项目任务分页列表 |
| GET | `/api/v1/projects/{projectId}/tasks/events` | Task 生命周期 SSE |
| POST | `/api/v1/admin/tasks/{taskId}/requeue` | 管理员手动重新入队 |
| GET/POST | `/api/v1/admin/task-scheduler/*` | Scheduler 状态与启停 |
| GET | `/api/v1/admin/task-metrics` | Task 运行指标 |
| GET/POST | `/api/v1/admin/task-retention/*` | 保留策略、预估与清理 |

### 4.4 Workflow 与 Catalog

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/workflow-steps` | Step Catalog |
| GET | `/api/v1/workflow-steps/{stepType}` | Step 描述 |
| GET | `/api/v1/workflow-steps/{stepType}/schema` | 参数 JSON Schema |
| POST | `/api/v1/workflows/validate` | 严格校验 Workflow IR |
| POST | `/api/v1/workflows/compile` | 编译 Workflow IR |

### 4.5 DataFile、转换、分析与报表

| 方法 | 路径 | 用途 |
|---|---|---|
| POST/GET | `/api/v1/projects/{projectId}/data-files` | multipart 上传/文件列表 |
| GET/DELETE | `/api/v1/projects/{projectId}/data-files/{dataFileId}` | 详情/删除 |
| GET | `/api/v1/projects/{projectId}/data-files/{dataFileId}/download` | 流式下载 |
| GET | `/api/v1/projects/{projectId}/data-files/{dataFileId}/conversion-targets` | 可用转换目标 |
| GET/POST | `/api/v1/projects/{projectId}/data-files/{dataFileId}/conversions` | 派生文件/提交转换 |
| GET | `/api/v1/projects/{projectId}/analysis-handlers` | 可用 Analysis Handler |
| POST | `/api/v1/projects/{projectId}/analyses` | 提交分析任务 |
| GET | `/api/v1/projects/{projectId}/data-files/{dataFileId}/analyses` | 分析派生制品 |
| GET | `/api/v1/projects/{projectId}/reports/definitions` | Report Definition |
| GET | `/api/v1/projects/{projectId}/data-files/{dataFileId}/reports` | 报表制品 |

### 4.6 Agent 与管理接口

Agent API 提供 Session 创建/关闭、异步规划 Run、Proposal 查询/批准/提交、审计时间线与 SSE。管理员接口提供 Profile Catalog、Provider 健康、Token/调用指标、配额与 Retention。精确请求/响应结构以路由 DTO 和 OpenAPI 资源为准，客户端必须忽略未知响应字段。

现有机器可读契约：

- `/api/v1/openapi/task-0.25.yaml`
- `/api/v1/openapi/datafile-0.25.yaml`
- Workflow Step Catalog 与各 Step Schema

## 5. HTTP 契约演进规范

为了让其他软件在字段变化后仍能识别 API，必须遵守：

1. 新增响应字段：只能新增可选字段；客户端必须忽略未知字段。
2. 新增请求字段：必须可选或具有服务端默认值。
3. 重命名字段：先保留旧字段并标记废弃，至少跨一个 minor 版本双读；响应可在迁移期双写。
4. 删除字段、改变类型、改变枚举语义或必填性：属于破坏性修改，必须发布新的 API major 或新路径。
5. 枚举扩展：客户端必须有 `UNKNOWN`/fallback，不得假定集合封闭。
6. 时间使用 ISO-8601 UTC，ID 不应由客户端推断格式。
7. 错误响应保持稳定的机器码、消息和 request/correlation ID；自动化软件按错误码而非中文消息分支。
8. OpenAPI、DTO、CLI 和前端类型必须在同一提交更新；CI 至少执行契约解析与兼容性检查。
9. Workflow IR 必须携带 `schemaVersion`；Step 使用 `type + version`，不得仅凭显示名称识别。
10. 外部 Worker 协议使用明确版本和 JSON Content-Type；未知字段应忽略，缺失必填字段应返回结构化错误。

## 6. Plugin Contract v1

### 6.1 插件如何被识别

当前实现**不读取 `plugin.json`**。一个 JAR 被识别为 EDB 插件需要同时满足：

1. JAR 直接放在配置的 `plugins.directory`，默认 `./plugins`；不会递归扫描子目录。
2. JAR 中恰好存在一个 `EdbPlugin` ServiceLoader Provider。
3. 存在文件：
   `META-INF/services/me.moiclathy.edb.plugin.api.EdbPlugin`
4. 文件内容是实现类全限定名，例如：

```text
com.example.edb.ExamplePlugin
```

5. `descriptor.apiVersion` 与宿主兼容。
6. Plugin ID、Task Type、Workflow Step Type 不与已加载插件或内置 Registry 冲突。

配置示例：

```yaml
plugins:
  enabled: true
  directory: ./plugins
  fail-on-error: true
```

`fail-on-error=true` 适合生产环境：任一插件失败即阻止错误配置的服务启动。Demo 可设为 `false`，但必须检查启动日志。

### 6.2 描述字段

```kotlin
PluginDescriptor(
    id = "acme.analysis",
    name = "ACME Analysis",
    version = "1.0.0",
    apiVersion = EdbPluginApi.VERSION,
    vendor = "ACME",
    description = "Calls the ACME analysis API"
)
```

约束：

- `id`：`[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*`，发布后永久稳定。
- `version`：语义化版本 `major.minor.patch`。
- `apiVersion`：`major.minor`，当前为 `1.0`。
- `vendor`、`description` 可空，但非空时不能是空白字符串。
- 宿主兼容规则：major 必须相等，插件要求的 minor 不得高于宿主 minor。

### 6.3 最小插件实现

```kotlin
package com.example.edb

import me.moiclathy.edb.plugin.api.*

class ExamplePlugin : EdbPlugin {
    override val descriptor = PluginDescriptor(
        id = "example.remote",
        name = "Example Remote Adapter",
        version = "1.0.0",
        apiVersion = EdbPluginApi.VERSION,
        vendor = "Example"
    )

    override fun initialize(context: PluginContext) {
        context.logger.log(PluginLogLevel.INFO, "Example plugin initialized", null)
    }

    override fun contributions() = PluginContributions(
        taskHandlers = listOf(RemoteTaskHandler()),
        workflowStepHandlers = listOf(RemoteWorkflowStepHandler())
    )
}
```

插件构建应依赖与目标宿主一致的 `plugin-api`：

```kotlin
dependencies {
    compileOnly(files("libs/plugin-api.jar"))
}
```

不要把 `plugin-api` 打进 fat JAR；宿主 ClassLoader 必须拥有唯一的契约类定义。

### 6.4 外部软件 Task API 适配

```kotlin
class RemoteTaskHandler : PluginTaskHandler {
    override val taskType = "example.remote.analysis"

    override suspend fun execute(
        request: PluginTaskRequest,
        context: PluginExecutionContext
    ): PluginTaskResult {
        context.ensureNotCancelled()
        context.reportProgress(10, "Submitting remote analysis")

        // 在此调用第三方 HTTP API。Token 从 PluginContext.properties 或安全配置注入，
        // 不要写入 descriptor、metadata、日志或异常消息。

        context.ensureNotCancelled()
        context.reportProgress(100, "Remote analysis completed")
        return PluginTaskResult(
            outputPath = "remote://result/123",
            contentType = "application/json",
            metadata = mapOf("provider" to "example", "remoteId" to "123")
        )
    }
}
```

`taskType` 最长 50 字符，匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,99}`。建议采用反向域/厂商前缀，发布后不要改名。Task Request v1 提供 task/project/user/attempt/configSnapshot；外部请求应带幂等键（推荐 Task ID + attempt），设置连接/读取超时，并把可重试错误与永久错误区分开。

### 6.5 Workflow Step 适配

```kotlin
class RemoteWorkflowStepHandler : PluginWorkflowStepHandler {
    override val type = "example.remote.lookup"

    override suspend fun execute(request: PluginWorkflowStepRequest): PluginWorkflowStepResult {
        val query = request.config["query"]
            ?: return PluginWorkflowStepResult.Failure("MISSING_QUERY", "query is required")

        return PluginWorkflowStepResult.Success(
            mapOf("${request.stepId}.result" to query.uppercase())
        )
    }
}
```

`config` 与 `context` 在 v1 都是 `Map<String, String>`。不要把复杂嵌套 JSON 强塞进无版本字符串；确需复杂参数时，应定义内容类型/子 schema 字段并准备迁移到 Plugin Contract v2 的 `JsonObject`。

### 6.6 生命周期与资源管理

- 构造函数：只做轻量对象初始化。
- `initialize(context)`：读取宿主传入属性、创建 HTTP Client/连接池、执行快速配置校验。
- `contributions()`：返回稳定的 Handler 实例；不能依赖每次调用产生副作用。
- `close()`：关闭 HTTP Client、线程池和文件句柄。
- 插件由独立 `URLClassLoader` 加载，并在应用停止时按逆序关闭。
- 不要创建不可控的全局线程；协程/请求结束后不得泄漏后台工作。

### 6.7 装载失败代码

| 代码 | 含义 |
|---|---|
| `NO_PROVIDER` | 没有 ServiceLoader 声明 |
| `MULTIPLE_PROVIDERS` | 单个 JAR 声明多个 EdbPlugin |
| `INVALID_DESCRIPTOR` | 描述字段无效或读取失败 |
| `API_INCOMPATIBLE` | Plugin API 版本不兼容 |
| `INVALID_CONTRIBUTION` | Handler 类型非法或 JAR 内重复 |
| `DUPLICATE_PLUGIN` | Plugin ID 重复 |
| `DUPLICATE_CONTRIBUTION` | Task/Step 类型重复 |
| `INITIALIZATION_FAILED` | initialize 失败 |
| `LOAD_FAILED` | 类加载或 ServiceLoader 失败 |

### 6.8 Plugin 字段变更与兼容性

这是第三方软件能否持续被识别的核心规则：

- 不要直接修改已发布 data class 主构造器并仍声称 API `1.0`。Kotlin 的默认参数不能保证旧二进制插件在构造器变化后继续工作。
- 只修改字段含义、类型、空值规则或默认行为也可能是破坏性修改。
- 兼容新增能力优先使用新接口，例如 `EdbPluginV2`、新的 contribution 类型或 capability map；宿主同时识别旧接口。
- 向已有接口增加 abstract 方法是破坏性修改；新增方法必须有默认实现，但仍需完成旧 JAR 二进制测试。
- 破坏性修改提升 API major；兼容能力新增提升 minor。插件自身实现变化提升 `descriptor.version`。
- 宿主每次升级 Plugin API 都要运行“上一发布版示例插件 JAR”装载测试，而不只是重新编译插件源码。
- `id`、`taskType`、Step `type` 是外部持久化标识，不得随显示名称重命名。需要迁移时保留旧 Handler 作为 alias，并给出废弃周期。
- 外部 HTTP API 字段应通过插件内部 DTO 隔离；不要把厂商 DTO 暴露到 `plugin-api`。
- JSON 客户端应忽略未知字段，插件应容忍供应商响应新增字段；必填字段缺失时返回稳定错误码。

推荐兼容性测试矩阵：

| 宿主 | 插件 | 预期 |
|---|---|---|
| 1.0 | requires 1.0 | 加载成功 |
| 1.1 | requires 1.0 | 加载成功且行为不变 |
| 1.0 | requires 1.1 | `API_INCOMPATIBLE` |
| 2.x | requires 1.x | 拒绝，除非宿主提供显式 v1 bridge |
| 任意 | 重复 ID/type | 启动失败或记录明确失败 |

## 7. 插件项目目录模板

```text
example-plugin/
├── build.gradle.kts
├── libs/
│   └── plugin-api.jar
└── src/main/
    ├── kotlin/com/example/edb/
    │   ├── ExamplePlugin.kt
    │   ├── RemoteTaskHandler.kt
    │   └── RemoteWorkflowStepHandler.kt
    └── resources/META-INF/services/
        └── me.moiclathy.edb.plugin.api.EdbPlugin
```

开发验收：

1. `jar tf plugin.jar` 能看到 ServiceLoader 文件与实现类。
2. JAR 内不包含 `me/moiclathy/edb/plugin/api/*.class`。
3. Descriptor、API 版本和所有 contribution type 合法且唯一。
4. HTTP 超时、鉴权失败、限流、5xx、无效 JSON 和取消均有测试。
5. 日志和 Task metadata 不包含 Token、密码、Cookie 或原始敏感数据。
6. 把 JAR 放入 `plugins/` 后重启，启动日志出现已加载的 Plugin ID 和能力数量。
7. Workflow Step 应出现在 Registry；若提供 Schema，还应出现在 Step Catalog。
8. 服务关闭时 `close()` 被调用且资源释放。

## 8. 开发、测试与封包命令

```powershell
# 核心编译
.\gradlew.bat :plugin-api:test :core:compileKotlin :agent:compileKotlin :web:compileKotlin

# Web 类型检查
cd web-ui
npm.cmd run typecheck

# IDEA/Gradle 一键生成 Demo v1
cd ..
.\gradlew.bat packageDemoV1
```

发行包生成到 `release/edb-demo-v1`。封包任务会构建 Web、Fat JAR 和 CLI，并执行迁移检查、Secret 扫描及 SHA-256 清单生成。真实 HTTP 冒烟测试还需要可用 MySQL 和显式提供的管理员凭据。

发行包第一次运行 `start.bat` 只生成 `eula.txt` 与
`config/application.yml` 并退出。接受 EULA、替换全部 `CHANGE_ME` 后，第二次运行才会启动 JVM、等待健康检查并打开 Web 控制台。启动错误写入
`logs/server.err.log`，普通输出写入 `logs/server.out.log`。

## 9. 修改检查清单

### 修改 HTTP 字段时

- 更新请求/响应 DTO、OpenAPI、CLI、前端类型与示例。
- 判断 additive 还是 breaking；执行旧客户端兼容测试。
- 确认权限校验没有因新入口被绕过。
- 确认日志、SSE 和审计数据已脱敏。

### 修改 Workflow/Step 时

- 保持 `schemaVersion`、Step `type + version` 稳定。
- 更新 JSON Schema、Catalog 与 IR validate/compile 测试。
- 不把编辑器坐标写入 Workflow IR v1；坐标属于编辑器元数据。

### 修改 Plugin API 时

- 先确定 API major/minor，并更新 `EdbPluginApi.VERSION`。
- 使用已发布旧 JAR 做二进制装载测试。
- 保留旧 ID/type alias 或提供迁移器。
- 更新本手册、示例插件与失败码说明。
- 确认 ServiceLoader 文件名未变化。

### 发布前

- 运行 `packageDemoV1`。
- 确认 Fat JAR 合并了 `META-INF/services`；Flyway 11 依赖该 SPI 识别迁移类型。
- 检查 Flyway 版本唯一性。
- Secret 扫描必须通过。
- 验证 `SHA256SUMS`。
- 在隔离数据库执行登录、健康检查、Step Catalog、Task 提交/SSE、取消和插件装载冒烟测试。
