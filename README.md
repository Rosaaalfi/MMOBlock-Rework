# 🪨 MMOBlock

**MMOBlock** is a high-performance [PaperMC](https://papermc.io/) plugin that lets you place fully customizable interactive block-entities anywhere in your world. Players mine them with specific tools, earn drops and XP, and the blocks respawn automatically — all driven by plain YAML config files.

> Built on an **Entity Component System (ECS)** architecture with native NMS support across multiple Minecraft versions.

---

## ✨ Features

| Feature | Details |
|---|---|
| 🧱 **Custom Block Entities** | Spawn interactive blocks at any location via command or API |
| ⛏️ **Mining System** | Per-tool click counts, durability cost, and animated breaking feedback |
| 🎁 **Flexible Drop System** | Items, experience, and command rewards with configurable chance |
| 🔁 **Auto Respawn** | Configurable dead-delay and respawn timers per block type |
| 💬 **Hologram Display** | Up to 4 lines including live progress bars and respawn countdowns |
| 🎨 **Multiple Model Types** | Vanilla block, MMOBlock `.bbmodel`, or ModelEngine rig |
| 💾 **Persistence** | Block state survives restarts via H2 (default), MySQL, or Redis |
| 🌐 **Multi-Language** | Ships with `en-us`, `id-id`, and `ja-jp`; fully customisable |
| 📊 **bStats** | Anonymous usage stats (can be disabled) |

---

## 🔧 Requirements

| Requirement | Version |
|---|---|
| **Server** | [PaperMC](https://papermc.io/) |
| **Java** | 21+ |
| **Minecraft** | 1.19.4 · 1.20.4 · 1.21.1 · 1.21.4 · 1.21.11 |

### Soft Dependencies (optional)

| Plugin | Purpose |
|---|---|
| [ModelEngine](https://mythiccraft.io/index.php?resources/model-engine%E2%80%94ultimate-entity-model-manager-1-16-5-1-21.389/) | 3D rigged model support |
| [ItemsAdder](https://www.spigotmc.org/resources/itemsadder%E2%8F%B3custom-items-textures-animated-emotes-etc.73355/) | Custom texture/resource-pack integration |
| [DecentHolograms](https://www.spigotmc.org/resources/decentholograms-1-8-1-21-4-no-dependencies.96927/) | High-performance hologram backend (replaces Vanilla packets) |

---

## 📦 Installation

1. Download the latest `MMOBlock-<version>.jar` from [Releases](../../releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server — example config and block definitions are generated automatically.
4. Optionally install soft-dependency plugins before starting.

---

## ⚙️ Configuration

### `config.yml`

```yaml
debug: false          # verbose console logging
bStats: true          # anonymous usage stats
updateChecker: true   # notify admins of new versions
lang: en-us           # language file (en-us | id-id | ja-jp)
hologramLib: "Vanilla" # "Vanilla" or "DecentHolograms"

databases:
  h2:
    enabled: true
    file: data.db
  mysql:
    enabled: false
    host: localhost
    port: 3306
    database: mmoblock
    username: root
    password: password
  redis:
    enabled: false
    host: localhost
    port: 6379
    password: ""
    database: 0
```

---

## 📁 Block Definition (`blocks/`)

Each `.yml` file under `plugins/MMOBlock/blocks/` defines one or more block types.

```yaml
exampleEntity:
  name: Example
  respawnTime: 60       # seconds before block reappears
  deadDelay: 5          # seconds the corpse lingers before despawning

  sound:
    onClick: block.stone.hit
    onDead: block.stone.break
    onRespawn: block.stone.place

  particleBreak: true   # burst of block particles on death
  breakAnimation: true  # packet-based crack animation while mining

  hitbox:
    width: 1.0
    height: 1.0

  raycastOutline:
    enabled: false
    color: "&0"         # glow outline color when player looks at block

  modelType:
    block:
      enabled: true
      block: minecraft:diamond_block   # vanilla block display

    mmoblock:
      enabled: false
      model: example_ore.bbmodel
      size: 1.0
      onDead: death
      onCLick: click

    modelEngine:
      enabled: false
      model: iron_crystal:1.0
      onClick: example_onClick_animation;0.1;0.1;1.0
      onDead: example_onDead_animation;0.1;0.1;1.0

  allowedTools:
    - exampleTools           # references tools/<name>.yml

  display:
    - line: 1
      text: "&6Example Entity"
    - line: 2
      text: "<gray>This is an example entity.</gray>"
      click: "&7[&r{progress_bar}&7]"   # shown while mining
    - line: 3
      text: "&7Click to Mine!"
      dead: "&7Respawns in: &c{respawn_time}s"
    - line: 4
      item: minecraft:diamond            # floating item icon

  displayHeight: 2.5
```

---

## 🪓 Tool Definition (`tools/`)

```yaml
exampleTools:
  - material: DIAMOND_PICKAXE
    both_click:              # left_click | right_click | both_click
      decreaseDurability: 15 # durability removed per hit
      clickNeeded: 4         # hits required to break
    allowedDrops:
      - exampleDrops         # references drops/<name>.yml

  - material: NETHERITE_PICKAXE
    left_click:
      decreaseDurability: 10
      clickNeeded: 3
    allowedDrops:
      - exampleDrops
```

---

## 🎁 Drop Definition (`drops/`)

```yaml
exampleDrops:
  - material: iron_ingot
    total: [ 1-2 ]       # random amount in range
    chances: 0.4         # 40 % drop chance
    drop_type: inventory # inventory | front_ground | center_ground

  - experience: [ 10-20 ]
    chances: 0.5         # 50 % chance
    drop_type: front_ground

  - command: "give %player% minecraft:diamond 5"
    chances: 0.1         # 10 % chance
```

---

## 💻 Commands

All commands require the `mmoblock.admin` permission (default: **OP**).

| Command | Description |
|---|---|
| `/mmoblock place <id> <x> <y> <z> <world> <facing>` | Place a block entity |
| `/mmoblock remove <id> <x> <y> <z> <world>` | Remove a placed block entity |
| `/mmoblock reload [config\|blocks\|drops\|lang\|tools]` | Hot-reload configuration files |

**Facing values:** `north` · `south` · `east` · `west`

---

## 🔑 Permissions

| Permission | Description | Default |
|---|---|---|
| `mmoblock.admin` | Full access to all `/mmoblock` commands | OP |

---

## 🏗️ Architecture

```
MMOBlock/
├── plugin/               Core plugin module (Java 21)
│   ├── command/          /mmoblock command handler
│   ├── config/           YAML config loading & hot-reload
│   ├── listener/         Bukkit event listeners
│   ├── model/            Data models (BlockDefinition, DropEntry, etc.)
│   ├── persistence/      DB layer (H2 / MySQL / Redis)
│   └── runtime/
│       ├── ecs/          Entity Component System
│       │   └── system/   DropSystem, MiningSystem, RespawnSystem, …
│       ├── BlockRuntimeService.java
│       └── HologramRuntimeService.java
├── nms-loader/           NMS adapter selection at runtime
├── nms-v1_19_4/          NMS implementation for 1.19.4
├── nms-v1_20_4/          NMS implementation for 1.20.4
├── nms-v1_21_1/          NMS implementation for 1.21.1
├── nms-v1_21_4/          NMS implementation for 1.21.4
└── nms-v1_21_11/         NMS implementation for 1.21.11
```

---

## 🛠️ Building from Source

```bash
git clone https://github.com/Rosaaalfi/MMOBlock-Rework.git
cd MMOBlock-Rework
./gradlew pluginJar
# Output: plugin/build/libs/MMOBlock-<version>.jar
```

Requires **Java 21+** and an internet connection for dependency resolution.

---

## 📜 License

This project is not currently under a public license. All rights reserved — © Aniko / [chyxelmc.me](https://chyxelmc.me).

---

## 🙋 Support

- **Website:** [chyxelmc.me](https://chyxelmc.me)
- **Issues:** [GitHub Issues](../../issues)
