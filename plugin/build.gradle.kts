import org.gradle.api.file.DuplicatesStrategy
import xyz.jpenilla.runtask.task.AbstractRun

plugins {
    java
    alias(libs.plugins.run.paper)
    alias(libs.plugins.run.shadow)
}

dependencies {
    compileOnly(libs.paperApiV1194)
    compileOnly(libs.miniMessageLib)
    compileOnly(libs.h2SqlLib)
    compileOnly(libs.papi)
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
    compileOnly("com.zaxxer:HikariCP:7.0.2")

    //implementation("me.chyxelmc:mmoblock-api:3.0.0-SNAPSHOT")
    implementation(project(":mmoblock-api"))
    implementation(project(":nms-loader"))
    implementation(project(":nms-v1_21_1"))
    implementation(project(":nms-v1_21_4"))
    implementation(project(":nms-v1_21_11"))
    implementation(project(":nms-v26_1"))
    implementation(project(":nms-mojang-v1_19_4"))
    implementation(project(":nms-mojang-v1_20_4"))
    implementation(project(":platform-scheduler"))
    implementation(project(":plugin-folia"))
    implementation(project(":plugin-paper"))

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
tasks.assemble {
    dependsOn(tasks.shadowJar)
}