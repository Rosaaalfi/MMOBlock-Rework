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

<img src="./svg/roadmap.svg" width="860" alt="Development Roadmap"/>

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
