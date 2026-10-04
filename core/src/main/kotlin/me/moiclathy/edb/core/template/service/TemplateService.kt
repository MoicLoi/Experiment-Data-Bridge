package me.moiclathy.edb.core.template.service

import me.moiclathy.edb.core.template.exception.TemplateAlreadyExistsException
import me.moiclathy.edb.core.template.exception.TemplateNotFoundException
import me.moiclathy.edb.core.template.model.Template
import me.moiclathy.edb.core.template.model.TemplateQuery
import me.moiclathy.edb.core.template.model.TemplateSummary
import me.moiclathy.edb.core.template.repository.TemplateRepository

class TemplateService(

    private val repository: TemplateRepository,

    private val validator: TemplateValidationService

) {

    fun create(

        template: Template

    ): Template {

        val validation =

            validator.validate(template)

        require(validation.valid) {

            validation.errors.joinToString()

        }

        if (

            repository.exists(

                template.name

            )

        ) {

            throw TemplateAlreadyExistsException(

                template.name

            )

        }

        return repository.save(

            template

        )

    }

    fun update(template: Template): Template {
        val id = template.id
            ?: throw IllegalArgumentException("Template id must not be null when updating")

        val validation = validator.validate(template)
        require(validation.valid) {
            validation.errors.joinToString()
        }

        // 保留第一段的严谨检查：确保要更新的记录真实存在
        val existing = repository.findById(id)
            ?: throw TemplateNotFoundException(id)

        // 保留第一段的查重防错：改名时如果新名字已被其他人占用则抛异常
        if (
            !existing.name.equals(template.name, ignoreCase = true) &&
            repository.exists(template.name)
        ) {
            throw TemplateAlreadyExistsException(template.name)
        }

        return repository.update(template)
    }

    fun load(

        id: Long

    ): Template {

        return repository.findById(id)

            ?: throw TemplateNotFoundException(id)

    }

    fun delete(id: Long) {
        // 保留第一段的严谨检查：删除前确认是否存在
        repository.findById(id)
            ?: throw TemplateNotFoundException(id)

        repository.delete(id)
    }

    fun search(

        query: TemplateQuery

    ): List<TemplateSummary> {

        return repository.search(query)

    }

    fun count(): Long {

        return repository.count()

    }

}