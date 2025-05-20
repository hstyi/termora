import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly(project(":"))
}


tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to rootProject.version,
            "TO-Plugin-Entry" to "app.termora.plugins.sync.SyncPlugin",
            "TO-Plugin-Range" to "${rootProject.version}+",
        )
    }
    destinationDirectory.set(file("${layout.buildDirectory.get().asFile.absolutePath}/distributions/${project.name}"))
}

tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath.get().filterNot {
        it.name.startsWith("kotlin-stdlib") || it.name.startsWith("annotations")
    })
    into("${layout.buildDirectory.get().asFile.absolutePath}/distributions/${project.name}")
}

tasks.named("build") {
    dependsOn("copyDependencies")
}

tasks.register("run") {
    dependsOn("build")
    doLast {
        val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
        val runtimeCompileOnly by configurations.creating { extendsFrom(configurations.compileOnly.get()) }
        val mainClass = "app.termora.MainKt"
        val executable = System.getProperty("java.home") + "/bin/java"
        val classpath = (configurations.compileClasspath.get() + configurations.runtimeClasspath.get()
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
                "TERMORA_PLUGIN_DIRECTORY" to "${layout.buildDirectory.get().asFile.absolutePath}/distributions",
                "TERMORA_BASE_DATA_DIR" to "${layout.buildDirectory.get().asFile.absolutePath}/data",
            )
        }
    }
}

tasks.test {
    useJUnitPlatform()
}