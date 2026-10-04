package me.moiclathy.edb.web.routes


import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import me.moiclathy.edb.core.security.AuthService
import me.moiclathy.edb.core.security.LoginResult
import kotlin.collections.mapOf
import io.ktor.http.HttpStatusCode


fun Route.authRoutes(
    authService: AuthService
){


    route("/api/v1/auth") {


        post("/login") {


            val request =
                call.receive<LoginRequest>()


            when(
                val result =
                    authService.login(
                        request.username,
                        request.password
                    )
            ){

                is LoginResult.Success -> {

                    call.respond(
                        HttpStatusCode.OK,
                        mapOf(
                            "token" to result.token
                        )
                    )

                }


                else -> {

                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "message" to "invalid credential"
                        )
                    )

                }

            }

        }

    }

}



@Serializable
data class LoginRequest(

    val username:String,

    val password:String

)