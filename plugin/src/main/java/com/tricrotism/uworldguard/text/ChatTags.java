package com.tricrotism.uworldguard.text;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player chat prefix/suffix currently in effect from the chat-prefix / chat-suffix region flags.
 * Written on the player's region thread as they move and read from the async chat thread, so both
 * maps are concurrent. Absence of an entry means no override.
 */
@NullMarked
public final class ChatTags {

    private final Map<UUID, String> prefixes = new ConcurrentHashMap<>();
    private final Map<UUID, String> suffixes = new ConcurrentHashMap<>();

    public void setPrefix(final UUID uuid, final @Nullable String value) {
        if (value == null || value.isBlank()) {
            prefixes.remove(uuid);
        } else {
            prefixes.put(uuid, value);
        }
    }

    public void setSuffix(final UUID uuid, final @Nullable String value) {
        if (value == null || value.isBlank()) {
            suffixes.remove(uuid);
        } else {
            suffixes.put(uuid, value);
        }
    }

    public @Nullable String prefix(final UUID uuid) {
        return prefixes.get(uuid);
    }

    public @Nullable String suffix(final UUID uuid) {
        return suffixes.get(uuid);
    }

    public void clear(final UUID uuid) {
        prefixes.remove(uuid);
        suffixes.remove(uuid);
    }

    public boolean isEmpty() {
        return prefixes.isEmpty() && suffixes.isEmpty();
    }
}
