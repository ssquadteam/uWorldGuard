// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags.registry;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StringFlag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The flag registry consumers see. Built-in WorldGuard flags are seeded from {@link Flags};
 * uWorldGuard engine flags with no WorldGuard counterpart are wrapped lazily on lookup, so
 * introspecting consumers see the whole flag universe.
 *
 * <p>Registering a custom flag installs it in the uWorldGuard engine, so it persists, resolves and
 * appears in uWorldGuard's own menus and commands.
 */
public class SimpleFlagRegistry implements FlagRegistry {

    private final Map<String, Flag<?>> flags = new ConcurrentHashMap<>(256);

    /**
     * The names WorldGuard itself defines. Fixed after construction, and kept separate from
     * {@link #flags} because that map also accumulates lazily-wrapped engine flags — by the time a
     * conflict is reported it can no longer say which kind of name was hit.
     */
    private final Set<String> worldGuardNames;

    private volatile boolean initialized = true;

    public SimpleFlagRegistry() {
        final Set<String> builtIn = new HashSet<>(256);
        for (final Flag<?> flag : Flags.uwgAll()) {
            final String key = flag.getName().toLowerCase(Locale.ROOT);
            flags.put(key, flag);
            builtIn.add(key);
        }
        this.worldGuardNames = Set.copyOf(builtIn);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(final boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public void register(final Flag<?> flag) throws FlagConflictException {
        final String name = flag.getName();
        if (!Flag.isValidName(name)) {
            throw new IllegalArgumentException("Invalid flag name: " + name);
        }
        final String key = name.toLowerCase(Locale.ROOT);
        if (flags.containsKey(key) || com.tricrotism.uworldguard.flags.WgFlagNames.resolve(name) != null) {
            throw conflict(name, key);
        }
        try {
            com.tricrotism.uworldguard.wgcompat.FlagBridge.registerConsumerFlag(flag);
        } catch (final IllegalStateException e) {
            throw conflict(name, key);
        }
        if (flags.putIfAbsent(key, flag) != null) {
            throw conflict(name, key);
        }
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.FLAG_REGISTRATIONS.increment();
    }

    /**
     * Builds the conflict, and logs it. A caller that used {@link #registerAll} never sees the
     * exception — WorldGuard's contract is to skip conflicts silently — which leaves an operator with
     * a flag that is simply absent and nothing to explain it.
     *
     * <p>The distinction in the message matters: uWorldGuard's flag set is a superset of
     * WorldGuard's, so a name WorldGuard never claimed can still be taken here. A plugin that
     * registers cleanly on WorldGuard and conflicts on uWorldGuard has hit that, and neither its
     * author nor the operator can tell from "already registered" alone.
     */
    private FlagConflictException conflict(final String name, final String key) {
        final StringBuilder message = new StringBuilder(128)
            .append("A flag named '").append(name).append("' is already registered");
        if (!worldGuardNames.contains(key)) {
            message.append(" — as a built-in uWorldGuard flag, which WorldGuard does not define.")
                .append(" This registration succeeds on WorldGuard and conflicts here. Rename the")
                .append(" flag to something plugin-specific, or read the existing '").append(key)
                .append("' flag instead of registering your own.");
        }
        final String text = message.toString();
        Logger.getLogger("uWorldGuard").warning("[wg-compat] " + text);
        return new FlagConflictException(text);
    }

    @Override
    public void registerAll(final Collection<Flag<?>> toRegister) {
        for (final Flag<?> flag : toRegister) {
            try {
                register(flag);
            } catch (final FlagConflictException ignored) {
                // Matching WorldGuard: a conflicting flag in a bulk registration is skipped.
            }
        }
    }

    @Override
    public Flag<?> get(final String name) {
        final String key = name.toLowerCase(Locale.ROOT);
        final Flag<?> known = flags.get(key);
        if (known != null) {
            return known;
        }
        final com.tricrotism.uworldguard.flags.Flag<?> engine =
            com.tricrotism.uworldguard.flags.WgFlagNames.resolve(name);
        if (engine == null) {
            return null;
        }
        final Flag<?> wrapped = (Flag<?>) com.tricrotism.uworldguard.wgcompat.FlagBridge.wrapEngineFlag(engine);
        final Flag<?> existing = flags.putIfAbsent(key, wrapped);
        return existing == null ? wrapped : existing;
    }

    @Override
    public List<Flag<?>> getAll() {
        wrapEngineOnlyFlags();
        return List.copyOf(flags.values());
    }

    @Override
    public Map<Flag<?>, Object> unmarshal(final Map<String, Object> rawValues, final boolean createUnknown) {
        final Map<Flag<?>, Object> values = new LinkedHashMap<>(rawValues.size());
        for (final Map.Entry<String, Object> entry : rawValues.entrySet()) {
            Flag<?> flag = get(entry.getKey());
            if (flag == null) {
                if (!createUnknown) {
                    continue;
                }
                flag = new StringFlag(entry.getKey());
            }
            final Object value = flag.unmarshal(entry.getValue());
            if (value != null) {
                values.put(flag, value);
            }
        }
        return values;
    }

    @Override
    public int size() {
        wrapEngineOnlyFlags();
        return flags.size();
    }

    @Override
    public Iterator<Flag<?>> iterator() {
        return getAll().iterator();
    }

    private void wrapEngineOnlyFlags() {
        final List<com.tricrotism.uworldguard.flags.Flag<?>> engineFlags =
            com.tricrotism.uworldguard.flags.Flags.all();
        for (int i = 0; i < engineFlags.size(); i++) {
            final com.tricrotism.uworldguard.flags.Flag<?> engine = engineFlags.get(i);
            final Flag<?> wrapped = (Flag<?>) com.tricrotism.uworldguard.wgcompat.FlagBridge.wrapEngineFlag(engine);
            flags.putIfAbsent(wrapped.getName().toLowerCase(Locale.ROOT), wrapped);
        }
    }

    @Override
    public String toString() {
        return new ArrayList<>(flags.keySet()).toString();
    }
}
