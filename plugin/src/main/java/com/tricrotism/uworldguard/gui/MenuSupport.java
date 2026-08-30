package com.tricrotism.uworldguard.gui;

import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MenuSupport {

    private static final boolean AVAILABLE = detect();

    private MenuSupport() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    public static void install(final Plugin plugin) {
        xyz.xenondevs.invui.InvUI.getInstance().setPlugin(plugin);
    }

    private static boolean detect() {
        try {
            Class.forName("xyz.xenondevs.invui.InvUI");
            return true;
        } catch (final ClassNotFoundException absent) {
            return false;
        }
    }
}
