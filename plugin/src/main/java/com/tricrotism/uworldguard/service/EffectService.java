package com.tricrotism.uworldguard.service;

import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.RegionContainerImpl;
import com.tricrotism.uworldguard.region.RegionQuery;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

/**
 * Applies the give-effects / blocked-effects flags once per second. give-effects re-applies each
 * configured effect with a short duration, so it lingers only briefly after the player leaves;
 * blocked-effects strips the listed effect types while the player is inside.
 *
 * <p>Folia-correct: a global repeating task fans each player out to that player's own entity
 * scheduler, so potion API is only ever touched on the entity's region thread. Region flag reads
 * go through the thread-safe {@link RegionQuery}. The whole tick is skipped when no region uses
 * either flag.
 */
@NullMarked
public final class EffectService {

    private static final int REAPPLY_TICKS = 40;

    private final Plugin plugin;
    private final RegionContainerImpl container;
    private final RegionQuery query;

    public EffectService(final Plugin plugin, final RegionContainerImpl container, final RegionQuery query) {
        this.plugin = plugin;
        this.container = container;
        this.query = query;
    }

    public void start() {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!container.anyRegionUses(Flags.GIVE_EFFECTS) && !container.anyRegionUses(Flags.BLOCKED_EFFECTS)) {
                return;
            }
            for (final Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> apply(player), null);
            }
        }, 20L, 20L);
    }

    private void apply(final Player player) {
        final ApplicableRegionSet regions = query.getApplicableRegions(player);

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
}
