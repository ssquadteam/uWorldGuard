// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import java.util.Locale;

/**
 * A flag whose value is an enum constant.
 *
 * @param <T> the enum type
 */
public class EnumFlag<T extends Enum<T>> extends Flag<T> {

    private final Class<T> enumClass;

    public EnumFlag(final String name, final Class<T> enumClass) {
        super(name);
        this.enumClass = enumClass;
    }

    public EnumFlag(final String name, final Class<T> enumClass, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
        this.enumClass = enumClass;
    }

    public Class<T> getEnumClass() {
        return enumClass;
    }

    public T detectValue(final String input) {
        if (input == null) {
            return null;
        }
        final String needle = input.trim().replace('-', '_').replace(' ', '_');
        for (final T constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(needle)) {
                return constant;
            }
        }
        final String lower = needle.toLowerCase(Locale.ROOT);
        for (final T constant : enumClass.getEnumConstants()) {
            if (constant.name().toLowerCase(Locale.ROOT).startsWith(lower)) {
                return constant;
            }
        }
        return null;
    }

    @Override
    public T parseInput(final FlagContext context) throws InvalidFlagFormat {
        final T value = detectValue(context.getUserInput());
        if (value == null) {
            throw new InvalidFlagFormat("Unknown value '" + context.getUserInput() + "' for " + getName());
        }
        return value;
    }

    @Override
    public T unmarshal(final Object o) {
        return o == null ? null : detectValue(String.valueOf(o));
    }

    @Override
    public Object marshal(final T o) {
        return o == null ? null : o.name();
    }
}
