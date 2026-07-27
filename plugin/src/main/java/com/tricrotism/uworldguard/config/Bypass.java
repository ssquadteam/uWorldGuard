package com.tricrotism.uworldguard.config;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a player's region protections are currently bypassed.
 *
 * <p>Holding {@code uworldguard.bypass} is what grants the ability; {@code /uwg bypass} lets a holder
 * switch it off for themselves so they can check a region behaves the way an ordinary player
 * experiences it, without an operator having to strip and re-grant the node. Staff who never touch
 * the command are unaffected.
 *
 * <p>Static like {@link EventGate}, and for the same reason: it is consulted from three dozen places
 * across every listener, and threading a service through each of their constructors to carry one
 * boolean would cost far more than it explains. The set is concurrent — toggled from a command
 * thread, read from every region thread.
 */
@NullMarked
public final class Bypass {

    public static final String NODE = "uworldguard.bypass";

    /**
     * Holders who have switched their own bypass off. Absence means "on", so the default is unchanged.
     */
    private static final Set<UUID> suspended = ConcurrentHashMap.newKeySet();

    private Bypass() {}

    /**
     * Whether {@code player} currently bypasses region protection.
     */
    public static boolean has(final Player player) {
        return player.hasPermission(NODE) && (suspended.isEmpty() || !suspended.contains(player.getUniqueId()));
    }

    /**
     * Flips the player's bypass and returns its new state.
     */
    public static boolean toggle(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (suspended.remove(uuid)) {
            return true;
        }
        suspended.add(uuid);
        return false;
    }

    /**
     * Whether the player has switched their bypass off (only meaningful if they hold the node).
     */
    public static boolean suspended(final Player player) {
        return suspended.contains(player.getUniqueId());
    }

    /**
     * Drop a player's state on quit, so the set cannot grow without bound.
     */
    public static void clear(final UUID uuid) {
        suspended.remove(uuid);
    }
}
