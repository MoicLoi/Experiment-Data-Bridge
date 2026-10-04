package me.moiclathy.edb.core.security.exception

import me.moiclathy.edb.core.exception.EDBException

/**
 * 当前用户没有执行指定操作的权限。
 */
class PermissionDeniedException(
    val username: String,
    val permission: String,
    val projectId: Int? = null
) : EDBException(
    code = "PERMISSION_DENIED",
    message = buildMessage(
        username = username,
        permission = permission,
        projectId = projectId
    )
) {

    companion object {

        private fun buildMessage(
            username: String,
            permission: String,
            projectId: Int?
        ): String {

            return if (projectId != null) {
                "User '$username' does not have permission '$permission' for project $projectId."
            } else {
                "User '$username' does not have permission '$permission'."
            }
        }
    }
}