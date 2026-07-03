package com.tricrotism.uworldguard.listeners;

import com.tricrotism.uworldguard.text.ChatTags;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Applies the chat-prefix / chat-suffix region flags by wrapping the player's rendered chat with the
 * prefix/suffix current at their location (cached on move by {@code MovementListener}). The flag
 * values are parsed as MiniMessage. This runs on the async chat thread, so it only touches the
 * concurrent {@link ChatTags} cache — never Bukkit API.
 */
@NullMarked
public final class ChatListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ChatTags chatTags;

    public ChatListener(final ChatTags chatTags) {
        this.chatTags = chatTags;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        if (chatTags.isEmpty()) {
            return;
        }
        final UUID uuid = event.getPlayer().getUniqueId();
        final String prefix = chatTags.prefix(uuid);
        final String suffix = chatTags.suffix(uuid);
        if (prefix == null && suffix == null) {
            return;
        }
        final Component pre = prefix == null ? Component.empty() : MM.deserialize(prefix);
        final Component suf = suffix == null ? Component.empty() : MM.deserialize(suffix);
        final ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
            pre.append(previous.render(source, sourceDisplayName, message, viewer)).append(suf));
    }
}
