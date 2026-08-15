// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.MapFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.*;

/**
 * An {@link ApplicableRegionSet} over a list the shim assembled itself, for the WorldGuard API calls
 * the engine has no query for — notably {@code RegionManager.getApplicableRegions(ProtectedRegion)},
 * which asks for the regions overlapping another region rather than a point.
 *
 * <p>Engine sets are never constructed by the shim, so this is the only other implementation.
 * Everything resolves through {@link FlagQueryAlgorithms}; the list must be priority-descending.
 */
public final class ListRegionSet implements ApplicableRegionSet {

    private final List<ProtectedRegion> regions;
    private final ProtectedRegion global;

    private Set<ProtectedRegion> regionSet;

    public ListRegionSet(final List<ProtectedRegion> regions, final ProtectedRegion global) {
        this.regions = regions;
        this.global = global;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public int size() {
        return regions.size();
    }

    @Override
    public Set<ProtectedRegion> getRegions() {
        Set<ProtectedRegion> cached = regionSet;
        if (cached == null) {
            cached = new LinkedHashSet<>(regions);
            regionSet = cached;
        }
        return cached;
    }

    @Override
    public Iterator<ProtectedRegion> iterator() {
        return regions.iterator();
    }

    @Override
    public boolean isMemberOfAll(final LocalPlayer player) {
        final java.util.UUID uniqueId = player.getUniqueId();
        for (int i = 0, n = regions.size(); i < n; i++) {
            if (!regions.get(i).isMember(uniqueId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isOwnerOfAll(final LocalPlayer player) {
        final java.util.UUID uniqueId = player.getUniqueId();
        for (int i = 0, n = regions.size(); i < n; i++) {
            if (!regions.get(i).isOwner(uniqueId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public StateFlag.State queryState(final RegionAssociable subject, final StateFlag... flags) {
        final Association association = FlagQueryAlgorithms.association(subject, regions);
        StateFlag.State result = null;
        for (int i = 0; i < flags.length; i++) {
            final StateFlag.State state =
                FlagQueryAlgorithms.queryState(regions, global, association, flags[i]);
            if (state == StateFlag.State.DENY) {
                return StateFlag.State.DENY;
            }
            if (state == StateFlag.State.ALLOW) {
                result = StateFlag.State.ALLOW;
            }
        }
        return result;
    }

    @Override
    public boolean testState(final RegionAssociable subject, final StateFlag... flags) {
        return queryState(subject, flags) == StateFlag.State.ALLOW;
    }

    /**
     * Whether {@code subject} may build across these regions.
     */
    public boolean canBuild(final RegionAssociable subject) {
        return FlagQueryAlgorithms.canBuild(regions, global,
            FlagQueryAlgorithms.association(subject, regions));
    }

    @Override
    public <V> V queryValue(final RegionAssociable subject, final Flag<V> flag) {
        return FlagQueryAlgorithms.queryValue(regions, global,
            FlagQueryAlgorithms.association(subject, regions), flag);
    }

    @Override
    public <V> Collection<V> queryAllValues(final RegionAssociable subject, final Flag<V> flag) {
        return FlagQueryAlgorithms.queryAllValues(regions, global,
            FlagQueryAlgorithms.association(subject, regions), flag);
    }

    @Override
    public <V, K> V queryMapValue(final RegionAssociable subject, final MapFlag<K, V> flag, final K key) {
        return queryMapValue(subject, flag, key, null);
    }

    @Override
    public <V, K> V queryMapValue(
        final RegionAssociable subject, final MapFlag<K, V> flag, final K key, final Flag<V> fallback
    ) {
        final Map<K, V> map = queryValue(subject, flag);
        if (map != null) {
            final V value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return fallback == null ? null : queryValue(subject, fallback);
    }
}
