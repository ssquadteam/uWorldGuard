// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldedit.registry.Keyed;
import com.sk89q.worldedit.registry.Registry;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * A flag whose value comes from a WorldEdit registry.
 *
 * <p>The registry is held as an {@link Object} and may be supplied lazily, so this class can be
 * instantiated by {@link Flags}' static initializer on a server with no WorldEdit installed — the
 * WorldEdit types resolve only when the flag is actually used.
 *
 * @param <T> the registered value type
 */
public class RegistryFlag<T extends Keyed> extends Flag<T> {

    private volatile Object registry;

    public RegistryFlag(final String name, final Registry<T> registry) {
        super(name);
        this.registry = registry;
    }

    public RegistryFlag(final String name, final RegionGroup defaultGroup, final Registry<T> registry) {
        super(name, defaultGroup);
        this.registry = registry;
    }

    /**
     * Internal: defers registry resolution until first use. Not part of the WorldGuard API.
     */
    public RegistryFlag(final String name, final RegionGroup defaultGroup, final Supplier<Object> registrySupplier) {
        super(name, defaultGroup);
        this.registry = registrySupplier;
    }

    @SuppressWarnings("unchecked")
    public Registry<T> getRegistry() {
        Object resolved = registry;
        if (resolved instanceof Supplier<?> supplier) {
            resolved = supplier.get();
            registry = resolved;
        }
        return (Registry<T>) resolved;
    }

    @Override
    public T parseInput(final FlagContext context) throws InvalidFlagFormat {
        final T value = lookup(context.getUserInput());
        if (value == null) {
            throw new InvalidFlagFormat("Unknown value '" + context.getUserInput() + "' for " + getName());
        }
        return value;
    }

    @Override
    public T unmarshal(final Object o) {
        return o == null ? null : lookup(String.valueOf(o));
    }

    @Override
    public Object marshal(final T o) {
        return o == null ? null : o.id();
    }

    private T lookup(final String input) {
        final Registry<T> resolved = getRegistry();
        if (resolved == null) {
            return null;
        }
        return resolved.get(input.trim().toLowerCase(Locale.ROOT));
    }
}
