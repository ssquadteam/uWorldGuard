// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 Sage Kummer
// Clean-room reimplementation of the public WorldGuard 7 API for interoperability.
// Not derived from WorldGuard source code.

package com.tricrotism.uworldguard.wgcompat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Builds {@code com.sk89q.worldguard.LocalPlayer} instances.
 *
 * <p>WorldEdit's {@code Player}/{@code Actor}/{@code Entity} surface is large and drifts between
 * WorldEdit releases, so it is never hand-implemented: a {@link Proxy} forwards every WorldEdit
 * method to the real {@code Player} that {@code BukkitAdapter.adapt} produces, and answers
 * WorldGuard's own methods against the Bukkit {@link Player}. Only the shape WorldGuard adds is
 * maintained here, and it moves with whatever WorldEdit is installed.
 *
 * <p>Each proxy also implements {@link UuidSubject}, which is what keeps region queries made with a
 * {@code LocalPlayer} on the engine's UUID fast path.
 *
 * <p>This class is the only place the shim resolves a WorldEdit type from a static initialiser, and
 * it is loaded lazily — {@code WorldGuardPlugin} reaches it through {@link #wrap(Player)}, declared
 * to return {@link Object}, so a server without WorldEdit never loads it.
 */
public final class PlayerWrapping {

    private static final Class<?> LOCAL_PLAYER = com.sk89q.worldguard.LocalPlayer.class;
    private static final Class<?> REGION_ASSOCIABLE =
        com.sk89q.worldguard.protection.association.RegionAssociable.class;

    private static final Class<?>[] INTERFACES = {LOCAL_PLAYER, UuidSubject.class};

    /**
     * Time-bounded rather than reference-bounded: nothing else holds a wrapper strongly, so weak
     * values would be collected between calls and defeat the cache. Staleness is handled by the
     * identity check in {@link #wrap(Player)}, not by the expiry.
     */
    private static final Cache<UUID, Wrapper> CACHE = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();

    private PlayerWrapping() {
    }

    /**
     * The {@code LocalPlayer} for an online player, cached per UUID. Declared to return
     * {@link Object} so callers that must stay loadable without WorldEdit can cast at the call site
     * instead of naming the type in their own descriptors.
     */
    public static Object wrap(final Player player) {
        final UUID uniqueId = player.getUniqueId();
        final Wrapper cached = CACHE.getIfPresent(uniqueId);
        if (cached != null && cached.bukkit == player) {
            return cached.proxy;
        }
        CompatDiagnostics.WRAPS.increment();
        final Wrapper wrapper = create(player, player, uniqueId);
        CACHE.put(uniqueId, wrapper);
        return wrapper.proxy;
    }

    /**
     * The {@code LocalPlayer} for an offline player. An online player resolves through
     * {@link #wrap(Player)}; otherwise the result answers identity and permission questions only,
     * and every WorldEdit method on it throws {@link UnsupportedOperationException}.
     */
    public static Object wrapOffline(final OfflinePlayer player) {
        final Player online = player.getPlayer();
        if (online != null) {
            return wrap(online);
        }
        CompatDiagnostics.WRAPS.increment();
        return create(null, player, player.getUniqueId()).proxy;
    }

    /**
     * A WorldEdit {@code Actor} for any command sender: a {@code LocalPlayer} for a player, and
     * WorldEdit's own console actor otherwise.
     */
    public static Object wrapSender(final CommandSender sender) {
        if (sender instanceof Player player) {
            return wrap(player);
        }
        return com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(Bukkit.getConsoleSender());
    }

    /**
     * The Bukkit sender behind a WorldEdit actor, or {@code null} when it is not one this shim can
     * unwrap.
     */
    public static CommandSender unwrap(final Object actor) {
        if (actor instanceof UuidSubject subject) {
            return Bukkit.getPlayer(subject.uwgUuid());
        }
        if (actor instanceof com.sk89q.worldedit.entity.Player player) {
            return com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(player);
        }
        if (actor instanceof com.sk89q.worldedit.extension.platform.Actor typed && !typed.isPlayer()) {
            return Bukkit.getConsoleSender();
        }
        return null;
    }

    private static Wrapper create(final Player online, final OfflinePlayer offline, final UUID uniqueId) {
        final com.sk89q.worldedit.entity.Player actor =
            online == null ? null : com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(online);
        final Handler handler = new Handler(online, offline, actor, uniqueId);
        final Object proxy = Proxy.newProxyInstance(LOCAL_PLAYER.getClassLoader(), INTERFACES, handler);
        return new Wrapper(online, proxy);
    }

    private record Wrapper(Player bukkit, Object proxy) {
    }

    private static final class Handler implements InvocationHandler {

        private final Player bukkit;
        private final OfflinePlayer offline;
        private final com.sk89q.worldedit.entity.Player actor;
        private final UUID uniqueId;

        private Handler(final Player bukkit, final OfflinePlayer offline,
                        final com.sk89q.worldedit.entity.Player actor, final UUID uniqueId) {
            this.bukkit = bukkit;
            this.offline = offline;
            this.actor = actor;
            this.uniqueId = uniqueId;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final Class<?> declaring = method.getDeclaringClass();
            if (declaring == Object.class) {
                return object(proxy, method, args);
            }
            if (declaring == UuidSubject.class) {
                return uniqueId;
            }
            if (declaring == LOCAL_PLAYER || declaring == REGION_ASSOCIABLE) {
                return worldGuard(method, args);
            }
            if (actor == null) {
                return offlineFallback(method);
            }
            try {
                return method.invoke(actor, args);
            } catch (final InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Object object(final Object proxy, final Method method, final Object[] args) {
            return switch (method.getName()) {
                case "equals" -> args[0] instanceof UuidSubject other && uniqueId.equals(other.uwgUuid());
                case "hashCode" -> uniqueId.hashCode();
                default -> "LocalPlayer{" + uniqueId + '}';
            };
        }

        private Object worldGuard(final Method method, final Object[] args) {
            switch (method.getName()) {
                case "getAssociation":
                    return association(args[0]);
                case "hasGroup":
                    return bukkit != null && Groups.inGroup(bukkit.getUniqueId(), (String) args[0]);
                default:
                    break;
            }
            if (bukkit == null) {
                CompatDiagnostics.stub("LocalPlayer." + method.getName() + " (offline)");
                throw new UnsupportedOperationException(
                    "LocalPlayer." + method.getName() + " is not available for an offline player");
            }
            return online(method, args);
        }

        private Object online(final Method method, final Object[] args) {
            switch (method.getName()) {
                case "getHealth":
                    return bukkit.getHealth();
                case "setHealth":
                    bukkit.setHealth((Double) args[0]);
                    return null;
                case "getMaxHealth": {
                    final AttributeInstance attribute =
                        bukkit.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                    return attribute == null ? 20.0D : attribute.getValue();
                }
                case "getFoodLevel":
                    return (double) bukkit.getFoodLevel();
                case "setFoodLevel":
                    bukkit.setFoodLevel((int) (double) (Double) args[0]);
                    return null;
                case "getSaturation":
                    return (double) bukkit.getSaturation();
                case "setSaturation":
                    bukkit.setSaturation((float) (double) (Double) args[0]);
                    return null;
                case "getExhaustion":
                    return bukkit.getExhaustion();
                case "setExhaustion":
                    bukkit.setExhaustion((Float) args[0]);
                    return null;
                case "getFireTicks":
                    return bukkit.getFireTicks();
                case "setFireTicks":
                    bukkit.setFireTicks((Integer) args[0]);
                    return null;
                case "resetFallDistance":
                    bukkit.setFallDistance(0.0F);
                    return null;
                case "setCompassTarget":
                    bukkit.setCompassTarget(toBukkit(args[0]));
                    return null;
                case "getPlayerTimeOffset":
                    return bukkit.getPlayerTimeOffset();
                case "isPlayerTimeRelative":
                    return bukkit.isPlayerTimeRelative();
                case "setPlayerTime":
                    bukkit.setPlayerTime((Long) args[0], (Boolean) args[1]);
                    return null;
                case "resetPlayerTime":
                    bukkit.resetPlayerTime();
                    return null;
                case "getPlayerWeather":
                    return weather();
                case "setPlayerWeather":
                    bukkit.setPlayerWeather(isClear(args[0])
                        ? org.bukkit.WeatherType.CLEAR
                        : org.bukkit.WeatherType.DOWNFALL);
                    return null;
                case "resetPlayerWeather":
                    bukkit.resetPlayerWeather();
                    return null;
                case "kick":
                    bukkit.kick(Component.text(String.valueOf(args[0])));
                    return null;
                case "ban":
                    ban((String) args[0]);
                    return null;
                case "sendTitle":
                    bukkit.showTitle(Title.title(
                        Component.text(args[0] == null ? "" : (String) args[0]),
                        Component.text(args[1] == null ? "" : (String) args[1])));
                    return null;
                case "teleport":
                    teleport(args);
                    return null;
                default:
                    CompatDiagnostics.stub("LocalPlayer." + method.getName());
                    throw new UnsupportedOperationException("LocalPlayer." + method.getName());
            }
        }

        @SuppressWarnings("unchecked")
        private Object association(final Object regions) {
            final List<com.sk89q.worldguard.protection.regions.ProtectedRegion> list =
                (List<com.sk89q.worldguard.protection.regions.ProtectedRegion>) regions;
            boolean member = false;
            for (int i = 0, n = list.size(); i < n; i++) {
                final com.sk89q.worldguard.protection.regions.ProtectedRegion region = list.get(i);
                if (region.isOwner(uniqueId)) {
                    return com.sk89q.worldguard.domains.Association.OWNER;
                }
                if (!member && region.isMember(uniqueId)) {
                    member = true;
                }
            }
            return member
                ? com.sk89q.worldguard.domains.Association.MEMBER
                : com.sk89q.worldguard.domains.Association.NON_MEMBER;
        }

        private Object offlineFallback(final Method method) {
            return switch (method.getName()) {
                case "getUniqueId" -> uniqueId;
                case "getName", "getDisplayName" -> offline.getName();
                case "isPlayer" -> Boolean.FALSE;
                default -> {
                    CompatDiagnostics.stub("LocalPlayer." + method.getName() + " (offline)");
                    throw new UnsupportedOperationException(
                        "LocalPlayer." + method.getName() + " is not available for an offline player");
                }
            };
        }

        private Object weather() {
            final org.bukkit.WeatherType player = bukkit.getPlayerWeather();
            final boolean clear = player != null
                ? player == org.bukkit.WeatherType.CLEAR
                : !bukkit.getWorld().hasStorm();
            return clear
                ? com.sk89q.worldedit.world.weather.WeatherTypes.CLEAR
                : com.sk89q.worldedit.world.weather.WeatherTypes.RAIN;
        }

        private void ban(final String message) {
            bukkit.ban(message, (java.util.Date) null, null);
        }

        private void teleport(final Object[] args) {
            final org.bukkit.Location target = toBukkit(args[0]);
            final String success = (String) args[1];
            final String failure = (String) args[2];
            bukkit.teleportAsync(target).thenAccept(moved -> {
                final String message = moved ? success : failure;
                if (message != null && !message.isEmpty()) {
                    bukkit.sendMessage(Component.text(message));
                }
            });
        }

        private org.bukkit.Location toBukkit(final Object weLocation) {
            final com.sk89q.worldedit.util.Location location = (com.sk89q.worldedit.util.Location) weLocation;
            return new org.bukkit.Location(bukkit.getWorld(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
        }

        private static boolean isClear(final Object weWeather) {
            return weWeather == null
                || ((com.sk89q.worldedit.world.weather.WeatherType) weWeather)
                .id().equals(com.sk89q.worldedit.world.weather.WeatherTypes.CLEAR.id());
        }
    }
}
