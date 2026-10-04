package me.moiclathy.edb.core.database


data class DatabaseProperties(

    val jdbcUrl: String,

    val username: String,

    val password: String,

    val maximumPoolSize: Int = 20,

    val minimumIdle: Int = 5,

    val connectionTimeout: Long = 30000

)