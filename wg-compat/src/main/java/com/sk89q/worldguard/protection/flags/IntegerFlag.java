// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

/**
 * An integer flag, bridging to uWorldGuard's {@code com.tricrotism.uworldguard.flags.IntegerFlag}.
 */
public class IntegerFlag extends NumberFlag<Integer> {

    public IntegerFlag(final String name) {
        super(name);
    }

    public IntegerFlag(final String name, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    @Override
    public Integer parseInput(final FlagContext context) throws InvalidFlagFormat {
        return context.getUserInputAsInt();
    }

    @Override
    public Integer unmarshal(final Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(o).trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Object marshal(final Integer o) {
        return o;
    }
}
