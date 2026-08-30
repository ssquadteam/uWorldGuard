package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.text.MessageService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Enforces the build, block-break, block-place, interact, use, and pvp flags — including the two
 * paths that bypass {@code BlockPlaceEvent} entirely, bucket fluid placement and hanging entities.
 */
@NullMarked
public final class BuildProtectionListener implements Listener {

    private final RegionQuery query;
    private final MessageService messages;

    public BuildProtectionListener(final RegionQuery query, final MessageService messages) {
        this.query = query;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final ApplicableRegionSet set = query.getApplicableRegions(block);
        final Material type = block.getType();
        if (set.flagSetContains(Flags.DENY_BLOCK_BREAK, type)) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.BLOCK_BREAK, set.queryValue(Flags.DENY_MESSAGE));
            return;
        }
        if (set.flagSetContains(Flags.ALLOW_BLOCK_BREAK, type)) {
            return;
        }
        if (!set.canBuild(player.getUniqueId()) || !set.testState(Flags.BLOCK_BREAK, player.getUniqueId())) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.BLOCK_BREAK, set.queryValue(Flags.DENY_MESSAGE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final ApplicableRegionSet set = query.getApplicableRegions(block);
        final Material type = block.getType();
        if (set.flagSetContains(Flags.DENY_BLOCK_PLACE, type)) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.BLOCK_PLACE, set.queryValue(Flags.DENY_MESSAGE));
            return;
        }
        if (set.flagSetContains(Flags.ALLOW_BLOCK_PLACE, type)) {
            return;
        }
        if (!set.canBuild(player.getUniqueId()) || !set.testState(Flags.BLOCK_PLACE, player.getUniqueId())) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.BLOCK_PLACE, set.queryValue(Flags.DENY_MESSAGE));
        }
    }

    /**
     * Emptying or filling a bucket never fires {@link BlockPlaceEvent} or {@link BlockBreakEvent} —
     * fluid placement is its own event — so without this a non-member could pour lava into an
     * otherwise fully protected region, or drain its water, and nothing would stop them.
     *
     * <p>Emptying is judged as a place and filling as a break, matching what the player is doing to
     * the world, so the same block-place / block-break flags and material lists govern both.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        if (!EventGate.disabled(event)) {
            checkBucket(event, event.getBlock(), Flags.BLOCK_PLACE);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        if (!EventGate.disabled(event)) {
            checkBucket(event, event.getBlock(), Flags.BLOCK_BREAK);
        }
    }

    /**
     * Shared by both bucket handlers. They cannot be one handler on {@code PlayerBucketEvent}: that
     * class declares no handler list of its own, so registering against it fails outright.
     *
     * <p>Both pass {@code event.getBlock()} — the block the server says this event changes, which is
     * where the fluid lands when emptying and the source drained when filling. It is not the clicked
     * block and needs no offset applied to it; adding one checked the block beyond the one actually
     * changing, so a pour aimed at a region's edge was judged against its neighbour.
     */
    private void checkBucket(final PlayerBucketEvent event, final Block block, final StateFlag flag) {
        final Player player = event.getPlayer();
        final ApplicableRegionSet set = query.getApplicableRegions(block);
        if (set.canBuild(player.getUniqueId()) && set.testState(flag, player.getUniqueId())) {
            return;
        }
        if (Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
        messages.sendDeny(player, flag, set.queryValue(Flags.DENY_MESSAGE));
    }

    /**
     * Item frames and paintings are entities, so hanging them fires neither {@link BlockPlaceEvent}
     * nor any flag we already check — the destroy side was covered but the place side was not.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(event.getEntity());
        if (set.canBuild(player.getUniqueId()) && set.testState(Flags.BLOCK_PLACE, player.getUniqueId())) {
            return;
        }
        if (Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
        messages.sendDeny(player, Flags.BLOCK_PLACE, set.queryValue(Flags.DENY_MESSAGE));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Player player = event.getPlayer();
        final ApplicableRegionSet set = query.getApplicableRegions(block);
        if (!set.canBuild(player.getUniqueId())
            && (!set.testState(Flags.INTERACT, player.getUniqueId())
            || (block.getType().isInteractable() && !set.testState(Flags.USE, player.getUniqueId())))) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.INTERACT, set.queryValue(Flags.DENY_MESSAGE));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(final EntityDamageByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        final Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (!query.getApplicableRegions(event.getEntity())
            .testState(Flags.PVP, attacker.getUniqueId())) {
            if (Bypass.has(attacker)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    private static @Nullable Player resolvePlayer(final Object damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player p) {
                return p;
            }
        }
        return null;
    }
}
