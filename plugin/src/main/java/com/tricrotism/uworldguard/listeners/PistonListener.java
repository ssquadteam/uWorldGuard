package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionQuery;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Enforces the pistons flag. Without it a piston placed outside a region can push blocks in, or pull
 * blocks out, entirely bypassing block-place / block-break — the classic border grief.
 *
 * <p>Both the piston itself and every block it moves are checked: on extend against the position each
 * block is pushed into, on retract against the position each block is pulled from. Coordinates are
 * offset arithmetically rather than via {@code getRelative}, which would allocate a {@link Block} per
 * moved block. The whole handler exits on a single bitset test when no region in the world uses the
 * flag, so servers that do not set it pay nothing.
 */
@NullMarked
public final class PistonListener implements Listener {

    private final RegionQuery query;

    public PistonListener(final RegionQuery query) {
        this.query = query;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExtend(final BlockPistonExtendEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final BlockFace direction = event.getDirection();
        if (denied(event.getBlock(), event.getBlocks(),
            direction.getModX(), direction.getModY(), direction.getModZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRetract(final BlockPistonRetractEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (denied(event.getBlock(), event.getBlocks(), 0, 0, 0)) {
            event.setCancelled(true);
        }
    }

    private boolean denied(
        final Block piston, final List<Block> moved, final int dx, final int dy, final int dz
    ) {
        final ApplicableRegionSet atPiston = query.getApplicableRegions(piston);
        if (!atPiston.worldUses(Flags.PISTONS)) {
            return false;
        }
        if (!atPiston.testState(Flags.PISTONS)) {
            return true;
        }
        final World world = piston.getWorld();
        for (int i = 0, n = moved.size(); i < n; i++) {
            final Block block = moved.get(i);
            if (!query.getApplicableRegions(world, block.getX(), block.getY(), block.getZ())
                .testState(Flags.PISTONS)) {
                return true;
            }
            if ((dx | dy | dz) != 0
                && !query.getApplicableRegions(world, block.getX() + dx, block.getY() + dy, block.getZ() + dz)
                .testState(Flags.PISTONS)) {
                return true;
            }
        }
        return false;
    }
}
