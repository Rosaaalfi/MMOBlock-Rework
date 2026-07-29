plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    implementation(project(":mmoblock-domain"))
    implementation(project(":mmoblock-utils"))
    compileOnly(libs.paperApiV1194)
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly(libs.h2SqlLib)
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.4")
}
