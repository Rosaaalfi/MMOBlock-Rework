rootProject.name = "MMOBlock"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.canvasmc.io/releases")
    }
}

include("mmoblock-ecs")
project(":mmoblock-ecs").projectDir = file("mmoblock-ecs")

include("mmoblock-plugin")
project(":mmoblock-plugin").projectDir = file("mmoblock-plugin")

include("mmoblock-api")

include("mmoblock-domain")
project(":mmoblock-domain").projectDir = file("mmoblock-domain")

include("mmoblock-persistence")
project(":mmoblock-persistence").projectDir = file("mmoblock-persistence")

include("mmoblock-utils")
project(":mmoblock-utils").projectDir = file("mmoblock-utils")

include("mmoblock-integration")
project(":mmoblock-integration").projectDir = file("mmoblock-integration")

include("nms-common")
project(":nms-common").projectDir = file("mmoblock-nms/nms-common")
include("nms-v1_21_1")
project(":nms-v1_21_1").projectDir = file("mmoblock-nms/nms-v1_21_1")
include("nms-v1_21_4")
project(":nms-v1_21_4").projectDir = file("mmoblock-nms/nms-v1_21_4")
include("nms-v1_21_11")
project(":nms-v1_21_11").projectDir = file("mmoblock-nms/nms-v1_21_11")
include("nms-v26_1")
project(":nms-v26_1").projectDir = file("mmoblock-nms/nms-v26_1")
include("nms-v26_2")
project(":nms-v26_2").projectDir = file("mmoblock-nms/nms-v26_2")

include("nms-mojang-v1_19_4")
project(":nms-mojang-v1_19_4").projectDir = file("mmoblock-nms/nms-mojang-v1_19_4")
include("nms-spigot-v1_19_4")
project(":nms-spigot-v1_19_4").projectDir = file("mmoblock-nms/nms-spigot-v1_19_4")
include("nms-mojang-v1_20_4")
project(":nms-mojang-v1_20_4").projectDir = file("mmoblock-nms/nms-mojang-v1_20_4")
include("nms-spigot-v1_20_4")
project(":nms-spigot-v1_20_4").projectDir = file("mmoblock-nms/nms-spigot-v1_20_4")

include("mmoblock-platform")
project(":mmoblock-platform").projectDir = file("mmoblock-platform")
include("platform-api")
project(":platform-api").projectDir = file("mmoblock-platform/platform-api")
include("platform-folia")
project(":platform-folia").projectDir = file("mmoblock-platform/platform-folia")
include("platform-paper")
project(":platform-paper").projectDir = file("mmoblock-platform/platform-paper")
