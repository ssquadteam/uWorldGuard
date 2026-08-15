// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.association;

import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factories for simple {@link RegionAssociable} implementations.
 */
public final class Associables {

    private static final Map<Association, RegionAssociable> CONSTANTS = new EnumMap<>(Association.class);

    static {
        for (final Association association : Association.values()) {
            CONSTANTS.put(association, new Constant(association));
        }
    }

    private Associables() {
    }

    /**
     * An associable that answers the same association for every region.
     */
    public static RegionAssociable constant(final Association association) {
        return CONSTANTS.get(association);
    }

    private record Constant(Association association) implements RegionAssociable {

        @Override
        public Association getAssociation(final List<ProtectedRegion> regions) {
            return association;
        }
    }
}
