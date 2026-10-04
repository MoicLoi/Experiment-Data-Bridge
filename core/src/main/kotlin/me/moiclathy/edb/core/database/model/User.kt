package me.moiclathy.edb.core.database.model

import me.moiclathy.edb.core.security.UserRole


data class User(

    /**
     * 数据库主键
     */
    val id: Int,


    /**
     * 登录用户名
     */
    val username: String,


    /**
     * BCrypt密码
     */
    val passwordHash: String,


    /**
     * 显示名称
     */
    val fullName: String,


    /**
     * 邮箱
     */
    val email: String?,


    /**
     * 员工编号
     */
    val employeeId: String?,


    /**
     * 权限角色
     */
    val role: UserRole,


    /**
     * 是否启用
     */
    val isActive: Boolean
)