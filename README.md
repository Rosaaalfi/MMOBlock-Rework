<div align="center">

![](https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png)

# MMOBlock
*- Unblock the Fun, One Click at a Time with MMOBlock -*

[![Build Status](https://img.shields.io/github/actions/workflow/status/Rosaaalfi/MMOBlock-Rework/gradle.yml?style=for-the-badge&label=build&labelColor=1A1B26&color=2EA043&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Open Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?label=open%20issues&style=for-the-badge&labelColor=1A1B26&color=D15794&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Maven Central](https://img.shields.io/badge/maven_central-v3.0.5--RELEASE-007ACC?style=for-the-badge&labelColor=1A1B26&logo=apachemaven)](https://search.maven.org/search?q=g:me.chyxelmc%20AND%20a:mmoblock-api)
[![Javadoc](https://img.shields.io/badge/javadoc-javadoc.io-8957E5?style=for-the-badge&labelColor=1A1B26&logo=openjdk&logoColor=white)](https://www.javadoc.io/doc/me.chyxelmc/mmoblock-api)

</div>

---

## 📖 About MMOBlock

**MMOBlock** is a modular plugin built for Paper-based Minecraft servers and compatible server software.  
The project is designed with scalability, maintainability, and cross-version compatibility in mind.

To ensure maximum stability and performance, the repository is divided into multiple independent modules that work together seamlessly.

---

# 📚 Repository Module Overview

| Module | Description |
|---|---|
| `mmoblock-api` | Public API bridge for third-party developers. |
| `nms-loader` | Internal engine responsible for integrations, holograms, and runtime systems. |
| `plugin` | Main plugin module handling gameplay logic and player interactions. |
| `nms-mojang-v*` / `nms-spigot-v*` / `nms-v*` | Minecraft version compatibility layers. |
| `platform/` | Thread-safe abstraction layer for Paper/Folia compatibility. |

> **Development Note:**  
> If you are working on a specific Minecraft version, navigate to the corresponding NMS module (e.g., `nms-v1_21_4`).

---

# ✨ Features

- Custom block entities via YAML configuration
- Advanced mining system
- Tool-based mechanics
- Custom drops & reward commands
- Hologram support
- Multi-database support (H2 / MySQL / Redis)
- 3D model compatibility
- Cross-version compatibility architecture
- Paper & Folia support layer

---

# 🗺️ Development Roadmap

<p align="center">
  <img src="https://img.shields.io/badge/Completed-2ea043?style=for-the-badge">
  <img src="https://img.shields.io/badge/In_Progress-d29922?style=for-the-badge">
  <img src="https://img.shields.io/badge/Not_Started-6e7681?style=for-the-badge">
</p>

<table>
<tr>
<td width="50%" valign="top">

## 🛠️ Phase 1 — Foundation

<img src="https://img.shields.io/badge/Progress-50%25-238636?style=flat-square">

<br><br>

- <img src="https://img.shields.io/badge/-2ea043?style=flat-square"> Add legacy support  
- <img src="https://img.shields.io/badge/-d29922?style=flat-square"> Stabilize base version `1.19.4`  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Stabilize other supported Minecraft versions  

</td>
<td width="50%" valign="top">

## ⚙️ Phase 2 — Core Compatibility

<img src="https://img.shields.io/badge/Progress-50%25-1f6feb?style=flat-square">

<br><br>

- <img src="https://img.shields.io/badge/-d29922?style=flat-square"> Add multi-thread safe support  
- <img src="https://img.shields.io/badge/-d29922?style=flat-square"> Add single-thread support  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Ensure full compatibility with:
  - Folia
  - Bukkit
  - Paper

</td>
</tr>

<tr>
<td width="50%" valign="top">

## ✨ Phase 3 — Feature Expansion

<img src="https://img.shields.io/badge/Progress-50%25-8957e5?style=flat-square">

<br><br>

- <img src="https://img.shields.io/badge/-d29922?style=flat-square"> Add Essentials-like utility features  
- <img src="https://img.shields.io/badge/-d29922?style=flat-square"> Improve optimization and performance  

</td>
<td width="50%" valign="top">

## 🧩 Phase 4 — Plugin & Model Integrations

### MMO Ecosystem
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> MMOItems integration  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> MMOCore integration  

### Resource & Model Systems
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> ItemsAdder integration  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Nexo integration  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Oraxen integration  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> CraftEngine integration  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> ModelEngine integration  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> BetterModel integration  

</td>
</tr>

<tr>
<td width="50%" valign="top">

## 🧪 Phase 5 — Testing & Validation

- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Cross-version testing  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Thread stress testing  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Plugin compatibility validation  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Performance benchmarking  

</td>
<td width="50%" valign="top">

## 🚀 Final Phase — Release

- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Production-ready build  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Final bug fixing  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Documentation & changelog polishing  
- <img src="https://img.shields.io/badge/-6e7681?style=flat-square"> Public release  

</td>
</tr>
</table>

> **Note:** The roadmap may evolve over time as development priorities shift and new integrations/features are planned.

---

# 🔧 Quick Usage

## Build the Project

```bash
./gradlew build
```

## Installation

1. Build the project using Gradle.
2. Move the generated plugin jar from the `plugin` module into your server's `plugins/` folder.
3. Configure MMOBlock inside:

```text
plugins/MMOBlock/
```

Folders:
- `blocks/`
- `drops/`
- `tools/`

---

# 📦 Published Artifacts

The `mmoblock-api` artifact is published on Maven Central.

| Property | Value |
|---|---|
| GroupId | `me.chyxelmc` |
| ArtifactId | `mmoblock-api` |
| Version | `3.0.5-RELEASE` |

---

## Gradle (Kotlin DSL)

```kotlin
implementation("me.chyxelmc:mmoblock-api:3.0.5-RELEASE")
```

---

## Maven

```xml
<dependency>
    <groupId>me.chyxelmc</groupId>
    <artifactId>mmoblock-api</artifactId>
    <version>3.0.5-RELEASE</version>
</dependency>
```

---

# 🧩 API Examples

## Place a Block Programmatically

```java
import me.chyxelmc.mmoblock.api.MMOBlockApi;
import org.bukkit.Bukkit;

MMOBlockApi api = MMOBlockApi.get();

if (api != null) {
    api.getBlockService().placeBlock(
        "exampleEntity",
        Bukkit.getWorlds().get(0),
        100,
        64,
        100,
        "north"
    );
}
```

---

## Listen to Block Events

```java
import me.chyxelmc.mmoblock.api.event.BlockMineEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onBlockMine(BlockMineEvent e) {
        if (e.isCompleted()) {
            e.getPlayer().sendMessage(
                "You finished mining: " + e.getDefinition().getId()
            );
        }
    }
}
```

---

# 🤝 Contributing

- Use `mmoblock-api` when depending on the API from other modules.
- Register `NmsAdapter` implementations under `META-INF/services`.
- Run module-specific tests before opening pull requests.

---

# 📜 License & Support

- Website: https://chyxelmc.me
- Report issues via GitHub Issues

---

<div align="center">

### Thanks for using and contributing to MMOBlock ❤️

</div>