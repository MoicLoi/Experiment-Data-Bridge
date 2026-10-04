package me.moiclathy.edb.core.database.repository


import me.moiclathy.edb.core.audit.AuditStatus
import me.moiclathy.edb.core.database.table.AuditLogsTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant


class AuditRepository {


    fun save(

        userId:Int,

        action:String,

        resourceType:String,

        resourceId:Int?,

        details:String?,

        status: AuditStatus = AuditStatus.SUCCESS // 也可以给个默认值

    ){

        transaction {


            AuditLogsTable.insert {


                it[AuditLogsTable.userId] =
                    userId


                it[AuditLogsTable.action] =
                    action


                it[AuditLogsTable.resourceType] =
                    resourceType


                it[AuditLogsTable.resourceId] =
                    resourceId


                it[AuditLogsTable.details] =
                    details

                it[AuditLogsTable.status] =
                    status

                it[AuditLogsTable.createdAt] =
                    Instant.now()
            }


        }

    }

}