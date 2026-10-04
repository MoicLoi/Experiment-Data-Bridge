package me.moiclathy.edb.core.template.service

import me.moiclathy.edb.core.template.model.Template

class TemplatePreviewService {

    fun preview(
        template: Template
    ): String {

        return template.yaml

    }

}