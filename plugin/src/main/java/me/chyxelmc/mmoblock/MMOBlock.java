package me.chyxelmc.mmoblock;

import me.chyxelmc.mmoblock.command.MMOBlockCommand;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.listener.FakeBlockSyncListener;
import me.chyxelmc.mmoblock.listener.InteractionListener;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapterRegistry;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.DatabaseManager;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.utils.Metrics;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class MMOBlock extends JavaPlugin {

    private NmsAdapter nmsAdapter;
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
        this.runtimeCoordinator = new RuntimeCoordinator(persistenceReadSystem, this.blockRuntimeService);

        if (getConfig().getBoolean("bStats", true)) {
            new Metrics(this, 30727);
        }

        final MMOBlockCommand commandExecutor = new MMOBlockCommand(this, this.blockConfigService, this.blockRuntimeService, this.runtimeCoordinator);
        registerCommand("mmoblock", "Manage MMOBlock interaction entities", List.of(), new BasicCommand() {
            @Override
            public void execute(final @NonNull CommandSourceStack commandSourceStack, final String @NonNull [] args) {
                commandExecutor.onCommand(commandSourceStack.getSender(), null, "mmoblock", args);
            }

            @Override
            public @NonNull Collection<String> suggest(final @NonNull CommandSourceStack commandSourceStack, final String @NonNull [] args) {
                return Objects.requireNonNull(commandExecutor.onTabComplete(commandSourceStack.getSender(), null, "mmoblock", args));
            }

            @Override
            public String permission() {
                return "mmoblock.admin";
            }
        });
        getServer().getPluginManager().registerEvents(new InteractionListener(this.blockRuntimeService), this);
        getServer().getPluginManager().registerEvents(new FakeBlockSyncListener(this, this.blockRuntimeService), this);

        this.runtimeCoordinator.restoreFromPersistence();

        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.nmsAdapter.sendSystemMessage(player, "MMOBlock active on NMS " + this.nmsAdapter.targetMinecraftVersion());
        }
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
        this.nmsAdapter = null;
    }
}
