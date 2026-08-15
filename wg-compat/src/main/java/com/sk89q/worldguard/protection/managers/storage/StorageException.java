// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.managers.storage;

/**
 * Thrown when region storage fails.
 *
 * <p>uWorldGuard owns persistence and never surfaces a failure through the shim, so nothing here
 * throws this. It exists so consumer bytecode that declares {@code catch (StorageException)} around
 * {@code RegionManager.save()} still verifies.
 */
public class StorageException extends Exception {

    public StorageException() {
    }

    public StorageException(final String message) {
        super(message);
    }

    public StorageException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public StorageException(final Throwable cause) {
        super(cause);
    }
}
