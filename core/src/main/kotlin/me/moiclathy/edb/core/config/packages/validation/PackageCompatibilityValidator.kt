package me.moiclathy.edb.core.config.packages.validation

import me.moiclathy.edb.core.config.packages.EdbManifest
import me.moiclathy.edb.core.config.packages.PackageVersion

class PackageCompatibilityValidator {

    fun validate(

        manifest: EdbManifest

    ): PackageValidationResult {

        val errors = mutableListOf<PackageValidationError>()

        if (

            manifest.packageVersion < PackageVersion.MIN

        ) {

            errors += PackageValidationError(

                "packageVersion",

                "Package version is too old"

            )

        }

        if (

            manifest.packageVersion > PackageVersion.CURRENT

        ) {

            errors += PackageValidationError(

                "packageVersion",

                "Package version is newer than current EDB"

            )

        }

        return if (

            errors.isEmpty()

        ) {

            PackageValidationResult.success()

        } else {

            PackageValidationResult(

                false,

                errors

            )

        }

    }

}