package me.moiclathy.edb.web.security

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

/*suspend fun ApplicationCall.requireUserId(): Int? {
    val principal = principal<JWTPrincipal>() ?: run {
        respond(HttpStatusCode.Unauthorized)
        return null
    }

    val userId = principal.userId() ?: run {
        respond(HttpStatusCode.Unauthorized)
        return null
    }

    return userId
}*/

suspend fun ApplicationCall.requireUserId(): Int? {

    val principal = principal<JWTPrincipal>()

    println("principal = $principal")

    if (principal == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }

    val userId = principal.userId()

    println("jwt userId = $userId")

    if (userId == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }

    return userId
}

//(注：如果希望在未通过时直接中断并响应，也可以让它在内部调用 respond 并返回 Int?，在路由中用 ?.let { ... } 或 ?: return@... 处理)