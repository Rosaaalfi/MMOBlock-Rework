plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    // Pure ECS framework — zero dependencies beyond Java standard library
}

