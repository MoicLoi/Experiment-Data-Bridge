package me.moiclathy.edb.core.config.test

import me.moiclathy.edb.core.config.OutputConfig
import me.moiclathy.edb.core.config.ProjectConfig
import me.moiclathy.edb.core.config.ProjectInfo
import me.moiclathy.edb.core.config.WorkflowConfig
import me.moiclathy.edb.core.config.mapper.SnakeYamlProjectConfigMapper
import kotlin.test.assertEquals

class testRound
fun main() {
    val mapper = SnakeYamlProjectConfigMapper()

    val original =
        ProjectConfig(
            version = 1,
            project = ProjectInfo(
                name = "Demo",
                description = "Example",
                species = "rat",
                owner = "admin"
            ),
            workflow = WorkflowConfig(
                steps = listOf(
                    "collect",
                    "analyse"
                )
            ),
            output = OutputConfig(
                format = "excel",
                path = "./output"
            )
        )

    val yaml = mapper.toYaml(original)
    println("--- Generated YAML ---")
    println(yaml)

    val parsed = mapper.fromYaml(yaml)

    // 验证往返前后是否完全一致
    assertEquals(original, parsed)
    println("--- Round-trip Test Passed! ---")
}