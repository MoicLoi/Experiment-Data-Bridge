package me.moiclathy.edb.core.template.service

import me.moiclathy.edb.core.template.model.Template
import me.moiclathy.edb.core.template.validation.TemplateValidationResult

class TemplateValidationService {

    fun validate(
        template: Template
    ): TemplateValidationResult {
        val errors = mutableListOf<String>()

        // 1. 模板名称校验
        if (template.name.isBlank()) {
            errors += "Template name must not be blank"
        } else if (template.name.length > 255) {
            errors += "Template name must not exceed 255 characters"
        }

        // 2. 模板描述校验
        if (template.description.length > 2000) {
            errors += "Template description must not exceed 2000 characters"
        }

        // 3. 模板 YAML 内容校验
        if (template.yaml.isBlank()) {
            errors += "Template YAML must not be blank"
        } else if (template.yaml.length > 1_000_000) {
            errors += "Template YAML is too large"
        }

        // 4. Metadata 校验
        if (template.metadata.author.isNotBlank() == false) { // 或者 template.metadata.author.isBlank()
            errors += "Template author must not be blank"
        }

        if (template.metadata.version <= 0) {
            errors += "Template version must be greater than zero"
        }

        return TemplateValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
}