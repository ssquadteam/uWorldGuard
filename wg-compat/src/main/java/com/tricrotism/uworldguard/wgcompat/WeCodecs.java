// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import com.sk89q.worldguard.protection.flags.LocationCodec;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Value converters for the flags whose WorldGuard type is a WorldEdit type and whose uWorldGuard
 * counterpart stores something simpler. Every method erases to {@code Object}, and the class is
 * loaded only when such a flag is first converted, so WorldEdit never resolves on the
 * class-initialization path of the shim.
 */
final class WeCodecs {

    private WeCodecs() {
    }

    static Object gameModeToShim(final Object stored) {
        return com.sk89q.worldedit.world.gamemode.GameMode.REGISTRY
            .get(String.valueOf(stored).toLowerCase(java.util.Locale.ROOT));
    }

    static Object gameModeToEngine(final Object value) {
        return ((com.sk89q.worldedit.world.gamemode.GameMode) value).id();
    }

    static Object weatherToShim(final Object stored) {
        return com.sk89q.worldedit.world.weather.WeatherType.REGISTRY
            .get(String.valueOf(stored).toLowerCase(java.util.Locale.ROOT));
    }

    static Object weatherToEngine(final Object value) {
        return ((com.sk89q.worldedit.world.weather.WeatherType) value).id();
    }

    static Object entityTypesToShim(final Object stored) {
        if (!(stored instanceof Collection<?> types)) {
            return null;
        }
        final Set<com.sk89q.worldedit.world.entity.EntityType> out = new LinkedHashSet<>(types.size());
        for (final Object type : types) {
            final com.sk89q.worldedit.world.entity.EntityType resolved =
                com.sk89q.worldedit.world.entity.EntityType.REGISTRY
                    .get(((org.bukkit.entity.EntityType) type).getKey().asString());
            if (resolved != null) {
                out.add(resolved);
            }
        }
        return out;
    }

    static Object entityTypesToEngine(final Object value) {
        if (!(value instanceof Collection<?> types)) {
            return null;
        }
        final Set<org.bukkit.entity.EntityType> out = new LinkedHashSet<>(types.size());
        for (final Object type : types) {
            final NamespacedKey key =
                NamespacedKey.fromString(((com.sk89q.worldedit.world.entity.EntityType) type).id());
            if (key == null) {
                continue;
            }
            final org.bukkit.entity.EntityType resolved =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE).get(key);
            if (resolved != null) {
                out.add(resolved);
            }
        }
        return out;
    }

    static Object locationToShim(final Object stored) {
        return LocationCodec.fromString(String.valueOf(stored));
    }

    static Object locationToEngine(final Object value) {
        return LocationCodec.toStorageString((com.sk89q.worldedit.util.Location) value);
    }
}
