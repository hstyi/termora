import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform


tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to rootProject.version,
            "TO-Plugin-Entry" to project.extra.properties["TO-Plugin-Entry"] as String,
            "TO-Plugin-Range" to "${rootProject.version}+",
        )
    }
    destinationDirectory.set(file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/plugins/${project.name}"))
}

tasks.register<Copy>("copy-dependencies") {
    from(configurations.getByName("runtimeClasspath").filterNot {
        it.name.startsWith("kotlin-stdlib") || it.name.startsWith("annotations")
    })
    into("${layout.buildDirectory.get().asFile.absolutePath}/distributions/${project.name}")
}

tasks.named("build") {
    dependsOn("copy-dependencies")
}

tasks.register("run-plugin") {
    dependsOn("build")

    doLast {
        val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()

        val runtimeCompileOnly by configurations.creating { extendsFrom(configurations.getByName("compileOnly")) }
        val mainClass = "app.termora.MainKt"
        val executable = System.getProperty("java.home") + "/bin/java"
        val classpath = (configurations.getByName("compileClasspath") + configurations.getByName("runtimeClasspath")
                + runtimeCompileOnly).joinToString(if (os.isWindows) ";" else ":")
        val commands = mutableListOf(executable)
        commands.add("--add-exports java.base/sun.nio.ch=ALL-UNNAMED")
        if (os.isMacOsX) {
            // NSWindow
            commands.add("--add-opens java.desktop/java.awt=ALL-UNNAMED")
            commands.add("--add-opens java.desktop/sun.lwawt=ALL-UNNAMED")
            commands.add("--add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
            commands.add("-Dapple.awt.application.appearance=system")
            commands.add("--add-opens java.desktop/sun.lwawt.macosx.concurrent=ALL-UNNAMED")
        }
        commands.addAll(listOf("-cp", classpath, mainClass))

        exec {
            commandLine = commands
            environment(
                "TERMORA_PLUGIN_DIRECTORY" to file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/plugins/"),
                "TERMORA_BASE_DATA_DIR" to "${layout.buildDirectory.get().asFile.absolutePath}/data",
            )
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named("clean") {
    doLast {
        file("${rootProject.layout.buildDirectory.get().asFile.absolutePath}/plugins/${project.name}").deleteRecursively()
    }
}