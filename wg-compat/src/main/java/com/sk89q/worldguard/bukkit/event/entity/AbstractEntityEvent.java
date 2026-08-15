// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event.entity;

import com.google.common.base.Predicate;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

/**
 * Shared state for the entity events: the entity involved (where there is one) and the location
 * the action happens at.
 */
public abstract class AbstractEntityEvent extends DelegateEvent {

    private final @Nullable Entity entity;
    private final Location target;

    protected AbstractEntityEvent(final @Nullable Event originalEvent, final Cause cause, final Entity target) {
        super(originalEvent, cause);
        this.entity = target;
        this.target = target.getLocation();
    }

    protected AbstractEntityEvent(final @Nullable Event originalEvent, final Cause cause, final Location target) {
        super(originalEvent, cause);
        this.entity = null;
        this.target = target;
    }

    public @Nullable Entity getEntity() {
        return entity;
    }

    public Location getTarget() {
        return target;
    }

    public World getWorld() {
        return target.getWorld();
    }

    /**
     * Deny the event when the predicate rejects this entity's location.
     *
     * @return whether the location was accepted
     */
    public boolean filter(final Predicate<Location> predicate, final boolean cancelEventOnFalse) {
        final boolean accepted = predicate.apply(target);
        if (!accepted && cancelEventOnFalse) {
            setResult(Event.Result.DENY);
        }
        return accepted;
    }
}
