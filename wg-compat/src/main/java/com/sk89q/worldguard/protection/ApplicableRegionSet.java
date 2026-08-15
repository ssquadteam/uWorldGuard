// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.MapFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.Collection;
import java.util.Set;

/**
 * The regions applicable at a point, with WorldGuard's flag resolution on top.
 *
 * <p>The only implementation the shim hands out is
 * {@code com.tricrotism.uworldguard.wgcompat.WrappedRegionSet}, which wraps an immutable engine
 * {@code com.tricrotism.uworldguard.region.ApplicableRegionSet}.
 */
public interface ApplicableRegionSet extends Iterable<ProtectedRegion> {

    boolean isVirtual();

    StateFlag.State queryState(RegionAssociable subject, StateFlag... flags);

    boolean testState(RegionAssociable subject, StateFlag... flags);

    <V> V queryValue(RegionAssociable subject, Flag<V> flag);

    <V> Collection<V> queryAllValues(RegionAssociable subject, Flag<V> flag);

    <V, K> V queryMapValue(RegionAssociable subject, MapFlag<K, V> flag, K key);

    <V, K> V queryMapValue(RegionAssociable subject, MapFlag<K, V> flag, K key, Flag<V> fallback);

    /**
     * Whether {@code player} is a member (or owner) of <em>every</em> region here. Vacuously true for
     * an empty set.
     */
    boolean isMemberOfAll(LocalPlayer player);

    /**
     * Whether {@code player} owns <em>every</em> region here. Vacuously true for an empty set.
     */
    boolean isOwnerOfAll(LocalPlayer player);

    Set<ProtectedRegion> getRegions();

    int size();
}
