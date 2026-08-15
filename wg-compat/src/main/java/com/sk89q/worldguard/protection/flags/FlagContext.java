// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldedit.extension.platform.Actor;

import java.util.HashMap;
import java.util.Map;

/**
 * The parse context handed to {@link Flag#parseInput(FlagContext)}: who is setting the flag, the
 * raw user input, and an arbitrary bag of extra values.
 */
public final class FlagContext {

    private final Actor sender;
    private final String input;
    private final Map<String, Object> values;

    private FlagContext(final Actor sender, final String input, final Map<String, Object> values) {
        this.sender = sender;
        this.input = input;
        this.values = values == null ? new HashMap<>(4) : new HashMap<>(values);
    }

    public static FlagContextBuilder create() {
        return new FlagContextBuilder();
    }

    public Actor getSender() {
        return sender;
    }

    /**
     * The sender as a {@link com.sk89q.worldguard.LocalPlayer}.
     *
     * @throws InvalidFlagFormat when the flag was not set by a player
     */
    public com.sk89q.worldguard.LocalPlayer getPlayerSender() throws InvalidFlagFormat {
        if (sender instanceof com.sk89q.worldguard.LocalPlayer local) {
            return local;
        }
        if (sender instanceof com.sk89q.worldedit.entity.Player player) {
            return (com.sk89q.worldguard.LocalPlayer) com.tricrotism.uworldguard.wgcompat.PlayerWrapping
                .wrap(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player));
        }
        throw new InvalidFlagFormat("Not a player");
    }

    public String getUserInput() {
        return input;
    }

    public Integer getUserInputAsInt() throws InvalidFlagFormat {
        try {
            return Integer.valueOf(input.trim());
        } catch (final NumberFormatException | NullPointerException e) {
            throw new InvalidFlagFormat("Not a number: " + input);
        }
    }

    public Double getUserInputAsDouble() throws InvalidFlagFormat {
        try {
            return Double.valueOf(input.trim());
        } catch (final NumberFormatException | NullPointerException e) {
            throw new InvalidFlagFormat("Not a number: " + input);
        }
    }

    public Object get(final String name) {
        return values.get(name);
    }

    public Object get(final String name, final Object defaultValue) {
        final Object value = values.get(name);
        return value == null ? defaultValue : value;
    }

    public void put(final String name, final Object value) {
        values.put(name, value);
    }

    public FlagContext copyWith(final Actor commandSender, final String s, final Map<String, Object> newValues) {
        final Map<String, Object> merged = new HashMap<>(values);
        if (newValues != null) {
            merged.putAll(newValues);
        }
        return new FlagContext(commandSender == null ? sender : commandSender, s == null ? input : s, merged);
    }

    public static class FlagContextBuilder {

        private Actor sender;
        private String input;
        private final Map<String, Object> values = new HashMap<>(4);

        public FlagContextBuilder setSender(final Actor sender) {
            this.sender = sender;
            return this;
        }

        public FlagContextBuilder setInput(final String input) {
            this.input = input;
            return this;
        }

        public FlagContextBuilder setObject(final String key, final Object value) {
            values.put(key, value);
            return this;
        }

        public boolean tryAddToMap(final String key, final Object value) {
            return values.putIfAbsent(key, value) == null;
        }

        public FlagContext build() {
            return new FlagContext(sender, input, values);
        }
    }
}
