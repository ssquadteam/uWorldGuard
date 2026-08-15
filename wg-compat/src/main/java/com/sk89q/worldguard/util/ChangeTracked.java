// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.util;

/**
 * Something that tracks whether it has been modified since it was last persisted.
 *
 * <p>uWorldGuard tracks dirtiness per world rather than per object, so the flag carried here is
 * advisory: every mutation made through the shim also marks the owning engine region manager dirty,
 * which is what actually triggers a save.
 */
public interface ChangeTracked {

    boolean isDirty();

    void setDirty(boolean dirty);
}
