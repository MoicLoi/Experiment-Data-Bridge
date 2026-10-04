package me.moiclathy.edb.web.plugin

import me.moiclathy.edb.core.config.AppConfig
import me.moiclathy.edb.core.database.DatabaseFactory
import me.moiclathy.edb.web.plugin.initDatabase
import org.slf4j.LoggerFactory

class DatabaseInitializer(private val config: AppConfig) {

    private val logger =
        LoggerFactory.getLogger(DatabaseInitializer::class.java)


    fun initialize() {
        logger.info("Initializing database...")
        initDatabase(config)
        logger.info("Connecting Exposed...")
        DatabaseFactory.connectExposed()
    }

}