<div align="center">

![](https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png)
# MMOBlock
*- Unblock the Fun, One Click at a Time with MMOBlock -*

[![Build Status](https://github.com/Rosaaalfi/MMOBlock-Rework/actions/workflows/gradle.yml/badge.svg)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Open Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?label=open%20issues)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Maven Central](https://img.shields.io/maven-central/v/me.chyxelmc/mmoblock-api?label=maven%20central)](https://search.maven.org/search?q=g:me.chyxelmc%20AND%20a:mmoblock-api)
[![Javadoc](https://img.shields.io/badge/javadoc-javadoc.io-blue)](https://www.javadoc.io/doc/me.chyxelmc/mmoblock-api)

</div>

**MMOBlock** is a modular plugin for Paper-based Minecraft servers (and compatible forks). This repository is organized into several independent modules to simplify development, testing, and compatibility across different NMS versions.
---

## 📚 Repository Module Overview

- **mmoblock-api**: Public interfaces and data models used across modules (the plugin API).
- **nms-loader**: Loader/registry for `NmsAdapter` implementations and runtime utilities (includes ECS examples and hologram utilities).
- **plugin**: Main plugin implementation (`MMOBlock`) containing runtime services, listeners, configuration, and user-facing resources.
- **nms-mojang-v* / nms-spigot-v* / nms-v* / nms-v26_1**: NMS implementations targeting specific Minecraft/server versions. Each module provides adapters for its target version and registers them via `NmsAdapterProvider`.
- **platform/**: Platform integration modules (schedulers, Paper/Folia adapters, etc.).

If you're working on NMS-related features, edit the module that matches your target server version (e.g. `nms-v1_21_4` for 1.21.4).
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

## Icon: saving locally

If you want to save the repository icon into the plugin resources locally, run:

```bash
mkdir -p plugin/src/main/resources
curl -L -o plugin/src/main/resources/icon.png \
	https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png
```

This will place the `icon.png` file under `plugin/src/main/resources` so the plugin can access it at runtime.


## 📦 Published Artifacts & Coordinates

The `mmoblock-api` artifact is published to Maven Central. Known published release on Central:

- GroupId: `me.chyxelmc`
- ArtifactId: `mmoblock-api`
- Example available version: `26.1.1-BETA` (available on Maven Central)

Add the dependency to your project:

- Gradle (Kotlin DSL):
```kotlin
implementation("me.chyxelmc:mmoblock-api:26.1.1-BETA")
```

- Maven:
```xml
<dependency>
	<groupId>me.chyxelmc</groupId>
	<artifactId>mmoblock-api</artifactId>
	<version>26.1.1-BETA</version>
</dependency>
```

Note: The repository's development version in `gradle.properties` is `3.0.0` (snapshots). Snapshots are published to Sonatype snapshots; releases must be published via the release flow.

---

## Example: Using the API

Below are short examples showing how to add the `mmoblock-api` dependency and use common API features in your plugin.

Dependency (Gradle Kotlin DSL):

```kotlin
implementation("me.chyxelmc:mmoblock-api:26.1.1-BETA")
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

These examples show the minimal integration points; consult the `mmoblock-api` module for more services and models.

---

## 🧩 For Contributors

- Use the `mmoblock-api` module when depending on the API from other modules to avoid cyclic dependencies.
- Register `NmsAdapter` implementations under `META-INF/services` for runtime discovery.
- Run module-specific tests before opening a PR.

---

## 📜 License & Support

- Website: https://chyxelmc.me
- Report issues via GitHub Issues.

Thanks for using and contributing to MMOBlock!
