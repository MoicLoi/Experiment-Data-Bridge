package me.moiclathy.edb.core.database.model


import java.time.Instant


data class AuditLog(

    val id:Long,

    val userId:Int,

    val action:String,

    val resourceType:String,

    val resourceId:Int?,

    val details:String?,

    val createdAt:Instant

)