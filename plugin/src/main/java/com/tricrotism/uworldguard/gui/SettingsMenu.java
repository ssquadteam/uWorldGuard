package com.tricrotism.uworldguard.gui;

import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.text.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * InvUI editor for the configurable messages and their cooldown. Each entry and the cooldown are
 * edited via a chat prompt; changes are persisted to messages.yml and applied live by
 * {@link MessageService}.
 */
@NullMarked
public final class SettingsMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final MessageService messages;
    private final ChatInputService chatInput;

    public SettingsMenu(final Plugin plugin, final MessageService messages, final ChatInputService chatInput) {
        this.plugin = plugin;
        this.messages = messages;
        this.chatInput = chatInput;
    }

    public void open(final Player player) {
        final PagedGui<Item> gui = PagedGui.items()
            .setStructure(
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "< T C . . . . . >")
            .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
            .addIngredient('<', new PageButtons.Previous())
            .addIngredient('>', new PageButtons.Next())
            .addIngredient('T', cooldownItem())
            .addIngredient('C', MenuItems.close())
            .setContent(messageItems())
            .build();

        Window.single()
            .setViewer(player)
            .setTitle(MenuItems.wrap(Messages.format("<dark_gray>Messages")))
            .setGui(gui)
            .build()
            .open();
    }

    private List<Item> messageItems() {
        final List<Item> items = new ArrayList<>();
        for (final String key : messages.keys()) {
            items.add(MenuItems.clickable(
                () -> {
                    final String value = messages.raw(key);
                    return new ItemBuilder(Material.PAPER)
                        .setDisplayName(MenuItems.wrap(MM.deserialize("<!i><yellow>" + key)))
                        .addLoreLines(
                            MenuItems.wrap(MM.deserialize("<!i><gray>Value: <white>"
                                + (value == null || value.isBlank() ? "<disabled>" : value))),
                            MenuItems.wrap(Messages.format("<!i><dark_gray>Click to edit (chat)")));
                },
                (_, _, player) -> promptMessage(player, key)));
        }
        return items;
    }

    private void promptMessage(final Player player, final String key) {
        player.closeInventory();
        player.sendMessage(Messages.format("<gray>Type the new text for <aqua>" + key
            + "</aqua>, <red>false</red> to disable, or <red>cancel</red>."));
        chatInput.await(player.getUniqueId(), value -> {
            messages.setMessage(key, value);
            player.sendMessage(Messages.format("<green>Updated <aqua>" + key + "</aqua>."));
            open(player);
        });
    }

    private Item cooldownItem() {
        return MenuItems.clickable(
            () -> new ItemBuilder(Material.CLOCK)
                .setDisplayName(MenuItems.wrap(Messages.format("<!i><yellow>Message cooldown")))
                .addLoreLines(
                    MenuItems.wrap(MM.deserialize("<!i><gray>Seconds: <white>" + messages.cooldownSeconds())),
                    MenuItems.wrap(Messages.format("<!i><dark_gray>Click to edit (chat)"))),
            (_, _, player) -> promptCooldown(player));
    }

    private void promptCooldown(final Player player) {
        player.closeInventory();
        player.sendMessage(Messages.format("<gray>Type the cooldown in seconds, or <red>cancel</red>."));
        chatInput.await(player.getUniqueId(), value -> {
            try {
                messages.setCooldownSeconds(Long.parseLong(value.trim()));
                player.sendMessage(Messages.format("<green>Cooldown updated."));
            } catch (final NumberFormatException e) {
                player.sendMessage(Messages.format("<red>Not a number."));
            }
            open(player);
        });
    }
}
