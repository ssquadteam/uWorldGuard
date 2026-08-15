// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session;

/**
 * Why a player moved, as handed to a {@link com.sk89q.worldguard.session.handler.Handler}.
 *
 * <p>A movement that cannot be cancelled (a respawn, a plugin teleport WorldGuard must not veto)
 * makes a handler's veto advisory: the session reports it, but nothing rolls the move back.
 */
public enum MoveType {

    EMBARK(true, false),
    GLIDE(true, false),
    MOVE(true, false),
    OTHER_CANCELLABLE(true, false),
    OTHER_NON_CANCELLABLE(false, false),
    RESPAWN(false, true),
    RIDE(true, false),
    SWIM(true, false),
    TELEPORT(true, true);

    private final boolean cancellable;
    private final boolean teleport;

    MoveType(final boolean cancellable, final boolean teleport) {
        this.cancellable = cancellable;
        this.teleport = teleport;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public boolean isTeleport() {
        return teleport;
    }
}
