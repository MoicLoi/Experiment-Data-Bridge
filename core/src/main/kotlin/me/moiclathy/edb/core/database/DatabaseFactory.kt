package me.moiclathy.edb.core.database


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.moiclathy.edb.core.database.DatabaseProperties
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource


object DatabaseFactory {


    private lateinit var dataSource: HikariDataSource



    fun init(
        properties: DatabaseProperties
    ) {


        val config = HikariConfig()


        config.jdbcUrl =
            properties.jdbcUrl


        config.username =
            properties.username


        config.password =
            properties.password



        config.maximumPoolSize =
            properties.maximumPoolSize


        config.minimumIdle =
            properties.minimumIdle

        config.connectionTimeout =
            properties.connectionTimeout



        config.driverClassName =
            "com.mysql.cj.jdbc.Driver"



        dataSource =
            HikariDataSource(config)


    }



    fun getDataSource(): DataSource {

        check(::dataSource.isInitialized) {

            "DatabaseFactory has not been initialized"

        }


        return dataSource
    }



    fun close(){

        if(::dataSource.isInitialized){

            dataSource.close()

        }

    }

    fun connectExposed(){


        Database.connect(
            getDataSource()
        )

    }

}