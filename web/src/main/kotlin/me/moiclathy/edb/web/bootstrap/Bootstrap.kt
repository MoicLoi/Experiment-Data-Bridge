package me.moiclathy.edb.web.bootstrap

import io.ktor.server.application.*
import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.security.AdminInitializationResult
import me.moiclathy.edb.core.security.AdminInitializer
import me.moiclathy.edb.web.plugin.DatabaseInitializer
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

fun Application.bootstrap() {
    val logger = LoggerFactory.getLogger("Bootstrap")

    val config by inject<AppConfig>()
    val databaseInitializer by inject<DatabaseInitializer>()
    val adminInitializer by inject<AdminInitializer>()

    // 1. 执行数据库初始化
    databaseInitializer.initialize()

    // 2. 初始化管理员账户
    if (config.security.initialAdmin.enabled) {
        val admin = config.security.initialAdmin

        when (
            adminInitializer.initialize(
                admin.username,
                admin.password,
                admin.email,
                admin.fullName
            )
        ) {
            AdminInitializationResult.CREATED -> logger.info("Initial admin account created.")
            AdminInitializationResult.ALREADY_EXISTS -> logger.info("Initial admin account already exists.")
            AdminInitializationResult.FAILED -> logger.error("Failed to initialize initial admin account.")
        }
    }
}