package com.tricrotism.uworldguard.integration;

import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.config.EventGate;
import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.FlagCategory;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.StateFlag;
import com.tricrotism.uworldguard.region.RegionQuery;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Optional GSit integration. When GSit is installed we register region flags mirroring its
 * sit / playersit / pose / crawl actions and enforce them ourselves — GSit cannot see uWorldGuard's
 * flags, so it never consults them on its own.
 *
 * <p>Flag registration ({@link #registerFlags()}) references no GSit class, so it is safe to call
 * after a plain presence check and must run before regions load so stored values resolve. Enforcement
 * binds GSit's cancellable {@code Pre*} events by class name via {@code Class.forName}; the handler
 * bodies use only standard Bukkit types ({@link PlayerEvent} / {@link EntityEvent} / {@link
 * Cancellable}), so no GSit type is referenced at compile time.
 */
@NullMarked
public final class GSitIntegration implements Listener {


    private final RegionQuery query;

    public GSitIntegration(final RegionQuery query) {
        this.query = query;
    }

    public static boolean isPresent(final Server server) {
        return server.getPluginManager().getPlugin("GSit") != null;
    }

    /**
     * Registers the GSit flags if they are not already present. Idempotent.
     */
    public static void registerFlags() {
        if (Flags.get("sit") != null) {
            return;
        }
        Flags.register(FlagCategory.MOVEMENT, new StateFlag("sit", true));
        Flags.register(FlagCategory.MOVEMENT, new StateFlag("playersit", true));
        Flags.register(FlagCategory.MOVEMENT, new StateFlag("pose", true));
        Flags.register(FlagCategory.MOVEMENT, new StateFlag("crawl", true));
    }

    public void register(final Plugin plugin) {
        bind(plugin, "dev.geco.gsit.api.event.PreEntitySitEvent", stateFlag("sit"), true);
        bind(plugin, "dev.geco.gsit.api.event.PrePlayerPlayerSitEvent", stateFlag("playersit"), false);
        bind(plugin, "dev.geco.gsit.api.event.PrePlayerPoseEvent", stateFlag("pose"), false);
        bind(plugin, "dev.geco.gsit.api.event.PrePlayerCrawlEvent", stateFlag("crawl"), false);
    }

    private void bind(final Plugin plugin, final String className,
                      final @Nullable StateFlag flag, final boolean entityEvent) {
        if (flag == null) {
            return;
        }
        final Class<? extends Event> eventClass;
        try {
            eventClass = Class.forName(className).asSubclass(Event.class);
        } catch (final ClassNotFoundException e) {
            plugin.getLogger().warning("GSit event " + className + " not found; '"
                + flag.getName() + "' will not be enforced.");
            return;
        }
        final EventExecutor executor = (listener, event) -> handle(event, flag, entityEvent);
        plugin.getServer().getPluginManager()
            .registerEvent(eventClass, this, EventPriority.HIGH, executor, plugin, true);
    }

    private void handle(final Event event, final StateFlag flag, final boolean entityEvent) {
        if (EventGate.disabled(event)) {
            return;
        }
        final Player player;
        if (entityEvent) {
            if (!(((EntityEvent) event).getEntity() instanceof Player sitter)) {
                return;
            }
            player = sitter;
        } else {
            player = ((PlayerEvent) event).getPlayer();
        }
        if (Bypass.has(player)) {
            return;
        }
        if (!query.testState(player, flag) && event instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
    }

    private static @Nullable StateFlag stateFlag(final String name) {
        final Flag<?> flag = Flags.get(name);
        return flag instanceof StateFlag stateFlag ? stateFlag : null;
    }
}
