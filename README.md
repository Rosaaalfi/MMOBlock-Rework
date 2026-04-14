# 🪨 MMOBlock

**MMOBlock** is a high-performance plugin for [PaperMC](https://papermc.io/) that allows you to create fully customizable, interactive block entities. Players can mine these blocks with specific tools, earn rewards, and watch them respawn automatically—all managed through easy-to-use config files.

> [!TIP]
> Perfect for RPG, Survival, or Skyblock servers looking to add "Custom Ore" systems, renewable resource nodes, or interactive world events.

---

## ✨ Features

* 🧱 **Custom Block Entities:** Place interactive blocks anywhere in your world that aren't restricted by standard Minecraft physics.
* ⛏️ **Mining System:** Set "Click Durability" (e.g., hit a block 10 times to break it) and apply durability costs to tools.
* 🎁 **Smart Drops:** Reward players with items, experience, or console commands with customizable drop chances.
* 🔁 **Auto Respawn:** Blocks automatically reappear after a cooldown, making resource zones renewable.
* 💬 **Hologram Displays:** Show live progress bars, block names, and respawn countdowns floating above the block.
* 🎨 **3D Model Support:** Compatible with **Blockbench (.bbmodel)**, **ModelEngine**, and **ItemsAdder**.
* 💾 **Reliable Storage:** Saves all block locations and states via H2 (default), MySQL, or Redis.

---

## 🔧 Requirements

| Requirement | Minimum Version |
|---|---|
| **Server Software** | [PaperMC](https://papermc.io/) or forks (Purpur, etc.) |
| **Java Version** | Java 21 or higher |
| **Minecraft** | 1.19.4 through 1.21.x |

---

## 📦 Installation

1. **Download** the latest `MMOBlock.jar` from the [Releases](../../releases) page.
2. **Drop** the file into your server's `plugins/` folder.
3. **Restart** your server to generate the default configuration files.
4. (Optional) Install **DecentHolograms** for better performance on text displays.

---

## 🎮 Commands & Permissions

All commands require the `mmoblock.admin` permission (Default: OP).

| Command | Description |
|---|---|
| `/mmoblock place <id>` | Spawns a custom block at your location based on an ID. |
| `/mmoblock remove` | Removes the custom block you are currently looking at. |
| `/mmoblock reload` | Refreshes all config files (Blocks, Drops, Tools) instantly. |

---

## 📁 Understanding the Folders

The plugin is organized into three simple parts within the `plugins/MMOBlock/` directory:

### 1. `blocks/` (The "What")
Define the block's appearance, its name, how long it takes to respawn, and the sounds it makes when hit.

### 2. `tools/` (The "How")
Define which tools are allowed to mine the block. For example, you can make a "Crystal" only breakable using a **Netherite Pickaxe**.

### 3. `drops/` (The "Reward")
Define what the player gets. You can set a 50% chance for an Iron Ingot, a 5% chance for a rare crate key (via command), and 100% chance for XP.

---

## 🛠️ Creating Your First Block

1.  Open the `blocks/example.yml` file to see how a block is structured.
2.  Stand where you want the block to appear in-game.
3.  Type `/mmoblock place exampleEntity`.
4.  Test it! Use the tool defined in the `tools/` folder to see the progress bar and rewards in action.

---

## 📜 License & Support

- **Website:** [chyxelmc.me](https://chyxelmc.me)
- **Issues:** Please report bugs via [GitHub Issues](../../issues).

*Developed with ❤️ by Aniko for the Minecraft community.*
