package me.moiclathy.edb.web.plugin

import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.database.DatabaseFactory
import me.moiclathy.edb.core.database.DatabaseProperties
import me.moiclathy.edb.core.database.FlywayMigration
import org.slf4j.LoggerFactory

fun initDatabase(
    config: AppConfig
) {
    val logger = LoggerFactory.getLogger("DatabaseInitializer")

    logger.info("Initializing database...")

    val mysql = config.database.mysql

    val properties =
        DatabaseProperties(
            jdbcUrl =
                "jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
            username =
                mysql.username,
            password =
                mysql.password,
            maximumPoolSize =
                mysql.pool.maximumPoolSize,
            minimumIdle =
                mysql.pool.minimumIdle,
            connectionTimeout =
                mysql.pool.connectionTimeout
        )

    // 1. Hikari 初始化
    DatabaseFactory.init(properties)

    // 2. Flyway 迁移（使用初始化好的 DataSource）
    logger.info("Running Flyway migrations...")
    FlywayMigration.migrate(
        DatabaseFactory.getDataSource()
    )

    // 3. Exposed 连接
    logger.info("Connecting Exposed...")
    DatabaseFactory.connectExposed()
}