plugins {
    base
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("io.canvasmc.weaver.userdev") version "2.4.3" apply false
}

allprojects {
    group = "me.chyxelmc"
    version = project.property("version").toString()

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://jitpack.io")
        maven("https://repo.tcoded.com/releases")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://maven.canvasmc.io/public")
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
