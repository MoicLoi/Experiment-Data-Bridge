package me.moiclathy.edb.core.datafile.upload

import java.util.UUID

fun interface DataFileStorageKeyGenerator {
    fun generate(projectId: Int, sanitizedFileName: String): String
}

class DefaultDataFileStorageKeyGenerator(
    private val identifierSupplier: () -> String = { UUID.randomUUID().toString() }
) : DataFileStorageKeyGenerator {
    override fun generate(projectId: Int, sanitizedFileName: String): String {
        require(projectId > 0) { "Project id must be positive." }
        require(sanitizedFileName.isNotBlank() && '/' !in sanitizedFileName && '\\' !in sanitizedFileName) {
            "Storage key generation requires a sanitized file name."
        }
        val identifier = identifierSupplier()
        require(IDENTIFIER.matches(identifier)) { "Generated DataFile storage identifier is invalid." }
        return "projects/$projectId/data/$identifier/$sanitizedFileName"
    }

    companion object {
        private val IDENTIFIER = Regex("^[A-Za-z0-9_-]+$")
    }
}

