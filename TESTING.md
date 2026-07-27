# MMOBlock — Testing Guide

> **This document contains testing guidelines for all supported Minecraft versions, platforms, and integrations of MMOBlock.**
>
> Last updated: **July 27, 2026**

---

## Table of Contents

1. [Support Matrix](#-support-matrix)
2. [Testing Preparation](#-testing-preparation)
3. [Testing NMS Versions](#-testing-nms-versions)
4. [Testing Platform](#-testing-platform)
5. [Testing Integrations](#-testing-integrations)
6. [Testing Database](#-testing-database)
7. [Testing Core Features](#-testing-core-features)
8. [Performance Testing](#-performance-testing)
9. [Testing Checklist](#-testing-checklist)
10. [Important Notes](#-important-notes)

---

## Support Matrix

### Minecraft Versions (NMS)

| Version | NMS Module | Mapping | Build Bundle | Status |
|---------|-----------|---------|-------------|--------|
| **1.19.4** | `nms-mojang-v1_19_4` | Mojang | `1.19.4-R0.1-SNAPSHOT` | ✅ **Stable (base)** |
| **1.19.4** | `nms-spigot-v1_19_4` | Spigot/Obf | `1.19.4-R0.1-SNAPSHOT` | ✅ **Stable (base)** |
| **1.20.4** | `nms-mojang-v1_20_4` | Mojang | `1.20.4-R0.1-SNAPSHOT` | ⬜ Needs testing |
| **1.20.4** | `nms-spigot-v1_20_4` | Spigot/Obf | `1.20.4-R0.1-SNAPSHOT` | ⬜ Needs testing |
| **1.21.1** | `nms-v1_21_1` | Mojang | `1.21.1-R0.1-SNAPSHOT` | ⬜ Needs testing |
| **1.21.4** | `nms-v1_21_4` | Mojang | `1.21.4-R0.1-SNAPSHOT` | ⬜ Needs testing |
| **1.21.11** | `nms-v1_21_11` | Mojang | `1.21.11-R0.1-SNAPSHOT` | ⬜ Needs testing |
| **26.1** (Folia) | `nms-v26_1` | Mojang | `26.1.2.build.8-stable` | ⬜ Needs testing |
| **26.2** (Canvas) | `nms-v26_2` | Mojang | `26.2.build.60-beta` | ⬜ Needs testing |

> **Status Key:**
> - ✅ = Tested & stable
> - ⬜ = Not yet tested
> - ❌ = Error/issue found
> - ⚠️ = Partial / minor issues

### Platform Support

| Platform | Module | Status |
|----------|--------|--------|
| **Paper** | `platform-paper` | ✅ **Stable (base)** |
| **Folia** | `platform-folia` | ✅ **Stable (base)** |
| **Canvas** | `platform-folia` (via Canvas API) | ✅ **Stable (base)** |

### Integrations (Soft Dependencies)

| Plugin | Version | Module | Status |
|--------|---------|--------|--------|
| **MMOCore** | 1.13.1-SNAPSHOT | `mmoblock-integration` | ⬜ Needs testing |
| **MMOItems** | 6.10.1-SNAPSHOT | `mmoblock-integration` | ⬜ Needs testing |
| **ItemsAdder** | 4.0.18-beta-10 | `mmoblock-integration` | ⬜ Needs testing |
| **CraftEngine** | 26.7 | `mmoblock-integration` | ⬜ Needs testing |
| **ModelEngine** | R4.1.0 | `mmoblock-integration` | ⬜ Needs testing |
| **BetterModel** | 3.2.0 | `mmoblock-integration` | ⬜ Needs testing |
| **PlaceholderAPI** | 2.12.2 | `mmoblock-integration` | ⬜ Needs testing |

### Database Support

| Database | Status |
|----------|--------|
| **H2** (embedded) | ✅ **Stable (base)** |
| **MySQL** | ⬜ Needs testing |

---

## Testing Preparation

### 1. Build Plugin

```bash
# Build all modules (without obfuscation)
./gradlew shadowJar

# Output at:
# mmoblock-plugin/build/libs/MMOBlock-<version>.jar
```

### 2. Setup Server

Create separate servers for each version to be tested:

```
server/
├── v1_19_4_paper/
│   └── plugins/
│       └── MMOBlock.jar
├── v1_19_4_folia/
│   └── plugins/
│       └── MMOBlock.jar
├── v1_20_4_paper/
│   └── plugins/
│       └── MMOBlock.jar
├── v1_21_1_paper/
│   └── plugins/
│       └── MMOBlock.jar
├── v1_21_4_paper/
│   └── plugins/
│       └── MMOBlock.jar
├── v1_21_11_paper/
│   └── plugins/
│       └── MMOBlock.jar
├── v26_1_folia/
│   └── plugins/
│       └── MMOBlock.jar
└── v26_2_paper/
    └── plugins/
        └── MMOBlock.jar
```

Or use the built-in Gradle run task:

```bash
# Default runtime (1.19.4 Paper)
./gradlew :mmoblock-plugin:runServer

# For other versions, modify mmoblock-plugin/build.gradle.kts:
# tasks.runServer { minecraftVersion("1.21.4") }
```

### 3. Testing Configuration

Enable **debug mode** in `config.yml` for testing:

```yaml
debug: true
```

### 4. Prepare Testing World

Use a superflat world to simplify testing:

```
/gamemode creative
/time set day
/tp @p 0 80 0
```

---

## Testing NMS Versions

### Per-Version Testing Procedure

Each NMS version must be tested with the following procedure:

#### 1. Basic Plugin Load

| Test | Step | Expected Result |
|------|------|-----------------|
| **Load Plugin** | Start server with MMOBlock.jar | Plugin loads without console errors. No `ClassNotFoundException` or `NoSuchMethodError`. |
| **Enable** | Check console after enable | See log: `MMOBlock enabled! (v<version>)` |
| **Dependency Check** | Check DependencyChecker log | All dependencies detected correctly (enabled/not found) |

#### 2. Debug Command

```
/mmoblock debug
```

Expected:
- Displays plugin information without errors
- Shows active NMS adapter
- No `NoClassDefFoundError`

#### 3. Basic Block Placement

```
/mmoblock give exampleBDEngine
# Place block in world
```

Expected:
- Item is given to inventory
- Block is visible when placed
- No packet errors in console

#### 4. Block Interaction

```
# Left click block
# Right click block
```

Expected:
- Hologram appears
- Click detection works
- Progress bar updates
- Break animation functions

#### 5. Block Mining Until Break

Click block until it breaks.

Expected:
- Block breaks
- Particle & sound effects work
- Drops appear
- Block respawns after `respawnTime`

#### 6. Per-Chunk Testing

```
# Load/unload chunk repeatedly
# Teleport to different chunks
```

Expected:
- Block persists in chunk
- Block reappears when chunk is reloaded
- No memory leak

### Per-Version Test Results

| Version | Basic Load | Block Place | Block Interact | Block Break | Chunk Persist | Notes |
|---------|-----------|-------------|----------------|-------------|---------------|-------|
| **1.19.4 Paper** | ✅ | ✅ | ✅ | ✅ | ✅ | Base stable |
| **1.19.4 Folia** | ✅ | ✅ | ✅ | ✅ | ✅ | Base stable |
| **1.20.4 Paper** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| **1.20.4 Folia** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| **1.21.1 Paper** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| **1.21.4 Paper** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| **1.21.11 Paper** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| **26.1 Folia** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |
| **26.2 Paper** | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | |

---

## Testing Platform

### Paper Testing Checklist

| Test | Step | Expected |
|------|------|----------|
| **Single-thread Scheduler** | Place block, interact, break | All tasks run on server thread |
| **Async Task** | Check database operations | Async tasks do not block main thread |
| **Event System** | Check BlockMineEvent, BlockPlaceEvent | Events fire correctly |
| **Packet Handling** | Check hologram packet | No packet leak / ghost entities |

### Folia Testing Checklist

| Test | Step | Expected |
|------|------|----------|
| **Region Scheduler** | Place block in world | Tasks run per region |
| **Global Scheduler** | Check global task (node respawn) | Tasks run on global thread |
| **Entity Scheduler** | Check interaction entity | Entity tasks run in their respective regions |
| **Thread Safety** | Load 10+ chunks | No `IllegalStateException` related to threading |
| **World Ownership** | Check cross-region world access | Does not access world from another region |

---

## Testing Integrations

### PlaceholderAPI

| Test | Step | Expected |
|------|------|----------|
| **Placeholder Hook** | Check startup log | `MMOBlock placeholder expansion registered` |
| **Basic Placeholder** | `%mmoblock_progress%` | Displays mining progress number |
| **Max Progress** | `%mmoblock_max_progress%` | Displays block max progress |
| **Respawn Time** | `%mmoblock_respawn_time%` | Displays remaining respawn time |
| **Condition** | Use `{condition_1}` in hologram | Displays require/notMet text based on condition |

### MMOItems

| Test | Step | Expected |
|------|------|----------|
| **Availability Check** | Check startup log | `MMOItems found - integration enabled` |
| **Get Item** | Use MMOItems ID in allowedTools | Tool detected correctly |
| **Match Item** | Hold MMOItems item, click block | Block interaction works |
| **Custom Durability** | Set MMOItems item with durability | Durability decreases on use |
| **Drop Item** | Configure drop with MMOItems ID | Drop is an MMOItems item |

### MMOCore

| Test | Step | Expected |
|------|------|----------|
| **Availability Check** | Check startup log | `MMOCore found - integration enabled` |
| **Level Condition** | Condition `%mmocore_level% >= 10` | Block can be mined based on level |
| **Profession Level** | Condition with profession ID | Working |
| **Give EXP** | Drop reward with MMOCore EXP | Player receives experience |
| **Profession EXP** | Drop reward profession EXP | Player receives profession experience |

### ItemsAdder

| Test | Step | Expected |
|------|------|----------|
| **Availability Check** | Check startup log | `ItemsAdder found - integration enabled` |
| **Get Item** | Use ItemsAdder ID (`ns:id`) | Item resolves correctly |
| **Match Item** | Hold ItemsAdder item | Match works |
| **Custom Durability** | ItemsAdder item with durability | Durability works |
| **Place Block** | Set block type to ItemsAdder custom block | Block is placed |
| **Custom Block Detection** | Check already placed block | Detected as custom block |

### CraftEngine

| Test | Step | Expected |
|------|------|----------|
| **Availability Check** | Check startup log | `CraftEngine found - integration enabled` |
| **Get Item** | Use CraftEngine ID (`ns:id`) | Item resolves |
| **Match Item** | Hold CraftEngine item | Match works |
| **Custom Durability** | CraftEngine item with durability | Durability works |
| **Place Block** | Set block to CraftEngine custom block | Block is placed |
| **Custom Block Detection** | Check already placed block | Detected |

### ModelEngine

| Test | Step | Expected |
|------|------|----------|
| **Availability Check** | Check startup log | `ModelEngine found - integration enabled` |
| **Model Spawn** | Config: `modelType.modelEngine.model: "iron_crystal:1.0"` | Model appears above entity |
| **Model Scale** | Test scale 0.5, 1.0, 2.0 | Scale matches |
| **Animation** | Config onClick animation | Animation plays on click |
| **Remove** | Break block | Model disappears |

### BetterModel

| Test | Step | Expected |
|------|------|----------|
| **Availability Check** | Check startup log | `BetterModel found - integration enabled` |
| **Model Spawn** | Config: `modelType.betterModel.model: "exampleEntity"` | Model appears |
| **Entity Tracker** | Place with Interaction entity | Model attaches to entity |
| **Dummy Tracker** | Model without entity | Model still appears at location |
| **Animation** | Play animation onClick | Animation plays |
| **Remove** | Break block / reload | Model disappears, tracker is closed |

---

## Testing Database

### H2 (Default)

| Test | Step | Expected |
|------|------|----------|
| **Plugin Start** | Start server with H2 enabled | Database file created at `.caches/data.mv.db` |
| **Place Block** | Place several blocks | Data saved |
| **Restart Server** | Restart server | All blocks persist |
| **Break Block** | Break block | Block data removed from database |
| **Respawn** | Wait for respawn | Block data updated |

### MySQL

| Test | Step | Expected |
|------|------|----------|
| **Connection** | Set MySQL credentials in config.yml | Connection successful (check log) |
| **CRUD** | Place, interact, break block | Database operations run smoothly |
| **Reconnect** | Turn off MySQL, turn on again | Auto-reconnect |
| **Pooling** | Check HikariCP pool | No pool leak |
| **Transaction** | Multi-thread stress test | No deadlock |

---

## Testing Core Features

### BDEngine (BlockDisplay Engine)

| Test | Step | Expected |
|------|------|----------|
| **Model Render** | Place block with BDEngine model | 3D model appears correctly |
| **Model Size** | Change size in config | Model size matches |
| **Collision** | Check collision box | Collision matches configuration |
| **Spawn Animation** | Set onSpawn animation | Animation plays on spawn |
| **Click Animation** | Click block | onClick animation plays |
| **Loop Animation** | Set mode: loop | Animation loops |
| **Dead State** | Break block | Model disappears / changes |

### Schematics

| Test | Step | Expected |
|------|------|----------|
| **Schematic Load** | Place block with schematic | Block appears per .schem file |
| **Facing** | Change placeFacing | Rotation matches (north/south/etc) |
| **Adjust Pos** | Change adjustPos | Block position shifts |
| **Dead Schematic** | Configure dead schematic | Schematic changes on break |
| **Multiple Blocks** | Schematic with 100+ blocks | All blocks sent via packet |

### Holograms

| Test | Step | Expected |
|------|------|----------|
| **Text Display** | Configure text line | Text appears as hologram |
| **Item Display** | Configure item line | Item appears in hologram |
| **Animations** | Use `<anim:wave:...>`, `<anim:burn:...>` | Animation runs |
| **Typewriter** | `<anim:typewriter:...>` | Typewriter effect |
| **Condition Text** | `{condition_1}` in hologram | Text changes based on condition |
| **Click State** | Set click: hide | Line hides when clicked |
| **Dead State** | Set dead: text | Text changes when block is dead |
| **Per-Player Facing** | Enable displayFacing | Hologram faces player |
| **Multiple Lines** | 4+ lines | Semantic matches config |

### Nodes

| Test | Step | Expected |
|------|------|----------|
| **Node Spawn** | Place node | Blocks within node appear |
| **Max Blocks** | Set maxBlocks: 4 | No more than 4 active blocks |
| **Random Location** | Enable randomLocation | Blocks spawn at random positions |
| **Closest Check** | Enable closest | Blocks only spawn near solid blocks |
| **Block List** | `{block_lists}` in hologram | Block list updates |
| **Node Break** | Break one block in node | Block respawns independently |
| **Full Node** | All blocks dead | Node waits for respawn |

### Drops

| Test | Step | Expected |
|------|------|----------|
| **Vanilla Drop** | Set material: diamond | Diamond drops |
| **MMOItems Drop** | Set MMOItems ID | MMOItems item drops |
| **ItemsAdder Drop** | Set ItemsAdder ID | ItemsAdder item drops |
| **CraftEngine Drop** | Set CraftEngine ID | CraftEngine item drops |
| **Amount** | Set amount > 1 | Multiple items drop |
| **Experience** | Set exp drop | Experience orb drops |

### Conditions

| Test | Step | Expected |
|------|------|----------|
| **Placeholder Condition** | `%player_level% >= 10` | Block can only be mined at level 10+ |
| **String Condition** | `%player_name% == "Player1"` | Condition for specific player |
| **Operator !=** | `%player_gamemode% != "creative"` | Block cannot be mined in creative |
| **Title Message** | Set sendTitle & sendSubtitle | Title appears when condition is not met |
| **Condition Text** | `{condition_1}` in hologram | Require/notMet text appears |

---

## Performance Testing

### 1. Massive Block Test

```yaml
# Create batch block test, spawn 100+ blocks in small radius
# Using /mmoblock debug mass-spawn or script
```

| Scenario | Metric | Target |
|----------|--------|--------|
| 100 active blocks | TPS | >19.5 |
| 500 active blocks | TPS | >19.0 |
| 1000 active blocks | TPS | >18.0 |
| 100 blocks destroyed simultaneously | Packet queue | No overflow |

### 2. Chunk Stress Test

```
# Load 100+ chunks with blocks
# Teleport between chunks quickly
```

| Test | Expected |
|------|----------|
| Chunk load with blocks | <50ms per chunk |
| Chunk unload | No memory leak |
| Cross-chunk teleport | <100ms |

### 3. Database Stress Test

| Scenario | Metric | Target |
|----------|--------|--------|
| 100 block place | Write time | <5ms per block |
| 100 block break | Delete time | <5ms per block |
| 1000 block startup load | Read time | <1s total |

### 4. Memory Usage

| Scenario | Heap Limit |
|----------|-----------|
| Server idle | <200MB |
| 100 active blocks | <350MB |
| 500 active blocks | <500MB |

---

## Testing Checklist

### Per Minecraft Version

- [ ] Plugin loads without errors
- [ ] Debug command works
- [ ] Block placement (BDEngine)
- [ ] Block placement (Schematic)
- [ ] Block interaction (click)
- [ ] Block mining progress
- [ ] Block break
- [ ] Block respawn
- [ ] Particle effects
- [ ] Sound effects
- [ ] Holograms (text & item)
- [ ] Hologram animations
- [ ] Break animation
- [ ] Node system
- [ ] Drop system (vanilla)
- [ ] Conditions (placeholder)
- [ ] Database persist H2
- [ ] Chunk load/unload
- [ ] Plugin disable/cleanup
- [ ] Rejoin server

### Per Platform

#### Paper
- [ ] Single-thread scheduler
- [ ] Bukkit events
- [ ] Packet handling
- [ ] Plugin compatibility
- [ ] bStats metrics sent (check https://bstats.org)
- [ ] Update checker (check console on startup)

#### Folia
- [ ] Region scheduler
- [ ] Global scheduler
- [ ] Entity scheduler
- [ ] Thread safety
- [ ] World ownership
- [ ] bStats metrics sent
- [ ] Update checker

### Per Integration

#### MMOItems
- [ ] Soft dependency load
- [ ] Item matching (allowedTools)
- [ ] Item drops
- [ ] Custom durability

#### MMOCore
- [ ] Soft dependency load
- [ ] Level conditions
- [ ] EXP rewards
- [ ] Profession EXP rewards
- [ ] Attribute conditions

#### ItemsAdder
- [ ] Soft dependency load
- [ ] Item matching
- [ ] Item drops
- [ ] Custom durability
- [ ] Custom block placement
- [ ] Custom block removal

#### CraftEngine
- [ ] Soft dependency load
- [ ] Item matching
- [ ] Item drops
- [ ] Custom durability
- [ ] Custom block placement
- [ ] Custom block removal

#### ModelEngine
- [ ] Soft dependency load
- [ ] Model spawn (entity-based)
- [ ] Model scale
- [ ] Animation (onClick, onDead)
- [ ] Model cleanup on break

#### BetterModel
- [ ] Soft dependency load
- [ ] Model spawn (entity-based)
- [ ] Model spawn (location-based)
- [ ] Model scale
- [ ] Animation
- [ ] Model cleanup on break

#### PlaceholderAPI
- [ ] Placeholder expansion registered
- [ ] `%mmoblock_progress%`
- [ ] `%mmoblock_max_progress%`
- [ ] `%mmoblock_respawn_time%`
- [ ] Conditional placeholders

### Per Feature

#### BDEngine
- [ ] Model render
- [ ] Model size/scale
- [ ] Collision box
- [ ] Spawn animation
- [ ] Click animation
- [ ] Loop animation
- [ ] Dead state change

#### Schematics
- [ ] Normal schematic load
- [ ] Dead schematic change
- [ ] Facing rotation
- [ ] Position adjustment
- [ ] Multi-block schematic

#### Holograms
- [ ] Text lines
- [ ] Item display
- [ ] Text animations
- [ ] Condition text
- [ ] Click state action
- [ ] Dead state action
- [ ] Per-player facing
- [ ] Multiple lines

#### Nodes
- [ ] Node spawn
- [ ] Max blocks limit
- [ ] Random location
- [ ] Closest solid block check
- [ ] Block list display
- [ ] Individual block respawn

#### Drops
- [ ] Vanilla item drops
- [ ] MMOItems drops
- [ ] ItemsAdder drops
- [ ] CraftEngine drops
- [ ] Experience drops
- [ ] Multiple amount

#### Conditions
- [ ] Placeholder-based condition
- [ ] Comparison operators (>, <, >=, <=, ==, !=)
- [ ] String comparison
- [ ] Title/subtitle message
- [ ] Condition display text

---

## Full Testing Procedure

### Testing Session 1: Basic Functionality (30 minutes)

1. Setup target version server
2. Copy MMOBlock.jar and default config
3. Start server, check log for errors
4. `/mmoblock debug` — verify plugin info
5. `/mmoblock give exampleBDEngine` — get item
6. Place block in world
7. Click block — check hologram & progress
8. Click until break — check drops & respawn
9. Record results in version table

### Testing Session 2: Integration (30 minutes per integration)

1. Install integration plugin (MMOItems, etc.)
2. Restart server
3. Check log: "X found - integration enabled"
4. Configure block with that integration
5. Test all related features
6. Uninstall integration plugin
7. Restart server
8. Verify: plugin still runs without errors (graceful fallback)

### Testing Session 3: Stress Test (1 hour)

1. Spawn 50+ blocks
2. Multiplayer testing (2-5 players)
3. All players mine blocks simultaneously
4. Load/unload chunks quickly
5. Restart server multiple times

### Testing Session 4: Database (15 minutes per database)

1. **H2 (default)**
   - Start server, place block, restart, check persistence
2. **MySQL** (if available)
   - Setup MySQL, configure, start, place, restart, check persistence
   - Check connection pool

---

## Important Notes

### 1. Java Version
- **Java 21**: For NMS versions 1.19.4 – 1.21.11
- **Java 25**: For NMS versions 26.1 (Folia) and 26.2 (Canvas)
- Ensure correct JDK when building and running server

### 2. Known Issues
- NMS adapters for newer versions may need method signature adjustments
- Paper/Folia API break changes across versions
- Folia vs Paper scheduler differ — do not swap JARs between platforms
- CraftEngine API depends on Minecraft server version

### 3. Debug Mode
Enable `debug: true` in config.yml during testing to see:
- Packet log
- NMS adapter calls
- Database queries
- Placeholder resolution

### 4. Build for Testing
```bash
# Build without ProGuard (recommended for testing)
./gradlew shadowJar

# Output:
# mmoblock-plugin/build/libs/MMOBlock-<version>.jar
```

### 5. Logs to Monitor
- **Startup**: `MMOBlock enabled!`
- **Error**: `java.lang.*Exception`, `NoClassDefFoundError`
- **Integration**: `X found - integration enabled`
- **NMS**: `NmsAdapter: Using adapter for vX_XX`

### 6. nms-common (Shared Backbone)

All NMS adapters depend on `nms-common`. A defect in `nms-common` will affect **all** versions.

Symptoms of nms-common defect:
- Errors appearing in **all** versions, not a specific one
- Errors in `AbstractFakeBlockPacketHandler`, `AbstractPacketBasedNmsAdapter`, or `NmsAdapterRegistry`
- Packet hologram or BDEngine issues across all versions

If errors occur in all versions → **check nms-common first**, then check specific adapters.

### 7. Spigot Runtime-only Adapters

The `nms-spigot-v1_19_4` and `nms-spigot-v1_20_4` modules use `runtimeOnly` + reobfuscation configuration in `build.gradle.kts`:

```kotlin
runtimeOnly(project(mapOf("path" to ":nms-spigot-v1_19_4", "configuration" to "reobf")))
```

This means:
- **Not compiled** into the main shadow JAR
- **Loaded at runtime** via Java `ServiceLoader` (file `META-INF/services/me.chyxelmc.mmoblock.nms.NmsAdapterProvider`)
- Uses obfuscated mapping (Spigot/obfuscated mapping), different from Mojang-mapped modules
- If running Spigot 1.19.4 or 1.20.4, ensure the Spigot adapter is active, not Mojang

### 8. ProGuard Obfuscation

The build has a ProGuard task (`obfuscatedJar`) that produces an obfuscated JAR:

```bash
# Build obfuscated JAR
./gradlew obfuscatedJar

# Output:
# mmoblock-plugin/build/libs/MMOBlock-<version>-obf.jar
```

**Testing obfuscated build:**
| Test | Step | Expected |
|------|------|----------|
| Basic load | Upload `-obf.jar` to server | Plugin loads without errors |
| Block place | Place block | Works normally |
| Stacktrace | Trigger intentional error (debug) | Stacktrace readable, class names obfuscated |

> Obfuscated JAR **not recommended** for debugging because stacktraces are hard to read.
> Use the regular shadow JAR (`MMOBlock-<version>.jar`) for testing sessions.

### 9. Notes for Testers

> **Base stable**: 1.19.4 Paper & Folia
>
> If you find an error on a specific version:
> 1. Check if the error is specific to that version's NMS adapter
> 2. Check if the error is in shared code (nms-common, plugin)
> 3. Record the full stacktrace
> 4. Report to developer with server version information

---

## Testing Notes Template

Use the following template to record test results:

```markdown
## Testing: [Minecraft Version] - [Platform]

**Date:** DD/MM/YYYY
**Tester:** [Name]
**Server:** [Paper/Folia] [server version]

### Results

| Feature | Status | Notes |
|---------|--------|-------|
| Plugin Load | ✅/⬜/❌ | |
| Block Place | ✅/⬜/❌ | |
| Block Interact | ✅/⬜/❌ | |
| Block Break | ✅/⬜/❌ | |
| Hologram | ✅/⬜/❌ | |
| Database | ✅/⬜/❌ | |
| Drops | ✅/⬜/❌ | |
| Conditions | ✅/⬜/❌ | |
| Integration | ✅/⬜/❌ | |

### Issues Found

1. **[Issue Title]** - Description, stacktrace, reproduction steps

### Additional Notes

...
```

---

> **Last Updated:** July 27, 2026
>
> **Base tested by:** Rosaaalfi (1.19.4 Paper & Folia ✅)
>
> _This document will be updated as testing progresses._
