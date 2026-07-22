plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

dependencies {
    api(project(":nms-common"))
    paperweight.foliaDevBundle("26.1.2.build.8-stable")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

paperweight {
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

