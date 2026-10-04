package me.moiclathy.edb.core.config.packages.validation

data class PackageValidationResult(

    val valid: Boolean,

    val errors: List<PackageValidationError>

) {

    companion object {

        fun success() =

            PackageValidationResult(

                valid = true,

                errors = emptyList()

            )

    }

}