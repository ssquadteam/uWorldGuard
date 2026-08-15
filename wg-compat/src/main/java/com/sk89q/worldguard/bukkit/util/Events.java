// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.util;

import com.sk89q.worldguard.bukkit.event.BulkEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Helpers for firing an event and folding its outcome back into the Bukkit event that triggered it.
 */
public final class Events {

    private Events() {
    }

    public static void fire(final Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * Fire the event and report whether it came back canceled.
     */
    public static <T extends Event & Cancellable> boolean fireAndTestCancel(final T eventToFire) {
        fire(eventToFire);
        return eventToFire.isCancelled();
    }

    /**
     * Fire the event and cancel {@code original} if it comes back canceled.
     *
     * @return whether the original was canceled
     */
    public static <T extends Event & Cancellable> boolean fireToCancel(
        final Cancellable original, final T eventToFire
    ) {
        if (fireAndTestCancel(eventToFire)) {
            original.setCancelled(true);
            return true;
        }
        return false;
    }

    /**
     * Fire a bulk event, cancelling {@code original} only when the listeners denied the batch as a
     * whole rather than filtering individual members out of it.
     */
    public static <T extends Event & Cancellable & BulkEvent> boolean fireBulkEventToCancel(
        final Cancellable original, final T eventToFire
    ) {
        fire(eventToFire);
        if (eventToFire.getExplicitResult() == Event.Result.DENY) {
            original.setCancelled(true);
            return true;
        }
        return false;
    }

    /**
     * Fire an item-use event, denying only the item half of the interaction so the block half is
     * left to its own check.
     */
    public static <T extends Event & Cancellable> boolean fireItemEventToCancel(
        final PlayerInteractEvent original, final T eventToFire
    ) {
        if (fireAndTestCancel(eventToFire)) {
            original.setUseItemInHand(Event.Result.DENY);
            return true;
        }
        return false;
    }

    public static boolean isExplosionCause(final EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    public static boolean isFireCause(final EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
            || cause == EntityDamageEvent.DamageCause.FIRE_TICK
            || cause == EntityDamageEvent.DamageCause.LAVA;
    }

    /**
     * WorldGuard restores a damage statistic that cancelling would otherwise lose. uWorldGuard does
     * not track those statistics, so this is a no-op kept for linkage.
     */
    public static void restoreStatistic(final Entity entity, final EntityDamageEvent.DamageCause cause) {
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.stub("Events.restoreStatistic");
    }
}
