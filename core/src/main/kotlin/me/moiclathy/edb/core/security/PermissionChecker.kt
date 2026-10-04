package me.moiclathy.edb.core.security


import me.moiclathy.edb.core.database.model.Project
import me.moiclathy.edb.core.exception.PermissionDeniedException


class PermissionChecker {


    fun canAccessProject(
        userId: Int,
        project: Project
    ): Boolean {

        return project.ownerId == userId

    }



    fun canModifyProject(
        userId: Int,
        project: Project
    ): Boolean {

        return project.ownerId == userId

    }



    fun requireReadable(
        userId: Int,
        project: Project
    ): Boolean {

        return canAccessProject(
            userId,
            project
        )

    }



    fun requireWritable(
        userId: Int,
        project: Project
    ): Boolean {

        return canModifyProject(
            userId,
            project
        )

    }

    /**
     * 判断用户是否拥有指定权限。
     */
    fun hasPermission(
        username: String,
        permission: Permission,
        projectId: Int? = null
    ): Boolean {

        if (username.isBlank()) {
            return false
        }

        return when {
            isAdmin(username) -> true

            else -> defaultPermissions(username)
                .contains(permission)
        }
    }

    /**
     * 要求用户拥有指定权限。
     *
     * 没有权限时统一抛出 PermissionDeniedException。
     */
    fun requirePermission(
        username: String,
        permission: Permission,
        projectId: Int? = null
    ) {

        if (!hasPermission(username, permission, projectId)) {
            throw RuntimeException("User '$username' does not have permission '${permission.name}' for project $projectId")
        }
    }

    /**
     * 当前用户是否为管理员。
     *
     * 0.23.14.1 暂时采用现有 EDB 管理员约定。
     *
     * 后续接入真正的 User / Role 权限数据后，
     * 这里只需要替换实现，不影响上层 Service。
     */
    private fun isAdmin(
        username: String
    ): Boolean {

        return username.equals(
            "admin",
            ignoreCase = true
        )
    }

    /**
     * 非管理员默认权限。
     *
     * 0.23.14.1 暂时不给普通用户开放高风险配置操作。
     */
    private fun defaultPermissions(
        username: String
    ): Set<Permission> {

        return emptySet()
    }

}