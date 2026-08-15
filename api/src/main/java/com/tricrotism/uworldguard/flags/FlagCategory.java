package com.tricrotism.uworldguard.flags;

import org.jspecify.annotations.NullMarked;

/**
 * Coarse grouping used to organise flags in menus and command output. Mirrors the
 * sections that built-in flags are declared under in {@link Flags}, plus {@link #EXTENSION}
 * for the flags that arrive from outside.
 */
@NullMarked
public enum FlagCategory {

    PROTECTION("Protection"),
    ENVIRONMENT("Environment"),
    MOBS("Mobs & Explosions"),
    MOVEMENT("Movement"),
    MESSAGES("Messages & Effects"),
    ITEMS("Items & Blocks"),
    ENTRY("Entry & Actions"),
    PLAYER("Player State"),

    /**
     * Flags another plugin registered through the WorldGuard API. What such a flag governs is
     * unknowable from here — the registry call carries a name and a type and nothing else — so they
     * are grouped by where they came from rather than by what they do. Declared last so the built-in
     * sections keep their order in the menu.
     */
    EXTENSION("Extension");

    private final String displayName;

    FlagCategory(final String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
