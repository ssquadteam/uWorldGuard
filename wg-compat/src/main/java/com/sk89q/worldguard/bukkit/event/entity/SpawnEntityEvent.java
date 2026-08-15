// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event.entity;

import com.sk89q.worldguard.bukkit.cause.Cause;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/**
 * Fired when an entity is about to spawn. The entity itself may not exist yet, in which case only
 * its type and location are known.
 */
public class SpawnEntityEvent extends AbstractEntityEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final EntityType effectiveType;

    public SpawnEntityEvent(final @Nullable Event originalEvent, final Cause cause, final Entity target) {
        super(originalEvent, cause, target);
        this.effectiveType = target.getType();
    }

    public SpawnEntityEvent(
        final @Nullable Event originalEvent, final Cause cause, final Location location,
        final EntityType type
    ) {
        super(originalEvent, cause, location);
        this.effectiveType = type;
    }

    public EntityType getEffectiveType() {
        return effectiveType;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
