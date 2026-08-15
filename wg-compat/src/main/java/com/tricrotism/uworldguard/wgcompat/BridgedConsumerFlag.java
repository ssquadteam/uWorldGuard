// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import com.tricrotism.uworldguard.flags.Flag;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * An engine flag that delegates to a flag a third-party plugin registered through the WorldGuard
 * API. Values are stored, resolved and displayed by uWorldGuard exactly like a built-in flag; only
 * parsing and marshaling defer to the consumer's implementation.
 *
 * @param <T> the value type
 */
@NullMarked final class BridgedConsumerFlag<T> extends Flag<T> {

    private final com.sk89q.worldguard.protection.flags.Flag<T> delegate;

    BridgedConsumerFlag(final com.sk89q.worldguard.protection.flags.Flag<T> delegate) {
        super(delegate.getName());
        this.delegate = delegate;
    }

    @Override
    public @Nullable T parse(final String input) {
        return parse(input, null);
    }

    /**
     * The sender has to reach the delegate. WorldGuard hands custom flags a {@code FlagContext} and
     * {@code parseInput} implementations routinely call {@code getPlayerSender()} on it — to resolve
     * a location relative to the setter, to check a permission, to name them in an error. Built with
     * no sender, that call throws {@link com.sk89q.worldguard.protection.flags.InvalidFlagFormat}
     * before the flag has looked at the input, so the flag rejects everything, always, with no way to
     * tell that apart from the input genuinely being wrong.
     *
     * <p>Wrapped as a {@code LocalPlayer} rather than adapted to a WorldEdit actor, so
     * {@code getPlayerSender} answers from its first branch instead of re-wrapping.
     */
    @Override
    public @Nullable T parse(final String input, final @Nullable Player sender) {
        final com.sk89q.worldguard.protection.flags.FlagContext.FlagContextBuilder context =
            com.sk89q.worldguard.protection.flags.FlagContext.create().setInput(input);
        if (sender != null) {
            context.setSender((com.sk89q.worldedit.extension.platform.Actor) PlayerWrapping.wrap(sender));
        }
        try {
            return delegate.parseInput(context.build());
        } catch (final com.sk89q.worldguard.protection.flags.InvalidFlagFormat e) {
            CompatDiagnostics.flagParseFailure(getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Read off the delegate's type. Every bridged flag is this one engine class, so the menu's
     * type-based hint has nothing to match on and falls through to "text" — which is wrong for a
     * consumer's integer or set flag, and indistinguishable from a correct answer.
     */
    @Override
    public @Nullable String getValueHint() {
        if (delegate instanceof com.sk89q.worldguard.protection.flags.StateFlag) {
            return "allow / deny";
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.BooleanFlag) {
            return "true / false";
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.NumberFlag) {
            return "a number";
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.SetFlag) {
            return "a comma-separated list";
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.LocationFlag) {
            return "world,x,y,z";
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.EnumFlag<?> enumFlag) {
            return hintFor(constants(enumFlag.getEnumClass()));
        }
        return null;
    }

    /**
     * The closed sets, for tab-completion. Enums are the case this exists for: their values cannot be
     * guessed and are not discoverable from the command line any other way. Uncapped, unlike the
     * hint — completion filters as the operator types, so a long list costs nothing there.
     */
    @Override
    public List<String> getValueSuggestions() {
        if (delegate instanceof com.sk89q.worldguard.protection.flags.StateFlag) {
            return List.of("allow", "deny");
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.BooleanFlag) {
            return List.of("true", "false");
        }
        if (delegate instanceof com.sk89q.worldguard.protection.flags.EnumFlag<?> enumFlag) {
            return constants(enumFlag.getEnumClass());
        }
        return List.of();
    }

    /**
     * Constant names, lowercased — {@code EnumFlag.detectValue} matches case-insensitively and treats
     * {@code -} as {@code _}, so the lowercase spelling is both accepted input and the one that reads
     * like the rest of the command line.
     */
    private static List<String> constants(final Class<? extends Enum<?>> type) {
        final Enum<?>[] values = type.getEnumConstants();
        if (values == null || values.length == 0) {
            return List.of();
        }
        final String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name().toLowerCase(Locale.ROOT);
        }
        return List.of(names);
    }

    /**
     * The same values as a hint, truncated past six: a hint lands in item lore and a chat reply,
     * where an enum with thirty constants would bury the line it sits in.
     */
    private static @Nullable String hintFor(final List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        final int shown = Math.min(values.size(), 6);
        final StringBuilder out = new StringBuilder(48);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(values.get(i));
        }
        if (values.size() > shown) {
            out.append(", … (").append(values.size()).append(" total)");
        }
        return out.toString();
    }

    @Override
    public @Nullable T unmarshal(final Object stored) {
        return delegate.unmarshal(stored);
    }

    @Override
    public Object marshal(final T value) {
        return delegate.marshal(value);
    }
}
