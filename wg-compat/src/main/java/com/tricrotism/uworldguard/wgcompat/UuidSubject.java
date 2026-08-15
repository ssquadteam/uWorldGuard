// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Implemented by shim subjects whose association is decided by a player UUID.
 *
 * <p>A {@code RegionAssociable} that carries one can be answered by the engine's UUID-aware
 * resolution, which is the fast path: no per-region callback into consumer code. Foreign
 * associables fall back to {@link FlagQueryAlgorithms}.
 */
@NullMarked
public interface UuidSubject {

    UUID uwgUuid();
}
