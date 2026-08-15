package com.tricrotism.uworldguard.service;

import com.tricrotism.uworldguard.packet.PacketHooks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disables player collision inside flagged regions using a native scoreboard team whose collision
 * rule is {@code NEVER}. Team membership changes are serialised on the global region scheduler (the
 * scoreboard is shared server-wide), and a per-player state set means we only touch the team when a
 * player's collision state actually flips.
 *
 * <p>The team is resolved lazily on first use: {@code getScoreboardManager()} is null until the
 * server has finished starting, so it cannot be created at plugin enable.
 */
@NullMarked
public final class CollisionService {

    private static final String TEAM_NAME = "uwg_nocollision";

    private final Plugin plugin;
    /**
     * Player to the team entry added for them. Team entries are names, not UUIDs, so the name used
     * to add has to be the name used to remove — a player who changes name while inside the region
     * would otherwise leave a stale entry behind and stay uncollidable for the rest of the session.
     */
    private final ConcurrentHashMap<UUID, String> disabled = new ConcurrentHashMap<>();
    private volatile @Nullable Team team;

    public CollisionService(final Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable or disable collision for a player, scheduling the team change only on a state flip.
     *
     * <p>Called on every block crossing, so the case where nobody on the server is in a no-collision
     * region exits before touching the set at all.
     */
    public void set(final Player player, final boolean collisionDisabled) {
        if (!collisionDisabled && disabled.isEmpty()) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (collisionDisabled) {
            final String entry = player.getName();
            if (disabled.putIfAbsent(uuid, entry) == null) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> applyEntry(entry, true));
            }
            return;
        }
        final String entry = disabled.remove(uuid);
        if (entry != null) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> applyEntry(entry, false));
        }
    }

    /**
     * Runs on the global region thread, where scoreboard access is safe.
     *
     * <p>The packet half rides the same hop. The server-side team lives on the main scoreboard, which
     * a player another plugin has put on a per-player scoreboard never sees — so their client keeps
     * pushing even though the server has stopped. When PacketEvents is available the team is sent
     * straight to those players; collision is predicted client-side, so both halves have to agree.
     * It cannot run on the caller's thread: it reads every online player's scoreboard, and the
     * moving player's region thread owns none of them.
     */
    private void applyEntry(final String entry, final boolean add) {
        if (PacketHooks.ACTIVE) {
            PacketHooks.collision(entry, add);
        }
        final Team resolved = team();
        if (resolved == null) {
            return;
        }
        if (add) {
            resolved.addEntry(entry);
        } else {
            resolved.removeEntry(entry);
        }
    }

    /**
     * Empties the team on disable. The main scoreboard outlives the plugin, so without this every
     * player who was inside a no-collision region stays in {@code uwg_nocollision} — still collision-
     * free after uWorldGuard is gone, with nothing left to explain why.
     */
    public void shutdown() {
        if (PacketHooks.ACTIVE) {
            for (final String entry : disabled.values()) {
                PacketHooks.collision(entry, false);
            }
        }
        final Team resolved = team;
        if (resolved != null) {
            for (final String entry : Set.copyOf(resolved.getEntries())) {
                resolved.removeEntry(entry);
            }
        }
        disabled.clear();
    }

    /**
     * Resolves the team on first use, and empties whatever the last run left in it. The main
     * scoreboard is persisted in {@code scoreboard.dat}, so a crash — or any stop that never reaches
     * {@link #shutdown} — leaves that session's members in the team; without this drain they come back
     * collision-free at boot and stay that way, since nothing here would ever ask to remove them.
     */
    private @Nullable Team team() {
        Team resolved = team;
        if (resolved != null) {
            return resolved;
        }
        final ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) {
            return null;
        }
        final Scoreboard scoreboard = manager.getMainScoreboard();
        resolved = scoreboard.getTeam(TEAM_NAME);
        if (resolved == null) {
            resolved = scoreboard.registerNewTeam(TEAM_NAME);
        } else {
            for (final String entry : Set.copyOf(resolved.getEntries())) {
                resolved.removeEntry(entry);
            }
        }
        resolved.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team = resolved;
        return resolved;
    }
}
