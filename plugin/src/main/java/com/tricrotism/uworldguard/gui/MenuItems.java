package com.tricrotism.uworldguard.gui;

import com.tricrotism.uworldguard.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jspecify.annotations.NullMarked;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.inventoryaccess.component.ComponentWrapper;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.function.Supplier;

/**
 * Shared static GUI items and InvUI helpers.
 */
@NullMarked final class MenuItems {

    private MenuItems() {
    }

    /**
     * Wraps an Adventure component for InvUI's item and window title builders.
     */
    static ComponentWrapper wrap(final Component component) {
        return new AdventureComponentWrapper(component);
    }

    /**
     * Click callback that also receives the clicked item so it can {@code notifyWindows()}.
     */
    @FunctionalInterface
    interface Clicked {
        void handle(Item item, ClickType clickType, Player player);
    }

    /**
     * An item whose provider is re-evaluated on every render, so {@code notifyWindows()} refreshes
     * its appearance. Replaces InvUI 2.x's {@code Item.builder()} which is absent in 1.x.
     */
    static Item clickable(final Supplier<? extends ItemProvider> provider, final Clicked onClick) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider() {
                return provider.get();
            }

            @Override
            public void handleClick(final ClickType clickType, final Player player, final InventoryClickEvent event) {
                onClick.handle(this, clickType, player);
            }
        };
    }

    static Item close() {
        return clickable(
            () -> new ItemBuilder(Material.BARRIER).setDisplayName(wrap(Messages.format("<!i><red>Close"))),
            (_, _, player) -> player.closeInventory());
    }
}
