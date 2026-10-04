package me.moiclathy.edb.core.git.exception

open class GitRepositoryException(

    message:String,

    cause:Throwable?=null

):RuntimeException(

    message,

    cause

)