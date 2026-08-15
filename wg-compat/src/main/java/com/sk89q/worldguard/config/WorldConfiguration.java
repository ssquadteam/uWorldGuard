// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.config;

import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldguard.LocalPlayer;

import java.util.*;
import java.util.logging.Logger;

/**
 * WorldGuard's per-world settings, which consumers read as public fields rather than through
 * accessors.
 *
 * <p>uWorldGuard has its own configuration and does not map onto these one for one, so the fields
 * carry WorldGuard's stock defaults. Only {@link #useRegions} is meaningful: uWorldGuard always
 * protects regions. Treat the rest as "WorldGuard's protections are not what is enforcing this
 * server" — a consumer branching on, say, {@link #disableFireSpread} will read {@code false}
 * regardless of how uWorldGuard is configured.
 *
 * <p>The {@code blacklist} members and the region-driver fields are not shipped; they reference
 * types this layer does not provide.
 */
public abstract class WorldConfiguration {

    public static final Logger log = Logger.getLogger("uWorldGuard");

    public static final String CONFIG_HEADER = "uWorldGuard's WorldGuard compatibility layer."
        + " These values are WorldGuard defaults and are not read from any file.";

    protected Map<String, Integer> maxRegionCounts = new HashMap<>(4);

    public boolean useRegions = true;
    public boolean opPermissions = true;
    public boolean itemDurability = true;
    public boolean summaryOnStart = true;
    public boolean buildPermissions;
    public boolean highFreqFlags;
    public boolean boundedLocationFlags = true;
    public boolean explosionFlagCancellation = true;
    public boolean fakePlayerBuildOverride = true;
    public boolean forceDefaultTitleTimes = true;
    public boolean useMaxPriorityAssociation;
    public boolean claimOnlyInsideExistingRegions;
    public boolean regionInvinciblityRemovesMobs;
    public boolean regionNetherPortalProtection = true;
    public boolean regionCancelEmptyChatEvents = true;
    public boolean checkLiquidFlow;
    public boolean disableDeathMessages;

    public boolean allowPortalAnywhere;
    public boolean allowTamedSpawns = true;
    public boolean alwaysRaining;
    public boolean alwaysThundering;
    public boolean antiWolfDumbness;
    public boolean blockCreeperBlockDamage;
    public boolean blockCreeperExplosions;
    public boolean blockEnderDragonBlockDamage;
    public boolean blockEnderDragonPortalCreation;
    public boolean blockEntityArmorStandDestroy;
    public boolean blockEntityItemFrameDestroy;
    public boolean blockEntityPaintingDestroy;
    public boolean blockEntityVehicleEntry;
    public boolean blockFireballBlockDamage;
    public boolean blockFireballExplosions;
    public boolean blockGroundSlimes;
    public boolean blockLighter;
    public boolean blockOtherExplosions;
    public boolean blockPluginSpawning = true;
    public boolean blockPotionsAlways;
    public boolean blockTNTBlockDamage;
    public boolean blockTNTExplosions;
    public boolean blockWindChargeExplosions;
    public boolean blockWitherBlockDamage;
    public boolean blockWitherExplosions;
    public boolean blockWitherSkullBlockDamage;
    public boolean blockWitherSkullExplosions;
    public boolean blockZombieDoorDestruction;
    public boolean breakDeniedHoppers;
    public boolean disableConduitEffects;
    public boolean disableContactDamage;
    public boolean disableCopperBlockFade;
    public boolean disableCoralBlockFade;
    public boolean disableCreatureCropTrampling;
    public boolean disableCreatureSnifferEggTrampling;
    public boolean disableCreatureTurtleEggTrampling;
    public boolean disableCreeperPower;
    public boolean disableCropGrowth;
    public boolean disableDrowningDamage;
    public boolean disableEndermanGriefing;
    public boolean disableExpDrops;
    public boolean disableExplosionDamage;
    public boolean disableFallDamage;
    public boolean disableFireDamage;
    public boolean disableFireSpread;
    public boolean disableGrassGrowth;
    public boolean disableHealthRegain;
    public boolean disableIceFormation;
    public boolean disableIceMelting;
    public boolean disableLavaDamage;
    public boolean disableLavaHarden;
    public boolean disableLeafDecay;
    public boolean disableLightningDamage;
    public boolean disableMobDamage;
    public boolean disableMushroomSpread;
    public boolean disableMyceliumSpread;
    public boolean disablePigZap;
    public boolean disablePlayerCropTrampling;
    public boolean disablePlayerSnifferEggTrampling;
    public boolean disablePlayerTurtleEggTrampling;
    public boolean disableRockGrowth;
    public boolean disableSculkGrowth;
    public boolean disableSignChestProtectionCheck;
    public boolean disableSnowFormation;
    public boolean disableSnowMelting;
    public boolean disableSnowmanTrails;
    public boolean disableSoilDehydration;
    public boolean disableSoilMoistureChange;
    public boolean disableSuffocationDamage;
    public boolean disableThunder;
    public boolean disableVillagerZap;
    public boolean disableVineGrowth;
    public boolean disableVoidDamage;
    public boolean disableWeather;
    public boolean fireSpreadDisableToggle;
    public boolean ignoreHopperMoveEvents;
    public boolean noPhysicsGravel;
    public boolean noPhysicsSand;
    public boolean preventLavaFire;
    public boolean preventLightningFire;
    public boolean pumpkinScuba;
    public boolean redstoneSponges;
    public boolean removeInfiniteStacks;
    public boolean ropeLadders;
    public boolean safeFallOnVoid;
    public boolean signChestProtection;
    public boolean simulateSponge;
    public boolean strictEntitySpawn = true;
    public boolean teleportOnSuffocation;
    public boolean teleportOnVoid;

    public int maxClaimVolume = 30000;
    public int maxRegionCountPerPlayer = 7;
    public int spongeRadius = 3;

    public String regionWand = "minecraft:leather";
    public String buildPermissionDenyMessage = "";
    public String setParentOnClaim = "";

    public Set<String> allowedLavaSpreadOver = new HashSet<>(0);
    public Set<String> allowedSnowFallOver = new HashSet<>(0);
    public Set<String> disableFireSpreadBlocks = new HashSet<>(0);
    public Set<String> disallowedLightningBlocks = new HashSet<>(0);
    public Set<String> preventWaterDamage = new HashSet<>(0);
    public Set<EntityType> blockCreatureSpawn = new HashSet<>(0);

    public WorldConfiguration() {
    }

    public abstract void loadConfiguration();

    /**
     * The claim limit for a player, from the per-group overrides if one applies.
     */
    public int getMaxRegionCount(final LocalPlayer player) {
        int max = -1;
        for (final Map.Entry<String, Integer> entry : maxRegionCounts.entrySet()) {
            if (entry.getValue() > max && player.hasGroup(entry.getKey())) {
                max = entry.getValue();
            }
        }
        return max < 0 ? maxRegionCountPerPlayer : max;
    }

    /**
     * Identity: uWorldGuard has no legacy numeric-id table to translate against.
     */
    public String convertLegacyBlock(final String legacy) {
        return legacy;
    }

    /**
     * @see #convertLegacyBlock(String)
     */
    public String convertLegacyItem(final String legacy) {
        return legacy;
    }

    /**
     * @see #convertLegacyBlock(String)
     */
    public List<String> convertLegacyBlocks(final List<String> legacyBlocks) {
        return legacyBlocks;
    }

    /**
     * @see #convertLegacyBlock(String)
     */
    public List<String> convertLegacyItems(final List<String> legacyItems) {
        return legacyItems;
    }
}
