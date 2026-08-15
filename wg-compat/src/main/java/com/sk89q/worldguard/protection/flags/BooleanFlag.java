// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import java.util.Locale;

/**
 * A boolean flag, bridging to uWorldGuard's {@code com.tricrotism.uworldguard.flags.BooleanFlag}.
 */
public class BooleanFlag extends Flag<Boolean> {

    public BooleanFlag(final String name) {
        super(name);
    }

    public BooleanFlag(final String name, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    @Override
    public Boolean parseInput(final FlagContext context) throws InvalidFlagFormat {
        final Boolean value = fromString(context.getUserInput());
        if (value == null) {
            throw new InvalidFlagFormat("Expected 'true' or 'false' but got '" + context.getUserInput() + "'");
        }
        return value;
    }

    @Override
    public Boolean unmarshal(final Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o == null ? null : fromString(String.valueOf(o));
    }

    @Override
    public Object marshal(final Boolean o) {
        return o;
    }

    private static Boolean fromString(final String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "allow" -> Boolean.TRUE;
            case "false", "no", "off", "deny" -> Boolean.FALSE;
            default -> null;
        };
    }
}
