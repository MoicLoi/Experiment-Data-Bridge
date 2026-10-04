package me.moiclathy.edb.web.security


import io.ktor.server.auth.jwt.JWTPrincipal
import me.moiclathy.edb.core.security.UserRole


fun JWTPrincipal.username():String {

    return payload.subject

}



fun JWTPrincipal.userId():Int {


    return payload
        .getClaim("userId")
        .asInt()

}



fun JWTPrincipal.role():UserRole {


    return UserRole.valueOf(
        payload
            .getClaim("role")
            .asString()
    )

}