import org.gradle.api.file.DuplicatesStrategy
import xyz.jpenilla.runtask.task.AbstractRun

plugins {
    java
    alias(libs.plugins.run.paper)
    alias(libs.plugins.run.shadow)
}

dependencies {
    compileOnly(libs.paperApiV12111)
    compileOnly(libs.miniMessageLib)
    compileOnly(libs.h2SqlLib)
    compileOnly(libs.papi)

    implementation(libs.foliaLib)

    implementation(project(":api"))
    implementation(project(":nms-loader"))
    implementation(project(":nms-v1_21_1"))
    implementation(project(":nms-v1_21_4"))
    implementation(project(":nms-v1_21_11"))
    implementation(project(":nms-mojang-v1_19_4"))
    implementation(project(":nms-mojang-v1_20_4"))

    runtimeOnly(project(mapOf("path" to ":nms-spigot-v1_19_4", "configuration" to "reobf")))
    runtimeOnly(project(mapOf("path" to ":nms-spigot-v1_20_4", "configuration" to "reobf")))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
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
    relocate("com.tcoded.folialib", "me.chyxelmc.mmoblock.lib.folialib")
    mergeServiceFiles()
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}