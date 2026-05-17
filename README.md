<div align="center">

<img src="https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png" width="140" alt="MMOBlock Logo"/>

# MMOBlock

### *Unblock the Fun, One Click at a Time.*

<br>

[![Build Status](https://img.shields.io/github/actions/workflow/status/Rosaaalfi/MMOBlock-Rework/gradle.yml?style=for-the-badge&label=Build&labelColor=1A1B26&color=2EA043&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?style=for-the-badge&label=Issues&labelColor=1A1B26&color=D15794&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Maven Central](https://img.shields.io/badge/Maven_Central-v3.0.5--RELEASE-007ACC?style=for-the-badge&labelColor=1A1B26&logo=apachemaven)](https://search.maven.org/search?q=g:me.chyxelmc%20AND%20a:mmoblock-api)
[![Javadocs](https://img.shields.io/badge/Javadocs-javadoc.io-8957E5?style=for-the-badge&labelColor=1A1B26&logo=openjdk&logoColor=white)](https://www.javadoc.io/doc/me.chyxelmc/mmoblock-api)

</div>

---

# 📖 About

**MMOBlock** is a modular Minecraft plugin designed for modern Paper-based servers and compatible server software such as Folia.

The project focuses on:
- Cross-version compatibility
- Modular architecture
- Thread-safe systems
- Performance optimization
- Extensible API integrations

The repository is separated into multiple independent modules to ensure scalability, maintainability, and long-term support.

---

# 📚 Repository Structure

| Module | Description |
|---|---|
| `mmoblock-api` | Public API bridge for third-party developers |
| `plugin` | Main gameplay & plugin logic |
| `nms-loader` | Internal runtime loader and adapters |
| `nms-v*` | Minecraft version compatibility layers |
| `platform/` | Thread-safe abstraction layer for Paper/Folia |

> If you are working on a specific Minecraft version, navigate to the corresponding `nms-v*` module.

---

# ✨ Features

- YAML-based custom block configuration
- Advanced mining systems
- Tool-based mechanics
- Custom rewards & drops
- Hologram support
- Multi-database support (H2 / MySQL / Redis)
- 3D model integrations
- Cross-version compatibility
- Paper & Folia support
- Developer-friendly API

---

# 🗺️ Development Roadmap

<table>
<tr>
<td width="50%" valign="top">

## 🛠️ Phase 1 — Foundation

<img src="https://img.shields.io/badge/Progress-50%25-238636?style=flat-square">

<br><br>

- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="12"> Add legacy support  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="12"> Stabilize base version `1.19.4`  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Stabilize other supported versions  

</td>
<td width="50%" valign="top">

## ⚙️ Phase 2 — Core Compatibility

<img src="https://img.shields.io/badge/Progress-50%25-1f6feb?style=flat-square">

<br><br>

- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="12"> Multi-thread safe support  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="12"> Single-thread support  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Full Folia compatibility  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Full Bukkit compatibility  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Full Paper compatibility  

</td>
</tr>

<tr>
<td width="50%" valign="top">

## ✨ Phase 3 — Feature Expansion

<img src="https://img.shields.io/badge/Progress-50%25-8957e5?style=flat-square">

<br><br>

- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="12"> Essentials-like utility systems  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="12"> Performance optimization  

</td>
<td width="50%" valign="top">

## 🧩 Phase 4 — Integrations

### MMO Ecosystem
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> MMOItems integration  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> MMOCore integration  

### Resource & Model Systems
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> ItemsAdder integration  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Nexo integration  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Oraxen integration  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> CraftEngine integration  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> ModelEngine integration  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> BetterModel integration  

</td>
</tr>

<tr>
<td width="50%" valign="top">

## 🧪 Phase 5 — Testing

- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Cross-version testing  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Stress testing  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Compatibility validation  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Performance benchmarking  

</td>
<td width="50%" valign="top">

## 🚀 Final Phase — Release

- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Production-ready build  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Final bug fixing  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Documentation polishing  
- <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="12"> Public release  

</td>
</tr>
</table>

---

# 🔧 Quick Usage

## Build

```bash
./gradlew build
```

---

## Installation

1. Build the project
2. Move the generated jar into:

```txt
/plugins/
```

3. Configure MMOBlock inside:

```txt
plugins/MMOBlock/
```

Folders:
- `blocks/`
- `drops/`
- `tools/`

---

# 📦 Maven Dependency

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

# 🧩 API Example

## Place a Block

```java
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

## Listen to Events

```java
@EventHandler
public void onBlockMine(BlockMineEvent e) {
    if (e.isCompleted()) {
        e.getPlayer().sendMessage(
            "You finished mining: " + e.getDefinition().getId()
        );
    }
}
```

---

# 🤝 Contributing

- Use `mmoblock-api` for API access
- Register `NmsAdapter` implementations
- Run tests before opening pull requests

---

# 📜 License & Support

- Website: https://chyxelmc.me
- Issues: GitHub Issues

---

<div align="center">

### ❤️ Thanks for using MMOBlock

</div>
