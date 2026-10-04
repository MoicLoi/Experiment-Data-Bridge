

package me.moiclathy.edb.core.config.schema

data class ConfigValidationResult(

    val valid: Boolean,

    val errors: List<ConfigValidationError>

) {

    companion object {

        fun success() =

            ConfigValidationResult(

                valid = true,

                errors = emptyList()

            )

    }

}