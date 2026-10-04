package me.moiclathy.edb.core.git

data class GitDiffResult(

    val oldCommit:String,

    val newCommit:String,

    val oldContent:String,

    val newContent:String

)