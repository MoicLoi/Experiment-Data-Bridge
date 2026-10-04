package me.moiclathy.edb.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val AGENT_OPENAPI_RESOURCE = "openapi/agent-0.25.yaml"

fun Route.agentApiDocumentationRoutes() {
    get("/api/v1/openapi/agent-0.25.yaml") {
        val specification = checkNotNull(
            Thread.currentThread().contextClassLoader.getResourceAsStream(AGENT_OPENAPI_RESOURCE)
        ) { "Missing classpath resource: $AGENT_OPENAPI_RESOURCE" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        call.respondText(specification, ContentType.parse("application/yaml"))
    }
    get("/api/v1/docs/agent-0.25") {
        call.respondText(
            """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>Agent API 0.25</title></head>
            <body><h1>Agent API 0.25</h1><p>用户委托的 Workflow 规划 Agent：Session、Run、Proposal、Audit 与 SSE。</p>
            <p>Agent 使用原用户权限，只生成需审批 Proposal，不执行 Workflow。</p>
            <p><a href="/api/v1/openapi/agent-0.25.yaml">下载 OpenAPI 契约</a></p></body></html>""",
            ContentType.Text.Html
        )
    }
}
