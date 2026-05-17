<div align="center">

![](https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png)
# MMOBlock
*- Unblock the Fun, One Click at a Time with MMOBlock -*

[![Build Status](https://img.shields.io/github/actions/workflow/status/Rosaaalfi/MMOBlock-Rework/gradle.yml?style=for-the-badge&label=build&labelColor=1A1B26&color=2EA043&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Open Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?label=open%20issues&style=for-the-badge&labelColor=1A1B26&color=D15794&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Maven Central](https://img.shields.io/badge/maven_central-v3.0.5--RELEASE-007ACC?style=for-the-badge&labelColor=1A1B26&logo=apachemaven)](https://search.maven.org/search?q=g:me.chyxelmc%20AND%20a:mmoblock-api)
[![Javadoc](https://img.shields.io/badge/javadoc-javadoc.io-8957E5?style=for-the-badge&labelColor=1A1B26&logo=openjdk&logoColor=white)](https://www.javadoc.io/doc/me.chyxelmc/mmoblock-api)

</div>

**MMOBlock** is a modular plugin built for Paper-based Minecraft servers and compatible server software. To ensure peak performance, stability, and seamless updates, the plugin's structure is divided into several independent modules that work together seamlessly.

---

## 📚 Repository Module Overview

* **`mmoblock-api`**
  The public bridge for third-party developers who want to integrate their own features with MMOBlock's core functionality.
* **`nms-loader`**
  The background engine responsible for automatic system integration, running visual elements like holograms, and managing internal components.
* **`plugin`**
  The main heart of `MMOBlock` that handles overall operations, including loading configurations, managing player interactions, and running core game features.
* **`nms-mojang-v*` / `nms-spigot-v*` / `nms-v*` / `nms-v26_1**`
  The version-compatibility modules. These ensure that MMOBlock runs smoothly and remains fully compatible across many different versions of Minecraft.
* **`platform/`**
  The performance optimization layer that ensures the plugin operates safely and efficiently on modern server software (such as Paper and Folia).

> **Development Note:** If you are making adjustments for a specific Minecraft version, simply navigate to the module that matches your target server version (e.g., use the `nms-v1_21_4` folder for Minecraft 1.21.4).

---

## ✨ Key Features (at a glance)

- Custom block entities configured via YAML.
- Configurable mining system tied to tools and actions.
- Custom drops with chances and command-based rewards.
- Holograms for displaying block status and progress.
- Flexible persistence (H2/MySQL/Redis) and 3D model support.

---

## 🔧 Quick Usage

1. Build the project with Gradle:

```bash
./gradlew build

```

2. Install the plugin jar from the `plugin` module into your server's `plugins/` folder.
3. Configure `plugins/MMOBlock/` (folders: `blocks/`, `drops/`, `tools/`).

---

## 📦 Published Artifacts & Coordinates

The `mmoblock-api` artifact is published to Maven Central.

* **GroupId:** `me.chyxelmc`
* **ArtifactId:** `mmoblock-api`
* **Version:** `3.0.5-RELEASE`

Add the dependency to your project:

* Gradle (Kotlin DSL):

```kotlin
implementation("me.chyxelmc:mmoblock-api:3.0.5-RELEASE")

```

* Maven:

```xml
<dependency>
    <groupId>me.chyxelmc</groupId>
    <artifactId>mmoblock-api</artifactId>
    <version>3.0.5-RELEASE</version>
</dependency>

```

*Note: The repository's development version in `gradle.properties` is `3.0.0` (snapshots). Snapshots are published to Sonatype snapshots; releases must be published via the release flow.*

---

## Example: Using the API

Below are short examples showing how to add the `mmoblock-api` dependency and use common API features in your project.

Dependency (Gradle Kotlin DSL):

```kotlin
implementation("me.chyxelmc:mmoblock-api:3.0.5-RELEASE")

```

Java example — place a block programmatically:

```java
import me.chyxelmc.mmoblock.api.MMOBlockApi;
import org.bukkit.Bukkit;

// inside your plugin code
MMOBlockApi api = MMOBlockApi.get();
if (api != null) {
    api.getBlockService().placeBlock("exampleEntity", Bukkit.getWorlds().get(0), 100, 64, 100, "north");
}

```

Java example — listen to block events:

```java
import me.chyxelmc.mmoblock.api.event.BlockMineEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {
    @EventHandler
    public void onBlockMine(BlockMineEvent e) {
        if (e.isCompleted()) {
            e.getPlayer().sendMessage("You finished mining: " + e.getDefinition().getId());
        }
    }
}

```

---

## 🧩 For Contributors

* Use the `mmoblock-api` module when depending on the API from other modules to avoid cyclic dependencies.
* Register `NmsAdapter` implementations under `META-INF/services` for runtime discovery.
* Run module-specific tests before opening a PR.

---

## 📜 License & Support

* Website: https://chyxelmc.me
* Report issues via GitHub Issues.

Thanks for using and contributing to MMOBlock!kembali agar terlihat rapi saat di-render GitHub.

```
