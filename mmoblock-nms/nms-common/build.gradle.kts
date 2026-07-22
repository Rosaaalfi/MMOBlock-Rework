plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.paperApiV1194)
    implementation(project(":mmoblock-ecs"))
    api(project(":platform-api"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
