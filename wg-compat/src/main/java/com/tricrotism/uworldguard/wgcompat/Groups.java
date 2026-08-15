// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Group membership, answered from the {@code group.<name>} permission node.
 *
 * <p>uWorldGuard has no permissions plugin of its own and Bukkit has no group concept, so this is
 * the same convention WorldGuard falls back to and the one every Vault-backed permissions plugin
 * publishes. It means a group domain works on a normally-configured server without uWorldGuard
 * taking a dependency on any particular permissions plugin.
 *
 * <p>A player who is offline has no permissible to ask, so every answer here is negative for them.
 * A wildcard permission is not a group either: {@code group.*} does not make {@code hasPermission
 * ("group.admin")} true unless the permissions plugin registers the child, so a wildcard-only admin
 * reports as being in no group. That is the conservative direction — a group domain that answers
 * "no" denies access it might have granted, rather than granting access it should not.
 */
@NullMarked
public final class Groups {

    private static final String PREFIX = "group.";

    private Groups() {
    }

    public static boolean inGroup(final @Nullable OfflinePlayer player, final String group) {
        return player != null && inGroup(player.getUniqueId(), group);
    }

    public static boolean inGroup(final UUID uniqueId, final String group) {
        final Player online = Bukkit.getPlayer(uniqueId);
        return online != null && online.hasPermission(PREFIX + group.toLowerCase(Locale.ROOT));
    }

    /**
     * Every group the player is in, lower-cased and without the {@code group.} prefix. Walks the
     * player's effective permissions, so it is a cold path — WorldGuard only calls it for display.
     */
    public static String[] of(final @Nullable OfflinePlayer player) {
        final Player online = player == null ? null : Bukkit.getPlayer(player.getUniqueId());
        if (online == null) {
            return new String[0];
        }
        final List<String> groups = new ArrayList<>(4);
        for (final PermissionAttachmentInfo info : online.getEffectivePermissions()) {
            final String node = info.getPermission();
            if (!info.getValue() || node.length() <= PREFIX.length() || !node.startsWith(PREFIX)) {
                continue;
            }
            final String name = node.substring(PREFIX.length()).toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && name.indexOf('.') < 0 && name.indexOf('*') < 0) {
                groups.add(name);
            }
        }
        return groups.toArray(new String[0]);
    }
}
