package me.moiclathy.edb.core.git.exception

class GitRepositoryInitException(

    projectId:Int,

    cause:Throwable

):GitRepositoryException(

    "Failed to initialize repository of project $projectId",

    cause

)