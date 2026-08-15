// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event.block;

import com.sk89q.worldguard.bukkit.cause.Cause;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Fired when one or more blocks are about to be interacted with.
 */
public class UseBlockEvent extends AbstractBlockEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UseBlockEvent(final @Nullable Event originalEvent, final Cause cause, final Block block) {
        super(originalEvent, cause, block);
    }

    public UseBlockEvent(
        final @Nullable Event originalEvent, final Cause cause, final Location target,
        final Material effectiveMaterial
    ) {
        super(originalEvent, cause, target, effectiveMaterial);
    }

    public UseBlockEvent(
        final @Nullable Event originalEvent, final Cause cause, final World world,
        final List<Block> blocks, final Material effectiveMaterial
    ) {
        super(originalEvent, cause, world, blocks, null, effectiveMaterial);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
