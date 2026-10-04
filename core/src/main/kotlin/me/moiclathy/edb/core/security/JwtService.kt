package me.moiclathy.edb.core.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtService(
    secret: String,
    private val issuer: String,
    private val audience: String,
    private val expiresIn: Long
) {

    init {
        require(secret.toByteArray().size >= 32) {
            "JWT secret must be at least 256 bits (32 bytes)"
        }
    }

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier =
        JWT
            .require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()

    fun generateToken(
        userId:Int,
        username:String,
        role:UserRole
    ):String {


        return JWT.create()

            .withSubject(username)

            .withClaim(
                "userId",
                userId
            )

            .withClaim(
                "role",
                role.name
            )

            .withIssuer(issuer)

            .withAudience(audience)

            .withIssuedAt(Date())

            .withExpiresAt(
                Date(
                    System.currentTimeMillis()
                            + expiresIn
                )
            )

            .sign(algorithm)

    }
}