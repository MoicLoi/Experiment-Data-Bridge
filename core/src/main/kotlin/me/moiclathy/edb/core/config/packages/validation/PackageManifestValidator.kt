package me.moiclathy.edb.core.config.packages.validation

import me.moiclathy.edb.core.config.packages.EdbManifest
import me.moiclathy.edb.core.config.packages.PackageVersion

class PackageManifestValidator {

    fun validate(

        manifest: EdbManifest

    ): PackageValidationResult {

        val errors = mutableListOf<PackageValidationError>()

        if (

            manifest.packageVersion < PackageVersion.MIN ||

            manifest.packageVersion > PackageVersion.CURRENT

        ) {

            errors += PackageValidationError(

                field = "packageVersion",

                message =
                    "Unsupported package version ${manifest.packageVersion}"

            )

        }

        if (

            manifest.projectName.isBlank()

        ) {

            errors += PackageValidationError(

                field = "projectName",

                message = "Project name is required"

            )

        }

        if (

            manifest.edbVersion.isBlank()

        ) {

            errors += PackageValidationError(

                field = "edbVersion",

                message = "EDB version is required"

            )

        }

        return if (

            errors.isEmpty()

        ) {

            PackageValidationResult.success()

        } else {

            PackageValidationResult(

                valid = false,

                errors = errors

            )

        }

    }

}