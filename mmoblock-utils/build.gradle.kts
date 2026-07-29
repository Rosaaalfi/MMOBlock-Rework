plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    compileOnly(libs.paperApiV1194)
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
}
