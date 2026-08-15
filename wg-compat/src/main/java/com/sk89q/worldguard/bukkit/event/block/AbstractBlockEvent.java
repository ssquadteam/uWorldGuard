// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.
package com.sk89q.worldguard.bukkit.event.block;

import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.BulkEvent;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared state for the block events: the world, the blocks involved, and the material the action
 * should be judged against.
 */
public abstract class AbstractBlockEvent extends DelegateEvent implements BulkEvent {

    private final World world;
    private final List<Block> blocks;
    private final @Nullable Location target;
    private final Material effectiveMaterial;
    private Event.Result explicitResult = Event.Result.DEFAULT;

    protected AbstractBlockEvent(
        final @Nullable Event originalEvent, final Cause cause, final World world,
        final List<Block> blocks, final @Nullable Location target, final Material effectiveMaterial
    ) {
        super(originalEvent, cause);
        this.world = world;
        this.blocks = blocks;
        this.target = target;
        this.effectiveMaterial = effectiveMaterial;
    }

    protected AbstractBlockEvent(final @Nullable Event originalEvent, final Cause cause, final Block block) {
        this(originalEvent, cause, block.getWorld(), listOf(block), block.getLocation(), block.getType());
    }

    protected AbstractBlockEvent(
        final @Nullable Event originalEvent, final Cause cause, final Location target,
        final Material effectiveMaterial
    ) {
        this(originalEvent, cause, target.getWorld(), new ArrayList<>(), target, effectiveMaterial);
    }

    private static List<Block> listOf(final Block block) {
        final List<Block> blocks = new ArrayList<>(1);
        blocks.add(block);
        return blocks;
    }

    public World getWorld() {
        return world;
    }

    /**
     * The blocks this event covers. Mutable — {@link #filter} removes entries from it.
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    public Material getEffectiveMaterial() {
        return effectiveMaterial;
    }

    public @Nullable Location getTarget() {
        return target;
    }

    /**
     * Drop every block whose location the predicate rejects.
     *
     * @return whether any block survived
     */
    public boolean filter(final Predicate<Location> predicate) {
        return filter(predicate, true);
    }

    /**
     * Drop every block whose location the predicate rejects.
     *
     * @param cancelEventOnFalse deny the whole event when nothing survives
     * @return whether any block survived
     */
    public boolean filter(final Predicate<Location> predicate, final boolean cancelEventOnFalse) {
        if (blocks.isEmpty()) {
            final boolean accepted = target == null || predicate.test(target);
            if (!accepted && cancelEventOnFalse) {
                setResult(Event.Result.DENY);
            }
            return accepted;
        }

        blocks.removeIf(block -> !predicate.test(block.getLocation()));
        if (blocks.isEmpty() && cancelEventOnFalse) {
            setResult(Event.Result.DENY);
        }
        return !blocks.isEmpty();
    }

    @Override
    public Event.Result getExplicitResult() {
        return explicitResult;
    }

    @Override
    public void setResult(final Event.Result result) {
        this.explicitResult = result;
        super.setResult(result);
    }
}
