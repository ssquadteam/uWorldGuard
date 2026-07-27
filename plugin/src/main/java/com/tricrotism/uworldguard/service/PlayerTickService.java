package com.tricrotism.uworldguard.service;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionQuery;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Applies the once-a-second player flags: heal-amount / heal-min-health / heal-max-health, and
 * give-effects / blocked-effects. Both used to be separate tasks, each iterating every online player
 * and each resolving that player's regions; merging them halves the scheduler churn and does one
 * region lookup per player per second instead of two.
 *
 * <p>Folia-correct: a global repeating task fans each player out to that player's own entity
 * scheduler, so health and potion API are only ever touched on the entity's region thread. Region
 * flag reads go through the thread-safe {@link RegionQuery}. The whole tick is skipped when no region
 * on the server uses any of these flags, and each half is skipped per-world via
 * {@link ApplicableRegionSet#worldUses}.
 */
@NullMarked
public final class PlayerTickService {

    private static final int REAPPLY_TICKS = 40;

    private final Plugin plugin;
    private final RegionContainerImpl container;
    private final RegionQuery query;

    public PlayerTickService(final Plugin plugin, final RegionContainerImpl container, final RegionQuery query) {
        this.plugin = plugin;
        this.container = container;
        this.query = query;
    }

    public void start() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!container.anyRegionUses(Flags.HEAL_AMOUNT)
                && !container.anyRegionUses(Flags.GIVE_EFFECTS)
                && !container.anyRegionUses(Flags.BLOCKED_EFFECTS)) {
                return;
            }
            for (final Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> apply(player), null);
            }
        }, 20L, 20L);
    }

    private void apply(final Player player) {
        final ApplicableRegionSet regions = query.getApplicableRegions(player);
        if (regions.worldUses(Flags.HEAL_AMOUNT)) {
            heal(player, regions);
        }
        if (regions.worldUses(Flags.GIVE_EFFECTS) || regions.worldUses(Flags.BLOCKED_EFFECTS)) {
            effects(player, regions);
        }
    }

    private void heal(final Player player, final ApplicableRegionSet regions) {
        final Double amount = regions.queryValue(Flags.HEAL_AMOUNT);
        if (amount == null || amount == 0.0) {
            return;
        }
        final AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }
        final double maxHealth = maxHealthAttribute.getValue();
        final double min = orDefault(regions.queryValue(Flags.HEAL_MIN_HEALTH), 0.0);
        final double max = Math.min(maxHealth, orDefault(regions.queryValue(Flags.HEAL_MAX_HEALTH), maxHealth));
        final double health = player.getHealth();

        if (amount > 0 && health >= max) {
            return;
        }
        if (amount < 0 && health <= min) {
            return;
        }
        final double target = Math.max(0.0, Math.clamp(health + amount, min, max));
        if (target != health) {
            player.setHealth(target);
        }
    }

    private void effects(final Player player, final ApplicableRegionSet regions) {
        final Set<PotionEffect> give = regions.queryValue(Flags.GIVE_EFFECTS);
        if (give != null) {
            for (final PotionEffect effect : give) {
                player.addPotionEffect(new PotionEffect(
                    effect.getType(), REAPPLY_TICKS, effect.getAmplifier(), true, false, false));
            }
        }

        final Set<PotionEffect> blocked = regions.queryValue(Flags.BLOCKED_EFFECTS);
        if (blocked != null) {
            for (final PotionEffect effect : blocked) {
                if (player.hasPotionEffect(effect.getType())) {
                    player.removePotionEffect(effect.getType());
                }
            }
        }
    }

    private static double orDefault(final @Nullable Double value, final double fallback) {
        return value != null ? value : fallback;
    }
}
