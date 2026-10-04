package me.moiclathy.edb.core.config.packages.validation

import me.moiclathy.edb.core.config.packages.EdbPackage

class PackageValidationService(

    private val compatibilityValidator: PackageCompatibilityValidator,

    private val manifestValidator: PackageManifestValidator,

    private val integrityValidator: PackageIntegrityValidator

) {

    fun validate(

        pkg: EdbPackage

    ): PackageValidationResult {

        val errors = mutableListOf<PackageValidationError>()

        errors += compatibilityValidator
            .validate(pkg.manifest)
            .errors

        errors += manifestValidator
            .validate(pkg.manifest)
            .errors

        errors += integrityValidator
            .validate(pkg)
            .errors

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