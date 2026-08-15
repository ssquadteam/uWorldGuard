// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session.handler;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;

import java.util.Set;

/**
 * A per-player, per-session piece of movement logic.
 *
 * <p>Consumers subclass this and register a {@link Factory} with the
 * {@link com.sk89q.worldguard.session.SessionManager}. uWorldGuard drives them from its own movement
 * tracker and player tick: {@link #tick} fires once a second, riding the per-player tick service.
 * {@link MoveType#OTHER_CANCELLABLE} and {@link MoveType#OTHER_NON_CANCELLABLE} are never produced;
 * every other constant is.
 */
public abstract class Handler {

    private final Session session;

    protected Handler(final Session session) {
        this.session = session;
    }

    public Session getSession() {
        return session;
    }

    public void initialize(final LocalPlayer player, final Location current, final ApplicableRegionSet set) {
    }

    public void uninitialize(final LocalPlayer player, final Location current, final ApplicableRegionSet set) {
    }

    public boolean testMoveTo(final LocalPlayer player, final Location from, final Location to,
                              final ApplicableRegionSet toSet, final MoveType moveType) {
        return true;
    }

    public boolean onCrossBoundary(final LocalPlayer player, final Location from, final Location to,
                                   final ApplicableRegionSet toSet, final Set<ProtectedRegion> entered,
                                   final Set<ProtectedRegion> exited, final MoveType moveType) {
        return true;
    }

    public void tick(final LocalPlayer player, final ApplicableRegionSet set) {
    }

    public StateFlag.State getInvincibility(final LocalPlayer player) {
        return null;
    }

    /**
     * Creates one handler per session. Registered once with the session manager; the manager owns
     * the instances it produces.
     */
    public abstract static class Factory<T extends Handler> {

        public Factory() {
        }

        public abstract T create(Session session);
    }
}
