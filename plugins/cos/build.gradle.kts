plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.2"



dependencies {
    testImplementation(kotlin("test"))
    implementation("com.qcloud:cos_api:5.6.245")
    compileOnly(project(":"))
}


apply(from = "$rootDir/plugins/common.gradle.kts")
