// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.regions;

/**
 * The shapes WorldGuard's API knows about.
 *
 * <p>uWorldGuard additionally supports cylinders and spheres. Those report {@link #POLYGON} here,
 * since no WorldGuard-era consumer can handle a constant that did not exist when it was compiled.
 */
public enum RegionType {

    CUBOID("cuboid"),
    POLYGON("poly2d"),
    GLOBAL("global");

    private final String name;

    RegionType(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
