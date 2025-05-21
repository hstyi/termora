plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(project(":"))

    implementation("io.minio:minio:8.5.17")
    compileOnly(project(":"))
}



ext.set("TO-Plugin-Entry", "app.termora.plugins.s3.S3Plugin")
apply(from = "$rootDir/plugins/common.gradle.kts")
