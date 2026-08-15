// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.session.handler.Handler;

/**
 * Owns one {@link Session} per player and the handler factories that populate them.
 *
 * <p>The implementation the shim hands out is
 * {@code com.tricrotism.uworldguard.wgcompat.SessionBridge}.
 */
public interface SessionManager {

    Session get(LocalPlayer player);

    Session getIfPresent(LocalPlayer player);

    /**
     * @deprecated use {@link #get(LocalPlayer)}, which creates on demand.
     */
    @Deprecated
    Session createSession(LocalPlayer player);

    boolean hasBypass(LocalPlayer player, com.sk89q.worldedit.world.World world);

    boolean registerHandler(Handler.Factory<? extends Handler> factory,
                            Handler.Factory<? extends Handler> after);

    boolean unregisterHandler(Handler.Factory<? extends Handler> factory);

    boolean customHandlersRegistered();

    void resetState(LocalPlayer player);

    void resetAllStates();
}
