package com.tricrotism.uworldguard.packet;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The seam the rest of the plugin uses to reach the packet layer.
 *
 * <p>PacketEvents is an optional dependency, so nothing here names a {@code com.github.retrooper}
 * type — loading this class on a server without PacketEvents must not fail. {@link PacketSink},
 * which does name them, is reachable only through the {@link Sink} it installs, and it installs one
 * only after uWorldGuard has seen the PacketEvents plugin.
 *
 * <p>{@link #ACTIVE} is the gate every caller reads first: one volatile boolean, false on every
 * server that does not have PacketEvents.
 */
@NullMarked
public final class PacketHooks {

    /**
     * Bukkit-typed view of the packet layer.
     */
    public interface Sink {

        /**
         * Publish the no-collision state of the player named {@code entry} to the clients that the
         * server-side scoreboard team does not reach. Must be called on the global region thread: it
         * reads every online player's scoreboard, which is server-wide state.
         */
        void collision(String entry, boolean disabled);
    }

    /**
     * True once PacketEvents is present and the sink is installed.
     */
    public static volatile boolean ACTIVE;

    private static volatile @Nullable Sink sink;

    private PacketHooks() {
    }

    static void install(final Sink installed) {
        sink = installed;
        ACTIVE = true;
    }

    static void uninstall() {
        ACTIVE = false;
        sink = null;
    }

    public static void collision(final String entry, final boolean disabled) {
        final Sink current = sink;
        if (current != null) {
            current.collision(entry, disabled);
        }
    }
}
