package me.moiclathy.edb.core.config.test

import me.moiclathy.edb.core.config.DefaultProjectConfig
import me.moiclathy.edb.core.config.validation.DefaultConfigValidator
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class valiTest {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // 1. 构造一个非法的配置：将项目名称设为空字符串
            val config = DefaultProjectConfig.DEFAULT.copy(
                project = DefaultProjectConfig.DEFAULT.project.copy(
                    name = ""
                )
            )

            // 2. 使用默认验证器进行校验
            val result = DefaultConfigValidator().validate(config)

            // 3. 断言验证结果不合法
            assertFalse(result.valid)
            println("--- Validation correctly failed as expected ---")

            // 4. 断言错误信息中包含预期的提示
            assertTrue(
                result.errors.contains("project.name is empty")
            )
            println("--- Error message verified: 'project.name is empty' ---")

            println("--- ValiTest Passed Successfully! ---")
        }
    }
}