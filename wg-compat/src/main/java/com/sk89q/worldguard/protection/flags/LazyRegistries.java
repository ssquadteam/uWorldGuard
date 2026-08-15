// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags;

/**
 * Internal: resolves the WorldEdit registries backing the registry-valued flags. Every WorldEdit
 * reference sits in a method body and every method erases to {@code Object}, so {@link Flags}'
 * static initialiser can build those flags on a server with no WorldEdit installed.
 *
 * <p>Not part of the WorldGuard API.
 */
final class LazyRegistries {

    private LazyRegistries() {
    }

    static Object gameModes() {
        return com.sk89q.worldedit.world.gamemode.GameMode.REGISTRY;
    }

    static Object weatherTypes() {
        return com.sk89q.worldedit.world.weather.WeatherType.REGISTRY;
    }

    static Object entityTypes() {
        return com.sk89q.worldedit.world.entity.EntityType.REGISTRY;
    }
}
