package me.moiclathy.edb.core.exception


class ProjectNotFoundException(

    id:Int

):EDBException(

    code = "PROJECT_NOT_FOUND",

    message = "Project $id not found"

)