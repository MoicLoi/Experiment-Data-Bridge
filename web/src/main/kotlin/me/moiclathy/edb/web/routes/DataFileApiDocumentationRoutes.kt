package me.moiclathy.edb.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DATA_FILE_OPENAPI_RESOURCE = "openapi/datafile-0.25.yaml"

fun Route.dataFileApiDocumentationRoutes() {
    get("/api/v1/openapi/datafile-0.25.yaml") {
        val specification = checkNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream(DATA_FILE_OPENAPI_RESOURCE)
        ) { "Missing classpath resource: $DATA_FILE_OPENAPI_RESOURCE" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        call.respondText(specification, ContentType.parse("application/yaml"))
    }

    get("/api/v1/docs/datafile-0.25") {
        call.respondText(DATA_FILE_API_DOCUMENTATION, ContentType.Text.Html)
    }
}

private val DATA_FILE_API_DOCUMENTATION = """
    <!doctype html>
    <html lang="zh-CN">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>DataFile API 0.25</title>
      <style>
        body { max-width: 1080px; margin: 40px auto; padding: 0 20px; color: #202124; font: 16px/1.6 system-ui, sans-serif; }
        h1, h2 { line-height: 1.25; } table { width: 100%; border-collapse: collapse; margin: 16px 0 32px; }
        th, td { padding: 10px 12px; border: 1px solid #dfe1e5; text-align: left; vertical-align: top; }
        th { background: #f6f8fa; } code { padding: 2px 5px; background: #f1f3f4; border-radius: 4px; }
        .method { font-weight: 700; } a { color: #0969da; }
      </style>
    </head>
    <body>
      <h1>DataFile API 0.25</h1>
      <p>统一描述文件上传、查询与下载、异步格式转换，以及报表定义和报表制品生成接口。</p>
      <p><a href="/api/v1/openapi/datafile-0.25.yaml">下载 OpenAPI 3.0 契约</a> · <a href="/api/v1/docs/task-0.25">Task API 文档</a></p>

      <h2>DataFile</h2>
      <table><thead><tr><th>方法</th><th>路径</th><th>用途</th></tr></thead><tbody>
        <tr><td class="method">POST</td><td><code>/api/v1/projects/{projectId}/data-files</code></td><td>multipart 文件上传</td></tr>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/data-files</code></td><td>项目文件列表</td></tr>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/data-files/{dataFileId}</code></td><td>文件详情</td></tr>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/data-files/{dataFileId}/download</code></td><td>流式下载</td></tr>
      </tbody></table>

      <h2>Conversion</h2>
      <table><thead><tr><th>方法</th><th>路径</th><th>用途</th></tr></thead><tbody>
        <tr><td class="method">GET</td><td><code>.../{dataFileId}/conversion-targets</code></td><td>查询真实可用转换能力</td></tr>
        <tr><td class="method">POST</td><td><code>.../{dataFileId}/conversions</code></td><td>提交转换 Task</td></tr>
        <tr><td class="method">GET</td><td><code>.../{dataFileId}/conversions</code></td><td>查询直接派生的转换文件</td></tr>
      </tbody></table>

      <h2>Analysis</h2>
      <table><thead><tr><th>Method</th><th>Path</th><th>Purpose</th></tr></thead><tbody>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/analysis-handlers</code></td><td>List configured analysis handlers</td></tr>
        <tr><td class="method">POST</td><td><code>/api/v1/projects/{projectId}/analyses</code></td><td>Submit an analysis Task</td></tr>
        <tr><td class="method">GET</td><td><code>.../{dataFileId}/analyses</code></td><td>List derived analysis artifacts</td></tr>
      </tbody></table>

      <h2>Orphan Cleanup (Admin)</h2>
      <table><thead><tr><th>Method</th><th>Path</th><th>Purpose</th></tr></thead><tbody>
        <tr><td class="method">GET</td><td><code>/api/v1/admin/data-file-orphan-cleanup</code></td><td>Policy and runtime state</td></tr>
        <tr><td class="method">GET</td><td><code>.../estimate</code></td><td>Scan and estimate without deletion</td></tr>
        <tr><td class="method">POST</td><td><code>.../cleanup</code></td><td>Run cleanup immediately</td></tr>
        <tr><td class="method">GET</td><td><code>.../last-result</code></td><td>Latest result and failure state</td></tr>
        <tr><td class="method">GET/POST</td><td><code>/api/v1/admin/data-file-retention/...</code></td><td>Deleted-record retention management</td></tr>
      </tbody></table>

      <h2>Report</h2>
      <table><thead><tr><th>方法</th><th>路径</th><th>用途</th></tr></thead><tbody>
        <tr><td class="method">GET</td><td><code>/api/v1/projects/{projectId}/reports/definitions</code></td><td>列出已加载报表定义</td></tr>
        <tr><td class="method">POST</td><td><code>/api/v1/projects/{projectId}/reports</code></td><td>提交报表 Task</td></tr>
        <tr><td class="method">GET</td><td><code>.../{dataFileId}/reports</code></td><td>查询源文件生成的报表制品</td></tr>
      </tbody></table>

      <p>转换和报表提交返回 <code>202 Accepted</code> 与 Task 快照。使用 <code>/api/v1/tasks/{taskId}</code> 或 Task SSE 接口跟踪进度；完成后的 <code>result.metadata.dataFileId</code> 指向生成文件。</p>
      <p>业务接口均使用 Bearer JWT，并执行项目读写权限校验。完整字段、状态码和错误结构以 OpenAPI 契约为准。</p>
    </body>
    </html>
""".trimIndent()
