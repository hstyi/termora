plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
}

ext.set("TO-Plugin-Entry", "app.termora.plugins.bg.BGPlugin")
apply(from = "$rootDir/plugins/common.gradle.kts")

