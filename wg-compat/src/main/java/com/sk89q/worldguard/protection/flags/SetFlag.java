// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import java.util.*;

/**
 * A flag whose value is a set of values of a sub-flag's type.
 *
 * @param <T> the element type
 */
public class SetFlag<T> extends Flag<Set<T>> {

    private final Flag<T> subFlag;

    public SetFlag(final String name, final Flag<T> subFlag) {
        super(name);
        this.subFlag = subFlag;
    }

    public SetFlag(final String name, final RegionGroup defaultGroup, final Flag<T> subFlag) {
        super(name, defaultGroup);
        this.subFlag = subFlag;
    }

    public Flag<T> getType() {
        return subFlag;
    }

    @Override
    public Set<T> parseInput(final FlagContext context) throws InvalidFlagFormat {
        final String input = context.getUserInput().trim();
        if (input.isEmpty()) {
            return null;
        }
        final Set<T> values = new LinkedHashSet<>();
        for (final String token : input.split(",")) {
            final String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            values.add(subFlag.parseInput(context.copyWith(context.getSender(), trimmed, null)));
        }
        return values;
    }

    @Override
    public Set<T> unmarshal(final Object o) {
        if (!(o instanceof Collection<?> collection)) {
            return null;
        }
        final Set<T> values = new LinkedHashSet<>(collection.size());
        for (final Object element : collection) {
            final T value = subFlag.unmarshal(element);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    @Override
    public Object marshal(final Set<T> o) {
        if (o == null) {
            return null;
        }
        final List<Object> marshalled = new ArrayList<>(o.size());
        for (final T value : o) {
            marshalled.add(subFlag.marshal(value));
        }
        return marshalled;
    }
}
