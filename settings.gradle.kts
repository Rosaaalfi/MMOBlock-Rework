rootProject.name = "MMOBlock"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include("plugin")
include("api")

include("nms-loader")
include("nms-v1_21_1")
include("nms-v1_21_4")
include("nms-v1_21_11")

include("nms-mojang-v1_19_4")
include("nms-spigot-v1_19_4")
include("nms-mojang-v1_20_4")
include("nms-spigot-v1_20_4")

