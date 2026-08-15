// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flag whose value is a map of sub-flag values.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class MapFlag<K, V> extends Flag<Map<K, V>> {

    private final Flag<K> keyFlag;
    private final Flag<V> valueFlag;

    public MapFlag(final String name, final Flag<K> keyFlag, final Flag<V> valueFlag) {
        super(name);
        this.keyFlag = keyFlag;
        this.valueFlag = valueFlag;
    }

    public MapFlag(final String name, final RegionGroup defaultGroup, final Flag<K> keyFlag, final Flag<V> valueFlag) {
        super(name, defaultGroup);
        this.keyFlag = keyFlag;
        this.valueFlag = valueFlag;
    }

    public Flag<K> getKeyFlag() {
        return keyFlag;
    }

    public Flag<V> getValueFlag() {
        return valueFlag;
    }

    @Override
    public Map<K, V> parseInput(final FlagContext context) throws InvalidFlagFormat {
        final String input = context.getUserInput().trim();
        if (input.isEmpty()) {
            return null;
        }
        final Map<K, V> values = new LinkedHashMap<>();
        for (final String entry : input.split(",")) {
            final int split = entry.indexOf(':');
            if (split < 0) {
                throw new InvalidFlagFormat("Expected 'key:value' pairs but got '" + entry + "'");
            }
            final K key = keyFlag.parseInput(context.copyWith(context.getSender(), entry.substring(0, split).trim(), null));
            final V value = valueFlag.parseInput(context.copyWith(context.getSender(), entry.substring(split + 1).trim(), null));
            values.put(key, value);
        }
        return values;
    }

    @Override
    public Map<K, V> unmarshal(final Object o) {
        if (!(o instanceof Map<?, ?> raw)) {
            return null;
        }
        final Map<K, V> values = new LinkedHashMap<>(raw.size());
        for (final Map.Entry<?, ?> entry : raw.entrySet()) {
            final K key = keyFlag.unmarshal(entry.getKey());
            final V value = valueFlag.unmarshal(entry.getValue());
            if (key != null && value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    @Override
    public Object marshal(final Map<K, V> o) {
        if (o == null) {
            return null;
        }
        final Map<Object, Object> marshalled = new LinkedHashMap<>(o.size());
        for (final Map.Entry<K, V> entry : o.entrySet()) {
            marshalled.put(keyFlag.marshal(entry.getKey()), valueFlag.marshal(entry.getValue()));
        }
        return marshalled;
    }
}
