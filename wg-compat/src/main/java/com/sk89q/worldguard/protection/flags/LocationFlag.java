// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

import com.sk89q.worldedit.util.Location;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flag whose value is a WorldEdit {@link Location}. Backed engine-side by a string flag using
 * uWorldGuard's {@code world,x,y,z,yaw,pitch} format; the map form here is only used when a
 * consumer marshals the value itself.
 *
 * <p>All WorldEdit references live in method bodies so the class stays loadable without WorldEdit.
 */
public class LocationFlag extends Flag<Location> {

    public LocationFlag(final String name) {
        super(name);
    }

    public LocationFlag(final String name, final RegionGroup defaultGroup) {
        super(name, defaultGroup);
    }

    @Override
    public Location parseInput(final FlagContext context) throws InvalidFlagFormat {
        final Location location = LocationCodec.fromString(context.getUserInput());
        if (location == null) {
            throw new InvalidFlagFormat("Expected 'world,x,y,z[,yaw,pitch]' but got '"
                + context.getUserInput() + "'");
        }
        return location;
    }

    @Override
    public Location unmarshal(final Object o) {
        if (o instanceof Map<?, ?> map) {
            return LocationCodec.fromMap(map);
        }
        return o == null ? null : LocationCodec.fromString(String.valueOf(o));
    }

    @Override
    public Object marshal(final Location o) {
        if (o == null) {
            return null;
        }
        final Map<String, Object> map = new LinkedHashMap<>(8);
        map.put("world", LocationCodec.worldName(o));
        map.put("x", o.getX());
        map.put("y", o.getY());
        map.put("z", o.getZ());
        map.put("yaw", o.getYaw());
        map.put("pitch", o.getPitch());
        return map;
    }
}
