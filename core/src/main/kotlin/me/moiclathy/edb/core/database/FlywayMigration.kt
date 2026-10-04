package me.moiclathy.edb.core.database

import javax.sql.DataSource
import org.flywaydb.core.Flyway



object FlywayMigration {
    fun migrate(dataSource: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .sqlMigrationPrefix("V")
            .sqlMigrationSeparator("__")
            .sqlMigrationSuffixes(".sql")
            .validateMigrationNaming(true)
            .cleanDisabled(true) // 开发阶段允许 clean
            .load()

        //flyway.clean()TODO 遇见数据库问题先取消注释 然后上面改成false

        //flyway.repair()


        flyway.migrate()
    }
}
