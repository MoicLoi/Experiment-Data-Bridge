plugins {

    id("buildsrc.convention.kotlin-common-conventions")
    kotlin("plugin.serialization")

}

group = "me.moiclathy.edb.core"


dependencies {

    implementation(project(":plugin-api"))


    // =========================
    // Kotlin 基础
    // =========================

    implementation(kotlin("stdlib"))

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"
    )

    implementation(
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"
    )

    implementation(
        "org.apache.logging.log4j:log4j-api:2.24.3"
    )







    testImplementation(libs.kotlin.test)

    testImplementation(libs.junit.jupiter)

    //yaml解析
    implementation(libs.snakeyaml.engine)
    // === 新增以下 JUnit 5 测试依赖 ===
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.test)
    //jgit
    implementation(libs.jgit)
    //mock
    testImplementation(libs.mockk)
    //h2
    testImplementation(libs.h2)




    // =========================
    // 数据库
    // =========================

    implementation(
        "com.zaxxer:HikariCP:6.3.0"
    )



    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    implementation(libs.mysql.connector)


    // Flyway API
    implementation(libs.flyway.core)

    implementation(libs.flyway.mysql)



    // =========================
    // Redis
    // =========================

    implementation(
        "io.lettuce:lettuce-core:6.7.1.RELEASE"
    )

    //koin

    implementation(libs.koin.core)

    implementation(libs.koin.ktor)

    implementation(libs.koin.logger)


    // =========================
    // Docker API
    // =========================

    implementation(
        "com.github.docker-java:docker-java-core:3.4.1"
    )

    implementation(
        "com.github.docker-java:docker-java-transport-httpclient5:3.4.1"
    )


    // =========================
    // YAML
    // =========================

    implementation(
        "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.0"
    )

    implementation(
        "com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0"
    )


    // =========================
    // 日志
    // =========================

    implementation(
        "org.apache.logging.log4j:log4j-core:2.24.3"
    )

    implementation(
        "org.apache.logging.log4j:log4j-slf4j2-impl:2.24.3"
    )


    // =========================
    // HTTP Client
    // =========================

    implementation(
        "io.ktor:ktor-client-core:3.2.3"
    )

    implementation(
        "io.ktor:ktor-client-cio:3.2.3"
    )

    implementation(
        "io.ktor:ktor-client-content-negotiation:3.2.3"
    )


    // =========================
    // 安全
    // =========================

    implementation(
        "org.mindrot:jbcrypt:0.4"
    )


    implementation(
        "io.jsonwebtoken:jjwt-api:0.12.6"
    )

    runtimeOnly(
        "io.jsonwebtoken:jjwt-impl:0.12.6"
    )

    runtimeOnly(
        "io.jsonwebtoken:jjwt-jackson:0.12.6"
    )


    // =========================
    // Git
    // =========================

    implementation(
        "org.eclipse.jgit:org.eclipse.jgit:7.2.0.202503040940-r"
    )


    // =========================
    // 文件处理
    // =========================

    implementation(
        "org.apache.poi:poi-ooxml:5.4.1"
    )

    implementation(
        "org.apache.pdfbox:pdfbox:3.0.3"
    )


    //jwt
    implementation(libs.java.jwt)
    implementation(kotlin("test"))


}
