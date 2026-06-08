import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
val pluginName = project.name
val pluginVersion = project.version.toString()
val rootProjectVersion = rootProject.version.toString()
val pluginsDirectory = rootProject.layout.buildDirectory.dir("plugins")
val pluginDirectory = pluginsDirectory.map { it.dir(pluginName) }
val pluginDataDirectory = layout.buildDirectory.dir("data")
val runtimeClasspathConfiguration = configurations.named("runtimeClasspath")
val compileClasspathConfiguration = configurations.named("compileClasspath")
val compileOnlyConfiguration = configurations.named("compileOnly")
val runtimeCompileOnly = configurations.create("runtimeCompileOnly") {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(compileOnlyConfiguration.get())
}
val pluginXmlProperties = mapOf(
    "projectName" to pluginName,
    "projectVersion" to pluginVersion,
    "rootProjectVersion" to rootProjectVersion,
)

tasks.withType<Jar>().configureEach {

    manifest {
        attributes(
            "Implementation-Title" to pluginName,
            "Implementation-Version" to pluginVersion,
        )
    }

    from(rootProject.layout.projectDirectory.file("plugins/LICENSE")) {
        into("META-INF")
    }

    from(rootProject.layout.projectDirectory.file("plugins/THIRDPARTY")) {
        into("META-INF")
    }

    // archiveBaseName.set("${project.name}-${rootProject.version}")
    destinationDirectory.set(pluginDirectory)
}

tasks.named<Copy>("processResources") {
    filesMatching("META-INF/plugin.xml") {
        expand(pluginXmlProperties)
    }
}

tasks.register<Copy>("copy-dependencies") {
    from(runtimeClasspathConfiguration) {
        exclude {
            it.file.name.startsWith("kotlin-stdlib") || it.file.name.startsWith("annotations")
        }
    }
    into(pluginDirectory)
}

tasks.named("build") {
    dependsOn("copy-dependencies")
}

tasks.register<JavaExec>("run-plugin") {
    dependsOn("build")

    setExecutable("${System.getProperty("java.home")}/bin/java")
    mainClass.set("app.termora.MainKt")
    classpath = files(compileClasspathConfiguration, runtimeClasspathConfiguration, runtimeCompileOnly)
    workingDir = rootProject.layout.projectDirectory.asFile

    systemProperty("app-version", rootProjectVersion)
    jvmArgs("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED")
    if (os.isMacOsX) {
        // NSWindow
        jvmArgs(
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx.concurrent=ALL-UNNAMED",
            "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
        )
        systemProperty("apple.awt.application.appearance", "system")
    }

    doFirst {
        environment(
            "TERMORA_PLUGIN_DIRECTORY" to pluginsDirectory.get().asFile.absolutePath,
            "TERMORA_BASE_DATA_DIR" to pluginDataDirectory.get().asFile.absolutePath,
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    failOnNoDiscoveredTests.set(false)
}

tasks.named<Delete>("clean") {
    delete(pluginDirectory)
}
