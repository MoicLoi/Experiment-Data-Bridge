package me.moiclathy.edb.core.git

import java.time.Instant

data class GitCommitInfo(

    val commitId: String,

    val shortCommitId: String,

    val message: String,

    val author: String,

    val time: Instant,

    val email:String

)