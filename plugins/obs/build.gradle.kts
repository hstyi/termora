plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.huaweicloud:esdk-obs-java-bundle:3.25.4")
    compileOnly(project(":"))
}


ext.set("TO-Plugin-Entry", "app.termora.plugins.obs.OBSPlugin")
apply(from = "$rootDir/plugins/common.gradle.kts")
