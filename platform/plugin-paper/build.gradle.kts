plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.paperApiV1194)
    api(project(":platform-scheduler"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
