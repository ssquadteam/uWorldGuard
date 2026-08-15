// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.sk89q.worldguard.protection.flags.registry;

import com.sk89q.worldguard.protection.flags.Flag;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The set of flags known to the server. Backed by uWorldGuard's flag registry.
 */
public interface FlagRegistry extends Iterable<Flag<?>> {

    void register(Flag<?> flag) throws FlagConflictException;

    void registerAll(Collection<Flag<?>> flags);

    Flag<?> get(String name);

    List<Flag<?>> getAll();

    Map<Flag<?>, Object> unmarshal(Map<String, Object> rawValues, boolean createUnknown);

    int size();
}
