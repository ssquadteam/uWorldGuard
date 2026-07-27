package com.tricrotism.uworldguard;

import com.tricrotism.uworldguard.commands.RegionCommands;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.config.Settings;
import com.tricrotism.uworldguard.gui.ChatInputListener;
import com.tricrotism.uworldguard.gui.ChatInputService;
import com.tricrotism.uworldguard.integration.GSitIntegration;
import com.tricrotism.uworldguard.listeners.*;
import com.tricrotism.uworldguard.migration.MigrationCommands;
import com.tricrotism.uworldguard.region.RegionContainer;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.selection.SelectionService;
import com.tricrotism.uworldguard.selection.WandSelectionProvider;
import com.tricrotism.uworldguard.service.ChamberedPearlTracker;
import com.tricrotism.uworldguard.service.ChunkUnloadService;
import com.tricrotism.uworldguard.service.CollisionService;
import com.tricrotism.uworldguard.service.PlayerTickService;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.storage.SqlRegionStore;
import com.tricrotism.uworldguard.storage.YamlRegionStore;
import com.tricrotism.uworldguard.text.ChatTags;
import com.tricrotism.uworldguard.text.MessageService;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.xenondevs.invui.InvUI;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@NullMarked
public final class UWorldGuard extends JavaPlugin {

    private @Nullable RegionContainerImpl container;
    private @Nullable Settings settings;
    private @Nullable MovementListener movement;
    private @Nullable CollisionService collision;

    /**
     * Re-reads {@code config.yml} and applies everything that can change at runtime. Movement mode is
     * included, so switching EVENT/TASK or retuning the poll no longer needs a restart; storage
     * backend and the selection wand are still bound at enable.
     */
    public void reloadSettings() {
        reloadConfig();
        final Settings current = this.settings;
        if (current != null) {
            current.load(getConfig());
        }
        EventGate.load(getConfig());
        final MovementListener listener = this.movement;
        if (listener != null && current != null) {
            listener.applySettings(current);
        }
    }

    @Override
    public void onEnable() {
        InvUI.getInstance().setPlugin(this);
        saveDefaultConfig();
        final Settings settings = new Settings();
        settings.load(getConfig());
        this.settings = settings;
        EventGate.load(getConfig());

        if (GSitIntegration.isPresent(getServer())) {
            GSitIntegration.registerFlags();
        }

        final RegionStore store = createStore(settings);

        final RegionContainerImpl regionContainer = new RegionContainerImpl(this, store);
        regionContainer.loadAll();
        this.container = regionContainer;

        getServer().getServicesManager().register(
            RegionContainer.class, regionContainer, this, ServicePriority.Normal);
        UWorldGuardApi.bind(regionContainer);

        final RegionQuery query = regionContainer.createQuery();
        final SelectionService selection = new SelectionService(this, settings);
        final MessageService messages = new MessageService(this);
        final CollisionService collision = new CollisionService(this);
        this.collision = collision;
        final ChamberedPearlTracker pearls = new ChamberedPearlTracker(this);
        final ChatInputService chatInput = new ChatInputService();
        final ChatTags chatTags = new ChatTags();

        getServer().getPluginManager().registerEvents(new BuildProtectionListener(query, messages), this);
        final MovementListener movement = new MovementListener(this, query, messages, collision, pearls, chatTags, settings);
        this.movement = movement;
        getServer().getPluginManager().registerEvents(movement, this);
        getServer().getPluginManager().registerEvents(new NaturalListener(query), this);
        getServer().getPluginManager().registerEvents(new CropTrampleListener(query), this);
        getServer().getPluginManager().registerEvents(new EntityListener(query), this);
        getServer().getPluginManager().registerEvents(new PlayerStateListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new ItemUseListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new EndCrystalListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new WorkbenchListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new DeathListener(query), this);
        getServer().getPluginManager().registerEvents(new TravelListener(query), this);
        getServer().getPluginManager().registerEvents(new PearlListener(pearls), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this, chatInput), this);
        getServer().getPluginManager().registerEvents(new WorldListener(regionContainer), this);
        getServer().getPluginManager().registerEvents(new ChatListener(chatTags), this);
        getServer().getPluginManager().registerEvents(new CommandListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new PistonListener(query), this);
        final WandSelectionProvider wand = selection.wandListener();
        if (wand != null) {
            getServer().getPluginManager().registerEvents(wand, this);
        }

        new RegionCommands(this, regionContainer, selection, chatInput, messages)
            .register(new MigrationCommands(this, regionContainer));

        movement.start();
        new PlayerTickService(this, regionContainer, query).start();
        new ChunkUnloadService(this, regionContainer).start();

        if (getServer().getPluginManager().getPlugin("WorldEdit") != null) {
            new WorldEditFlagGuard(query, regionContainer).register();
        }
        if (GSitIntegration.isPresent(getServer())) {
            new GSitIntegration(query).register(this);
        }

        if (settings.autoSaveMinutes() > 0) {
            final long period = settings.autoSaveMinutes();
            getServer().getAsyncScheduler().runAtFixedRate(this,
                task -> regionContainer.saveAll(), period, period, TimeUnit.MINUTES);
        }

        // bStats | https://bstats.org/plugin/bukkit/uWorldGuard/32190
        new Metrics(this, 32190);
    }

    private RegionStore createStore(final Settings settings) {
        if (settings.isSqlEnabled()) {
            try {
                getLogger().info("Using SQL storage backend.");
                return new SqlRegionStore(settings.sqlUrl(), settings.sqlUser(), settings.sqlPassword());
            } catch (final Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialise SQL storage; falling back to YAML.", e);
            }
        }
        return new YamlRegionStore(getDataFolder());
    }

    @Override
    public void onDisable() {
        UWorldGuardApi.bind(null);
        if (movement != null) {
            movement.stop();
        }
        if (collision != null) {
            collision.shutdown();
        }
        if (container != null) {
            container.saveAllBlocking();
        }
    }
}
