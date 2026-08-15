// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

/**
 * A double flag, bridging to uWorldGuard's {@code com.tricrotism.uworldguard.flags.DoubleFlag}.
 */
public class DoubleFlag extends NumberFlag<Double> {

    public DoubleFlag(final String name) {
        super(name);
    }

    public DoubleFlag(final String name, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    @Override
    public Double parseInput(final FlagContext context) throws InvalidFlagFormat {
        return context.getUserInputAsDouble();
    }

    @Override
    public Double unmarshal(final Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(o).trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Object marshal(final Double o) {
        return o;
    }
}
