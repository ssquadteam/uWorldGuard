// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.handler.Handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's live session state and the handlers attached to it.
 *
 * <p>uWorldGuard creates real sessions, registers the handlers a consumer asked for, and drives
 * them from its own movement tracker and player tick — see
 * {@code com.tricrotism.uworldguard.wgcompat.SessionDispatch}.
 */
public class Session {

    private final SessionManager manager;
    private final List<Handler> handlers = new ArrayList<>(4);

    private volatile boolean bypassDisabled;

    public Session(final SessionManager manager) {
        this.manager = manager;
    }

    public SessionManager getManager() {
        return manager;
    }

    public void register(final Handler handler) {
        handlers.add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T extends Handler> T getHandler(final Class<T> type) {
        for (int i = 0, n = handlers.size(); i < n; i++) {
            final Handler handler = handlers.get(i);
            if (type.isInstance(handler)) {
                return (T) handler;
            }
        }
        return null;
    }

    public boolean hasBypassDisabled() {
        return bypassDisabled;
    }

    public void setBypassDisabled(final boolean disabled) {
        this.bypassDisabled = disabled;
    }

    public void initialize(final LocalPlayer player) {
        final Location location = player.getLocation();
        final ApplicableRegionSet set = regionsAt(location);
        for (int i = 0, n = handlers.size(); i < n; i++) {
            handlers.get(i).initialize(player, location, set);
        }
    }

    public void uninitialize(final LocalPlayer player) {
        final Location location = player.getLocation();
        final ApplicableRegionSet set = regionsAt(location);
        for (int i = 0, n = handlers.size(); i < n; i++) {
            handlers.get(i).uninitialize(player, location, set);
        }
    }

    public void resetState(final LocalPlayer player) {
        uninitialize(player);
        initialize(player);
    }

    public void tick(final LocalPlayer player) {
        final ApplicableRegionSet set = regionsAt(player.getLocation());
        for (int i = 0, n = handlers.size(); i < n; i++) {
            handlers.get(i).tick(player, set);
        }
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.SESSION_DISPATCHES.increment();
    }

    public boolean isInvincible(final LocalPlayer player) {
        for (int i = 0, n = handlers.size(); i < n; i++) {
            final StateFlag.State state = handlers.get(i).getInvincibility(player);
            if (state != null) {
                return state == StateFlag.State.ALLOW;
            }
        }
        return false;
    }

    public Location testMoveTo(final LocalPlayer player, final Location to, final MoveType moveType) {
        return testMoveTo(player, to, moveType, false);
    }

    /**
     * The location the player should end up at, or {@code null} when no handler objected. A forced
     * move skips the handlers entirely, as does a movement type that cannot be canceled.
     */
    public Location testMoveTo(
        final LocalPlayer player, final Location to, final MoveType moveType, final boolean forced
    ) {
        if (forced) {
            return null;
        }
        return uwgTestMoveTo(player, player.getLocation(), to, moveType);
    }

    /**
     * Internal: the same test with the origin supplied rather than read back off the player, which
     * is what uWorldGuard's polled movement tracker has and a {@code PlayerMoveEvent} gives for
     * free. Runs the handlers' {@code testMoveTo}, then — only when the region set actually changed
     * — their {@code onCrossBoundary}.
     *
     * <p>A movement that cannot be cancelled (a respawn) still runs every handler, so their entry
     * and exit bookkeeping stays correct; only the veto is dropped. Stopping short would leave a
     * handler believing the player is still in the region they respawned out of.
     */
    public Location uwgTestMoveTo(
        final LocalPlayer player, final Location from, final Location to, final MoveType moveType
    ) {
        if (handlers.isEmpty()) {
            return null;
        }
        final boolean cancellable = moveType.isCancellable();
        final ApplicableRegionSet toSet = regionsAt(to);
        for (int i = 0, n = handlers.size(); i < n; i++) {
            if (!handlers.get(i).testMoveTo(player, from, to, toSet, moveType) && cancellable) {
                return from;
            }
        }

        final ApplicableRegionSet fromSet = regionsAt(from);
        if (fromSet.size() == 0 && toSet.size() == 0) {
            com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.SESSION_DISPATCHES.increment();
            return null;
        }
        final Set<ProtectedRegion> toRegions = setOf(toSet);
        final Set<ProtectedRegion> fromRegions = setOf(fromSet);
        if (!fromRegions.equals(toRegions)) {
            final Set<ProtectedRegion> entered = difference(toRegions, fromRegions);
            final Set<ProtectedRegion> exited = difference(fromRegions, toRegions);
            for (int i = 0, n = handlers.size(); i < n; i++) {
                if (!handlers.get(i).onCrossBoundary(player, from, to, toSet, entered, exited, moveType)
                    && cancellable) {
                    return from;
                }
            }
        }

        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.SESSION_DISPATCHES.increment();
        return null;
    }

    private static Set<ProtectedRegion> setOf(final ApplicableRegionSet set) {
        final Set<ProtectedRegion> regions = new HashSet<>(Math.max(2, set.size() * 2));
        for (final ProtectedRegion region : set) {
            regions.add(region);
        }
        return regions;
    }

    /**
     * Always a fresh set: the result is handed to consumer handlers, and returning {@code a} itself
     * when {@code b} is empty would alias the caller's live region set into their hands.
     */
    private static Set<ProtectedRegion> difference(final Set<ProtectedRegion> a, final Set<ProtectedRegion> b) {
        final Set<ProtectedRegion> out = new HashSet<>(a);
        out.removeAll(b);
        return out;
    }

    private static ApplicableRegionSet regionsAt(final Location location) {
        return new RegionQuery().getApplicableRegions(location);
    }
}
