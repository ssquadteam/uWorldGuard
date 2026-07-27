package com.tricrotism.uworldguard.service;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ProtectedRegion;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionManager;
import com.tricrotism.uworldguard.region.RegionType;
import com.tricrotism.uworldguard.util.BlockVector3;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the chunk-unload flag by keeping the chunks of any region with chunk-unload=DENY loaded
 * via plugin chunk tickets. Paper scopes these tickets to this plugin and drops them automatically
 * on disable, so no shutdown cleanup is needed and other plugins' tickets are never disturbed.
 *
 * <p>A slow global task reconciles the desired ticket set every few seconds, so flag changes, region
 * edits, and world loads are all picked up without per-edit hooks; the steady state issues no work.
 * The reconcile is skipped when no region uses the flag and nothing is currently ticketed. Tickets
 * are added/removed on the owning chunk's region thread for Folia safety, and a per-region chunk-span
 * cap stops one oversized region from pinning an unbounded number of chunks.
 */
@NullMarked
public final class ChunkUnloadService {

    private static final long PERIOD_TICKS = 100L;
    private static final int MAX_CHUNKS_PER_REGION = 4096;

    private final Plugin plugin;
    private final RegionContainerImpl container;
    private final Map<UUID, Set<Long>> ticketed = new ConcurrentHashMap<>();
    private final Map<UUID, Long> signatures = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public ChunkUnloadService(final Plugin plugin, final RegionContainerImpl container) {
        this.plugin = plugin;
        this.container = container;
    }

    public void start() {
        plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(plugin, task -> reconcile(), PERIOD_TICKS, PERIOD_TICKS);
    }

    private void reconcile() {
        if (!container.anyRegionUses(Flags.CHUNK_UNLOAD) && ticketed.isEmpty()) {
            return;
        }
        for (final World world : plugin.getServer().getWorlds()) {
            final RegionManager manager = container.get(world);
            if (manager == null) {
                continue;
            }
            final UUID uid = world.getUID();
            final long signature = signatureOf(manager);
            final Long previous = signatures.get(uid);
            if (previous != null && previous == signature) {
                continue;
            }

            final Set<Long> desired = desiredChunks(manager);
            final Set<Long> current = ticketed.getOrDefault(uid, Set.of());

            for (final long key : desired) {
                if (!current.contains(key)) {
                    setTicket(world, key, true);
                }
            }
            for (final long key : current) {
                if (!desired.contains(key)) {
                    setTicket(world, key, false);
                }
            }
            if (desired.isEmpty()) {
                ticketed.remove(uid);
            } else {
                ticketed.put(uid, desired);
            }
            signatures.put(uid, signature);
        }
    }

    /**
     * A cheap order-independent digest of every region that currently denies chunk-unload, covering
     * its identity and bounds — everything the ticket set is derived from. Order-independent because
     * region iteration order is not stable, and a reshuffle must not read as a change.
     */
    private static long signatureOf(final RegionManager manager) {
        long signature = 0L;
        for (final ProtectedRegion region : manager.getRegions()) {
            if (region.getType() == RegionType.GLOBAL || region.getFlag(Flags.CHUNK_UNLOAD) != State.DENY) {
                continue;
            }
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            long h = region.getId().hashCode();
            h = h * 31 + min.x();
            h = h * 31 + min.z();
            h = h * 31 + max.x();
            h = h * 31 + max.z();
            signature += h * 0x9E3779B97F4A7C15L;
        }
        return signature;
    }

    private Set<Long> desiredChunks(final RegionManager manager) {
        final Set<Long> chunks = new HashSet<>();
        for (final ProtectedRegion region : manager.getRegions()) {
            if (region.getType() == RegionType.GLOBAL || region.getFlag(Flags.CHUNK_UNLOAD) != State.DENY) {
                continue;
            }
            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();
            final int cxMin = min.x() >> 4;
            final int cxMax = max.x() >> 4;
            final int czMin = min.z() >> 4;
            final int czMax = max.z() >> 4;
            final long span = (long) (cxMax - cxMin + 1) * (czMax - czMin + 1);
            if (span > MAX_CHUNKS_PER_REGION) {
                if (warned.add(region.getId())) {
                    plugin.getLogger().warning("Region '" + region.getId() + "' spans " + span
                        + " chunks (> " + MAX_CHUNKS_PER_REGION + "); chunk-unload not enforced for it.");
                }
                continue;
            }
            for (int cx = cxMin; cx <= cxMax; cx++) {
                for (int cz = czMin; cz <= czMax; cz++) {
                    chunks.add((((long) cx) << 32) | (cz & 0xffffffffL));
                }
            }
        }
        return chunks;
    }

    private void setTicket(final World world, final long key, final boolean add) {
        final int cx = (int) (key >> 32);
        final int cz = (int) key;
        plugin.getServer().getRegionScheduler().execute(plugin, world, cx, cz, () -> {
            if (add) {
                world.addPluginChunkTicket(cx, cz, plugin);
            } else {
                world.removePluginChunkTicket(cx, cz, plugin);
            }
        });
    }
}
