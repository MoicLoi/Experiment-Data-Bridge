package me.moiclathy.edb.web.security
//TODO 这是暂时重构完留下的无调用类
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.respond
import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.project.ProjectResult
import me.moiclathy.edb.web.dto.toResponse


suspend fun ApplicationCall.respondProjectResult(
    result: ProjectResult<Project>
) {

    when(result) {

        is ProjectResult.Success -> {

            respond(
                HttpStatusCode.OK,
                result.data.toResponse()
            )

        }


        ProjectResult.NotFound -> {

            respond(
                HttpStatusCode.NotFound
            )

        }


        ProjectResult.Forbidden -> {

            respond(
                HttpStatusCode.Forbidden
            )

        }


        is ProjectResult.Failure -> {

            respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "message" to result.message
                )
            )

        }

    }

}

suspend fun ApplicationCall.respondDeleteResult(
    result: ProjectResult<Unit>
){

    when(result){

        is ProjectResult.Success -> {

            respond(
                HttpStatusCode.NoContent
            )

        }


        ProjectResult.NotFound -> {

            respond(
                HttpStatusCode.NotFound
            )

        }


        ProjectResult.Forbidden -> {

            respond(
                HttpStatusCode.Forbidden
            )

        }


        is ProjectResult.Failure -> {

            respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "message" to result.message
                )
            )

        }

    }

}