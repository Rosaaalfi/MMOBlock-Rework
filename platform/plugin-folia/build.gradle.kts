plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.foliaApi)
    api(project(":platform-scheduler"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}
