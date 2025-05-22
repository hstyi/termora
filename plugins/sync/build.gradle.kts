plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
}

ext.set("TO-Plugin-Entry", "app.termora.plugins.sync.SyncPlugin")
apply(from = "$rootDir/plugins/common.gradle.kts")
