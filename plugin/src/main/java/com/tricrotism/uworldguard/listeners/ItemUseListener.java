package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.text.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.projectiles.ProjectileSource;
import org.jspecify.annotations.NullMarked;

/**
 * Item-use flags: {@code disable-completely} (right-click use, melee weapon, totem resurrect),
 * {@code disable-throw} (egg / snowball / ender pearl / xp bottle), {@code wind-charge},
 * {@code villager-trade}, and {@code deny-item-drops} / {@code deny-item-pickup}. Each handler
 * filters cheaply and returns fast.
 */
@NullMarked
public final class ItemUseListener implements Listener {

    private static final String BYPASS = "uworldguard.bypass";

    private final RegionQuery query;
    private final MessageService messages;

    public ItemUseListener(final RegionQuery query, final MessageService messages) {
        this.query = query;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(final PlayerInteractEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        final Player player = event.getPlayer();
        final Material item = event.getItem().getType();
        if (query.getApplicableRegions(player).flagSetContains(Flags.DISABLE_COMPLETELY, item)) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(final EntityDamageByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        final Material weapon = player.getInventory().getItemInMainHand().getType();
        if (weapon.isAir()) {
            return;
        }
        if (query.getApplicableRegions(event.getEntity()).flagSetContains(Flags.DISABLE_COMPLETELY, weapon)) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResurrect(final EntityResurrectEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final EntityEquipment equipment = player.getEquipment();
        final boolean holdingTotem = equipment.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || equipment.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
        if (!holdingTotem) {
            return;
        }
        if (query.getApplicableRegions(player).flagSetContains(Flags.DISABLE_COMPLETELY, Material.TOTEM_OF_UNDYING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onThrow(final ProjectileLaunchEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Projectile projectile = event.getEntity();
        if (!(projectile instanceof Egg || projectile instanceof Snowball
            || projectile instanceof EnderPearl || projectile instanceof ThrownExpBottle)) {
            return;
        }
        final ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Player player)) {
            return;
        }
        if (Boolean.TRUE.equals(query.getApplicableRegions(player).queryValue(Flags.DISABLE_THROW))) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "no-permission");
        }
    }

    // The wind-charge flag gates the burst's knockback, not the throw, so players can still launch
    // themselves (the self-jump) in a denied region while being unable to blast other entities around.
    // Only the deprecated EntityKnockbackByEntityEvent exposes the wind charge as the push source; the
    // modern event resolves it to the thrower, which can't be told apart from their other explosions.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @SuppressWarnings({"deprecation", "removal"})
    public void onWindChargeKnockback(final EntityKnockbackByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getSourceEntity() instanceof AbstractWindCharge windCharge)) {
            return;
        }
        final Entity victim = event.getEntity();
        final ProjectileSource shooter = windCharge.getShooter();
        if (shooter instanceof Entity thrower && thrower.equals(victim)) {
            return;
        }
        if (query.testState(victim, Flags.WIND_CHARGE)) {
            return;
        }
        if (shooter instanceof Player player && player.hasPermission(BYPASS)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(final PlayerInteractEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getRightClicked() instanceof AbstractVillager villager)) {
            return;
        }
        final Player player = event.getPlayer();
        if (!query.getApplicableRegions(villager).testState(Flags.VILLAGER_TRADE)) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(final PlayerDropItemEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        final Material item = event.getItemDrop().getItemStack().getType();
        if (query.getApplicableRegions(player).flagSetContains(Flags.DENY_ITEM_DROPS, item)) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "no-permission");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(final EntityPickupItemEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final Material item = event.getItem().getItemStack().getType();
        if (query.getApplicableRegions(player).flagSetContains(Flags.DENY_ITEM_PICKUP, item)) {
            if (player.hasPermission(BYPASS)) {
                return;
            }
            event.setCancelled(true);
        }
    }
}
