package me.moiclathy.edb.core.config.validation

import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.validation.validator.OutputValidator
import me.moiclathy.edb.core.config.validation.validator.ProjectValidator
import me.moiclathy.edb.core.config.validation.validator.WorkflowValidator

class DefaultConfigValidator : ConfigValidator {

    private val projectValidator = ProjectValidator()

    private val workflowValidator = WorkflowValidator()

    private val outputValidator = OutputValidator()

    override fun validate(
        config: ProjectConfig
    ): ValidationResult {

        val errors = mutableListOf<String>()

        errors += projectValidator.validate(config)

        errors += workflowValidator.validate(config)

        errors += outputValidator.validate(config)

        return ValidationResult(

            valid = errors.isEmpty(),

            errors = errors

        )
    }

}