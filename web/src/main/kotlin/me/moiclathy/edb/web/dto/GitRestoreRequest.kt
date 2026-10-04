package me.moiclathy.edb.web.dto


import kotlinx.serialization.Serializable


@Serializable
data class GitRestoreRequest(

    val commitId:String

)