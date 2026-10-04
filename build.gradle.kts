group = "me.moiclathy.edb"
version = "1.0.0-SNAPSHOT"

val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val typecheckWebUi by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Type-checks the web UI before creating a release package."
    workingDir(layout.projectDirectory.dir("web-ui"))
    commandLine(npmCommand, "run", "typecheck")
}

val buildWebUi by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the production web UI."
    dependsOn(typecheckWebUi)
    workingDir(layout.projectDirectory.dir("web-ui"))
    commandLine(npmCommand, "run", "build")
}

tasks.register<Exec>("packageDemoV1") {
    group = "distribution"
    description = "Builds and verifies the complete Demo v1 distribution under release/edb-demo-v1."
    dependsOn(buildWebUi, ":web:buildFatJar", ":cli:installDist")

    val packagingScript = layout.projectDirectory.file("packaging/demo-v1/package-demo.ps1")
    workingDir(layout.projectDirectory)

    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", packagingScript.asFile.absolutePath, "-SkipBuild",
        )
    } else {
        commandLine(
            "pwsh", "-NoProfile", "-File",
            packagingScript.asFile.absolutePath, "-SkipBuild",
        )
    }

    doFirst {
        check(packagingScript.asFile.isFile) {
            "Demo packaging script not found: ${packagingScript.asFile}"
        }
    }
}

tasks.register<Exec>("packagePretest") {
    group = "distribution"
    description = "Builds a non-stable pretest distribution under release/edb-demo-v1-pretest."
    dependsOn(buildWebUi, ":web:buildFatJar", ":cli:installDist")
    val packagingScript = layout.projectDirectory.file("packaging/demo-v1/package-demo.ps1")
    workingDir(layout.projectDirectory)
    commandLine(
        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", packagingScript.asFile.absolutePath, "-SkipBuild",
        "-ReleaseName", "edb-demo-v1-pretest", "-VersionLabel", "0.25.0-pretest.1",
    )
}
