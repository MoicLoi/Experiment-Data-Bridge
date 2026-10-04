package me.moiclathy.edb.core.database.repository

import me.moiclathy.edb.core.database.model.CreateUserCommand
import me.moiclathy.edb.core.database.table.UsersTable
import me.moiclathy.edb.core.security.UserRole
// 假设你的 User 实体类在这个包下，根据实际情况调整导包
import me.moiclathy.edb.core.database.model.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.ForUpdateOption

enum class RoleChangeResult { UPDATED, UNCHANGED, NOT_FOUND, LAST_ACTIVE_ADMIN }

class UserRepository {

    /**
     * 第二步：将 ResultRow 映射为领域模型 User，确保 Repository 对外屏蔽数据库细节
     */
    private fun toUser(row: ResultRow): User =
        User(
            id = row[UsersTable.id],
            username = row[UsersTable.username],
            passwordHash = row[UsersTable.passwordHash],
            fullName = row[UsersTable.fullName],
            email = row[UsersTable.email],
            employeeId = row[UsersTable.employeeId],
            role = row[UsersTable.role],
            isActive = row[UsersTable.isActive]
        )

    /**
     * 第三步：使用 .any() 代替 count() 检查用户名是否存在
     */
    fun existsByUsername(username: String): Boolean {
        return transaction {
            UsersTable
                .select(UsersTable.username)
                .where {
                    UsersTable.username eq username
                }
                .limit(1)
                .any()
        }
    }

    /**
     * 第四步：根据用户名查找用户并映射为 User 对象（供登录等核心逻辑使用）
     */
    fun findByUsername(username: String): User? {
        return transaction {
            UsersTable
                .selectAll()
                .where {
                    UsersTable.username eq username
                }
                .singleOrNull()
                ?.let(::toUser)
        }
    }

    /**
     * 第五步：创建用户，注意将枚举转换为 String 存储（command.role.name）
     */
    fun createUser(command: CreateUserCommand) {
        transaction {
            UsersTable.insert {
                it[username] = command.username
                it[passwordHash] = command.passwordHash
                it[fullName] = command.fullName
                it[email] = command.email
                it[employeeId] = command.employeeId
                it[role] = command.role
                it[isActive] = command.isActive
            }
        }
    }

    /**
     * 第六步：更新用户密码
     */
    fun updatePassword(
        username: String,
        passwordHash: String
    ): Boolean {
        return transaction {
            UsersTable.update(
                { UsersTable.username eq username }
            ) {
                it[UsersTable.passwordHash] = passwordHash
            } > 0
        }
    }

    /**
     * 第七步：禁用用户（将 isActive 置为 false）
     */
    fun deactivateUser(username: String): Boolean {
        return transaction {
            UsersTable.update(
                { UsersTable.username eq username }
            ) {
                it[UsersTable.isActive] = false
            } > 0
        }
    }

//根据ID查询
    fun findById(
        id:Int
    ): User? {

        return transaction {

            UsersTable
                .selectAll()
                .where {
                    UsersTable.id eq id
                }
                .singleOrNull()
                ?.let(::toUser)
        }
    }

//获取全部用户
    fun findAll():List<User>{

        return transaction {

            UsersTable
                .selectAll()
                .map(::toUser)

        }
    }
    //激活用户
    fun activateUser(
        username:String
    ):Boolean{

        return transaction {

            UsersTable.update(
                {
                    UsersTable.username eq username
                }
            ){
                it[isActive]=true
            } > 0
        }
    }

    fun findIdByUsername(
        username:String
    ):Int? {

        return transaction {

            UsersTable
                .selectAll()
                .where {
                    UsersTable.username eq username
                }
                .singleOrNull()
                ?.get(
                    UsersTable.id
                )

        }

    }

    fun changeRoleSafely(username: String, newRole: UserRole): RoleChangeResult = transaction {
        val target = UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
            ?: return@transaction RoleChangeResult.NOT_FOUND

        val currentRole = target[UsersTable.role]
        if (currentRole == newRole) return@transaction RoleChangeResult.UNCHANGED

        if (currentRole == UserRole.ADMIN && target[UsersTable.isActive] && newRole != UserRole.ADMIN) {
            val activeAdministrators = UsersTable
                .selectAll()
                .where { (UsersTable.role eq UserRole.ADMIN) and (UsersTable.isActive eq true) }
                .forUpdate(ForUpdateOption.ForUpdate)
                .count()
            if (activeAdministrators <= 1L) return@transaction RoleChangeResult.LAST_ACTIVE_ADMIN
        }

        UsersTable.update({ UsersTable.username eq username }) {
            it[role] = newRole
        }
        RoleChangeResult.UPDATED
    }
}
