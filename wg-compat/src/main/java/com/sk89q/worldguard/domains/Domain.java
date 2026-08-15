// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.domains;

import com.sk89q.worldguard.LocalPlayer;

import java.util.UUID;

/**
 * A set of subjects that a region can grant ownership or membership to.
 *
 * <p>Backed by uWorldGuard's {@code com.tricrotism.uworldguard.domain.DefaultDomain}, which stores
 * player UUIDs and lowercase permission-group names.
 */
public interface Domain {

    /**
     * @deprecated uWorldGuard stores members by UUID; names resolve from the server's player cache
     * only and answer {@code false} when the player has never joined.
     */
    @Deprecated
    boolean contains(String playerName);

    boolean contains(UUID uniqueId);

    boolean contains(LocalPlayer player);

    int size();

    void clear();
}
