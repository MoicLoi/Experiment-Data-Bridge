

plugins {

    kotlin("jvm")

    kotlin("plugin.serialization")

    id("buildsrc.convention.kotlin-common-conventions")

    alias(libs.plugins.ktor)

    application
}


group = "me.moiclathy.edb.web"


dependencies {
    implementation(project(":agent"))


    implementation(project(":core"))

    testImplementation(libs.snakeyaml.engine)


    // ======================
    // Ktor Server
    // ======================

    implementation(
        libs.ktor.server.core
    )

    implementation(
        libs.ktor.server.netty
    )

    implementation(
        libs.ktor.server.content.negotiation
    )

    implementation(
        libs.ktor.serialization.kotlinx.json
    )

    implementation(
        libs.ktor.server.cors
    )

    implementation(
        libs.ktor.server.auth
    )

    implementation(
        libs.ktor.server.auth.jwt
    )

    implementation(
        libs.ktor.server.status.pages
    )

    implementation(
        libs.ktor.server.default.headers
    )

    implementation(
        libs.ktor.server.call.logging
    )

    implementation(
        libs.ktor.server.freemarker
    )

    //implementation(
     //   libs.ktor.server.static.content
    //)

    //jackson
    implementation(
        libs.ktor.server.content.negotiation
    )

    // 替换成 Jackson 序列化支持
    implementation(
        libs.ktor.serialization.jackson
    )

    implementation(libs.java.jwt)


    // ======================
    // Test
    // ======================

    testImplementation(
        libs.ktor.server.test.host
    )

    testImplementation(
        libs.testcontainers.core
    )

    testImplementation(
        libs.testcontainers.mysql
    )

    testImplementation(
        libs.testcontainers.redis
    )

    // === 新增以下 JUnit 5 测试依赖 ===
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.test)

    implementation(
        "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
    )

    //koin
    implementation(libs.koin.core)

    implementation(libs.koin.ktor)

    implementation(libs.koin.logger)

    testImplementation(libs.koin.test)
    //mockk
    testImplementation(libs.mockk)

}


ktor {

    fatJar {

        archiveFileName.set(
            "EXPERIMENT_DATA_BRIDGE.jar"
        )

    }

}

application {
    mainClass.set("me.moiclathy.edb.web.ApplicationKt")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    // Flyway 11 and other SPI-based libraries discover providers through
    // META-INF/services. A fat JAR must merge these files instead of letting
    // one dependency overwrite the others.
    mergeServiceFiles()
}
