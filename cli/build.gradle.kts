


plugins {

    id("buildsrc.convention.kotlin-common-conventions")

    application

}


group = "me.moicalthy.edb.cli"


dependencies {

    // 核心业务模块
    implementation(project(":core"))
    implementation(project(":plugin-api"))


    // CLI框架
    implementation(
        libs.clikt
    )

    implementation(libs.kotlinxSerialization)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

}


application {

    mainClass.set(
        "me.moiclathy.edb.cli.CliMainKt"
    )

}
