package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
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
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.DISABLE_COMPLETELY);
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
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.DISABLE_COMPLETELY);
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
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.DISABLE_THROW);
        }
    }

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
        if (shooter instanceof Player player && Bypass.has(player)) {
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
        if (!query.getApplicableRegions(villager).testState(Flags.VILLAGER_TRADE, player.getUniqueId())) {
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.VILLAGER_TRADE);
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
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
            messages.sendDeny(player, Flags.DENY_ITEM_DROPS);
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
            if (Bypass.has(player)) {
                return;
            }
            event.setCancelled(true);
        }
    }
}
