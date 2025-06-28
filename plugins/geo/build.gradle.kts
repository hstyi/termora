import org.jetbrains.kotlin.org.apache.commons.io.FileUtils

plugins {
    alias(libs.plugins.kotlin.jvm)
}

project.version = "0.0.3"

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.maxmind.geoip2:geoip2:4.3.1")
    // https://github.com/hstyi/geolite2
    implementation("com.github.hstyi:geolite2:v1.0-202506280303")
}

/**
 * Download GeoLite2-Country.mmdb
 */
tasks.withType<Jar> {
    val file = file("${project.layout.buildDirectory.get().asFile.absolutePath}/GeoLite2-Country.mmdb")
    if (file.exists().not()) {
        FileUtils.forceMkdirParent(file)
        ant.invokeMethod("get", mapOf("src" to "https://git.io/GeoLite2-Country.mmdb", "dest" to file.absolutePath))
    }
    from(file.absolutePath) { into(".") }
}

apply(from = "$rootDir/plugins/common.gradle.kts")

