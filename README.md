<div align="center">

<img src="https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png" width="120" alt="MMOBlock Logo"/>

# MMOBlock

### *Unblock the Fun, One Click at a Time.*

[![Build](https://img.shields.io/github/actions/workflow/status/Rosaaalfi/MMOBlock-Rework/gradle.yml?style=for-the-badge&label=Build&labelColor=1A1B26&color=2EA043&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?style=for-the-badge&label=Issues&labelColor=1A1B26&color=D15794&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Maven Central](https://img.shields.io/badge/Maven_Central-v3.0.5--RELEASE-007ACC?style=for-the-badge&labelColor=1A1B26&logo=apachemaven)](https://search.maven.org/search?q=g:me.chyxelmc%20AND%20a:mmoblock-api)
[![Javadocs](https://img.shields.io/badge/Javadocs-javadoc.io-8957E5?style=for-the-badge&labelColor=1A1B26&logo=openjdk&logoColor=white)](https://www.javadoc.io/doc/me.chyxelmc/mmoblock-api)

</div>

---

## 📖 About

**MMOBlock** is a modular Minecraft plugin designed for modern **Paper-based servers** and compatible server software such as **Folia**.

The project focuses on:

| Goal | Description |
|------|-------------|
| 🔀 **Cross-version** | Supports multiple Minecraft versions seamlessly |
| 🧱 **Modular Architecture** | Independent modules for scalability |
| 🧵 **Thread-safe Systems** | Full Folia & Paper multi-thread safety |
| ⚡ **Performance** | Optimized for high-load servers |
| 🔌 **Extensible API** | Developer-friendly API for integrations |

---

## 📚 Repository Structure

```
MMOBlock-Rework/
├── mmoblock-api/     → Public API bridge for third-party developers
├── plugin/           → Main gameplay & plugin logic
├── nms-loader/       → Internal runtime loader and adapters
├── nms-v*/           → Minecraft version compatibility layers
└── platform/         → Thread-safe abstraction layer for Paper/Folia
```

> 💡 If you are working on a specific Minecraft version, navigate to the corresponding `nms-v*` module.

---

## ✨ Features

- 🟩 YAML-based custom block configuration
- ⛏️ Advanced mining systems
- 🔨 Tool-based mechanics
- 🎁 Custom rewards & drops
- 💬 Hologram support
- 🗄️ Multi-database support (**H2 / MySQL / Redis**)
- 🎨 3D model integrations
- 🔁 Cross-version compatibility
- 🍃 Paper & Folia support
- 🧩 Developer-friendly API

---

## 🗺️ Development Roadmap

> **Legend:** &nbsp;<img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="60" height="20"/> Done &nbsp;|&nbsp; <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="60" height="20"/> In Progress &nbsp;|&nbsp; <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="60" height="20"/> Not Started

---

### 🛠️ Phase 1 — Foundation
![Progress](https://img.shields.io/badge/Progress-50%25-238636?style=flat-square)

<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Add legacy support</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>Stabilize base version <code>1.19.4</code></td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Stabilize other supported versions</td></tr></table>

---

### ⚙️ Phase 2 — Core Compatibility
![Progress](https://img.shields.io/badge/Progress-50%25-1f6feb?style=flat-square)

<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>Multi-thread safe support</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>Single-thread support</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Full Folia compatibility</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Full Bukkit compatibility</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Full Paper compatibility</td></tr></table>

---

### ✨ Phase 3 — Feature Expansion
![Progress](https://img.shields.io/badge/Progress-50%25-8957e5?style=flat-square)

<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>Essentials-like utility systems</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>Performance optimization</td></tr></table>

---


### 🧩 Phase 4 — Integrations
![Progress](https://img.shields.io/badge/Progress-0%25-6e7681?style=flat-square)

**MMO Ecosystem**

<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>MMOItems integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>MMOCore integration</td></tr></table>


**Resource & Model Systems**


<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>ItemsAdder integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Nexo integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Oraxen integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>CraftEngine integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>ModelEngine integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>BetterModel integration</td></tr></table>

---

### 🧪 Phase 5 — Testing
![Progress](https://img.shields.io/badge/Progress-0%25-6e7681?style=flat-square)


<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Cross-version testing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Stress testing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Compatibility validation</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Performance benchmarking</td></tr></table>

---

### 🚀 Final Phase — Release
![Progress](https://img.shields.io/badge/Progress-0%25-6e7681?style=flat-square)


<table><tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Production-ready build</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Final bug fixing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Documentation polishing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Public release</td></tr></table>

---

## 🔧 Quick Start

### 1. Build

```bash
./gradlew build
```

### 2. Install

Copy the generated `.jar` into your server's plugin folder:

```
server/
└── plugins/
    └── MMOBlock.jar   ← here
```

### 3. Configure

Edit your settings under:

```
plugins/MMOBlock/
├── blocks/    → Block definitions
├── drops/     → Drop tables
└── tools/     → Tool configurations
```

---

## 📦 Dependency

### Gradle (Kotlin DSL)

```kotlin
implementation("me.chyxelmc:mmoblock-api:3.0.5-RELEASE")
```

### Maven

```xml
<dependency>
    <groupId>me.chyxelmc</groupId>
    <artifactId>mmoblock-api</artifactId>
    <version>3.0.5-RELEASE</version>
</dependency>
```

---

## 🧩 API Examples

### Place a Block

```java
MMOBlockApi api = MMOBlockApi.get();

if (api != null) {
    api.getBlockService().placeBlock(
        "exampleEntity",
        Bukkit.getWorlds().get(0),
        100, 64, 100,
        "north"
    );
}
```

### Listen to Events

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

## 🤝 Contributing

1. Use `mmoblock-api` for all API access — avoid touching internals
2. Register your `NmsAdapter` implementations properly
3. Run tests before opening a pull request
4. Follow existing code style and module structure

---

## 📜 License & Support

- 🌐 **Website:** [chyxelmc.me](https://chyxelmc.me)
- 🐛 **Issues:** [GitHub Issues](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)

---

<div align="center">

❤️ **Thanks for using MMOBlock!**

</div>
