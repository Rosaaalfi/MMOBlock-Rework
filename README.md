<div align="center">

<img src="https://raw.githubusercontent.com/Rosaaalfi/MMOBlock-Rework/refs/heads/support-old-clients/plugin/src/main/resources/icon.png" width="120" alt="MMOBlock Logo"/>

# MMOBlock

### *Unblock the Fun, One Click at a Time.*

[![Build](https://img.shields.io/github/actions/workflow/status/Rosaaalfi/MMOBlock-Rework/gradle.yml?style=for-the-badge&label=Build&labelColor=1A1B26&color=2EA043&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Code Quality Badge](https://img.shields.io/codacy/grade/372316daf86d4bfeb5d01b4d53473782?style=for-the-badge&label=Code%20Quality&labelColor=1A1B26&logo=codacy)](https://app.codacy.com/gh/Rosaaalfi/MMOBlock-Rework/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?style=for-the-badge&label=Issues&labelColor=1A1B26&color=D15794&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Chyxel Repo](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Frepo.chyxelmc.me%2Frepository%2Findex.json&query=%24.artifacts%5B0%5D.latestVersion&prefix=mmoblock-api%20-%20v&style=for-the-badge&logo=apachemaven&label=Chyxel%20Repo&labelColor=1A1B26)](https://repo.chyxelmc.me)</div>

---

## About

**MMOBlock** is a modular Minecraft plugin designed for modern **Paper-based servers** and compatible server software such as **Folia**.

The project focuses on:

| Goal                     | Description                                     |
|--------------------------|-------------------------------------------------|
| **Cross-version**        | Supports multiple Minecraft versions seamlessly |
| **Modular Architecture** | Independent modules for scalability             |
| **Thread-safe Systems**  | Full Folia & Paper multi-thread safety          |
| **Performance**          | Optimized for high-load servers                 |
| **Extensible API**       | Developer-friendly API for integrations         |

---

## Repository Structure

```
MMOBlock-Rework/
 ├── mmoblock-api/          → Public API bridge for third-party developers
 ├── mmoblock-ecs/          → ECS (Entity Component System) core engine
 ├── mmoblock-plugin/       → Main gameplay & plugin logic (shadowJar artifact)
 ├── mmoblock-nms/          → NMS compatibility umbrella
 │   ├── nms-common/        →   Internal runtime loader & adapter abstraction
 │   ├── nms-v1_21_1/       →   Minecraft 1.21.1 (Mojang-mapped)
 │   ├── nms-v1_21_4/       →   Minecraft 1.21.4 (Mojang-mapped)
 │   ├── nms-v1_21_11/      →   Minecraft 1.21.11 (Mojang-mapped)
 │   ├── nms-v26_1/         →   Minecraft 26.1 (Mojang-mapped)
 │   ├── nms-v26_2/         →   Minecraft 26.2 (Mojang-mapped)
 │   ├── nms-mojang-v1_19_4/→   Minecraft 1.19.4 (Mojang-mapped)
 │   ├── nms-mojang-v1_20_4/→   Minecraft 1.20.4 (Mojang-mapped)
 │   ├── nms-spigot-v1_19_4/ →   Minecraft 1.19.4 (Spigot/obfuscated)
 │   └── nms-spigot-v1_20_4/ →   Minecraft 1.20.4 (Spigot/obfuscated)
 ├── mmoblock-platform/ → Thread-safe scheduler abstraction
 │   ├── platform-api/  →   Shared scheduler interface
 │   ├── platform-paper/→   Paper-specific scheduler
 │   └── platform-folia/→   Folia-specific scheduler
 ├── docs/              → Static project page & consumer documentation
 ├── server/            → Pre-configured server directories for testing
 └── runClient/         → Local test server instances
```

> 💡 If you are working on a specific Minecraft version, navigate to the corresponding `nms-*` module under `mmoblock-nms/`.

---

## Features

- YAML-based custom block configuration
- Advanced mining systems
- Tool-based mechanics
- Custom rewards & drops
- Hologram support
- Multi-database support (**H2 / MySQL**)
- 3D model integrations
- Cross-version compatibility
- Paper & Folia support
- Developer-friendly API

---

## Development Roadmap

> **Legend:** &nbsp;<img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="60" height="20"/> Done &nbsp;|&nbsp; <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="60" height="20"/> In Progress &nbsp;|&nbsp; <img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="60" height="20"/> Not Started

---

### Foundation
![Progress](https://img.shields.io/badge/Progress-100%25-238636?style=flat-square)

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Cross-version NMS adapter system</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Legacy support — <code>1.19.4</code> (Mojang &amp; Spigot)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td><code>1.20.4</code> support (Mojang &amp; Spigot)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td><code>1.21.1</code> / <code>1.21.4</code> / <code>1.21.11</code> support</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td><code>26.1</code> / <code>26.2</code> support</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>ServiceLoader-based adapter registration</td></tr>
</table>

---

### Core Compatibility & Performance
![Progress](https://img.shields.io/badge/Progress-100%25-1f6feb?style=flat-square)

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Paper scheduler (single-thread)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Folia scheduler (region-thread safe)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>FoliaSafeScheduler abstraction layer</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Full Paper compatibility</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Full Folia compatibility</td></tr>
</table>

---

### ECS Engine
![Progress](https://img.shields.io/badge/Progress-100%25-8957e5?style=flat-square)

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Core ECS — Entity, Component, System managers</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Block state management (BlockStateComponent)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Mining progress &amp; interaction systems</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Drop spawning system</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Hologram rendering &amp; packet sync</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Respawn timer system</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Persistence read/write systems</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Visual sync &amp; reconciliation</td></tr>
</table>

---

### Feature Expansion
![Progress](https://img.shields.io/badge/Progress-85%25-dcab0e?style=flat-square)

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>YAML-based block/tool/drop/node configuration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Advanced mining &amp; tool-based mechanics</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Custom rewards &amp; drop tables</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Full hologram system (packet-based, animated)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Multi-database support (<b>H2 / MySQL</b>)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Data caching layer</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Fake block system (packet-level)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Block respawn system</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Node system (multi-block structures)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>BDEngine 3D model support</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Schematic system (dead/variant block states)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>PlaceholderAPI expansion</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Multi-language support (EN, ID, JA, ZH-CN, ZH-TW)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Paper command API (Brigadier) integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Utility systems (ColorLogger, ConditionEvaluator, UpdateChecker, Metrics)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>Performance optimization &amp; profiling</td></tr>
</table>

---

### Integrations
![Progress](https://img.shields.io/badge/Progress-37%25-1f6feb?style=flat-square)

**Model Systems**

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>ItemsAdder integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>ModelEngine R4 integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>BetterModel v3 integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Nexo integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Oraxen integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>CraftEngine integration</td></tr>
</table>

**MMO Ecosystem**

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>MMOItems integration</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>MMOCore integration</td></tr>
</table>

---

### Testing & Quality
![Progress](https://img.shields.io/badge/Progress-0%25-6e7681?style=flat-square)

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Cross-version testing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Stress testing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Compatibility validation</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Performance benchmarking</td></tr>
</table>

---

### Final Release
![Progress](https://img.shields.io/badge/Progress-25%25-238636?style=flat-square)

<table>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/done.svg" width="72" height="22"/></td><td>Stable API surface</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/wip.svg" width="72" height="22"/></td><td>CI/CD pipeline (GitHub Actions)</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Final bug fixing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Documentation polishing</td></tr>
<tr><td><img src="https://raw.githubusercontent.com/Rosaaalfi/Iseng/refs/heads/main/undone.svg" width="72" height="22"/></td><td>Public release</td></tr>
</table>

---

## Quick Start

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

## Dependency

MMOBlock API artifacts are published to **Chyxel Repository**. Repository docs and browsing live at <https://repo.chyxelmc.me/home/>, while Maven artifacts are served from:

```
https://public-repo.chyxelmc.me/repository
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://repo.chyxelmc.me/repository")
}

dependencies {
    implementation("me.chyxelmc:mmoblock-api:26.7.25")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>chyxel-repo</id>
        <url>https://repo.chyxelmc.me/repository</url>
    </repository>
</repositories>

<dependency>
    <groupId>me.chyxelmc</groupId>
    <artifactId>mmoblock-api</artifactId>
    <version>26.7.25</version>
</dependency>
```

---

## API Examples

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

## Contributing

1. Use `mmoblock-api` for all API access — avoid touching internals
2. Register your `NmsAdapter` implementations properly via `ServiceLoader`
3. ECS work should submit `EcsCommand` work to the command queue; systems inside the ECS tick loop may mutate components/entities directly
4. NMS features must be implemented in the oldest supported adapters first (1.19.4), then ported forward
5. Run tests before opening a pull request
6. Follow existing code style and module structure

---

## License & Support

- **Website:** [chyxelmc.me](https://chyxelmc.me)
- **Issues:** [GitHub Issues](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)

---

<div align="center">

❤️ **Thanks for using MMOBlock!**

</div>
