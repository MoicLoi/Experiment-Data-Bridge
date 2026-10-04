package me.moiclathy.edb.core.exception


class ValidationException(
    message:String
):
    EDBException(
        code="VALIDATION_ERROR",
        message=message
    )