import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import xyz.jpenilla.runtask.task.AbstractRun

plugins {
    java
    alias(libs.plugins.run.paper)
}

dependencies {
    compileOnly(libs.paperApiV12111)
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.8.13")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("com.h2database:h2:2.2.224")

    implementation(project(":nms-loader"))
    implementation(project(":nms-v1_19_4"))
    implementation(project(":nms-v1_20_4"))
    implementation(project(":nms-v1_21_1"))
    implementation(project(":nms-v1_21_4"))
    implementation(project(":nms-v1_21_11"))
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
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveClassifier.set("slim")
}

tasks.register<Jar>("pluginJar") {
    group = "build"
    description = "Builds the plugin jar with all NMS modules included."
    archiveBaseName.set(rootProject.name)
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

tasks.assemble {
    dependsOn("pluginJar")
}
