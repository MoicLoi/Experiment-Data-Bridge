package me.moiclathy.edb.core.security

/**
 * Configuration 领域权限定义。
 *
 * 用于集中表达配置相关操作需要的权限。
 */
object ConfigurationPermissions {

    val MERGE =
        Permission.CONFIGURATION_MERGE

    val COMPARE =
        Permission.CONFIGURATION_COMPARE

    val SNAPSHOT_READ =
        Permission.SNAPSHOT_READ

    val SNAPSHOT_CREATE =
        Permission.SNAPSHOT_CREATE

    val SNAPSHOT_DELETE =
        Permission.SNAPSHOT_DELETE
}