package me.moiclathy.edb.core.exception


class PermissionDeniedException:

    EDBException(

        code = "PERMISSION_DENIED",

        message = "Permission denied"

    )