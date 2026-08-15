// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldguard.domains.Association;

/**
 * Which subjects a flag value applies to. Constant names match uWorldGuard's
 * {@code com.tricrotism.uworldguard.flags.RegionGroup}, so the two convert by name.
 */
public enum RegionGroup {

    MEMBERS,
    OWNERS,
    NON_MEMBERS,
    NON_OWNERS,
    ALL,
    NONE;

    /**
     * Whether a subject with the given association is covered by this group. A {@code null}
     * association is treated as {@link Association#NON_MEMBER}.
     */
    public boolean contains(final Association association) {
        final Association resolved = association == null ? Association.NON_MEMBER : association;
        return switch (this) {
            case ALL -> true;
            case NONE -> false;
            case MEMBERS -> resolved == Association.MEMBER || resolved == Association.OWNER;
            case OWNERS -> resolved == Association.OWNER;
            case NON_MEMBERS -> resolved == Association.NON_MEMBER;
            case NON_OWNERS -> resolved != Association.OWNER;
        };
    }
}
