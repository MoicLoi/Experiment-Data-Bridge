package me.moiclathy.edb.core.config.packages.validation

import me.moiclathy.edb.core.config.packages.EdbPackage

class PackageIntegrityValidator {

    fun validate(
        pkg: EdbPackage
    ): PackageValidationResult {

        val errors = mutableListOf<PackageValidationError>()

        if (pkg.configuration.isBlank()) {
            errors += PackageValidationError(
                field = "configuration",
                message = "configuration.yaml is empty"
            )
        }

        return if (errors.isEmpty()) {
            PackageValidationResult.success()
        } else {
            PackageValidationResult(
                valid = false,
                errors = errors
            )
        }
    }
}