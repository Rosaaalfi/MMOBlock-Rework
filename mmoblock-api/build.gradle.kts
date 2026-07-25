import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.IOException
import java.time.Instant
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
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
        "$baseVersion"
    }
}
println("Publishing version: ${project.version}")

fun loadRootDotEnv(): Map<String, String> {
    val envFile = rootProject.file(".env")
    if (!envFile.isFile) {
        return emptyMap()
    }

    return envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                key to value
            }
        }
        .toMap()
}

val rootDotEnv: Map<String, String> by lazy { loadRootDotEnv() }

fun envKey(name: String): String {
    return buildString {
        name.forEachIndexed { index, char ->
            if (char.isUpperCase() && index > 0) {
                append('_')
            }
            append(char.uppercaseChar())
        }
    }
}

fun optionalPublishProperty(name: String): String? {
    return providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(envKey(name)).orNull?.takeIf { it.isNotBlank() }
        ?: rootDotEnv[name]?.takeIf { it.isNotBlank() }
        ?: rootDotEnv[envKey(name)]?.takeIf { it.isNotBlank() }
}

fun requiredPublishProperty(name: String): String {
    return optionalPublishProperty(name)
        ?: error("Missing publish setting '$name'. Add it to .env or provide it as an environment variable.")
}

// ==========================================================
// MAVEN PUBLISH
// ==========================================================

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("MMOBlock API")
                description.set("API for MMOBlock")
                url.set("https://github.com/Rosaaalfi/MMOBlock-Rework")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("chyxelmc")
                        name.set("ChyxelMC")
                        email.set("anikosyahraramadhani@outlook.com")
                    }
                }

                scm {
                    connection.set(
                        "scm:git:git@github.com:Rosaaalfi/MMOBlock-Rework.git"
                    )
                    developerConnection.set(
                        "scm:git:git@github.com:Rosaaalfi/MMOBlock-Rework.git"
                    )
                    url.set("https://github.com/Rosaaalfi/MMOBlock-Rework")
                }
            }
        }
    }

    repositories {
        maven {
            name = "LocalRepo"
            url = uri(
                "${rootProject.layout.buildDirectory.get()}/maven-repo"
            )
        }
    }
}

// ==========================================================
// CHYXEL REPOSITORY AUTO PUBLISH
// ==========================================================

tasks.register("publishToChyxelRepo") {
    group = "publishing"
    description =
        "Publishes MMOBlock API to Cloudflare R2 using .env or environment credentials."

    dependsOn("publishMavenJavaPublicationToLocalRepoRepository")

    doLast {
        val localRepoDir = rootProject.layout.buildDirectory
            .dir("maven-repo")
            .get()
            .asFile
        val artifactId = base.archivesName.get()
        val versionName = project.version.toString()
        val groupId = project.group.toString()
        val groupPath = groupId.replace('.', '/')
        val artifactDir = localRepoDir.resolve("$groupPath/$artifactId")
        val versionDir = artifactDir.resolve(versionName)

        if (!versionDir.isDirectory) {
            error("Expected Maven artifact directory was not created: $versionDir")
        }

        val r2AccountId = requiredPublishProperty("r2AccountId")
        val r2Bucket = requiredPublishProperty("r2Bucket")
        val r2AccessKeyId = requiredPublishProperty("r2AccessKeyId")
        val r2SecretAccessKey = requiredPublishProperty("r2SecretAccessKey")
        val r2PublicUrl =
            optionalPublishProperty("r2PublicUrl")
                ?: "https://repo.chyxelmc.me"
        val r2Endpoint = "https://$r2AccountId.r2.cloudflarestorage.com"
        val awsCliPath = optionalPublishProperty("awsCliPath") ?: "aws"

        fun runAws(
            vararg args: String,
            ignoreExitValue: Boolean = false,
            printIgnoredOutput: Boolean = false
        ): Int {
            val command = listOf(awsCliPath) + args
            val process = try {
                ProcessBuilder(command)
                    .directory(rootProject.projectDir)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["AWS_ACCESS_KEY_ID"] = r2AccessKeyId
                        environment()["AWS_SECRET_ACCESS_KEY"] = r2SecretAccessKey
                        environment()["AWS_DEFAULT_REGION"] = "auto"
                    }
                    .start()
            } catch (error: IOException) {
                error(
                    "AWS CLI was not found. Install AWS CLI v2 or set " +
                        "awsCliPath in .env. Current value: $awsCliPath"
                )
            }

            val output = process.inputStream.bufferedReader().readText()

            val exitCode = process.waitFor()
            if (output.isNotBlank() && (exitCode == 0 || !ignoreExitValue || printIgnoredOutput)) {
                print(output)
            }
            if (exitCode != 0 && !ignoreExitValue) {
                error(
                    "AWS CLI failed with exit code $exitCode: ${command.joinToString(" ")}\n" +
                        "Check r2Bucket='$r2Bucket', r2AccountId='$r2AccountId', and make sure " +
                        "the R2 access key belongs to the same Cloudflare account and can write to that bucket."
                )
            }

            return exitCode
        }

        val indexFile = localRepoDir.resolve("index.json")
        val indexJsFile = localRepoDir.resolve("index.js")
        val currentIndexFile = temporaryDir.resolve("chyxel-index-current.json")

        val downloadIndexExitCode = runAws(
            "s3",
            "cp",
            "s3://$r2Bucket/repository/index.json",
            currentIndexFile.absolutePath,
            "--endpoint-url",
            r2Endpoint,
            ignoreExitValue = true,
            printIgnoredOutput = false
        )

        val index: MutableMap<String, Any?> =
            if (downloadIndexExitCode == 0 && currentIndexFile.isFile) {
            (JsonSlurper().parse(currentIndexFile) as Map<*, *>)
                .mapKeys { it.key.toString() }
                .mapValues { it.value }
                .toMutableMap()
        } else {
            mutableMapOf("generatedAt" to "", "artifacts" to mutableListOf<Any>())
        }

        val artifacts = (index["artifacts"] as? List<*>)
            ?.mapNotNull { item ->
                (item as? Map<*, *>)
                    ?.mapKeys { it.key.toString() }
                    ?.toMutableMap()
            }
            ?.toMutableList()
            ?: mutableListOf()

        val existingArtifact = artifacts.firstOrNull {
            it["groupId"] == groupId && it["artifactId"] == artifactId
        }

        artifacts.removeAll {
            it["groupId"] == groupId && it["artifactId"] == artifactId
        }

        val versionFiles = versionDir
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?: emptyList()
        val jarFile = versionFiles.firstOrNull {
            it.name == "$artifactId-$versionName.jar"
        } ?: error("JAR file not found in $versionDir")

        @Suppress("UNCHECKED_CAST")
        val existingVersions =
            existingArtifact?.get("versions") as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val existingFiles =
            existingArtifact?.get("files") as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val existingFileSizes =
            existingArtifact?.get("fileSizes") as? Map<String, Any?> ?: emptyMap()

        val updatedFiles = existingFiles.toMutableMap()
        updatedFiles[versionName] = versionFiles.map { it.name }

        val updatedFileSizes = existingFileSizes.toMutableMap()
        updatedFileSizes[versionName] =
            versionFiles.associate { it.name to it.length() }

        val artifactEntry = linkedMapOf<String, Any?>(
            "groupId" to groupId,
            "artifactId" to artifactId,
            "latestVersion" to versionName,
            "description" to "$groupId:$artifactId",
            "size" to jarFile.length(),
            "versions" to (existingVersions + versionName).distinct().sorted(),
            "files" to updatedFiles,
            "fileSizes" to updatedFileSizes
        )

        artifacts.add(artifactEntry)
        artifacts.sortWith(
            compareBy<MutableMap<String, Any?>>(
                { it["groupId"].toString() },
                { it["artifactId"].toString() }
            )
        )

        index["generatedAt"] = Instant.now().toString()
        index["artifacts"] = artifacts
        indexFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(index)))
        indexJsFile.writeText(
            "window.CHYXEL_REPOSITORY_INDEX = ${indexFile.readText()};\n"
        )

        runAws(
            "s3",
            "sync",
            localRepoDir.absolutePath,
            "s3://$r2Bucket/repository",
            "--endpoint-url",
            r2Endpoint,
            "--cache-control",
            "public, max-age=600"
        )

        println(
            "Published $groupId:$artifactId:$versionName to Chyxel Repository"
        )
        println(
            "$r2PublicUrl/repository/$groupPath/$artifactId/$versionName/"
        )
    }
}

tasks.register("printChyxelRepoProperties") {
    group = "help"
    description = "Prints .env keys used by Chyxel auto publish."

    doLast {
        println(
            """
            Add these to .env:

            r2AccountId=your-cloudflare-account-id
            r2Bucket=your-r2-bucket
            r2AccessKeyId=your-r2-access-key-id
            r2SecretAccessKey=your-r2-secret-access-key
            r2PublicUrl=https://repo.chyxelmc.me
            # awsCliPath=C:/Program Files/Amazon/AWSCLIV2/aws.exe
            """.trimIndent()
        )
    }
}
