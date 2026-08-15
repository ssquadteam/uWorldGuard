// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event.entity;

import com.sk89q.worldguard.bukkit.cause.Cause;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/**
 * Fired when an entity is about to be damaged.
 */
public class DamageEntityEvent extends AbstractEntityEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public DamageEntityEvent(final @Nullable Event originalEvent, final Cause cause, final Entity target) {
        super(originalEvent, cause, target);
    }

    @Override
    public Entity getEntity() {
        return super.getEntity();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
