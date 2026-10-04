package me.moiclathy.edb.core.template.exception

class TemplateNotFoundException(

    id: Long

) : RuntimeException(

    "Template $id not found"

)