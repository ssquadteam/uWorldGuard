// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event.inventory;

import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Fired when an item is about to be used.
 */
public class UseItemEvent extends DelegateEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final ItemStack itemStack;

    public UseItemEvent(
        final @Nullable Event originalEvent, final Cause cause, final World world,
        final ItemStack itemStack
    ) {
        super(originalEvent, cause);
        this.world = world;
        this.itemStack = itemStack;
    }

    public World getWorld() {
        return world;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
