package com.tricrotism.uworldguard.flags;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * WorldGuard flag names that uWorldGuard implements under a different name — the single
 * source of truth shared by the migration importer and the WorldGuard API compatibility
 * layer. Only exact behavioral equivalents belong here; a flag whose uWorldGuard
 * counterpart is broader or narrower is deliberately left unmapped rather than silently
 * widened.
 */
@NullMarked
public final class WgFlagNames {

    private static final Map<String, String> WG_TO_UWG = Map.of(
        "block-trampling", "crop-trample",
        "wind-charge-burst", "wind-charge",
        "frosted-ice-form", "frostwalker",
        "min-heal", "heal-min-health",
        "max-heal", "heal-max-health",
        "feed-min-hunger", "min-food",
        "feed-max-hunger", "max-food",
        "spawn", "respawn-location"
    );

    private WgFlagNames() {
    }

    /**
     * The uWorldGuard name for a WorldGuard-only spelling, or {@code null} if the names match
     * (or no equivalent exists).
     */
    public static @Nullable String uwgName(final String wgName) {
        return WG_TO_UWG.get(wgName.toLowerCase(Locale.ROOT));
    }

    /**
     * Resolve a WorldGuard flag name to the registered flag: directly, then through the alias
     * table. {@code null} when uWorldGuard has no equivalent.
     */
    public static @Nullable Flag<?> resolve(final String wgName) {
        final Flag<?> direct = Flags.get(wgName);
        if (direct != null) {
            return direct;
        }
        final String alias = WG_TO_UWG.get(wgName.toLowerCase(Locale.ROOT));
        return alias == null ? null : Flags.get(alias);
    }
}
