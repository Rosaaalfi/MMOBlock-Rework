package me.chyxelmc.mmoblock.api.integration;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Integration layer for <a href="https://www.spigotmc.org/resources/mmocore.1030/">MMOCore</a>.
 * <p>
 * Uses direct MMOCore API references guarded by a static availability check.
 * If MMOCore is not installed the methods are no-ops and the class loads safely
 * because the JVM resolves class references lazily.
 * </p>
 * <p>
 * Provides utilities for reading player data (level, class, profession, resources,
 * attributes) and granting experience through MMOCore's system.
 * </p>
 */
public final class MMOCoreIntegration {

    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            available = true;
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            // MMOCore not installed or incompatible
        }
        AVAILABLE = available;
    }

    private MMOCoreIntegration() {
    }

    // -------------------------------------------------------------
    // Availability
    // -------------------------------------------------------------

    /**
     * @return {@code true} if MMOCore is installed and its API classes are resolvable
     */
    public static boolean isAvailable() {
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isMMOCoreAvailable();
        }
        return true;
    }

    // -------------------------------------------------------------
    // Player Data Lookup
    // -------------------------------------------------------------

    /**
     * Get the MMOCore PlayerData for a Bukkit player.
     *
     * @param player the Bukkit player
     * @return MMOCore PlayerData, or null if unavailable
     */
    @Nullable
    private static Object getPlayerData(final Player player) {
        if (!AVAILABLE || player == null) return null;
        try {
            return net.Indyuce.mmocore.api.player.PlayerData.get(player);
        } catch (final Exception ignored) {
            return null;
        }
    }

    // -------------------------------------------------------------
    // Level
    // -------------------------------------------------------------

    /**
     * Get the player's MMOCore class level.
     *
     * @param player the Bukkit player
     * @return the player's level, or 0 if MMOCore is unavailable
     */
    public static int getLevel(final Player player) {
        final Object data = getPlayerData(player);
        if (data == null) return 0;
        try {
            return (int) data.getClass().getMethod("getLevel").invoke(data);
        } catch (final Exception ignored) {
            return 0;
        }
    }

    // -------------------------------------------------------------
    // Class
    // -------------------------------------------------------------

    /**
     * Get the player's MMOCore class name.
     *
     * @param player the Bukkit player
     * @return the class name, or an empty string if unavailable
     */
    public static String getClassName(final Player player) {
        final Object data = getPlayerData(player);
        if (data == null) return "";
        try {
            final Object profess = data.getClass().getMethod("getProfess").invoke(data);
            if (profess == null) return "";
            final Object name = profess.getClass().getMethod("getName").invoke(profess);
            return name instanceof String str ? str : "";
        } catch (final Exception ignored) {
            return "";
        }
    }

    /**
     * Get the player's MMOCore class ID.
     *
     * @param player the Bukkit player
     * @return the class ID, or an empty string if unavailable
     */
    public static String getClassId(final Player player) {
        final Object data = getPlayerData(player);
        if (data == null) return "";
        try {
            final Object profess = data.getClass().getMethod("getProfess").invoke(data);
            if (profess == null) return "";
            final Object id = profess.getClass().getMethod("getId").invoke(profess);
            return id instanceof String str ? str : "";
        } catch (final Exception ignored) {
            return "";
        }
    }

    // -------------------------------------------------------------
    // Professions (Collection Skills)
    // -------------------------------------------------------------

    /**
     * Get the player's profession level by profession ID.
     *
     * @param player       the Bukkit player
     * @param professionId the profession ID (e.g. "mining", "woodcutting")
     * @return the profession level, or 0 if MMOCore is unavailable
     */
    public static int getProfessionLevel(final Player player, final String professionId) {
        final Object data = getPlayerData(player);
        if (data == null || professionId == null || professionId.isBlank()) return 0;
        try {
            final Object skills = data.getClass().getMethod("getCollectionSkills").invoke(data);
            if (skills == null) return 0;
            final Object level = skills.getClass().getMethod("getLevel", String.class).invoke(skills, professionId.toLowerCase());
            return level instanceof Integer i ? i : 0;
        } catch (final Exception ignored) {
            return 0;
        }
    }

    // -------------------------------------------------------------
    // Resources (Mana, Stamina, Stellium)
    // -------------------------------------------------------------

    /**
     * Get the player's current mana.
     *
     * @param player the Bukkit player
     * @return the mana value, or 0.0 if MMOCore is unavailable
     */
    public static double getMana(final Player player) {
        final Object data = getPlayerData(player);
        if (data == null) return 0.0D;
        try {
            final Object mana = data.getClass().getMethod("getMana").invoke(data);
            return mana instanceof Number n ? n.doubleValue() : 0.0D;
        } catch (final Exception ignored) {
            return 0.0D;
        }
    }

    /**
     * Get the player's current stamina.
     *
     * @param player the Bukkit player
     * @return the stamina value, or 0.0 if MMOCore is unavailable
     */
    public static double getStamina(final Player player) {
        final Object data = getPlayerData(player);
        if (data == null) return 0.0D;
        try {
            final Object stamina = data.getClass().getMethod("getStamina").invoke(data);
            return stamina instanceof Number n ? n.doubleValue() : 0.0D;
        } catch (final Exception ignored) {
            return 0.0D;
        }
    }

    /**
     * Get the player's current stellium.
     *
     * @param player the Bukkit player
     * @return the stellium value, or 0.0 if MMOCore is unavailable
     */
    public static double getStellium(final Player player) {
        final Object data = getPlayerData(player);
        if (data == null) return 0.0D;
        try {
            final Object stellium = data.getClass().getMethod("getStellium").invoke(data);
            return stellium instanceof Number n ? n.doubleValue() : 0.0D;
        } catch (final Exception ignored) {
            return 0.0D;
        }
    }

    // -------------------------------------------------------------
    // Attributes
    // -------------------------------------------------------------

    /**
     * Get the player's attribute level/points for a specific attribute ID.
     * <p>
     * The attribute ID is configured in MMOCore's config (e.g. "strength", "dexterity").
     * This method is fully flexible and will work with any ID the server has configured.
     *
     * @param player        the Bukkit player
     * @param attributeId   the attribute ID from MMOCore config (e.g. "strength")
     * @return the attribute level, or 0 if MMOCore is unavailable or attribute not found
     */
    public static int getAttribute(final Player player, final String attributeId) {
        final Object data = getPlayerData(player);
        if (data == null || attributeId == null || attributeId.isBlank()) return 0;
        try {
            final Object attributes = data.getClass().getMethod("getAttributes").invoke(data);
            if (attributes == null) return 0;
            final Object instance = attributes.getClass().getMethod("getInstance", String.class).invoke(attributes, attributeId.toLowerCase());
            if (instance == null) return 0;
            // AttributeInstance has a getLevel() or getAttribute() method. Let's try getAttribute.
            try {
                final Object level = instance.getClass().getMethod("getAttribute").invoke(instance);
                if (level instanceof Integer i) return i;
            } catch (final NoSuchMethodException ignored) {
            }
            // Also try getLevel()
            try {
                final Object level = instance.getClass().getMethod("getLevel").invoke(instance);
                if (level instanceof Integer i) return i;
            } catch (final NoSuchMethodException ignored) {
            }
            return 0;
        } catch (final Exception ignored) {
            return 0;
        }
    }

    // -------------------------------------------------------------
    // Experience
    // -------------------------------------------------------------

    /**
     * Give MMOCore class (main) experience to a player.
     *
     * @param player the Bukkit player
     * @param amount the amount of experience to give
     */
    public static void giveExperience(final Player player, final int amount) {
        final Object data = getPlayerData(player);
        if (data == null || amount <= 0) return;
        try {
            // EXPSource.SOURCE is used for plugin-granted experience
            final Class<?> expSourceClass = Class.forName("net.Indyuce.mmocore.experience.EXPSource");
            final Object source = expSourceClass.getField("SOURCE").get(null);
            data.getClass().getMethod("giveExperience", double.class, expSourceClass, Location.class, boolean.class)
                    .invoke(data, (double) amount, source, player.getLocation(), true);
        } catch (final Exception ignored) {
        }
    }

    /**
     * Give MMOCore profession (collection skill) experience to a player.
     * <p>
     * Profession IDs can be default MMOCore professions (mining, woodcutting,
     * alchemy, enchanting, farming, fishing, smelting, smithing) or
     * custom professions defined in MMOCore.
     * </p>
     * <p>
     * Based on MMOCore's {@code ExperienceCommandTreeNode.ActionCommandTreeNode} bytecode,
     * the correct API is:
     * <pre>
     * PlayerProfessions.giveExperience(Profession, double, EXPSource)
     * PlayerProfessions.giveExperience(Profession, double, EXPSource, Location, boolean)
     * </pre>
     * The Profession object is resolved from its ID via:
     * <pre>
     * MMOCore.plugin.professionManager.get(String id)
     * </pre>
     * </p>
     *
     * @param player       the Bukkit player
     * @param professionId the profession ID (e.g. "mining", "woodcutting", "carpenter")
     * @param amount       the amount of experience to give
     */
    public static void giveProfessionExperience(final Player player, final String professionId, final int amount) {
        final Object data = getPlayerData(player);
        if (data == null || professionId == null || professionId.isBlank() || amount <= 0) return;
        try {
            // Resolve the Profession object from the profession ID using MMOCore's ProfessionManager
            final Class<?> mmocoreClass = Class.forName("net.Indyuce.mmocore.MMOCore");
            final Object plugin = mmocoreClass.getField("plugin").get(null);
            final Object profManager = plugin.getClass().getField("professionManager").get(plugin);
            final Object profession = profManager.getClass().getMethod("get", String.class)
                    .invoke(profManager, professionId.toLowerCase());
            if (profession == null) return; // profession not found

            final Object skills = data.getClass().getMethod("getCollectionSkills").invoke(data);
            if (skills == null) return;

            final double doubleAmount = (double) amount;
            final Class<?> expSourceClass = Class.forName("net.Indyuce.mmocore.experience.EXPSource");
            final Object source = expSourceClass.getField("SOURCE").get(null);

            try {
                // giveExperience(Profession, double, EXPSource, Location, boolean)
                skills.getClass().getMethod("giveExperience",
                                profession.getClass(), double.class, expSourceClass, Location.class, boolean.class)
                        .invoke(skills, profession, doubleAmount, source, player.getLocation(), true);
                return;
            } catch (final NoSuchMethodException ignored) {
            }

            // Fallback: giveExperience(Profession, double, EXPSource)
            try {
                skills.getClass().getMethod("giveExperience",
                                profession.getClass(), double.class, expSourceClass)
                        .invoke(skills, profession, doubleAmount, source);
            } catch (final NoSuchMethodException ignored) {
            }
        } catch (final Exception ignored) {
        }
    }
}
