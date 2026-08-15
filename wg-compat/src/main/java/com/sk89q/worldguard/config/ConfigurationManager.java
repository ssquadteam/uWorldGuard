// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.config;

import com.sk89q.worldguard.LocalPlayer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * WorldGuard's global settings, read by consumers as public fields.
 *
 * <p>uWorldGuard keeps its own configuration; these fields carry WorldGuard's stock defaults and are
 * not written to from anywhere. See {@link WorldConfiguration} for what that means for a consumer.
 *
 * <p>The region-driver fields ({@code selectedRegionStoreDriver}, {@code regionStoreDriverMap}) are
 * not shipped — they reference storage types this layer does not provide.
 */
public abstract class ConfigurationManager {

    protected static final Logger log = Logger.getLogger("uWorldGuard");

    public Map<String, String> hostKeys = new HashMap<>(0);

    public boolean activityHaltToggle;
    public boolean announceBypassStatus;
    public boolean blockInGameOp;
    public boolean deopOnJoin;
    public boolean disableDefaultBypass;
    public boolean disablePermissionCache;
    public boolean hostKeysAllowFMLClients = true;
    public boolean keepUnresolvedNames;
    public boolean migrateRegionsToUuid = true;
    public boolean particleEffects = true;
    public boolean useAmphibiousGroup = true;
    public boolean useGodGroup = true;
    public boolean useGodPermission = true;
    public boolean usePlayerMove = true;
    public boolean usePlayerTeleports = true;
    public boolean useRegionsCreatureSpawnEvent = true;

    public ConfigurationManager() {
    }

    public abstract File getDataFolder();

    public abstract WorldConfiguration get(com.sk89q.worldedit.world.World world);

    public abstract void load();

    public abstract void unload();

    public abstract void disableUuidMigration();

    public File getWorldsDataFolder() {
        return new File(getDataFolder(), "worlds");
    }

    /**
     * Always false: god mode is a WorldGuard command feature uWorldGuard does not implement.
     */
    public boolean hasGodMode(final LocalPlayer player) {
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.stub("ConfigurationManager.hasGodMode");
        return false;
    }

    /**
     * @see #hasGodMode(LocalPlayer)
     */
    public boolean hasAmphibiousMode(final LocalPlayer player) {
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.stub("ConfigurationManager.hasAmphibiousMode");
        return false;
    }

    /**
     * No-op: see {@link #hasAmphibiousMode(LocalPlayer)}.
     */
    public void enableAmphibiousMode(final LocalPlayer player) {
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.stub("ConfigurationManager.enableAmphibiousMode");
    }

    /**
     * No-op: see {@link #hasAmphibiousMode(LocalPlayer)}.
     */
    public void disableAmphibiousMode(final LocalPlayer player) {
        com.tricrotism.uworldguard.wgcompat.CompatDiagnostics.stub("ConfigurationManager.disableAmphibiousMode");
    }
}
