// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

/**
 * Thrown when user input cannot be parsed into a flag value.
 */
public class InvalidFlagFormat extends Exception {

    public InvalidFlagFormat(final String msg) {
        super(msg);
    }
}
