package me.moiclathy.edb.core.security


import org.mindrot.jbcrypt.BCrypt


object PasswordService {


    fun verify(
        raw:String,
        hash:String
    ):Boolean {


        return BCrypt.checkpw(
            raw,
            hash
        )

    }


    fun hash(
        password:String
    ):String {


        return BCrypt.hashpw(
            password,
            BCrypt.gensalt(12)
        )

    }

}