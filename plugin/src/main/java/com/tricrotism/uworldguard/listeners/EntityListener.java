package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionQuery;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jspecify.annotations.NullMarked;

/**
 * Enforces mob-spawning, explosion, enderman-grief, mob-damage, damage-animals, item-frame/painting
 * destruction, and mob-drops / exp-drops flags.
 */
@NullMarked
public final class EntityListener implements Listener {

    private final RegionQuery query;

    public EntityListener(final RegionQuery query) {
        this.query = query;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(final CreatureSpawnEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!isNaturalSpawn(event.getSpawnReason())) return;

        if (!query.testState(event.getEntity(), Flags.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (event.blockList().isEmpty()) {
            return;
        }
        final StateFlag flag = explosionFlag(event.getEntity());

        event.blockList().removeIf(block -> !query.testState(block, flag));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanGrief(final EntityChangeBlockEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (event.getEntity() instanceof Enderman
            && !query.testState(event.getBlock(), Flags.ENDERMAN_GRIEF)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Entity victim = event.getEntity();
        final Entity damager = event.getDamager();

        if (victim instanceof Player && damager instanceof Mob
            && !query.testState(victim, Flags.MOB_DAMAGE)) {
            event.setCancelled(true);
            return;
        }

        if (victim instanceof Animals && isPlayerSource(damager)
            && !query.testState(victim, Flags.DAMAGE_ANIMALS)) {
            event.setCancelled(true);
        }
    }

    /**
     * Protects item frames and paintings from being broken by an entity — a player, a skeleton's
     * arrow, a creeper blast. Block-break protection does not cover these: they are entities, so
     * without this they stay destroyable inside an otherwise protected region.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(final HangingBreakByEntityEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Hanging hanging = event.getEntity();
        final StateFlag flag = hanging instanceof ItemFrame
            ? Flags.ENTITY_ITEM_FRAME_DESTROY
            : hanging instanceof Painting ? Flags.ENTITY_PAINTING_DESTROY : null;
        if (flag == null || query.testState(hanging, flag)) {
            return;
        }
        final Entity remover = event.getRemover();
        if (remover instanceof Player player && Bypass.has(player)) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Enforces mob-drops / exp-drops. Players are excluded — their drops are governed by the
     * keep-inventory / keep-exp flags on {@code PlayerDeathEvent}, which is a subclass of this event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent event) {
        if (event.getEntity() instanceof Player || EventGate.disabled(event)) {
            return;
        }
        final ApplicableRegionSet set = query.getApplicableRegions(event.getEntity());
        if (set.worldUses(Flags.MOB_DROPS) && !set.testState(Flags.MOB_DROPS)) {
            event.getDrops().clear();
        }
        if (set.worldUses(Flags.EXP_DROPS) && !set.testState(Flags.EXP_DROPS)) {
            event.setDroppedExp(0);
        }
    }

    private static boolean isNaturalSpawn(final CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, SPAWNER, REINFORCEMENTS, PATROL, RAID, JOCKEY, MOUNT, VILLAGE_INVASION, TRAP -> true;
            default -> false;
        };
    }

    private static StateFlag explosionFlag(final Entity entity) {
        return switch (entity) {
            case Creeper _ -> Flags.CREEPER_EXPLOSION;
            case TNTPrimed _ -> Flags.TNT;
            case Fireball _ -> Flags.GHAST_FIREBALL;
            default -> Flags.OTHER_EXPLOSION;
        };
    }

    private static boolean isPlayerSource(final Entity damager) {
        if (damager instanceof Player) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            final ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player;
        }
        return false;
    }
}
