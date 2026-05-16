plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.paperApiV1194)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
