package me.moiclathy.edb.core.config.packages.validation

class PackageValidationException(

    val validation: PackageValidationResult

) : RuntimeException(

    "Package validation failed"

)