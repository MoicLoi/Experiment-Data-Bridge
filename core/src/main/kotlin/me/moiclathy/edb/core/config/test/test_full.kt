package me.moiclathy.edb.core.config.test

import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.ProjectInfo
import me.moiclathy.edb.core.config.WorkflowConfig
import me.moiclathy.edb.core.config.OutputConfig
import me.moiclathy.edb.core.config.loader.SnakeYamlProjectConfigLoader
import java.nio.file.Paths
import kotlin.test.assertEquals

class test_full {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val loader = SnakeYamlProjectConfigLoader()

            // 如果你的项目中已经有 DefaultProjectConfig.DEFAULT，可以直接使用它。
            // 这里为了保证独立运行，使用和前面一样的完整配置对象作为示例：
            val defaultConfig = ProjectConfig(
                version = 1,
                project = ProjectInfo(
                    name = "Demo",
                    description = "Example",
                    species = "rat",
                    owner = "admin"
                ),
                workflow = WorkflowConfig(
                    steps = listOf("collect", "analyse")
                ),
                output = OutputConfig(
                    format = "excel",
                    path = "./output"
                )
            )

            // 1. 保存到文件 test.yaml
            val filePath = Paths.get("test.yaml")
            loader.save(defaultConfig, filePath)
            println("--- Saved config to ${filePath.toAbsolutePath()} ---")

            // 2. 从文件重新加载
            val config = loader.load(filePath)
            println("--- Loaded config ---")
            println(config)

            // 3. 断言验证
            assertEquals(defaultConfig, config)
            println("--- Full Loader Test Passed! ---")
        }
    }
}