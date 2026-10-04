package me.moiclathy.edb.core.config.test

import me.moiclathy.edb.core.config.mapper.SnakeYamlProjectConfigMapper

fun main() {
    val yaml = """
        version: 1
        project:
          name: Demo
          description: Test
          species: rat
          owner: admin
        workflow:
          steps:
            - collect
            - analyse
        output:
          format: excel
          path: ./output
    """.trimIndent()

    val mapper = SnakeYamlProjectConfigMapper()
    val config = mapper.fromYaml(yaml)

    println(config)
}