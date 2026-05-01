plugins {
    base
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
    }
}
