// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event;

import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Base class for the events uWorldGuard fires alongside its own protection decisions, wrapping the
 * Bukkit event that triggered them together with the {@link Cause} chain behind it.
 *
 * <p>A listener denies an action by setting the result to {@link Event.Result#DENY} (or cancelling).
 * Unlike WorldGuard, setting {@link Event.Result#ALLOW} does not override a denial uWorldGuard has
 * already made — the deny wins.
 */
public abstract class DelegateEvent extends Event implements Cancellable, Handleable {

    private final @Nullable Event originalEvent;
    private final Cause cause;
    private Event.Result result = Event.Result.DEFAULT;
    private boolean silent;

    protected DelegateEvent(final @Nullable Event originalEvent, final Cause cause) {
        this.originalEvent = originalEvent;
        this.cause = cause;
    }

    /**
     * The Bukkit event this one accompanies, or {@code null} if the action had no single event.
     */
    public @Nullable Event getOriginalEvent() {
        return originalEvent;
    }

    public Cause getCause() {
        return cause;
    }

    @Override
    public Event.Result getResult() {
        return result;
    }

    @Override
    public void setResult(final Event.Result result) {
        this.result = result;
    }

    @Override
    public boolean isCancelled() {
        return result == Event.Result.DENY;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        setResult(cancel ? Event.Result.DENY : Event.Result.ALLOW);
    }

    public DelegateEvent setAllowed(final boolean allowed) {
        setResult(allowed ? Event.Result.ALLOW : Event.Result.DENY);
        return this;
    }

    /**
     * Whether the acting player should be told the action was denied.
     */
    public boolean isSilent() {
        return silent;
    }

    public DelegateEvent setSilent(final boolean silent) {
        this.silent = silent;
        return this;
    }

    /**
     * The flags uWorldGuard consulted when it made its own decision about this action.
     */
    public List<StateFlag> getRelevantFlags() {
        return List.of();
    }
}
