rootProject.name = "MMOBlock"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.canvasmc.io/releases")
    }
}

include("plugin")
include("mmoblock-api")

include("nms-loader")
project(":nms-loader").projectDir = file("nms/nms-loader")
include("nms-v1_21_1")
project(":nms-v1_21_1").projectDir = file("nms/nms-v1_21_1")
include("nms-v1_21_4")
project(":nms-v1_21_4").projectDir = file("nms/nms-v1_21_4")
include("nms-v1_21_11")
project(":nms-v1_21_11").projectDir = file("nms/nms-v1_21_11")
include("nms-v26_1")
project(":nms-v26_1").projectDir = file("nms/nms-v26_1")
include("nms-v26_2")
project(":nms-v26_2").projectDir = file("nms/nms-v26_2")

include("nms-mojang-v1_19_4")
project(":nms-mojang-v1_19_4").projectDir = file("nms/nms-mojang-v1_19_4")
include("nms-spigot-v1_19_4")
project(":nms-spigot-v1_19_4").projectDir = file("nms/nms-spigot-v1_19_4")
include("nms-mojang-v1_20_4")
project(":nms-mojang-v1_20_4").projectDir = file("nms/nms-mojang-v1_20_4")
include("nms-spigot-v1_20_4")
project(":nms-spigot-v1_20_4").projectDir = file("nms/nms-spigot-v1_20_4")

include("platform")
include("platform-scheduler")
include("plugin-folia")
include("plugin-paper")

project(":platform-scheduler").projectDir = file("platform/platform-scheduler")
project(":plugin-folia").projectDir = file("platform/plugin-folia")
project(":plugin-paper").projectDir = file("platform/plugin-paper")

