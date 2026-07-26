package me.chyxelmc.mmoblock.nms.utils;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for all NMS reflection lookups used by the entity glow path
 * ({@code NmsAdapter.applyEntityGlow()} and its helper methods).
 * <p>
 * NMS classes, fields, methods, and constructors are loaded once on first access
 * via {@link #ensureInitialized()}. Per-class lookups (e.g. {@code Player.getHandle()},
 * {@code connection.send()}) are cached in {@link ConcurrentHashMap} keyed by the
 * concrete class.
 */
public final class GlowReflectionCache {

    // ── Initialisation guard ──────────────────────────────────────────────
    private static volatile boolean initialized = false;

    // ── NMS class references ──────────────────────────────────────────────
    private static Class<?> NMS_ENTITY;
    private static Class<?> ENTITY_DATA_ACCESSOR;
    private static Class<?> SYNCED_ENTITY_DATA;
    private static Class<?> DATA_VALUE;
    private static Class<?> METADATA_PACKET;
    private static Class<?> SCOREBOARD;
    private static Class<?> PLAYER_TEAM;
    private static Class<?> CHAT_FORMATTING;
    private static Class<?> TEAM_PACKET;
    private static Class<?> COLLISION_RULE;
    private static Class<?> TEAM_ACTION;

    // ── Cached reflectors (NMS-global, loaded once) ───────────────────────
    private static Field SHARED_FLAGS_FIELD;
    private static Method GET_ENTITY_DATA;
    private static Method SYNCED_ENTITY_DATA_GET;
    private static Method DATA_VALUE_CREATE;
    private static Constructor<?> METADATA_CONSTRUCTOR;
    private static Constructor<?> SCOREBOARD_CONSTRUCTOR;
    private static Constructor<?> PLAYER_TEAM_CONSTRUCTOR;
    private static Method TEAM_SET_COLOR;
    private static Method TEAM_SET_COLLISION;
    private static Method CREATE_TEAM_PACKET;
    private static Method CREATE_PLAYER_PACKET;
    private static Method CHAT_FORMATTING_GET_BY_CODE;

    // ── Per-class caches ──────────────────────────────────────────────────
    private static final ConcurrentHashMap<Class<?>, Method> GET_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Field> CONNECTION_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Method> SEND_METHOD_CACHE = new ConcurrentHashMap<>();

    private GlowReflectionCache() {
    }

    // ── Initialisation ────────────────────────────────────────────────────

    /**
     * Ensures all NMS reflectors are loaded. Safe to call from any thread.
     *
     * @throws ReflectiveOperationException if any NMS class/field/method cannot be found
     */
    private static void ensureInitialized() throws ReflectiveOperationException {
        if (initialized) {
            return;
        }
        synchronized (GlowReflectionCache.class) {
            if (initialized) {
                return;
            }
            loadReflectors();
            initialized = true;
        }
    }

    @SuppressWarnings("deprecation")
    private static void loadReflectors() throws ReflectiveOperationException {
        // ── Metadata packet classes ──
        NMS_ENTITY = Class.forName("net.minecraft.world.entity.Entity");
        ENTITY_DATA_ACCESSOR = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
        SYNCED_ENTITY_DATA = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
        DATA_VALUE = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
        METADATA_PACKET = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");

        SHARED_FLAGS_FIELD = NMS_ENTITY.getDeclaredField("DATA_SHARED_FLAGS_ID");
        ReflectionUtil.safeSetAccessible(SHARED_FLAGS_FIELD, "NMS DATA_SHARED_FLAGS_ID field");

        GET_ENTITY_DATA = NMS_ENTITY.getMethod("getEntityData");
        SYNCED_ENTITY_DATA_GET = SYNCED_ENTITY_DATA.getMethod("get", ENTITY_DATA_ACCESSOR);
        DATA_VALUE_CREATE = DATA_VALUE.getMethod("create", ENTITY_DATA_ACCESSOR, Object.class);

        METADATA_CONSTRUCTOR = METADATA_PACKET.getDeclaredConstructor(int.class, List.class);
        ReflectionUtil.safeSetAccessible(METADATA_CONSTRUCTOR, "NMS ClientboundSetEntityDataPacket constructor");

        // ── Team packet classes ──
        SCOREBOARD = Class.forName("net.minecraft.world.scores.Scoreboard");
        PLAYER_TEAM = Class.forName("net.minecraft.world.scores.PlayerTeam");
        CHAT_FORMATTING = Class.forName("net.minecraft.ChatFormatting");
        TEAM_PACKET = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
        COLLISION_RULE = Class.forName("net.minecraft.world.scores.Team$CollisionRule");
        TEAM_ACTION = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Action");

        SCOREBOARD_CONSTRUCTOR = SCOREBOARD.getConstructor();
        PLAYER_TEAM_CONSTRUCTOR = PLAYER_TEAM.getDeclaredConstructor(SCOREBOARD, String.class);
        ReflectionUtil.safeSetAccessible(PLAYER_TEAM_CONSTRUCTOR, "NMS PlayerTeam constructor");

        TEAM_SET_COLOR = PLAYER_TEAM.getMethod("setColor", CHAT_FORMATTING);
        TEAM_SET_COLLISION = PLAYER_TEAM.getMethod("setCollisionRule", COLLISION_RULE);
        CREATE_TEAM_PACKET = TEAM_PACKET.getMethod("createAddOrModifyPacket", PLAYER_TEAM, boolean.class);
        CREATE_PLAYER_PACKET = TEAM_PACKET.getMethod("createPlayerPacket", PLAYER_TEAM, String.class, TEAM_ACTION);
        CHAT_FORMATTING_GET_BY_CODE = CHAT_FORMATTING.getMethod("getByCode", char.class);
    }

    // ── Public helpers ────────────────────────────────────────────────────

    /**
     * Returns the NMS Entity handle for a Bukkit entity.
     * Results are cached per entity class.
     */
    /**
     * Returns the NMS handle for any Bukkit object (Player or Entity).
     * Results are cached per class using computeIfAbsent.
     */
    public static Object getHandle(final Object bukkitObject) throws ReflectiveOperationException {
        final Class<?> clazz = bukkitObject.getClass();
        final Method method = GET_HANDLE_CACHE.computeIfAbsent(clazz, k -> {
            try {
                return k.getMethod("getHandle");
            } catch (final NoSuchMethodException e) {
                throw new RuntimeException("No getHandle method for " + k, e);
            }
        });
        return method.invoke(bukkitObject);
    }

    /**
     * Returns the NMS connection (PacketListener) from an NMS player handle.
     * Results are cached per handle class.
     */
    public static Object getConnection(final Object nmsHandle) throws ReflectiveOperationException {
        final Class<?> clazz = nmsHandle.getClass();
        final Field field = CONNECTION_FIELD_CACHE.computeIfAbsent(clazz, k -> {
            try {
                return k.getField("connection");
            } catch (final NoSuchFieldException e) {
                throw new RuntimeException("No connection field for " + k, e);
            }
        });
        return field.get(nmsHandle);
    }

    /**
     * Sends an NMS packet to a player connection.
     * The {@code send} method is cached per connection class.
     */
    public static void sendPacket(final Object connection, final Object packet) throws ReflectiveOperationException {
        if (connection == null || packet == null) {
            return;
        }
        final Class<?> connClass = connection.getClass();
        final Method sendMethod = SEND_METHOD_CACHE.computeIfAbsent(connClass, k -> {
            for (final Method m : k.getMethods()) {
                if ("send".equals(m.getName()) && m.getParameterCount() == 1) {
                    return m;
                }
            }
            return null;
        });
        if (sendMethod != null && sendMethod.getParameterTypes()[0].isInstance(packet)) {
            sendMethod.invoke(connection, packet);
        }
    }

    /**
     * Loads the NMS classes (idempotent). Call this early during plugin init
     * so that any linkage errors surface at startup rather than at first glow.
     *
     * @throws ReflectiveOperationException if any NMS class/field/method cannot be found
     */
    public static void warmup() throws ReflectiveOperationException {
        ensureInitialized();
    }

    // ── Metadata packet construction ──────────────────────────────────────

    /**
     * Constructs and sends a {@code ClientboundSetEntityDataPacket} that sets
     * the glowing flag (bit 6 of the shared flags) for the given entity.
     */
    public static void sendGlowMetadataPacket(final Object connection, final Entity entity)
            throws ReflectiveOperationException {
        ensureInitialized();
        final Object nmsEntity = getHandle(entity);
        final Object sharedFlags = SHARED_FLAGS_FIELD.get(null);
        final Object entityData = GET_ENTITY_DATA.invoke(nmsEntity);
        final byte currentFlags = (byte) SYNCED_ENTITY_DATA_GET.invoke(entityData, sharedFlags);
        final byte glowingFlags = (byte) ((currentFlags & 0xff) | (1 << 6));
        final Object dataValue = DATA_VALUE_CREATE.invoke(null, sharedFlags, glowingFlags);
        final Object metadataPacket = METADATA_CONSTRUCTOR.newInstance(entity.getEntityId(), List.of(dataValue));
        sendPacket(connection, metadataPacket);
    }

    // ── Team packet construction ──────────────────────────────────────────

    /**
     * Constructs and sends two packets: a team create/modify packet with the
     * given color and collision rule set to NEVER, and an add-entity packet
     * that adds the entity's UUID to the team.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void sendGlowTeamPackets(final Object connection, final Entity entity,
                                           final String teamName, final ChatColor chatColor)
            throws ReflectiveOperationException {
        ensureInitialized();
        final Object scoreboard = SCOREBOARD_CONSTRUCTOR.newInstance();
        final Object playerTeam = PLAYER_TEAM_CONSTRUCTOR.newInstance(scoreboard, teamName);
        final Object formatting = CHAT_FORMATTING_GET_BY_CODE.invoke(null, chatColor.getChar());
        TEAM_SET_COLOR.invoke(playerTeam, formatting);
        TEAM_SET_COLLISION.invoke(playerTeam,
                Enum.valueOf((Class<Enum>) COLLISION_RULE.asSubclass(Enum.class), "NEVER"));
        final String entry = entity.getUniqueId().toString();

        final Object createTeamPacket = CREATE_TEAM_PACKET.invoke(null, playerTeam, true);
        sendPacket(connection, createTeamPacket);

        final Enum<?> addAction = Enum.valueOf(TEAM_ACTION.asSubclass(Enum.class), "ADD");
        final Object addEntityPacket = CREATE_PLAYER_PACKET.invoke(null, playerTeam, entry, addAction);
        sendPacket(connection, addEntityPacket);
    }
}
