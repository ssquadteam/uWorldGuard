// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.session.handler;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;

import java.util.Set;

/**
 * A {@link Handler} that tracks one flag's value for the player and reports transitions: the value
 * appearing, changing, or going away as the player crosses region boundaries.
 *
 * @param <T> the flag's value type
 */
public abstract class FlagValueChangeHandler<T> extends Handler {

    private final Flag<T> flag;

    private T lastValue;

    protected FlagValueChangeHandler(final Session session, final Flag<T> flag) {
        super(session);
        this.flag = flag;
    }

    protected abstract void onInitialValue(LocalPlayer player, ApplicableRegionSet set, T value);

    protected abstract boolean onSetValue(LocalPlayer player, Location from, Location to,
                                          ApplicableRegionSet toSet, T currentValue, T lastValue,
                                          MoveType moveType);

    protected abstract boolean onAbsentValue(LocalPlayer player, Location from, Location to,
                                             ApplicableRegionSet toSet, T lastValue, MoveType moveType);

    protected void onClearValue(final LocalPlayer player, final ApplicableRegionSet set) {
    }

    @Override
    public final void initialize(final LocalPlayer player, final Location current,
                                 final ApplicableRegionSet set) {
        lastValue = set.queryValue(player, flag);
        onInitialValue(player, set, lastValue);
    }

    @Override
    public final void uninitialize(final LocalPlayer player, final Location current,
                                   final ApplicableRegionSet set) {
        lastValue = null;
        onClearValue(player, set);
    }

    @Override
    public boolean onCrossBoundary(final LocalPlayer player, final Location from, final Location to,
                                   final ApplicableRegionSet toSet, final Set<ProtectedRegion> entered,
                                   final Set<ProtectedRegion> exited, final MoveType moveType) {
        final T currentValue = toSet.queryValue(player, flag);
        final T previous = lastValue;
        lastValue = currentValue;
        if (currentValue == null) {
            return onAbsentValue(player, from, to, toSet, previous, moveType);
        }
        if (currentValue.equals(previous)) {
            return true;
        }
        return onSetValue(player, from, to, toSet, currentValue, previous, moveType);
    }
}
