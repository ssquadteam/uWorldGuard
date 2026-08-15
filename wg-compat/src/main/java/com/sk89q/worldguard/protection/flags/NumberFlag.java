// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

/**
 * Shared supertype of the numeric flags, carrying the suggested-value hints used by command
 * completion.
 *
 * @param <T> the numeric value type
 */
public abstract class NumberFlag<T extends Number> extends Flag<T> {

    private static final Number[] NO_SUGGESTIONS = new Number[0];

    private Number[] suggestedValues = NO_SUGGESTIONS;

    protected NumberFlag(final String name) {
        super(name);
    }

    protected NumberFlag(final String name, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    public Number[] getSuggestedValues() {
        return suggestedValues;
    }

    public void setSuggestedValues(final Number[] values) {
        this.suggestedValues = values == null ? NO_SUGGESTIONS : values;
    }
}
