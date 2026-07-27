package com.tricrotism.uworldguard.flags;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * A flag whose value is a set of lower-cased strings — used by the command-list flags
 * {@code blocked-cmds} and {@code allowed-cmds}. Parsing accepts a comma-separated list; a leading
 * {@code /} on any entry is stripped, so {@code /home, /tp} and {@code home, tp} are equivalent.
 *
 * <p>Values are normalised to lower case on the way in so matching a typed command needs no
 * per-check case folding of the stored set.
 */
@NullMarked
public final class StringSetFlag extends Flag<Set<String>> {

    public StringSetFlag(final String name) {
        super(name);
    }

    @Override
    public @Nullable Set<String> parse(final String input) {
        final Set<String> values = new LinkedHashSet<>();
        for (final String raw : input.split(",")) {
            final String token = normalise(raw);
            if (!token.isEmpty()) {
                values.add(token);
            }
        }
        return values.isEmpty() ? null : values;
    }

    @Override
    public @Nullable Set<String> unmarshal(final Object stored) {
        if (!(stored instanceof Collection<?> list)) {
            return parse(String.valueOf(stored));
        }
        final Set<String> values = new LinkedHashSet<>();
        for (final Object element : list) {
            final String token = normalise(String.valueOf(element));
            if (!token.isEmpty()) {
                values.add(token);
            }
        }
        return values.isEmpty() ? null : values;
    }

    @Override
    public Object marshal(final Set<String> value) {
        return new ArrayList<>(value);
    }

    private static String normalise(final String raw) {
        final String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("/") ? trimmed.substring(1).trim() : trimmed;
    }
}
