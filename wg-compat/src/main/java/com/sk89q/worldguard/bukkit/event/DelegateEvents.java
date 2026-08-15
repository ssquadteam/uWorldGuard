// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event;

import org.bukkit.event.Event;

/**
 * Fluent helpers for configuring an event before it is fired.
 */
public final class DelegateEvents {

    private DelegateEvents() {
    }

    public static <T extends DelegateEvent> T setSilent(final T event) {
        return setSilent(event, true);
    }

    public static <T extends DelegateEvent> T setSilent(final T event, final boolean silent) {
        event.setSilent(silent);
        return event;
    }

    public static <T extends Handleable> T setAllowed(final T event, final boolean allowed) {
        event.setResult(allowed ? Event.Result.ALLOW : Event.Result.DENY);
        return event;
    }
}
