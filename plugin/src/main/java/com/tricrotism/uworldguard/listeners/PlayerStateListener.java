package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.text.MessageService;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces sleep, enderpearl/chorus teleport, chest-access, ride, invincible/godmode, and
 * item-durability flags.
 */
@NullMarked
public final class PlayerStateListener implements Listener {

    private static final String BYPASS = "uworldguard.bypass";

    private final RegionQuery query;
    private final MessageService messages;

    public PlayerStateListener(final RegionQuery query, final MessageService messages) {
        this.query = query;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSleep(final PlayerBedEnterEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!query.testState(event.getBed(), Flags.SLEEP)) {
            if (event.getPlayer().hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        final var flag = switch (event.getCause().name()) {
            case "ENDER_PEARL" -> Flags.ENDERPEARL;
            case "CHORUS_FRUIT" -> Flags.CHORUS_TELEPORT;
            default -> null;
        };
        if (flag != null && !query.testState(event.getTo(), flag)
            && !event.getPlayer().hasPermission(BYPASS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChestAccess(final PlayerInteractEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        final Block block = event.getClickedBlock();
        if (block == null || !(block.getState(false) instanceof Container)) {
            return;
        }
        final Player player = event.getPlayer();
        final ApplicableRegionSet set = query.getApplicableRegions(block);
        if (!set.testState(Flags.CHEST_ACCESS) && !set.canBuild(player.getUniqueId())) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRide(final VehicleEnterEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(event.getVehicle());
        if (!set.testState(Flags.RIDE) && !set.canBuild(player.getUniqueId())) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvincible(final EntityDamageEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(player);
        if (Boolean.TRUE.equals(set.queryValue(Flags.INVINCIBLE))
            || Boolean.TRUE.equals(set.queryValue(Flags.GODMODE))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(final PlayerItemDamageEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        if (!query.testState(player, Flags.ITEM_DURABILITY)) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
        }
    }
}
