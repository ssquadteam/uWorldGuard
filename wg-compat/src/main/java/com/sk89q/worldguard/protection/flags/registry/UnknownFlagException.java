// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags.registry;

/**
 * Thrown when a stored flag name has no registered flag.
 */
public class UnknownFlagException extends Exception {

    public UnknownFlagException(final String message) {
        super(message);
    }
}
