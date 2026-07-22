plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.foliaApi)
    api(project(":platform-api"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}
