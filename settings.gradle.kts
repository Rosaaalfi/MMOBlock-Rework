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
include("nms-v1_21_1")
include("nms-v1_21_4")
include("nms-v1_21_11")
include("nms-v26_1")

include("nms-mojang-v1_19_4")
include("nms-spigot-v1_19_4")

include("platform")
include("platform-scheduler")
include("plugin-folia")
include("plugin-paper")

project(":platform-scheduler").projectDir = file("platform/platform-scheduler")
project(":plugin-folia").projectDir = file("platform/plugin-folia")
project(":plugin-paper").projectDir = file("platform/plugin-paper")
include("nms-mojang-v1_20_4")
include("nms-spigot-v1_20_4")

