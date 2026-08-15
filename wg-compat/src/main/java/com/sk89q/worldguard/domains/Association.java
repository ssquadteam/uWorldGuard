// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.domains;

/**
 * A subject's relationship to a region. Mirrors uWorldGuard's
 * {@code com.tricrotism.uworldguard.domain.Association}.
 */
public enum Association {

    OWNER,
    MEMBER,
    NON_MEMBER
}
