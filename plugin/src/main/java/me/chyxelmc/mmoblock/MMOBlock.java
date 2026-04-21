package me.chyxelmc.mmoblock;

import me.chyxelmc.mmoblock.command.MMOBlockCommand;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.listener.ChunkLifecycleListener;
import me.chyxelmc.mmoblock.listener.FakeBlockSyncListener;
import me.chyxelmc.mmoblock.listener.HologramCleanupListener;
import me.chyxelmc.mmoblock.listener.InteractionListener;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapterRegistry;
import me.chyxelmc.mmoblock.nmsloader.ecs.EcsIntegrationExample;
import me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager;
import me.chyxelmc.mmoblock.nmsloader.ecs.SystemManager;
import org.bukkit.scheduler.BukkitTask;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.DatabaseManager;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.utils.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

public final class MMOBlock extends JavaPlugin{

    private NmsAdapter nmsAdapter;
    private EntityManager entityManager;
    private SystemManager systemManager;
    private BukkitTask ecsTask;
    private BlockConfigService blockConfigService;
    private DatabaseManager databaseManager;
    private BlockRepository blockRepository;
    private RespawnRepository respawnRepository;
    private BlockRuntimeService blockRuntimeService;
    private RuntimeCoordinator runtimeCoordinator;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        this.nmsAdapter = NmsAdapterRegistry.resolveCurrent(getLogger());
        this.nmsAdapter.validateNms();

        this.blockConfigService = new BlockConfigService(this);
        this.blockConfigService.reloadAll();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();
        this.blockRepository = new BlockRepository(this.databaseManager);
        this.respawnRepository = new RespawnRepository(this.databaseManager);
        final PersistenceReadSystem persistenceReadSystem = new PersistenceReadSystem(this.blockRepository, this.respawnRepository);
        final PersistenceSystem persistenceSystem = new PersistenceSystem(this, this.blockRepository, this.respawnRepository);
        this.blockRuntimeService = new BlockRuntimeService(
            this,
            this.nmsAdapter,
            this.blockConfigService,
            persistenceReadSystem,
            persistenceSystem
        );
        // Register FakeBlockPacketHandler checker if available (best-effort via reflection).
        try {
            final String handlerPkg = this.nmsAdapter.getClass().getPackage().getName();
            final Class<?> handlerClass = Class.forName(handlerPkg + ".FakeBlockPacketHandler");
            final Class<?> checkerIface = Class.forName(handlerPkg + ".FakeBlockPacketHandler$FakeBlockChecker");
            final Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    checkerIface.getClassLoader(),
                    new Class[]{checkerIface},
                    (proxyObj, method, args) -> {
                        try {
                            final org.bukkit.entity.Player p = (org.bukkit.entity.Player) args[0];
                            final Object blockPos = args[1];
                            if (p == null || blockPos == null) return false;
                            final int x = (int) blockPos.getClass().getMethod("getX").invoke(blockPos);
                            final int y = (int) blockPos.getClass().getMethod("getY").invoke(blockPos);
                            final int z = (int) blockPos.getClass().getMethod("getZ").invoke(blockPos);
                            return me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.contains(p.getWorld().getName(), x, y, z);
                        } catch (final Throwable t) {
                            return false;
                        }
                    }
            );
            final java.lang.reflect.Method setChecker = handlerClass.getMethod("setFakeChecker", checkerIface);
            setChecker.invoke(null, proxy);
        } catch (final Throwable ignored) {
        }
        this.runtimeCoordinator = new RuntimeCoordinator(persistenceReadSystem, this.blockRuntimeService);

        if (getConfig().getBoolean("bStats", true)) {
            new Metrics(this, 30727);
        }

        final MMOBlockCommand commandExecutor = new MMOBlockCommand(this, this.blockConfigService, this.blockRuntimeService, this.runtimeCoordinator);
        if (!tryRegisterPaperCommand(commandExecutor)) {
            final PluginCommand mmoblockCommand = resolveOrRegisterMmoBlockCommand();
            if (mmoblockCommand != null) {
                mmoblockCommand.setExecutor(commandExecutor);
                mmoblockCommand.setTabCompleter(commandExecutor);
                mmoblockCommand.setPermission("mmoblock.admin");
            } else {
                getLogger().warning("mmoblock command still not found by Bukkit!");
            }
        } else {
            getLogger().info("Registered /mmoblock using Paper command registration.");
        }

        getServer().getPluginManager().registerEvents(new InteractionListener(this.blockRuntimeService), this);
        getServer().getPluginManager().registerEvents(new FakeBlockSyncListener(this, this.blockRuntimeService), this);
        getServer().getPluginManager().registerEvents(new ChunkLifecycleListener(this.blockRuntimeService), this);
        getServer().getPluginManager().registerEvents(new HologramCleanupListener(this, this.blockRuntimeService), this);

        this.runtimeCoordinator.restoreFromPersistence();

        // Setup ECS integration: create managers/systems and schedule ticking every server tick
        try {
            this.entityManager = EcsIntegrationExample.createEntityManager();
            // Provide a callback so that when the ECS InteractionSpawnSystem successfully
            // spawns an NMS interaction, the plugin updates the corresponding PlacedBlock
            // with the spawned interaction UUID.
            this.systemManager = EcsIntegrationExample.createForAdapter(
                    this.nmsAdapter,
                    this.entityManager,
                    (blockId, nmsEntityId) -> {
                        try {
                            if (this.blockRuntimeService != null) {
                                this.blockRuntimeService.onInteractionSpawned(blockId, nmsEntityId);
                            }
                        } catch (final Throwable ignored) {
                        }
                    }
            );
            // Provide the entity manager to BlockRuntimeService so it can create ECS entities
            try {
                this.blockRuntimeService.setEntityManager(this.entityManager);
            } catch (final Throwable ignored) {
            }
            // schedule tick at 1 tick interval
            this.ecsTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    this.systemManager.tick(this.entityManager, System.currentTimeMillis());
                } catch (final Throwable t) {
                    getLogger().warning("ECS tick failed: " + t.getMessage());
                }
            }, 1L, 1L);
        } catch (final RuntimeException ex) {
            getLogger().warning("Failed to initialize ECS systems: " + ex.getMessage());
            this.entityManager = null;
            this.systemManager = null;
        }

        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.nmsAdapter.sendSystemMessage(player, "MMOBlock active on NMS " + this.nmsAdapter.targetMinecraftVersion());
            // Ensure the per-player Netty handler is injected for players already online when the
            // plugin enables. FakeBlockSyncListener injects on join, but this covers the case
            // where the plugin was (re)loaded while players were online.
            try {
                final String clsName = fakePacketHandlerClassName();
                if (clsName != null) {
                    final Class<?> cls = Class.forName(clsName);
                    final java.lang.reflect.Method inject = cls.getMethod("inject", org.bukkit.entity.Player.class);
                    inject.invoke(null, player);
                }
            } catch (final Throwable ignored) {
            }
        }
    }

    /**
     * Returns the fully-qualified FakeBlockPacketHandler class name for the active NMS adapter,
     * or null if no adapter is available.
     */
    public String fakePacketHandlerClassName() {
        if (this.nmsAdapter == null) return null;
        return this.nmsAdapter.getClass().getPackage().getName() + ".FakeBlockPacketHandler";
    }


    @Override
    public void onDisable() {
        if (this.blockRuntimeService != null) {
            this.runtimeCoordinator.shutdown();
            this.blockRuntimeService = null;
        }
        this.runtimeCoordinator = null;
        if (this.databaseManager != null) {
            this.databaseManager.close();
            this.databaseManager = null;
        }
        this.blockRepository = null;
        this.respawnRepository = null;
        this.blockConfigService = null;
        // Cleanup ECS-managed NMS entities and holograms
        try {
            if (this.ecsTask != null) {
                this.ecsTask.cancel();
                this.ecsTask = null;
            }
            if (this.entityManager != null && this.nmsAdapter != null) {
                EcsIntegrationExample.cleanupAll(this.entityManager, this.nmsAdapter);
            }
        } catch (final Throwable ignored) {
        }

        this.entityManager = null;
        this.systemManager = null;
        this.nmsAdapter = null;
        // Best-effort cleanup: uninject per-player handlers to avoid lingering pipeline state.
        try {
            final String clsName = fakePacketHandlerClassName();
            if (clsName != null) {
                final Class<?> cls = Class.forName(clsName);
                final java.lang.reflect.Method uninject = cls.getMethod("uninject", org.bukkit.entity.Player.class);
                for (final Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        uninject.invoke(null, p);
                    } catch (final Throwable ignored) {
                    }
                }
            }
        } catch (final Throwable ignored) {
        }
    }

    private boolean tryRegisterPaperCommand(final MMOBlockCommand commandExecutor) {
        try {
            final Class<?> basicCommandClass = Class.forName("io.papermc.paper.command.brigadier.BasicCommand");
            final Class<?> sourceStackClass = Class.forName("io.papermc.paper.command.brigadier.CommandSourceStack");

            final Object basicCommand = java.lang.reflect.Proxy.newProxyInstance(
                basicCommandClass.getClassLoader(),
                new Class[]{basicCommandClass},
                (proxy, method, args) -> {
                    final String name = method.getName();
                    if ("execute".equals(name) && args != null && args.length == 2) {
                        final org.bukkit.command.CommandSender sender = (org.bukkit.command.CommandSender) sourceStackClass.getMethod("getSender").invoke(args[0]);
                        commandExecutor.onCommand(sender, null, "mmoblock", (String[]) args[1]);
                        return null;
                    }
                    if ("suggest".equals(name) && args != null && args.length == 2) {
                        final org.bukkit.command.CommandSender sender = (org.bukkit.command.CommandSender) sourceStackClass.getMethod("getSender").invoke(args[0]);
                        return commandExecutor.onTabComplete(sender, null, "mmoblock", (String[]) args[1]);
                    }
                    if ("permission".equals(name)) {
                        return "mmoblock.admin";
                    }
                    if ("toString".equals(name)) {
                        return "MMOBlockBasicCommandProxy";
                    }
                    if (!method.getReturnType().isPrimitive()) {
                        return null;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == char.class) {
                        return '\0';
                    }
                    if (method.getReturnType() == byte.class) {
                        return (byte) 0;
                    }
                    if (method.getReturnType() == short.class) {
                        return (short) 0;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    if (method.getReturnType() == float.class) {
                        return 0.0F;
                    }
                    if (method.getReturnType() == double.class) {
                        return 0.0D;
                    }
                    return null;
                }
            );

            final java.lang.reflect.Method registerCommand = org.bukkit.plugin.java.JavaPlugin.class.getMethod(
                "registerCommand",
                String.class,
                String.class,
                java.util.List.class,
                basicCommandClass
            );
            registerCommand.invoke(this, "mmoblock", "Manage MMOBlock interaction entities", java.util.List.of(), basicCommand);
            return true;
        } catch (final ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }
    private PluginCommand resolveOrRegisterMmoBlockCommand() {
        PluginCommand command = null;
        try {
            command = getCommand("mmoblock");
        } catch (final UnsupportedOperationException ignored) {
            // Paper plugins may not support YAML command declarations.
        }
        if (command == null) {
            command = Bukkit.getPluginCommand("mmoblock");
        }
        if (command != null) {
            if (command.getPlugin() != this) {
                getLogger().severe("Cannot bind /mmoblock: command already owned by plugin " + command.getPlugin().getName());
                return null;
            }
            return command;
        }
        return registerDynamicMmoBlockCommand();
    }

    private PluginCommand registerDynamicMmoBlockCommand() {
        try {
            final Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            final PluginCommand dynamic = constructor.newInstance("mmoblock", this);
            dynamic.setDescription("Manage MMOBlock interaction entities");
            dynamic.setUsage("/mmoblock");
            dynamic.setPermission("mmoblock.admin");

            final Method getCommandMap = getServer().getClass().getMethod("getCommandMap");
            final Object commandMap = getCommandMap.invoke(getServer());
            if (commandMap == null) {
                getLogger().severe("Cannot register /mmoblock dynamically: command map is null");
                return null;
            }

            final Method register = commandMap.getClass().getMethod("register", String.class, org.bukkit.command.Command.class);
            final String fallbackPrefix = getDescription().getName().toLowerCase(Locale.ROOT);
            final boolean registered = (boolean) register.invoke(commandMap, fallbackPrefix, dynamic);
            if (!registered) {
                getLogger().warning("Dynamic /mmoblock registration returned false; continuing with command map lookup");
            }

            final PluginCommand resolved = Bukkit.getPluginCommand("mmoblock");
            if (resolved == null) {
                getLogger().severe("Dynamic /mmoblock registration completed but command is still unresolved");
            }
            return resolved;
        } catch (final ReflectiveOperationException exception) {
            getLogger().severe("Failed to dynamically register /mmoblock: " + exception.getMessage());
            return null;
        }
    }
}
