import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
    signing

    id("com.tddworks.central-publisher") version "0.2.0-alpha.1"
}

dependencies {
    compileOnly(libs.paperApiV1194)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)

    withJavadocJar()
    withSourcesJar()
}

base {
    archivesName.set("mmoblock-api")
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions)
            .addBooleanOption("html5", true)
    }

    (options as StandardJavadocDocletOptions)
        .addStringOption("Xdoclint:none", "-quiet")
}

// ==========================================================
// VERSIONING
// ==========================================================

val baseVersion = project.version.toString()
val isSnapshot = project.hasProperty("snapshot")
val isRelease = project.hasProperty("release")

project.version = when {
    isSnapshot -> {
        "$baseVersion-SNAPSHOT"
    }
    isRelease -> {
        baseVersion
    }
    else -> {
        "$baseVersion-RELEASE"
    }
}
println("Publishing version: ${project.version}")

// ==========================================================
// SIGNING
// ==========================================================

signing {
    val signingKey =
        System.getenv("SIGNING_KEY")
            ?: project.findProperty("signingKey")?.toString()
    val signingPassword =
        System.getenv("SIGNING_PASSWORD")
            ?: project.findProperty("signingPassword")?.toString()
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications)
}

// ==========================================================
// MAVEN PUBLISH
// ==========================================================

publishing {
    repositories {
        maven {
            name = "SonatypeSnapshots"
            url = uri(
                "https://central.sonatype.com/repository/maven-snapshots/"
            )
            credentials {
                username =
                    System.getenv("MAVEN_CENTRAL_USERNAME")
                        ?: project.findProperty("mavenCentralUsername")?.toString()
                password =
                    System.getenv("MAVEN_CENTRAL_PASSWORD")
                        ?: project.findProperty("mavenCentralPassword")?.toString()
            }
            mavenContent {
                snapshotsOnly()
            }
        }

        maven {
            name = "LocalRepo"
            url = uri(
                "${rootProject.layout.buildDirectory.get()}/maven-repo"
            )
        }
    }
}

// ==========================================================
// CENTRAL PUBLISHER (RELEASE ONLY)
// ==========================================================

centralPublisher {
    credentials {
        username =
            System.getenv("MAVEN_CENTRAL_USERNAME")
                ?: project.findProperty("mavenCentralUsername")?.toString()
                        ?: ""
        password =
            System.getenv("MAVEN_CENTRAL_PASSWORD")
                ?: project.findProperty("mavenCentralPassword")?.toString()
                        ?: ""
    }
    projectInfo {
        name = "MMOBlock API"
        description = "API for MMOBlock"
        url = "https://github.com/Rosaaalfi/MMOBlock-Rework"
        license {
            name = "MIT License"
            url = "https://opensource.org/licenses/MIT"
        }
        developer {
            id = "chyxelmc"
            name = "ChyxelMC"
            email = "anikosyahraramadhani@outlook.com"
        }
        scm {
            connection =
                "scm:git:git@github.com:Rosaaalfi/MMOBlock-Rework.git"
            developerConnection =
                "scm:git:git@github.com:Rosaaalfi/MMOBlock-Rework.git"
            url =
                "https://github.com/Rosaaalfi/MMOBlock-Rework"
        }
    }
    publishing {
        autoPublish = true
        aggregation = false
    }
}