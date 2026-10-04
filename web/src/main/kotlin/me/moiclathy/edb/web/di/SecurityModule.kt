package me.moiclathy.edb.web.di

import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.security.AdminInitializer
import me.moiclathy.edb.core.security.AuthService
import me.moiclathy.edb.core.security.JwtService
import me.moiclathy.edb.core.security.PermissionChecker
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val securityModule = module {
    singleOf(::PermissionChecker)
    single {
        val config = get<AppConfig>()
        JwtService(
            secret = config.security.jwt.secret,
            issuer = config.security.jwt.issuer,
            audience = config.security.jwt.audience,
            expiresIn = config.security.jwt.expiresIn
        )
    }
    singleOf(::AuthService)
    singleOf(::AdminInitializer)
}