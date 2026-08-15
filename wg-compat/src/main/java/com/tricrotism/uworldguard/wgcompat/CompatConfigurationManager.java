// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import java.io.File;

/**
 * The concrete configuration manager the shim hands out. Every world answers with the same
 * {@code WorldConfiguration} instance carrying WorldGuard's defaults — uWorldGuard's own settings do
 * not map onto WorldGuard's field-per-protection layout, so nothing is copied across.
 */
public final class CompatConfigurationManager extends com.sk89q.worldguard.config.ConfigurationManager {

    public static final CompatConfigurationManager INSTANCE = new CompatConfigurationManager();

    private final com.sk89q.worldguard.config.WorldConfiguration shared = new Shared();

    private CompatConfigurationManager() {
    }

    @Override
    public File getDataFolder() {
        return WgCompatBridge.plugin().getDataFolder();
    }

    @Override
    public com.sk89q.worldguard.config.WorldConfiguration get(final com.sk89q.worldedit.world.World world) {
        return shared;
    }

    @Override
    public void load() {
    }

    @Override
    public void unload() {
    }

    @Override
    public void disableUuidMigration() {
    }

    private static final class Shared extends com.sk89q.worldguard.config.WorldConfiguration {

        @Override
        public void loadConfiguration() {
        }
    }
}
