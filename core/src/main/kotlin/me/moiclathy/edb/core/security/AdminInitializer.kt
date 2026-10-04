package me.moiclathy.edb.core.security

import me.moiclathy.edb.core.service.UserService
import org.slf4j.LoggerFactory

class AdminInitializer(
    private val userService: UserService
) {
    private val logger = LoggerFactory.getLogger(AdminInitializer::class.java)

    fun initialize(
        username: String,
        password: String,
        email: String,
        fullName: String
    ): AdminInitializationResult {
        logger.info("Checking administrator account...")

        return try {
            val exists = userService.findByUsername(username) != null

            if (!exists) {
                userService.createUser(
                    username = username,
                    password = password,
                    fullName = fullName,
                    email = email,
                    role = UserRole.ADMIN
                )

                logger.info(
                    "Administrator [{}] created successfully.",
                    username
                )
                AdminInitializationResult.CREATED
            } else {
                logger.info(
                    "Administrator [{}] already exists. Skipping creation.",
                    username
                )
                AdminInitializationResult.ALREADY_EXISTS
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to initialize administrator account [{}]",
                username,
                e
            )
            AdminInitializationResult.FAILED
        }
    }
}