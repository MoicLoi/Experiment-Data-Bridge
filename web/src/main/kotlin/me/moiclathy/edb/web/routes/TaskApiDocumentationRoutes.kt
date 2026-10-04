package me.moiclathy.edb.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val TASK_OPENAPI_RESOURCE = "openapi/task-0.25.yaml"

fun Route.taskApiDocumentationRoutes() {
    get("/api/v1/openapi/task-0.25.yaml") {
        val specification = checkNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream(TASK_OPENAPI_RESOURCE)
        ) { "Missing classpath resource: $TASK_OPENAPI_RESOURCE" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        call.respondText(
            text = specification,
            contentType = ContentType.parse("application/yaml")
        )
    }

    get("/api/v1/docs/task-0.25") {
        call.respondText(TASK_API_DOCUMENTATION, ContentType.Text.Html)
    }
}

private val TASK_API_DOCUMENTATION = """
    <!doctype html>
    <html lang="zh-CN">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Task API 0.25</title>
      <style>
        body { max-width: 1080px; margin: 40px auto; padding: 0 20px; color: #202124; font: 16px/1.6 system-ui, sans-serif; }
        h1, h2 { line-height: 1.25; } table { width: 100%; border-collapse: collapse; margin: 16px 0 32px; }
        th, td { padding: 10px 12px; border: 1px solid #dfe1e5; text-align: left; vertical-align: top; }
        th { background: #f6f8fa; } code { padding: 2px 5px; background: #f1f3f4; border-radius: 4px; }
        .method { font-weight: 700; } a { color: #0969da; }
      </style>
    </head>
    <body>
      <h1>Task API 0.25</h1>
      <p>Task、Scheduler、运行指标、Retention 与 SSE 生命周期推送的统一接口文档。</p>
      <p><a href="/api/v1/openapi/task-0.25.yaml">下载 OpenAPI 3.0 契约</a></p>

      <h2>Task</h2>
      <table><thead><tr><th>方法</th><th>路径</th><th>用途</th></tr></thead><tbody>
        <tr><td class="method">POST</td><td><code>/api/v1/tasks/pipeline</code></td><td>提交 Pipeline Task</td></tr>
        <tr><td class="method">GET</td><td><code>/api/v1/tasks/{taskId}</code></td><td>查询任务状态</td></tr>
        <tr><td class="method">POST</td><td><code>/api/v1/tasks/{taskId}/cancel</code></td><td>请求取消任务</td></tr>
        <tr><td class="method">GET</td><td><code>/api/v1/tasks/{taskId}/timeline</code></td><td>查询审计时间线</td></tr>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/tasks</code></td><td>分页查询项目任务</td></tr>
      </tbody></table>

      <h2>SSE</h2>
      <table><thead><tr><th>方法</th><th>路径</th><th>媒体类型</th></tr></thead><tbody>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/tasks/events</code></td><td><code>text/event-stream</code></td></tr>
      </tbody></table>

      <h2>管理员 API</h2>
      <table><thead><tr><th>分组</th><th>路径前缀</th><th>能力</th></tr></thead><tbody>
        <tr><td>Scheduler</td><td><code>/api/v1/admin/task-scheduler</code></td><td>状态、启停与 Worker 数量调整</td></tr>
        <tr><td>Workflow Step Catalog</td><td><code>/api/v1/workflow-steps</code></td><td>步骤版本、来源与参数 JSON Schema</td></tr>
        <tr><td>Workflow IR</td><td><code>/api/v1/workflows/validate</code> / <code>compile</code></td><td>严格校验、编译预览与管理员 Pipeline 注册</td></tr>
        <tr><td>Docker Worker Fleet</td><td><code>/api/v1/admin/docker-workers</code></td><td>成员健康、整体启停、定向重启与日志</td></tr>
        <tr><td>Metrics</td><td><code>/api/v1/admin/task-metrics</code></td><td>队列、运行状态、终态统计和平均耗时</td></tr>
        <tr><td>Retry</td><td><code>/api/v1/admin/tasks/{taskId}/requeue</code></td><td>失败任务手动重新入队</td></tr>
        <tr><td>Retention</td><td><code>/api/v1/admin/task-retention</code></td><td>策略、预估、手动清理与最近结果</td></tr>
      </tbody></table>

      <p>除文档本身外，接口使用 Bearer JWT；管理员接口要求 <code>ADMIN</code> 角色。完整请求、响应及错误结构以 OpenAPI 契约为准。</p>
    </body>
    </html>
""".trimIndent()
