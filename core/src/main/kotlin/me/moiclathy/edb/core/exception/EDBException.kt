package me.moiclathy.edb.core.exception


open class EDBException(
    val code:String,
    override val message:String
):RuntimeException(message)