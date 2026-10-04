package me.moiclathy.edb.core.service


import me.moiclathy.edb.core.database.model.CreateUserCommand
import me.moiclathy.edb.core.database.model.User
import me.moiclathy.edb.core.database.repository.UserRepository
import me.moiclathy.edb.core.security.PasswordHasher
import me.moiclathy.edb.core.security.UserRole


class UserService(
    private val userRepository: UserRepository
) {




    /**
     * 根据用户名查找用户
     */
    fun findByUsername(
        username: String
    ): User? {
        return userRepository
            .findByUsername(username)
    }



    /**
     * 创建用户
     */
    fun createUser(
        username:String,
        password:String,
        fullName:String,
        email:String? = null,
        role:UserRole = UserRole.MEMBER
    ):User {


        if(
            userRepository.existsByUsername(username)
        ){
            throw IllegalArgumentException(
                "Username already exists"
            )
        }


        val command =
            CreateUserCommand(

                username = username,

                passwordHash =
                    PasswordHasher.hash(password),

                fullName = fullName,

                email = email,

                role = role
            )


        userRepository.createUser(command)


        return userRepository
            .findByUsername(username)
            ?: throw IllegalStateException(
                "User creation failed"
            )
    }





    /**
     * 登录
     */
    fun authenticate(
        username:String,
        password:String
    ):User?{


        val user =
            userRepository
                .findByUsername(username)
                ?: return null



        if(!user.isActive){
            return null
        }


        if(
            !PasswordHasher.verify(
                password,
                user.passwordHash
            )
        ){
            return null
        }


        return user
    }

}