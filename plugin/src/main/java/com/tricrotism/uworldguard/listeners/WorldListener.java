package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.selection.WandSelectionProvider;
import com.tricrotism.uworldguard.service.ChunkUnloadService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Loads/unloads a world's regions as worlds come and go after startup.
 */
@NullMarked
public final class WorldListener implements Listener {

    private final RegionContainerImpl container;
    private final ChunkUnloadService chunkUnload;
    private final MovementListener movement;
    private final @Nullable WandSelectionProvider wand;

    public WorldListener(
        final RegionContainerImpl container, final ChunkUnloadService chunkUnload,
        final MovementListener movement, final @Nullable WandSelectionProvider wand
    ) {
        this.container = container;
        this.chunkUnload = chunkUnload;
        this.movement = movement;
        this.wand = wand;
    }

    @EventHandler
    public void onWorldLoad(final WorldLoadEvent event) {
        container.load(event.getWorld());
    }

    /**
     * Only once the unload is actually going ahead. {@link WorldUnloadEvent} is cancellable, and
     * dropping the world's manager on an unload another plugin then vetoes would leave that world
     * loaded with no regions behind it — silently unprotected until the next restart.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(final WorldUnloadEvent event) {
        container.unload(event.getWorld());
        chunkUnload.forget(event.getWorld());
        movement.forgetWorld(event.getWorld());
        if (wand != null) {
            wand.forgetWorld(event.getWorld());
        }
    }
}
