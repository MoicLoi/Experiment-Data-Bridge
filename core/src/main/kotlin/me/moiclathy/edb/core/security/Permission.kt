package me.moiclathy.edb.core.security

/**
 * EDB 配置管理相关权限。
 *
 * 权限只描述“能做什么”，
 * 不负责判断当前用户是否拥有权限。
 */
enum class Permission {

    /**
     * 执行配置 Merge。
     */
    CONFIGURATION_MERGE,

    /**
     * 执行配置 Compare。
     */
    CONFIGURATION_COMPARE,

    /**
     * 查看 Configuration Snapshot。
     */
    SNAPSHOT_READ,

    /**
     * 创建 Configuration Snapshot。
     */
    SNAPSHOT_CREATE,

    /**
     * 删除 Configuration Snapshot。
     */
    SNAPSHOT_DELETE
}