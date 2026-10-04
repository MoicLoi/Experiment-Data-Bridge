package me.moiclathy.edb.core.task.workflow.template

import java.util.concurrent.ConcurrentHashMap

class WorkflowTemplateRegistry {
    private val templates = ConcurrentHashMap<Pair<String, String>, WorkflowTemplate>()

    fun register(template: WorkflowTemplate) {
        val key = template.id to template.version
        require(templates.putIfAbsent(key, template) == null) { "Duplicate workflow template ${template.id}@${template.version}." }
    }

    fun list(): List<WorkflowTemplate> = templates.values.sortedWith(compareBy({ it.category }, { it.title }, { it.version }))

    fun require(id: String, version: String): WorkflowTemplate = templates[id to version]
        ?: throw NoSuchElementException("Workflow template $id@$version was not found.")
}
