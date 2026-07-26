plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    implementation(project(":mmoblock-utils"))
    compileOnly(libs.paperApiV1194)
    compileOnly(libs.modelEngineApi)
    compileOnly(libs.betterModelApi)
    compileOnly(libs.betterModelBukkitApi)
    compileOnly(libs.itemsAdderApi)
    compileOnly(libs.craftEngineBukkitApi)
    compileOnly(libs.craftEngineCoreApi)
    compileOnly(libs.mmoitemsApi)
    compileOnly(libs.mythiclibApi)
    compileOnly(libs.mmocoreApi)
}
