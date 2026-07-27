package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.config.Settings;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.State;
import com.tricrotism.uworldguard.region.ApplicableRegionSet;
import com.tricrotism.uworldguard.region.ProtectedRegion;
import com.tricrotism.uworldguard.region.RegionQuery;
import com.tricrotism.uworldguard.service.ChamberedPearlTracker;
import com.tricrotism.uworldguard.service.CollisionService;
import com.tricrotism.uworldguard.text.ChatTags;
import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.util.Locations;
import io.papermc.paper.event.entity.EntityMoveEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces entry/exit and entry-level flags, runs per-region enter/leave effects (greeting/farewell,
 * commands, teleport, sounds), and applies continuous player state (game-mode, walk/fly speed,
 * flight). The handler returns immediately when the player has not crossed a block boundary, so the
 * region lookups only run on actual movement between blocks.
 */
@NullMarked
public final class MovementListener implements Listener {


    private final Plugin plugin;
    private final RegionQuery query;
    private final MessageService messages;
    private final CollisionService collision;
    private final ChamberedPearlTracker pearls;
    private final ChatTags chatTags;
    private final Map<UUID, GameMode> savedGameModes = new ConcurrentHashMap<>();
    private final Map<UUID, Float> savedWalkSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Float> savedFlySpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savedAllowFlight = new ConcurrentHashMap<>();
    private final Set<UUID> riddenMounts = ConcurrentHashMap.newKeySet();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> lastPosition = new ConcurrentHashMap<>();

    private volatile boolean taskMode;
    private volatile int taskTicks;
    /**
     * Polls between containment sweeps, chosen so a sweep lands roughly once a second.
     */
    private volatile int sweepEvery;
    /**
     * Only ever touched from the global region task, which runs its repeats sequentially.
     */
    private int sweepTick;
    private @Nullable ScheduledTask pollTask;

    public MovementListener(
        final Plugin plugin, final RegionQuery query, final MessageService messages,
        final CollisionService collision, final ChamberedPearlTracker pearls, final ChatTags chatTags,
        final Settings settings
    ) {
        this.plugin = plugin;
        this.query = query;
        this.messages = messages;
        this.collision = collision;
        this.pearls = pearls;
        this.chatTags = chatTags;
        readSettings(settings);
    }

    private void readSettings(final Settings settings) {
        this.taskMode = settings.movementMode() == Settings.MovementMode.TASK;
        this.taskTicks = settings.movementTaskTicks();
        this.sweepEvery = Math.max(1, Math.round(20f / settings.movementTaskTicks()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        if (taskMode) {
            return;
        }
        final Location from = event.getFrom();
        final Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (EventGate.disabled(event)) {
            return;
        }
        final Player player = event.getPlayer();
        final ApplicableRegionSet fromSet = query.getApplicableRegions(from);
        final ApplicableRegionSet toSet = query.getApplicableRegions(to);

        if (processCrossing(player, fromSet, toSet)) {
            event.setCancelled(true);
            return;
        }
        applyState(player, toSet);
    }

    /**
     * Starts the polled movement tracker when {@code movement.mode: TASK} is configured; a no-op in
     * event mode. A global repeating task fans each player out to their own entity scheduler, so the
     * position read and any teleport happen on the thread that owns them.
     */
    public void start() {
        if (!taskMode) {
            return;
        }
        pollTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            final boolean sweep = ++sweepTick >= sweepEvery;
            if (sweep) {
                sweepTick = 0;
            }
            for (final Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> poll(player, sweep), null);
            }
        }, taskTicks, taskTicks);
    }

    /**
     * Re-reads the movement settings and restarts the poll to match, so {@code /uwg reload} can switch
     * between EVENT and TASK or retune the interval without a restart. Positions tracked for the old
     * mode are dropped: in EVENT mode they go stale immediately, and on the way back into TASK mode a
     * stale position would be judged as a crossing the player never made.
     */
    public void applySettings(final Settings settings) {
        stop();
        readSettings(settings);
        lastPosition.clear();
        sweepTick = 0;
        start();
    }

    /**
     * Cancels the poll. Paper drops a plugin's tasks on disable anyway, but holding the handle keeps
     * reload honest — without it a mode switch would leave the previous poll running alongside the new
     * one, double-enforcing every crossing.
     */
    public void stop() {
        final ScheduledTask task = pollTask;
        if (task != null) {
            task.cancel();
            pollTask = null;
        }
    }

    /**
     * One polled sample for a player. Compares the current block against the last sampled one without
     * allocating, and only on a change resolves regions and runs the crossing. A denied crossing is
     * undone by teleporting back to the last accepted position — the task path has no event to cancel
     * — and the stored position is left untouched so the player cannot creep forward one interval at
     * a time.
     *
     * <p>On a sweep the region set is resolved even when the player has not moved, so anyone standing
     * inside a region that now refuses them is removed. That covers logging in inside one, a region
     * being created or its entry flag flipped around them, and any teleport another plugin performed
     * that landed them past the boundary between polls.
     */
    private void poll(final Player player, final boolean sweep) {
        final UUID uuid = player.getUniqueId();
        final Location last = lastPosition.get(uuid);
        final boolean sameBlock = last != null
            && last.getWorld() == player.getWorld()
            && last.getBlockX() == Location.locToBlock(player.getX())
            && last.getBlockY() == Location.locToBlock(player.getY())
            && last.getBlockZ() == Location.locToBlock(player.getZ());
        if (sameBlock && !sweep) {
            return;
        }
        if (EventGate.disabled(player.getWorld(), "PlayerMoveEvent")) {
            return;
        }

        if (sameBlock) {
            final ApplicableRegionSet here = query.getApplicableRegions(player);
            if (deniedInside(player, uuid, here)) {
                eject(player, uuid, last, here);
            } else {
                applyState(player, here);
            }
            return;
        }

        final Location current = player.getLocation();
        final ApplicableRegionSet toSet = query.getApplicableRegions(current);

        if (last == null || last.getWorld() != current.getWorld()) {
            if (deniedInside(player, uuid, toSet)) {
                eject(player, uuid, last, toSet);
                return;
            }
            lastPosition.put(uuid, current);
            applyState(player, toSet);
            return;
        }

        final ApplicableRegionSet fromSet = query.getApplicableRegions(last);
        if (processCrossing(player, fromSet, toSet)) {
            player.teleport(last);
            return;
        }
        lastPosition.put(uuid, current);
        applyState(player, toSet);
    }

    /**
     * Whether the player is standing somewhere the entry flag refuses them. Cheapest checks first:
     * wilderness and bypassing staff leave immediately, before any flag resolution.
     */
    private boolean deniedInside(final Player player, final UUID uuid, final ApplicableRegionSet set) {
        if (set.isEmpty() || Bypass.has(player)) {
            return false;
        }
        return !set.testState(Flags.ENTRY, uuid) && !isMember(set, uuid);
    }

    /**
     * Removes a player from a region that refuses them, preferring the last position they were
     * accepted at and falling back to world spawn. The fallback is re-checked, because a region whose
     * entry flag changed while someone stood in it makes their last accepted position denied too —
     * teleporting them back there would leave them stuck, re-ejected every sweep.
     */
    private void eject(
        final Player player, final UUID uuid, final @Nullable Location last, final ApplicableRegionSet set
    ) {
        Location target = last;
        if (target == null || deniedInside(player, uuid, query.getApplicableRegions(target))) {
            target = player.getWorld().getSpawnLocation();
        }
        dismountIfRiding(player);
        lastPosition.put(uuid, target);
        messages.sendFlag(player, set.queryValue(Flags.ENTRY_DENY_MESSAGE), "entry-denied");
        player.teleport(target);
    }

    /**
     * Entry/exit enforcement and per-region enter/leave effects for a player who moved from
     * {@code fromSet} to {@code toSet}. Returns true if the crossing was denied, leaving it to the
     * caller to undo the movement — the event path cancels, the polled path teleports back.
     */
    private boolean processCrossing(
        final Player player, final ApplicableRegionSet fromSet, final ApplicableRegionSet toSet
    ) {
        final boolean entering = !isInside(fromSet, toSet);
        final boolean leaving = !isInside(toSet, fromSet);
        if (!entering && !leaving) {
            return false;
        }

        final UUID uuid = player.getUniqueId();
        final boolean bypass = Bypass.has(player);

        if (!bypass && entering && !toSet.testState(Flags.ENTRY, uuid) && !isMember(toSet, uuid)) {
            dismountIfRiding(player);
            messages.sendFlag(player, toSet.queryValue(Flags.ENTRY_DENY_MESSAGE), "entry-denied");
            return true;
        }
        if (!bypass && leaving && !fromSet.testState(Flags.EXIT, uuid) && !isMember(fromSet, uuid)) {
            dismountIfRiding(player);
            messages.sendFlag(player, fromSet.queryValue(Flags.EXIT_DENY_MESSAGE), "exit-denied");
            return true;
        }
        if (!bypass && entering && !isMember(toSet, uuid) && levelDenied(player, toSet)) {
            messages.send(player, "entry-denied");
            return true;
        }

        if (entering) {
            for (int i = 0, n = toSet.size(); i < n; i++) {
                final ProtectedRegion region = toSet.get(i);
                if (!contains(fromSet, region)) {
                    onEnterRegion(player, region);
                }
            }
        }
        if (leaving) {
            for (int i = 0, n = fromSet.size(); i < n; i++) {
                final ProtectedRegion region = fromSet.get(i);
                if (!contains(toSet, region)) {
                    onLeaveRegion(player, region);
                }
            }
        }
        return false;
    }

    /**
     * Entry/exit enforcement for a living mount (pig, horse, strider) carrying a rider whose crossing
     * is not the one {@link #onMove} sees (the driver's crossing is, via the move-vehicle packet).
     * {@link #deniedCrossing} ejects and teleports the denied rider out — a mounted player is glued to
     * the vehicle, so a cancel alone does not hold them; the cancel here is just a cheap first stop.
     * The {@code riddenMounts} fast-path keeps this near-free when nobody is riding anything.
     * Per-region effects and continuous state still ride on {@link #onMove}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMountMove(final EntityMoveEvent event) {
        if (riddenMounts.isEmpty() || !event.hasChangedBlock()) {
            return;
        }
        final Entity mount = event.getEntity();
        if (!riddenMounts.contains(mount.getUniqueId())) {
            return;
        }
        if (EventGate.disabled(event)) {
            return;
        }
        if (deniedCrossing(mount, event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    /**
     * Same enforcement for boats and minecarts, which fire {@link VehicleMoveEvent} rather than
     * {@link EntityMoveEvent}. {@link #deniedCrossing} ejects the denied rider and teleports them out.
     * Vehicles register in {@code riddenMounts} on {@link EntityMountEvent} just like living mounts,
     * so the guard skips the work for driverless boats/minecarts, which fire this event just as often.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleMove(final VehicleMoveEvent event) {
        if (riddenMounts.isEmpty() || !riddenMounts.contains(event.getVehicle().getUniqueId())) {
            return;
        }
        if (EventGate.disabled(event)) {
            return;
        }
        deniedCrossing(event.getVehicle(), event.getFrom(), event.getTo());
    }

    /**
     * Shared entry/exit check for a moving vehicle/mount: every non-bypassing player passenger that
     * would cross a region boundary it may not is ejected and teleported back to {@code from}. A
     * mounted player is glued to the client-driven vehicle, so a plain cancel or a vehicle teleport
     * does not hold them — dismounting does. Returns true if any rider was denied (so the mount path
     * can also cancel its move). Cheap guards first: same-block and no-boundary-change exits before
     * any membership work.
     */
    private boolean deniedCrossing(final Entity vehicle, final Location from, final Location to) {
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()) {
            return false;
        }
        final List<Entity> passengers = vehicle.getPassengers();
        if (passengers.isEmpty()) {
            return false;
        }

        final ApplicableRegionSet fromSet = query.getApplicableRegions(from);
        final ApplicableRegionSet toSet = query.getApplicableRegions(to);
        final boolean entering = !isInside(fromSet, toSet);
        final boolean leaving = !isInside(toSet, fromSet);
        if (!entering && !leaving) {
            return false;
        }

        List<Player> denied = null;
        for (final Entity passenger : passengers) {
            if (!(passenger instanceof Player player) || Bypass.has(player)) {
                continue;
            }
            final UUID uuid = player.getUniqueId();
            if (entering && !toSet.testState(Flags.ENTRY, uuid) && !isMember(toSet, uuid)) {
                messages.sendFlag(player, toSet.queryValue(Flags.ENTRY_DENY_MESSAGE), "entry-denied");
            } else if (leaving && !fromSet.testState(Flags.EXIT, uuid) && !isMember(fromSet, uuid)) {
                messages.sendFlag(player, fromSet.queryValue(Flags.EXIT_DENY_MESSAGE), "exit-denied");
            } else {
                continue;
            }
            if (denied == null) {
                denied = new ArrayList<>(1);
            }
            denied.add(player);
        }
        if (denied == null) {
            return false;
        }
        for (final Player rider : denied) {
            rider.leaveVehicle();
            rider.teleportAsync(from);
        }
        return true;
    }

    /**
     * A cancelled {@link PlayerMoveEvent} reverts an on-foot player, but a mounted one is glued to
     * its vehicle — which the client drives — so the server's revert just snaps them back onto it.
     * Dismounting first lets the cancel's position revert actually hold. The driver of a mount/vehicle
     * produces this event through the move-vehicle packet, so this is the path that catches riding in.
     */
    private static void dismountIfRiding(final Player player) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
    }

    /**
     * Blocks mounting a vehicle that sits in a region the player may not enter (otherwise a player
     * could mount an animal standing inside a no-entry region and be carried in), and starts
     * tracking the mount so {@link #onMountMove} enforces its movement.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(final EntityMountEvent event) {
        if (EventGate.disabled(event)) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final Entity mount = event.getMount();
        if (!Bypass.has(player)) {
            final ApplicableRegionSet set = query.getApplicableRegions(mount);
            if (!set.testState(Flags.ENTRY, player.getUniqueId()) && !isMember(set, player.getUniqueId())) {
                event.setCancelled(true);
                messages.sendFlag(player, set.queryValue(Flags.ENTRY_DENY_MESSAGE), "entry-denied");
                return;
            }
        }
        riddenMounts.add(mount.getUniqueId());
    }

    @EventHandler
    public void onDismount(final EntityDismountEvent event) {
        if (event.getEntity() instanceof Player) {
            riddenMounts.remove(event.getDismounted().getUniqueId());
        }
    }

    private void onEnterRegion(final Player player, final ProtectedRegion region) {
        final String greeting = region.getFlag(Flags.GREETING);
        if (greeting != null) {
            player.sendMessage(messages.render(greeting, player));
        }
        runCommand(player, region.getFlag(Flags.COMMAND_ON_ENTRY), false);
        runCommand(player, region.getFlag(Flags.CONSOLE_COMMAND_ON_ENTRY), true);
        playSound(player, region.getFlag(Flags.PLAY_SOUNDS));
        teleport(player, region.getFlag(Flags.TELEPORT_ON_ENTRY));
        if (region.getFlag(Flags.CHAMBERED_ENDERPEARL) == State.DENY) {
            pearls.removeFor(player.getUniqueId());
        }
    }

    private void onLeaveRegion(final Player player, final ProtectedRegion region) {
        final String farewell = region.getFlag(Flags.FAREWELL);
        if (farewell != null) {
            player.sendMessage(messages.render(farewell, player));
        }
        runCommand(player, region.getFlag(Flags.COMMAND_ON_EXIT), false);
        runCommand(player, region.getFlag(Flags.CONSOLE_COMMAND_ON_EXIT), true);
        teleport(player, region.getFlag(Flags.TELEPORT_ON_EXIT));
    }

    /**
     * Dispatches an entry/exit command flag. Neither variant runs inline: a console command may touch
     * any world or player, so it goes to the global region thread, and a player command goes to that
     * player's own scheduler. Besides being the Folia-correct owning thread in each case, this keeps
     * an arbitrary command out of the middle of the move event we are still handling.
     */
    private void runCommand(final Player player, final @Nullable String raw, final boolean console) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        final String command = messages.expand(player, raw.replace("%player%", player.getName()));
        if (console) {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        } else {
            player.getScheduler().run(plugin, _ -> Bukkit.dispatchCommand(player, command), null);
        }
    }

    private void playSound(final Player player, final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        final String[] parts = raw.split(":");
        final String sound = parts[0].trim();
        final float volume = parts.length >= 2 ? parseFloat(parts[1], 1f) : 1f;
        final float pitch = parts.length >= 3 ? parseFloat(parts[2], 1f) : 1f;
        player.playSound(player, sound, volume, pitch);
    }

    /**
     * Teleports the moving player on their own entity scheduler (next tick), which is Folia-safe
     * and avoids re-entering the move event we are currently handling.
     */
    private void teleport(final Player player, final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        final Location target = Locations.parse(messages.expand(player, raw));
        if (target != null) {
            player.getScheduler().run(plugin, task -> player.teleport(target), null);
        }
    }

    private boolean levelDenied(final Player player, final ApplicableRegionSet toSet) {
        final int level = player.getLevel();
        final Integer min = levelThreshold(player, toSet.queryValue(Flags.ENTRY_MIN_LEVEL));
        if (min != null && level < min) {
            return true;
        }
        final Integer max = levelThreshold(player, toSet.queryValue(Flags.ENTRY_MAX_LEVEL));
        return max != null && level > max;
    }

    private @Nullable Integer levelThreshold(final Player player, final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(messages.expand(player, raw).trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static float parseFloat(final String value, final float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Applies the region's continuous player state (game-mode, walk/fly speed, flight) while inside,
     * restoring each to the value the player had before entering once the override no longer applies.
     */
    private void applyState(final Player player, final ApplicableRegionSet toSet) {
        final UUID uuid = player.getUniqueId();

        final GameMode mode = toSet.worldUses(Flags.GAME_MODE)
            ? parseGameMode(toSet.queryValue(Flags.GAME_MODE)) : null;
        if (mode != null) {
            if (player.getGameMode() != mode) {
                savedGameModes.putIfAbsent(uuid, player.getGameMode());
                player.setGameMode(mode);
            }
        } else {
            final GameMode saved = savedGameModes.remove(uuid);
            if (saved != null && player.getGameMode() != saved) {
                player.setGameMode(saved);
            }
        }

        final Double walk = toSet.worldUses(Flags.WALK_SPEED) ? toSet.queryValue(Flags.WALK_SPEED) : null;
        if (walk != null) {
            if (savedWalkSpeed.get(uuid) == null) {
                savedWalkSpeed.putIfAbsent(uuid, player.getWalkSpeed());
            }
            final float target = clampSpeed(walk.floatValue());
            if (player.getWalkSpeed() != target) {
                player.setWalkSpeed(target);
            }
        } else {
            final Float saved = savedWalkSpeed.remove(uuid);
            if (saved != null) {
                player.setWalkSpeed(saved);
            }
        }

        final Double fly = toSet.worldUses(Flags.FLY_SPEED) ? toSet.queryValue(Flags.FLY_SPEED) : null;
        if (fly != null) {
            if (savedFlySpeed.get(uuid) == null) {
                savedFlySpeed.putIfAbsent(uuid, player.getFlySpeed());
            }
            final float target = clampSpeed(fly.floatValue());
            if (player.getFlySpeed() != target) {
                player.setFlySpeed(target);
            }
        } else {
            final Float saved = savedFlySpeed.remove(uuid);
            if (saved != null) {
                player.setFlySpeed(saved);
            }
        }

        if (toSet.worldUses(Flags.FLY) && Boolean.TRUE.equals(toSet.queryValue(Flags.FLY))) {
            if (!player.getAllowFlight()) {
                savedAllowFlight.putIfAbsent(uuid, Boolean.FALSE);
                player.setAllowFlight(true);
            }
        } else {
            final Boolean saved = savedAllowFlight.remove(uuid);
            if (saved != null && !saved) {
                player.setAllowFlight(false);
            }
        }

        collision.set(player, toSet.worldUses(Flags.DISABLE_COLLISION)
            && Boolean.TRUE.equals(toSet.queryValue(Flags.DISABLE_COLLISION)));

        if (toSet.worldUses(Flags.CHAT_PREFIX) || toSet.worldUses(Flags.CHAT_SUFFIX)) {
            chatTags.setPrefix(uuid, toSet.queryValue(Flags.CHAT_PREFIX));
            chatTags.setSuffix(uuid, toSet.queryValue(Flags.CHAT_SUFFIX));
        }

        applyHidePlayers(player, uuid, toSet.worldUses(Flags.HIDE_PLAYERS)
            && Boolean.TRUE.equals(toSet.queryValue(Flags.HIDE_PLAYERS)));
    }

    /**
     * Hides every other online player from {@code player} while inside a hide-players region, and
     * restores them on leaving. State-transition based: the O(n) hide/show loop only runs when the
     * player crosses into or out of the hidden state, never per move.
     */
    private void applyHidePlayers(final Player player, final UUID uuid, final boolean shouldHide) {
        if (shouldHide) {
            if (hidden.add(uuid)) {
                for (final Player other : Bukkit.getOnlinePlayers()) {
                    if (other != player) {
                        player.hidePlayer(plugin, other);
                    }
                }
            }
        } else if (hidden.remove(uuid)) {
            for (final Player other : Bukkit.getOnlinePlayers()) {
                if (other != player) {
                    player.showPlayer(plugin, other);
                }
            }
        }
    }

    /**
     * On login, teleports the player out if they log in where the join-location flag is set, and
     * hides the newcomer from anyone currently inside a hide-players region so the late join does not
     * leak into their view. Both run on the relevant player's own scheduler for Folia safety.
     */
    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player joiner = event.getPlayer();

        if (!hidden.isEmpty()) {
            for (final UUID viewerId : hidden) {
                final Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer != joiner) {
                    viewer.getScheduler().run(plugin, task -> viewer.hidePlayer(plugin, joiner), null);
                }
            }
        }

        final String raw = query.queryValue(joiner, Flags.JOIN_LOCATION);
        if (raw != null && !raw.isBlank()) {
            final Location target = Locations.parse(messages.expand(joiner, raw));
            if (target != null) {
                joiner.getScheduler().run(plugin, task -> joiner.teleport(target), null);
            }
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        messages.clear(uuid);
        collision.set(player, false);
        pearls.clear(uuid);
        chatTags.clear(uuid);
        hidden.remove(uuid);
        lastPosition.remove(uuid);
        Bypass.clear(uuid);

        final GameMode mode = savedGameModes.remove(uuid);
        if (mode != null) {
            player.setGameMode(mode);
        }
        final Float walk = savedWalkSpeed.remove(uuid);
        if (walk != null) {
            player.setWalkSpeed(walk);
        }
        final Float fly = savedFlySpeed.remove(uuid);
        if (fly != null) {
            player.setFlySpeed(fly);
        }
        final Boolean allowFlight = savedAllowFlight.remove(uuid);
        if (allowFlight != null && !allowFlight) {
            player.setAllowFlight(false);
        }
    }

    private static float clampSpeed(final float value) {
        if (value < -1f) {
            return -1f;
        }
        return Math.min(value, 1f);
    }

    private static @Nullable GameMode parseGameMode(final @Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return GameMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Whether {@code region} is in {@code set}, by identity. Both sets in a crossing come from the
     * same world's manager, so a shared region is the same object — no id hashing, no set allocation
     * on the crossing path. Across a world change the objects differ even for equal names, which is
     * what we want: every region of the old world is left and every region of the new one entered.
     */
    private static boolean contains(final ApplicableRegionSet set, final ProtectedRegion region) {
        for (int i = 0, n = set.size(); i < n; i++) {
            if (set.get(i) == region) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMember(final ApplicableRegionSet set, final UUID uuid) {
        for (int i = 0, n = set.size(); i < n; i++) {
            if (set.get(i).isMember(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if every region in {@code inner} also appears in {@code outer} (no new boundary crossed).
     * Both sets come from the same world's manager, so a shared region is the same object — the small
     * lists are compared by identity directly, allocating nothing on the per-move hot path.
     */
    private static boolean isInside(final ApplicableRegionSet outer, final ApplicableRegionSet inner) {
        for (int i = 0, n = inner.size(); i < n; i++) {
            final ProtectedRegion region = inner.get(i);
            boolean matched = false;
            for (int j = 0, m = outer.size(); j < m; j++) {
                if (outer.get(j) == region) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
