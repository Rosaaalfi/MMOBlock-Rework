package me.chyxelmc.mmoblock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import me.chyxelmc.mmoblock.api.ApiProvider;
import me.chyxelmc.mmoblock.api.MMOBlockApiImpl;
import me.chyxelmc.mmoblock.api.integration.placeholder.HologramPlaceholderContextStore;
import me.chyxelmc.mmoblock.api.integration.placeholder.MMOBlockPlaceholderExpansion;
import me.chyxelmc.mmoblock.command.MMOBlockCommand;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.ecs.EntityManager;
import me.chyxelmc.mmoblock.ecs.SystemManager;
import me.chyxelmc.mmoblock.ecs.system.InteractionSpawnSystem;
import me.chyxelmc.mmoblock.ecs.system.PacketHologramSyncSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.listener.ChunkLifecycleListener;
import me.chyxelmc.mmoblock.listener.HologramCleanupListener;
import me.chyxelmc.mmoblock.listener.InteractionListener;
import me.chyxelmc.mmoblock.listener.PlatformSyncListener;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.nms.NmsAdapterRegistry;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.persistence.database.DatabaseManager;
import me.chyxelmc.mmoblock.platform.PlatformSchedulerProvider;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.BlockServiceFactory;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.utils.DatabaseUtils;
import me.chyxelmc.mmoblock.utils.DependencyChecker;
import me.chyxelmc.mmoblock.utils.InternalPlaceholderResolver;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import me.chyxelmc.mmoblock.utils.TargetedStore;
import me.chyxelmc.mmoblock.utils.UpdateChecker;
import me.chyxelmc.mmoblock.utils.analytics.Metrics;

public final class MMOBlock extends JavaPlugin{

    private NmsAdapter nmsAdapter;
    private EntityManager entityManager;
    private SystemManager systemManager;
    private Scheduler scheduler;
    private SchedulerTask ecsTask;
    private BlockConfigLoader blockConfigService;
    private me.chyxelmc.mmoblock.config.NodeConfigLoader nodeConfigService;
    private DatabaseUtils databaseUtils;
    private DatabaseManager databaseManager;
    private DataCache dataCache;
    private BlockRepository blockRepository;
    private RespawnRepository respawnRepository;
    private me.chyxelmc.mmoblock.persistence.NodeRepository nodeRepository;
    private BlockRuntimeService blockRuntimeService;
    private me.chyxelmc.mmoblock.runtime.NodeRuntimeService nodeRuntimeService;
    private RuntimeCoordinator runtimeCoordinator;
    private me.chyxelmc.mmoblock.i18n.TranslationService translationService;
    private HologramPlaceholderContextStore placeholderContextStore;
    private MMOBlockPlaceholderExpansion placeholderExpansion;
    private Method placeholderApiSetMethod;
    private MMOBlockApiImpl apiImpl;
    private PersistenceReadSystem persistenceReadSystem;
    private PersistenceSystem persistenceSystem;
    private volatile boolean ready;
    private static final String PERMISSION = "mmoblock.admin";
    private static final String CMD_NAME = "mmoblock";

    public boolean isReady() {
        return this.ready;
    }

    // ============================================================
    // Bootstrap phases
    // ============================================================

    @Override
    public void onEnable() {
        initLogger();
        if (!initTargetedStore()) {
            return; // Plugin was disabled by TargetedStore (invalid license / leaker)
        }
        checkDependencies();
        initCoreServices();
        initI18n();
        loadConfigs();
        initDatabase();
        initRuntime();
        initPlaceholders();
        setupFakeBlockChecker();
        finalizeBootstrap();
        this.ready = true;
    }

    private boolean initTargetedStore() {
        if (!getConfig().getBoolean("targeted-store", true)) {
            return true; // TargetedStore disabled in config
        }
        try {
            final var result = me.chyxelmc.mmoblock.utils.TargetedStore.verify(this);
            switch (result) {
                case VALID -> {
                    if (me.chyxelmc.mmoblock.utils.TargetedStore.IS_POLYMART_BUILD) {
                        MMOBlockLogger.info("targeted_store.purchase_verified",
                                "[Chyxel] Purchase verified! Thank you " + TargetedStore.getBuyerName());
                    }
                    return true;
                }
                case LEAKER -> {
                    TargetedStore.handleResult(this, result);
                    Bukkit.getPluginManager().disablePlugin(this);
                    return false;
                }
                case INVALID -> {
                    Bukkit.getPluginManager().disablePlugin(this);
                    return false;
                }
            }
        } catch (final NoClassDefFoundError e) {
            // TargetedStore was excluded from build (-PnoTargetedStore), skip verification
            return true;
        }
        return true;
    }

    private void initLogger() {
        saveDefaultConfig();
        MMOBlockLogger.init(this);
    }

    private void checkDependencies() {
        DependencyChecker.check(this);
        if (getConfig().getBoolean("updateChecker", true)) {
            UpdateChecker.checkAsync(getDescription().getVersion());
        }
    }

    private void initCoreServices() {
        this.scheduler = PlatformSchedulerProvider.createScheduler(this);
        this.nmsAdapter = NmsAdapterRegistry.resolveCurrent();
        this.nmsAdapter.validateNms();
    }

    private void loadConfigs() {
        this.blockConfigService = new BlockConfigLoader(this);
        this.blockConfigService.setTranslationService(this.translationService);
        this.blockConfigService.reloadAll();
        this.nodeConfigService = new me.chyxelmc.mmoblock.config.NodeConfigLoader(this);
        this.nodeConfigService.setTranslationService(this.translationService);
        this.nodeConfigService.reloadNodes();
    }

    private void initI18n() {
        this.translationService = new me.chyxelmc.mmoblock.i18n.TranslationService(this);
        this.translationService.reload();
        // Wire MMOBlockLogger translator for console i18n logging
        MMOBlockLogger.setTranslator((key, defaultMessage, placeholders) ->
                this.translationService.translateConsole(key, defaultMessage, placeholders));
    }

    private void initDatabase() {
        this.databaseUtils = new DatabaseUtils();
        this.databaseManager = new DatabaseManager(this, this.databaseUtils);
        this.databaseManager.initialize();
        this.dataCache = new DataCache();
        this.blockRepository = new BlockRepository(this.databaseManager, this.dataCache);
        this.respawnRepository = new RespawnRepository(this.databaseManager, this.dataCache);
        this.nodeRepository = new me.chyxelmc.mmoblock.persistence.NodeRepository(this.databaseManager, this.dataCache);
        this.persistenceReadSystem = new me.chyxelmc.mmoblock.ecs.system.PersistenceReadSystem(
                this.blockRepository, this.respawnRepository, this.dataCache);
        this.persistenceSystem = new me.chyxelmc.mmoblock.ecs.system.PersistenceSystem(
                this, this.scheduler, this.blockRepository, this.respawnRepository, this.dataCache);
    }

    private void initRuntime() {
        this.blockRuntimeService = new BlockRuntimeService(new BlockServiceFactory(
                this, this.nmsAdapter, this.scheduler, this.blockConfigService,
                this.persistenceReadSystem, this.persistenceSystem, this.dataCache
        ), this);
        this.nodeRuntimeService = new me.chyxelmc.mmoblock.runtime.NodeRuntimeService(
                this, this.nmsAdapter, this.scheduler, this.blockConfigService,
                this.nodeConfigService, this.blockRuntimeService, this.nodeRepository, this.dataCache
        );
    }

    private void initPlaceholders() {
        this.placeholderContextStore = new HologramPlaceholderContextStore();
        initializePlaceholderApiBridge();
    }

    private void setupFakeBlockChecker() {
        try {
            final String handlerPkg = this.nmsAdapter.getClass().getPackage().getName();
            final String handlerClassName = handlerPkg + ".FakeBlockPacketHandler";
            // FakeBlockChecker is defined as an inner interface of AbstractFakeBlockPacketHandler
            // in the nms-common module, NOT in version-specific subclasses.
            final String checkerClassName = "me.chyxelmc.mmoblock.nms.AbstractFakeBlockPacketHandler$FakeBlockChecker";
            validateFakeHandlerClassName(handlerClassName);
            validateFakeHandlerClassName(checkerClassName);
            final Class<?> handlerClass = me.chyxelmc.mmoblock.utils.SafeClassLoader.loadTrusted(handlerClassName);
            final Class<?> checkerIface = me.chyxelmc.mmoblock.utils.SafeClassLoader.loadTrusted(checkerClassName);
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
                        } catch (final Exception t) {
                            return false;
                        }
                    }
            );
            final java.lang.reflect.Method setChecker = handlerClass.getMethod("setFakeChecker", checkerIface);
            setChecker.invoke(null, proxy);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    private void finalizeBootstrap() {
        this.runtimeCoordinator = new RuntimeCoordinator(this.persistenceReadSystem, this.blockRuntimeService, this.nodeRuntimeService);

        this.apiImpl = new MMOBlockApiImpl(
                this.blockRuntimeService,
                this.blockConfigService,
                this.nodeRuntimeService,
                this.nodeConfigService
        );
        ApiProvider.register(this.apiImpl);

        // Wire config section parser registry (created after API is initialized)
        this.blockConfigService.setConfigSectionParserRegistry(
                this.apiImpl.getConfigSectionParserRegistry()
        );

        if (getConfig().getBoolean("bStats", true)) {
            new Metrics(this, 30727);
        }

        final MMOBlockCommand commandExecutor = new MMOBlockCommand(
                this,
                this.blockConfigService,
                this.nodeConfigService,
                this.blockRuntimeService,
                this.nodeRuntimeService,
                this.runtimeCoordinator
        );
        registerCommands(commandExecutor);
        registerListeners();

        this.runtimeCoordinator.restoreFromPersistence();

        setupEcs();
        syncOnlinePlayers();
    }

    private void registerCommands(final MMOBlockCommand commandExecutor) {
        if (!tryRegisterPaperCommand(commandExecutor)) {
            final PluginCommand mmoblockCommand = resolveOrRegisterMmoBlockCommand();
            if (mmoblockCommand != null) {
                mmoblockCommand.setExecutor(commandExecutor);
                mmoblockCommand.setTabCompleter(commandExecutor);
                mmoblockCommand.setPermission(PERMISSION);
            }
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new InteractionListener(this, this.blockRuntimeService, this.nodeRuntimeService, this.blockConfigService, this.nodeConfigService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new PlatformSyncListener(this, this.scheduler, this.blockRuntimeService, this.nodeRuntimeService), this);
        getServer().getPluginManager().registerEvents(
                new ChunkLifecycleListener(this.blockRuntimeService, this.nodeRuntimeService), this);
        final java.util.function.Consumer<java.util.UUID> hologramCleanup = playerId -> {
            if (this.systemManager == null) return;
            final PacketHologramSyncSystem holo = this.systemManager.getSystem(PacketHologramSyncSystem.class);
            if (holo != null) holo.removePlayerEntries(playerId);
        };
        getServer().getPluginManager().registerEvents(
                new HologramCleanupListener(this, this.scheduler, this.blockRuntimeService, this.nodeRuntimeService, hologramCleanup), this);

        // Listen for late-loading plugins (e.g. Minepacks) to update DependencyChecker flags
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(final PluginEnableEvent event) {
                DependencyChecker.notifyPluginEnabled(event.getPlugin().getName());
            }
        }, this);
    }

    private void setupEcs() {
        try {
            this.entityManager = new EntityManager();
            this.systemManager = new SystemManager();
            this.systemManager.register(new InteractionSpawnSystem(this.nmsAdapter, (blockId, nmsEntityId) -> {
                try {
                    if (this.blockRuntimeService != null) {
                        this.blockRuntimeService.onInteractionSpawned(blockId, nmsEntityId);
                    }
                } catch (final Exception e) {
                    MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                }
            }));
            this.systemManager.register(new PacketHologramSyncSystem(this.nmsAdapter));
            try {
                this.blockRuntimeService.setEntityManager(this.entityManager);
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
            try {
                this.nodeRuntimeService.setEntityManager(this.entityManager);
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
            this.ecsTask = this.scheduler.runTimer(() -> {
                try {
                    this.systemManager.tick(this.entityManager, System.currentTimeMillis());
                } catch (final Exception t) {
                    // logging removed
                }
            }, 1L, 1L);
        } catch (final RuntimeException ex) {
            this.entityManager = null;
            this.systemManager = null;
        }
    }

    private void syncOnlinePlayers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.nmsAdapter.sendSystemMessage(player, "MMOBlock active on NMS " + this.nmsAdapter.targetMinecraftVersion());
            try {
                final String clsName = fakePacketHandlerClassName();
                if (clsName != null) {
                    validateFakeHandlerClassName(clsName);
                    final Class<?> cls = me.chyxelmc.mmoblock.utils.SafeClassLoader.loadTrusted(clsName);
                    final java.lang.reflect.Method inject = cls.getMethod("inject", org.bukkit.entity.Player.class);
                    inject.invoke(null, player);
                }
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
            syncPlayerVisualsNowAndDelayed(player);
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

    /**
     * Validates that a class name is within the allowed NMS handler package before
     * reflective loading. Throws {@link IllegalArgumentException} if the name is
     * not on the allowlist.
     */
    public static void validateFakeHandlerClassName(final String clsName) {
        if (clsName == null) {
            throw new IllegalArgumentException("Class name must not be null");
        }
        if (!clsName.startsWith("me.chyxelmc.mmoblock.nms.")) {
            throw new IllegalArgumentException("Unauthorized class name outside allowed package: " + clsName);
        }
        if (!clsName.contains("FakeBlockPacketHandler")) {
            throw new IllegalArgumentException("Unauthorized class name: " + clsName);
        }
    }


    // ============================================================
    // Shutdown phases
    // ============================================================

    @Override
    public void onDisable() {
        shutdownPlaceholders();
        shutdownRuntime();
        shutdownEcs();
        shutdownDatabase();
        uninjectPlayers();
        clearRegistries();
    }

    private void shutdownPlaceholders() {
        if (this.placeholderExpansion != null) {
            try {
                this.placeholderExpansion.unregister();
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
        }
        this.placeholderExpansion = null;
        this.placeholderApiSetMethod = null;
        if (this.placeholderContextStore != null) {
            this.placeholderContextStore.clear();
            this.placeholderContextStore = null;
        }
    }

    private void shutdownRuntime() {
        if (this.blockRuntimeService != null) {
            if (this.runtimeCoordinator != null) {
                this.runtimeCoordinator.shutdown();
            }
            this.blockRuntimeService = null;
            this.nodeRuntimeService = null;
        }
        this.runtimeCoordinator = null;
    }

    private void shutdownEcs() {
        try {
            if (this.ecsTask != null) {
                this.ecsTask.cancel();
                this.ecsTask = null;
            }
            if (this.entityManager != null && this.nmsAdapter != null) {
                this.systemManager.tick(this.entityManager, System.currentTimeMillis());
            }
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
        this.entityManager = null;
        this.systemManager = null;
    }

    private void shutdownDatabase() {
        if (this.databaseManager != null) {
            this.databaseManager.close();
            this.databaseManager = null;
        }
        if (this.databaseUtils != null) {
            this.databaseUtils.close();
            this.databaseUtils = null;
        }
        if (this.dataCache != null) {
            this.dataCache.clear();
            this.dataCache = null;
        }
        this.blockRepository = null;
        this.respawnRepository = null;
        this.nodeRepository = null;
        this.blockConfigService = null;
        this.nodeConfigService = null;
        ApiProvider.register(null);
        this.apiImpl = null;
    }

    private void uninjectPlayers() {
        try {
            final String clsName = fakePacketHandlerClassName();
            if (clsName != null) {
                validateFakeHandlerClassName(clsName);
                final Class<?> cls = me.chyxelmc.mmoblock.utils.SafeClassLoader.loadTrusted(clsName);
                final java.lang.reflect.Method uninject = cls.getMethod("uninject", org.bukkit.entity.Player.class);
                for (final Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        uninject.invoke(null, p);
                    } catch (final Exception e) {
                        MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                    }
                }
            }
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    private void clearRegistries() {
        this.nmsAdapter = null;
        me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.clear();
    }

    private void syncPlayerVisualsNowAndDelayed(final Player player) {
        if (this.blockRuntimeService == null || player == null || !player.isOnline()) {
            return;
        }
        final var loc = player.getLocation();
        this.blockRuntimeService.syncFakeBlocksForPlayer(player);
        if (this.nodeRuntimeService != null) {
            this.nodeRuntimeService.syncForPlayer(player);
        }
        this.scheduler.runAtLocationLater(loc, () -> {
            if (this.blockRuntimeService != null && player.isOnline()) {
                this.blockRuntimeService.syncFakeBlocksForPlayer(player);
                if (this.nodeRuntimeService != null) {
                    this.nodeRuntimeService.syncForPlayer(player);
                }
            }
        }, 2L);
        this.scheduler.runAtLocationLater(loc, () -> {
            if (this.blockRuntimeService != null && player.isOnline()) {
                this.blockRuntimeService.syncFakeBlocksForPlayer(player);
                if (this.nodeRuntimeService != null) {
                    this.nodeRuntimeService.syncForPlayer(player);
                }
            }
        }, 20L);
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
                        commandExecutor.onCommand(sender, null, CMD_NAME, (String[]) args[1]);
                        return null;
                    }
                    if ("suggest".equals(name) && args != null && args.length == 2) {
                        final org.bukkit.command.CommandSender sender = (org.bukkit.command.CommandSender) sourceStackClass.getMethod("getSender").invoke(args[0]);
                        return commandExecutor.onTabComplete(sender, null, CMD_NAME, (String[]) args[1]);
                    }
                    if ("permission".equals(name)) {
                        return PERMISSION;
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
            registerCommand.invoke(this, CMD_NAME, "Manage MMOBlock interaction entities", java.util.List.of(), basicCommand);
            return true;
        } catch (final ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }
    private PluginCommand resolveOrRegisterMmoBlockCommand() {
        PluginCommand command = null;
        try {
            command = getCommand(CMD_NAME);
        } catch (final UnsupportedOperationException ignored) {
            // Paper plugins may not support YAML command declarations.
        }
        if (command == null) {
            command = Bukkit.getPluginCommand(CMD_NAME);
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
            // PluginCommand constructor is package-private; no public API for dynamic command registration.
            // This is a documented Bukkit pattern for plugins that need to register commands at runtime.
            final Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            // PluginCommand constructor is package-private; this is a documented Bukkit pattern.
            // No need to log at WARNING level — this is intentional and well-known.
            constructor.setAccessible(true);
            final PluginCommand dynamic = constructor.newInstance(CMD_NAME, this);
            dynamic.setDescription("Manage MMOBlock interaction entities");
            dynamic.setUsage("/mmoblock");
            dynamic.setPermission(PERMISSION);

            final Method getCommandMap = getServer().getClass().getMethod("getCommandMap");
            final Object commandMap = getCommandMap.invoke(getServer());
            if (commandMap == null) {
                // logging removed
                return null;
            }

            final Method register = commandMap.getClass().getMethod("register", String.class, org.bukkit.command.Command.class);
            final String fallbackPrefix = getDescription().getName().toLowerCase(Locale.ROOT);
            final boolean registered = (boolean) register.invoke(commandMap, fallbackPrefix, dynamic);
            if (!registered) {
                // logging removed
            }

            final PluginCommand resolved = Bukkit.getPluginCommand(CMD_NAME);
            if (resolved == null) {
                // logging removed
            }
            return resolved;
        } catch (final ReflectiveOperationException exception) {
            // logging removed
            return null;
        }
    }

    public Scheduler scheduler() {
        return this.scheduler;
    }

    public me.chyxelmc.mmoblock.runtime.BlockRuntimeService blockRuntimeService() {
        return this.blockRuntimeService;
    }

    public DatabaseUtils databaseUtils() {
        return this.databaseUtils;
    }

    public me.chyxelmc.mmoblock.config.BlockConfigLoader blockConfigService() {
        return this.blockConfigService;
    }

    public me.chyxelmc.mmoblock.i18n.TranslationService translationService() {
        return this.translationService;
    }

    public HologramPlaceholderContextStore placeholderContextStore() {
        return this.placeholderContextStore;
    }

    public String applyHologramPlaceholderApi(
            final Player player,
            final String text,
            final int progress,
            final int maxProgress,
            final long respawnTimeSeconds
    ) {
        if (player == null || text == null || text.isEmpty()) {
            return text;
        }

        // Step 1: Resolve internal placeholders ({mmocore_level}, etc.)
        String resolved = InternalPlaceholderResolver.resolve(player, text);

        // Step 2: Resolve PlaceholderAPI placeholders (%placeholder% etc.)
        final Method method = this.placeholderApiSetMethod;
        final HologramPlaceholderContextStore contextStore = this.placeholderContextStore;
        if (method == null || contextStore == null) {
            return resolved;
        }
        contextStore.set(
                player.getUniqueId(),
                new HologramPlaceholderContextStore.ContextValues(progress, maxProgress, respawnTimeSeconds)
        );
        try {
            final Object result = method.invoke(null, player, resolved);
            return result instanceof String finalResolved ? finalResolved : resolved;
        } catch (final Exception ignored) {
            return resolved;
        } finally {
            contextStore.clear(player.getUniqueId());
        }
    }

    private void initializePlaceholderApiBridge() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.placeholderApiSetMethod = null;
            this.placeholderExpansion = null;
            return;
        }
        try {
            final Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.placeholderApiSetMethod = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            this.placeholderExpansion = new MMOBlockPlaceholderExpansion(this);
            this.placeholderExpansion.register();
        } catch (final Exception throwable) {
            this.placeholderApiSetMethod = null;
            this.placeholderExpansion = null;
        }
    }
}
