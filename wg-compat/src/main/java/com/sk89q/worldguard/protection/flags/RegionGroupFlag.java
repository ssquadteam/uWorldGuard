// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.Locale;

/**
 * The {@code <flag>-group} qualifier attached to every grouped flag.
 */
public class RegionGroupFlag extends EnumFlag<RegionGroup> {

    private final RegionGroup def;

    public RegionGroupFlag(final String name, final RegionGroup def) {
        super(name, RegionGroup.class);
        this.def = def;
    }

    @Override
    public RegionGroup getDefault() {
        return def;
    }

    /**
     * Whether a group-qualified flag value applies to {@code player} across a whole region set. A
     * {@code null} group means {@link RegionGroup#ALL}.
     */
    public static boolean isMember(
        final ApplicableRegionSet set, final RegionGroup group, final LocalPlayer player
    ) {
        return switch (group == null ? RegionGroup.ALL : group) {
            case ALL -> true;
            case NONE -> false;
            case MEMBERS -> set.isMemberOfAll(player);
            case OWNERS -> set.isOwnerOfAll(player);
            case NON_MEMBERS -> !set.isMemberOfAll(player);
            case NON_OWNERS -> !set.isOwnerOfAll(player);
        };
    }

    /**
     * Whether a group-qualified flag value applies to {@code player} on one region. A {@code null}
     * player is treated as a non-member.
     */
    public static boolean isMember(
        final ProtectedRegion region, final RegionGroup group, final LocalPlayer player
    ) {
        final RegionGroup resolved = group == null ? RegionGroup.ALL : group;
        if (resolved == RegionGroup.ALL) {
            return true;
        }
        if (resolved == RegionGroup.NONE) {
            return false;
        }
        if (player == null) {
            return resolved == RegionGroup.NON_MEMBERS || resolved == RegionGroup.NON_OWNERS;
        }
        return switch (resolved) {
            case MEMBERS -> region.isMember(player);
            case OWNERS -> region.isOwner(player);
            case NON_MEMBERS -> !region.isMember(player);
            case NON_OWNERS -> !region.isOwner(player);
            default -> true;
        };
    }

    @Override
    public RegionGroup detectValue(final String input) {
        if (input == null) {
            return null;
        }
        return switch (input.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "all", "everyone" -> RegionGroup.ALL;
            case "members", "member" -> RegionGroup.MEMBERS;
            case "owners", "owner" -> RegionGroup.OWNERS;
            case "nonmembers", "non_members", "nonmember", "non_member" -> RegionGroup.NON_MEMBERS;
            case "nonowners", "non_owners", "nonowner", "non_owner" -> RegionGroup.NON_OWNERS;
            case "none" -> RegionGroup.NONE;
            default -> null;
        };
    }
}
