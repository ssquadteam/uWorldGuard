package com.tricrotism.uworldguard.config;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a player's region protections are currently bypassed.
 *
 * <p>Bypassing takes <em>both</em> halves: holding {@code uworldguard.bypass} grants the ability, and
 * {@code /uwg bypass} arms it. Holding the node alone does nothing. That way a protection plugin never
 * quietly stops protecting against whoever happens to hold the node — you are an ordinary player until
 * you say otherwise, and the state is visible because you had to ask for it.
 *
 * <p>Armed state is deliberately not persisted: {@link #clear} drops it on quit, so a session always
 * begins with protection applying. Losing the node while armed also takes effect immediately, since
 * the permission is re-checked on every call rather than captured at toggle time.
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
     * Holders who have armed their bypass. Absence means "off", so the default is that protection
     * applies to everyone.
     */
    private static final Set<UUID> armed = ConcurrentHashMap.newKeySet();

    private Bypass() {}

    /**
     * Whether {@code player} currently bypasses region protection — armed <em>and</em> still permitted.
     */
    public static boolean has(final Player player) {
        return !armed.isEmpty() && armed.contains(player.getUniqueId()) && player.hasPermission(NODE);
    }

    /**
     * Flips the player's bypass and returns its new state.
     */
    public static boolean toggle(final Player player) {
        final UUID uuid = player.getUniqueId();
        if (armed.remove(uuid)) {
            return false;
        }
        armed.add(uuid);
        return true;
    }

    /**
     * Drop a player's state on quit, so bypass never survives a session and the set cannot grow
     * without bound.
     */
    public static void clear(final UUID uuid) {
        armed.remove(uuid);
    }
}
