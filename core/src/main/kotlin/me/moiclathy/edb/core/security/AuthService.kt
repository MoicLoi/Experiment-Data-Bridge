package me.moiclathy.edb.core.security


import me.moiclathy.edb.core.database.repository.UserRepository


class AuthService(

    private val userRepository: UserRepository,

    private val jwtService: JwtService

){


    fun login(
        username:String,
        password:String
    ):LoginResult{


        val user =
            userRepository
                .findByUsername(username)
                ?: return LoginResult.Failed



        if(!user.isActive){

            return LoginResult.Disabled

        }


        if(
            !PasswordService.verify(
                password,
                user.passwordHash
            )
        ){

            return LoginResult.Failed

        }


        return LoginResult.Success(

            token =
                jwtService.generateToken(
                    user.id,
                    user.username,
                    user.role
                )

        )

    }

}



sealed class LoginResult {


    data class Success(
        val token:String
    ):LoginResult()


    object Failed:LoginResult()


    object Disabled:LoginResult()

}