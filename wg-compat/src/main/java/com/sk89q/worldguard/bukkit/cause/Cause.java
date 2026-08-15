// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.cause;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The chain of objects responsible for an action, most direct first — a player, the arrow they
 * shot, the dispenser that fired it. Listeners use it to attribute an action to a player even when
 * the immediate actor is a projectile or a block.
 */
public final class Cause {

    private static final Cause UNKNOWN = new Cause(List.of(), false);
    private static final String PARENT_CAUSE_KEY = "uworldguard-wg-compat-parent-cause";

    private final List<Object> causes;
    private final boolean indirect;

    private Cause(final List<Object> causes, final boolean indirect) {
        this.causes = causes;
        this.indirect = indirect;
    }

    /**
     * Build a cause from the responsible objects, most direct first. Nulls are ignored, and any
     * object with a tracked parent cause contributes that parent too.
     */
    public static Cause create(@Nullable final Object... cause) {
        if (cause == null || cause.length == 0) {
            return UNKNOWN;
        }
        final List<Object> resolved = new ArrayList<>(cause.length);
        boolean indirect = false;
        for (final Object object : cause) {
            if (object == null) {
                continue;
            }
            resolved.add(object);
            final Object parent = trackedParent(object);
            if (parent != null) {
                resolved.add(parent);
                indirect = true;
            }
        }
        return resolved.isEmpty() ? UNKNOWN : new Cause(List.copyOf(resolved), indirect);
    }

    public static Cause unknown() {
        return UNKNOWN;
    }

    /**
     * Remember that {@code parent} is ultimately responsible for {@code target}, so a later event
     * caused by {@code target} can still be attributed to it.
     */
    public static void trackParentCause(final Metadatable target, final Object parent) {
        target.setMetadata(PARENT_CAUSE_KEY,
            new org.bukkit.metadata.FixedMetadataValue(owningPlugin(), parent));
    }

    public static void untrackParentCause(final Metadatable target) {
        target.removeMetadata(PARENT_CAUSE_KEY, owningPlugin());
    }

    private static JavaPlugin owningPlugin() {
        return com.tricrotism.uworldguard.wgcompat.WgCompatBridge.plugin();
    }

    private static @Nullable Object trackedParent(final Object object) {
        if (!(object instanceof Metadatable metadatable)) {
            return null;
        }
        final List<MetadataValue> values = metadatable.getMetadata(PARENT_CAUSE_KEY);
        return values.isEmpty() ? null : values.get(0).value();
    }

    /**
     * Whether anything at all is known about who caused the action.
     */
    public boolean isKnown() {
        return !causes.isEmpty();
    }

    /**
     * Whether the direct actor is standing in for something else that was tracked as the real cause.
     */
    public boolean isIndirect() {
        return indirect;
    }

    /**
     * The least direct cause — the origin of the chain.
     */
    public @Nullable Object getRootCause() {
        return causes.isEmpty() ? null : causes.get(causes.size() - 1);
    }

    public @Nullable Player getFirstPlayer() {
        for (final Object object : causes) {
            if (object instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    public @Nullable Entity getFirstEntity() {
        for (final Object object : causes) {
            if (object instanceof Entity entity) {
                return entity;
            }
        }
        return null;
    }

    public @Nullable Entity getFirstNonPlayerEntity() {
        for (final Object object : causes) {
            if (object instanceof Entity entity && !(object instanceof Player)) {
                return entity;
            }
        }
        return null;
    }

    public @Nullable Block getFirstBlock() {
        for (final Object object : causes) {
            if (object instanceof Block block) {
                return block;
            }
        }
        return null;
    }

    /**
     * The first of {@code types} present in this chain, or {@code null} if none of them are.
     */
    public @Nullable EntityType find(final EntityType... types) {
        for (final Object object : causes) {
            if (object instanceof Entity entity) {
                for (final EntityType type : types) {
                    if (entity.getType() == type) {
                        return type;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return causes.isEmpty() ? "Cause{unknown}" : "Cause" + Arrays.toString(causes.toArray());
    }
}
