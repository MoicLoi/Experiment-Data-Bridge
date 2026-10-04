package me.moiclathy.edb.core.template.exception

class TemplateAlreadyExistsException(

    name: String

) : RuntimeException(

    "Template '$name' already exists"

)