plugins {
    id("buildsrc.convention.kotlin-common-conventions")
}

group = "me.moiclathy.edb.plugin"

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
