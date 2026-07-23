import org.gradle.api.file.DuplicatesStrategy
import xyz.jpenilla.runtask.task.AbstractRun

plugins {
    java
    alias(libs.plugins.run.paper)
    alias(libs.plugins.run.shadow)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.9.1")
    }
}

dependencies {
    compileOnly(libs.paperApiV1194)
    compileOnly(libs.miniMessageLib)
    compileOnly(libs.h2SqlLib)
    compileOnly(libs.gson)
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
    compileOnly("com.zaxxer:HikariCP:7.0.2")

    //Compability with other Plugins
    compileOnly(libs.papi)
    compileOnly(libs.modelEngineApi)
    compileOnly(libs.betterModelApi)
    compileOnly(libs.betterModelBukkitApi)
    compileOnly(libs.itemsAdderApi)
    compileOnly(libs.craftEngineBukkitApi)
    compileOnly(libs.craftEngineCoreApi)
    compileOnly(libs.mmoitemsApi)
    compileOnly(libs.mythiclibApi)
    compileOnly(libs.mmocoreApi)

    //implementation("me.chyxelmc:mmoblock-api:3.0.0-SNAPSHOT")
    implementation(project(":mmoblock-api"))
    implementation(project(":mmoblock-ecs"))
    implementation(project(":nms-common"))
    runtimeOnly(project(":nms-common"))
    implementation(project(":nms-v1_21_1"))
    implementation(project(":nms-v1_21_4"))
    implementation(project(":nms-v1_21_11"))
    implementation(project(":nms-v26_1"))
    implementation(project(":nms-v26_2"))
    implementation(project(":nms-mojang-v1_19_4"))
    implementation(project(":nms-mojang-v1_20_4"))
    implementation(project(":platform-api"))
    implementation(project(":platform-folia"))
    implementation(project(":platform-paper"))

    runtimeOnly(project(mapOf("path" to ":nms-spigot-v1_19_4", "configuration" to "reobf")))
    runtimeOnly(project(mapOf("path" to ":nms-spigot-v1_20_4", "configuration" to "reobf")))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<AbstractRun>().configureEach {
    jvmArgs("-Xms2G", "-Xmx2G")
}

tasks.runServer {
    minecraftVersion(libs.versions.minecraft.runtime.get())
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
        expand(props)
    }
}

tasks.jar {
    archiveClassifier.set("slim")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set(rootProject.name)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
}

// ── ProGuard obfuscation ──────────────────────────────────
val obfuscatedJar by tasks.registering(JavaExec::class) {
    dependsOn(tasks.shadowJar)

    val inJar = tasks.shadowJar.flatMap { it.archiveFile }
    val obfJar = layout.buildDirectory.file("libs/${rootProject.name}-${project.version}-obf.jar")

    // Use ProGuard's main class from buildscript dependencies
    classpath = buildscript.configurations["classpath"]
    mainClass = "proguard.ProGuard"

    // Collect all NMS module server dev bundle jars as library jars
    val nmsProjects = listOf(
        ":nms-v1_21_1", ":nms-v1_21_4", ":nms-v1_21_11",
        ":nms-v26_1", ":nms-v26_2",
        ":nms-mojang-v1_19_4", ":nms-mojang-v1_20_4",
        ":nms-spigot-v1_19_4", ":nms-spigot-v1_20_4"
    )

    // Build argument list (lazily evaluated at execution time)
    doFirst {
        val allArgs = mutableListOf(
            "-injars", inJar.get().asFile.absolutePath,
            "-outjars", obfJar.get().asFile.absolutePath
        )

        // Add JDK runtime library (using jmods directory)
        val javaHome = System.getProperty("java.home")
        val jmodsDir = file("$javaHome/jmods")
        if (jmodsDir.isDirectory) {
            allArgs.addAll(listOf("-libraryjars", jmodsDir.absolutePath))
        }

        // Add compile classpath jars as library jars
        configurations.compileClasspath.get().forEach { jar ->
            if (jar.isFile && jar.name.endsWith(".jar")) {
                allArgs.addAll(listOf("-libraryjars", jar.absolutePath))
            }
        }

        // Add NMS dev bundle server jars (contain Minecraft server classes)
        nmsProjects.forEach { nmsProject ->
            val nmsDir = project(nmsProject).projectDir
            val serverJar = file("$nmsDir/.gradle/caches/paperweight/taskCache/mappedServerJar.jar")
            if (serverJar.isFile) {
                allArgs.addAll(listOf("-libraryjars", serverJar.absolutePath))
            }
        }

        allArgs.addAll(listOf(
            "-include", file("proguard-rules.pro").absolutePath,
            "-ignorewarnings"
        ))
        args = allArgs
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
