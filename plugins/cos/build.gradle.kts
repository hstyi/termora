plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.qcloud:cos_api:5.6.245")
    compileOnly(project(":"))
}


ext.set("TO-Plugin-Entry", "app.termora.plugins.cos.COSPlugin")
apply(from = "$rootDir/plugins/common.gradle.kts")
