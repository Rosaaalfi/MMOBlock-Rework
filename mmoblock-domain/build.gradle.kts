plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    compileOnly(libs.paperApiV1194)
    api(project(":mmoblock-api"))
}
