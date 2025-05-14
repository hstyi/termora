import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.2")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("javax.activation:activation:1.1.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.3")
    compileOnly(project(":"))
}


tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to rootProject.version,
            "TO-Plugin-Entry" to "app.termora.plugins.oss.OSSPlugin",
            "TO-Plugin-Range" to rootProject.version,
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