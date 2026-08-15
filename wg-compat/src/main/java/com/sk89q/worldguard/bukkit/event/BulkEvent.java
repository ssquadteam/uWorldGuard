// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event;

import org.bukkit.event.Event;

/**
 * An event covering several objects at once, where a listener's decision may apply to the whole
 * batch rather than to individual members.
 */
public interface BulkEvent {

    Event.Result getExplicitResult();
}
