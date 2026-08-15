// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.association;

import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.List;

/**
 * Something that can say how it relates to a set of regions, used to resolve group-qualified flags.
 *
 * <p>An associable that also implements {@code com.tricrotism.uworldguard.wgcompat.UuidSubject} is
 * resolved by the engine directly; any other implementation is called back once per region by
 * {@code com.tricrotism.uworldguard.wgcompat.FlagQueryAlgorithms}.
 */
public interface RegionAssociable {

    Association getAssociation(List<ProtectedRegion> regions);
}
