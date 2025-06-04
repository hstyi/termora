plugins {
    alias(libs.plugins.kotlin.jvm)
}


dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    implementation("com.fifesoft:languagesupport:3.3.0")
    implementation("com.fifesoft:autocomplete:3.3.2")
}

ext.set("TO-Plugin-Entry", "app.termora.plugins.editor.EditorPlugin")
apply(from = "$rootDir/plugins/common.gradle.kts")

