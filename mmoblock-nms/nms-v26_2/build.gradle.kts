plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

dependencies {
    api(project(":nms-common"))
    paperweight.paperDevBundle("26.2.build.60-beta")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

paperweight {
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

