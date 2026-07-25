<div align="center">
  <img src="https://i.ibb.co.com/G3sB5Pwh/mmoblock.png" width="120" alt="MMOBlock Logo"/>

### MMOBlock
### *Unblock the Fun, One Click at a Time.*
#
###
[![Build](https://img.shields.io/github/actions/workflow/status/Rosaaalfi/MMOBlock-Rework/gradle.yml?style=for-the-badge&label=Build&labelColor=1A1B26&color=2EA043&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/actions)
[![Code Quality Badge](https://img.shields.io/codacy/grade/372316daf86d4bfeb5d01b4d53473782?style=for-the-badge&label=Code%20Quality&labelColor=1A1B26&logo=codacy)](https://app.codacy.com/gh/Rosaaalfi/MMOBlock-Rework/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![Issues](https://img.shields.io/github/issues/Rosaaalfi/MMOBlock-Rework?style=for-the-badge&label=Issues&labelColor=1A1B26&color=D15794&logo=github)](https://github.com/Rosaaalfi/MMOBlock-Rework/issues)
[![Chyxel Repo](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Frepo.chyxelmc.me%2Frepository%2Findex.json&query=%24.artifacts%5B0%5D.latestVersion&prefix=mmoblock-api%20-%20v&style=for-the-badge&logo=apachemaven&label=Chyxel%20Repo&labelColor=1A1B26)](https://repo.chyxelmc.me)

</div>

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

```mermaid
gantt
    title MMOBlock Development Roadmap (Mulai Maret 2026 – Q2 2027)
    dateFormat YYYY-MM-DD
    axisFormat %Y-%m

    section Phase 1 — Foundation
        Add legacy support                       :active,  p1_1, 2026-03-01, 2026-03-25
        Stabilize base 1.19.4                    :         p1_2, 2026-03-10, 2026-04-05
        Stabilize other versions                 :         p1_3, 2026-03-25, 2026-04-20

    section Phase 2 — Compatibility
        Multi-thread safe support                :         p2_1, 2026-04-05, 2026-05-10
        Single-thread support                    :         p2_2, 2026-04-15, 2026-06-01
        Full Folia compatibility                 :         p2_3, 2026-05-25, 2026-07-01
        Full Bukkit compatibility                :         p2_4, 2026-06-10, 2026-07-15
        Full Paper compatibility                 :         p2_5, 2026-07-05, 2026-07-30

    section Phase 3 — Features
        Essentials-like utility systems          :         p3_1, 2026-06-15, 2026-07-20
        Performance optimization                 :         p3_2, 2026-07-01, 2026-08-20
        New gameplay modules                     :         p3_3, 2026-07-25, 2026-10-01

    section Phase 4 — Integrations
        MMOItems & MMOCore integration           :         p4_1, 2026-09-10, 2026-11-05
        Resource & Model Systems integration     :         p4_2, 2026-10-15, 2026-12-01
        Plugin API expansion                     :         p4_3, 2026-11-01, 2027-01-15

    section Phase 5 — Testing
        Cross-version & Stress testing           :         p5_1, 2027-01-05, 2027-02-25
        Compatibility & Performance benchmarks   :         p5_2, 2027-02-15, 2027-04-01
        Gameplay balance testing                 :         p5_3, 2027-03-15, 2027-05-25

    section Final Phase — Release
        Production build & Bug fixing            :         f1,   2027-04-15, 2027-06-10
        Documentation & Public release           :         f2,   2027-06-01, 2027-06-30
```
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

MMOBlock API artifacts are published to **Chyxel Repository**. Repository docs and browsing live at <https://repo.chyxelmc.me/>, while Maven artifacts are served from:

```
https://repo.chyxelmc.me/repository
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://repo.chyxelmc.me/repository")
}

dependencies {
    implementation("me.chyxelmc:mmoblock-api:{version}")
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
<version>{version}</version>
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
