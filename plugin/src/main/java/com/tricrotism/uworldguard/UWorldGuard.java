package com.tricrotism.uworldguard;

import com.tricrotism.uworldguard.commands.RegionCommands;
import com.tricrotism.uworldguard.config.ConfigUpdater;
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
import com.tricrotism.uworldguard.service.*;
import com.tricrotism.uworldguard.storage.RegionStore;
import com.tricrotism.uworldguard.storage.SqlRegionStore;
import com.tricrotism.uworldguard.storage.YamlRegionStore;
import com.tricrotism.uworldguard.text.ChatTags;
import com.tricrotism.uworldguard.text.MessageService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import xyz.xenondevs.invui.InvUI;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@NullMarked
public final class UWorldGuard extends JavaPlugin {

    private @Nullable RegionContainerImpl container;
    private @Nullable Settings settings;
    private @Nullable MovementListener movement;
    private @Nullable CollisionService collision;
    private @Nullable WorldEditFlagGuard worldEditGuard;
    private @Nullable RegionStore store;
    private @Nullable Metrics metrics;
    private @Nullable ScheduledTask autoSaveTask;

    /**
     * Re-reads {@code config.yml} and applies everything that can change at runtime. Movement mode
     * and the autosave cadence are included, so switching EVENT/TASK, retuning the poll, or changing
     * {@code auto-save-minutes} no longer needs a restart; storage backend and the selection wand are
     * still bound at enable.
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
        if (current != null) {
            scheduleAutoSave(current);
        }
    }

    /**
     * (Re)starts the autosave at the configured cadence, cancelling any task already running. Reading
     * the period once at enable and dropping the handle meant a reload could neither retune it nor
     * start one that had been configured off at boot.
     */
    private void scheduleAutoSave(final Settings settings) {
        final ScheduledTask running = this.autoSaveTask;
        if (running != null) {
            running.cancel();
            this.autoSaveTask = null;
        }
        final RegionContainerImpl regionContainer = this.container;
        final long period = settings.autoSaveMinutes();
        if (period <= 0L || regionContainer == null) {
            return;
        }
        this.autoSaveTask = getServer().getAsyncScheduler().runAtFixedRate(this,
            _ -> regionContainer.saveAll(), period, period, TimeUnit.MINUTES);
    }

    @Override
    public void onEnable() {
        InvUI.getInstance().setPlugin(this);
        saveDefaultConfig();
        final List<String> addedSettings =
            ConfigUpdater.merge(this, new File(getDataFolder(), "config.yml"), "config.yml");
        if (!addedSettings.isEmpty()) {
            reloadConfig();
            getLogger().info("config.yml gained " + addedSettings.size()
                + " new setting(s) from this version: " + String.join(", ", addedSettings));
        }
        final Settings settings = new Settings();
        settings.load(getConfig());
        this.settings = settings;
        EventGate.load(getConfig());

        GSitIntegration.registerFlags();

        final RegionStore store = createStore(settings);
        this.store = store;

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
        getServer().getPluginManager().registerEvents(new EntityListener(regionContainer, query), this);
        getServer().getPluginManager().registerEvents(new PlayerStateListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new ItemUseListener(regionContainer, query, messages), this);
        getServer().getPluginManager().registerEvents(new EndCrystalListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new WorkbenchListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new DeathListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new TravelListener(query), this);
        getServer().getPluginManager().registerEvents(new PearlListener(pearls), this);
        getServer().getPluginManager().registerEvents(new ChatInputListener(this, chatInput), this);
        final ChunkUnloadService chunkUnload = new ChunkUnloadService(this, regionContainer);
        final WandSelectionProvider wand = selection.wandListener();
        getServer().getPluginManager().registerEvents(
            new WorldListener(regionContainer, chunkUnload, wand), this);
        getServer().getPluginManager().registerEvents(new ChatListener(chatTags), this);
        getServer().getPluginManager().registerEvents(new CommandListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new PistonListener(query), this);
        getServer().getPluginManager().registerEvents(new VehicleListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new InteractionListener(query, messages), this);
        getServer().getPluginManager().registerEvents(new MachineListener(regionContainer, query, messages), this);
        if (wand != null) {
            getServer().getPluginManager().registerEvents(wand, this);
        }

        new RegionCommands(this, regionContainer, selection, chatInput, messages)
            .register(new MigrationCommands(this, regionContainer));

        movement.start();
        new PlayerTickService(this, regionContainer, query).start();
        chunkUnload.start();

        if (getServer().getPluginManager().getPlugin("WorldEdit") != null) {
            final WorldEditFlagGuard guard = new WorldEditFlagGuard(query, regionContainer);
            guard.register();
            this.worldEditGuard = guard;
        }
        if (GSitIntegration.isPresent(getServer())) {
            new GSitIntegration(query).register(this);
        }

        scheduleAutoSave(settings);

        if (settings.updateCheck() && !settings.updateUrl().isBlank()) {
            new UpdateChecker(this, settings.updateUrl()).start();
        }

        // bStats | https://bstats.org/plugin/bukkit/uWorldGuard/32190
        this.metrics = new Metrics(this, 32190);
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

    /**
     * Releases in dependency order: stop the things that read regions, then the ones that hold state
     * outside this plugin, then write the regions out.
     */
    @Override
    public void onDisable() {
        UWorldGuardApi.bind(null);
        final ScheduledTask autoSave = this.autoSaveTask;
        if (autoSave != null) {
            autoSave.cancel();
            this.autoSaveTask = null;
        }
        if (movement != null) {
            movement.stop();
            movement.shutdown();
        }
        if (worldEditGuard != null) {
            worldEditGuard.unregister();
            worldEditGuard = null;
        }
        if (collision != null) {
            collision.shutdown();
        }
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        if (container != null) {
            container.saveAllBlocking();
        }
        if (store != null) {
            store.close();
            store = null;
        }
    }
}
