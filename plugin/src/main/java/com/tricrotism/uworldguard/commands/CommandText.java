package com.tricrotism.uworldguard.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NullMarked;

/**
 * Shared chat styling for commands, so every message uses one colour vocabulary and a player can tell
 * at a glance what part of a line is a literal, a value they must supply, and one they may omit.
 *
 * <p>Syntax strings are built as components rather than run through MiniMessage: they contain
 * {@code <id>}-style placeholders, which MiniMessage would try to parse as tags.
 */
@NullMarked final class CommandText {

    /**
     * Fixed words typed as-is.
     */
    static final TextColor LITERAL = NamedTextColor.AQUA;
    /**
     * {@code <required>} arguments.
     */
    static final TextColor REQUIRED = NamedTextColor.GOLD;
    /**
     * {@code [optional]} arguments.
     */
    static final TextColor OPTIONAL = NamedTextColor.DARK_AQUA;
    /**
     * Brackets and separators — dim, so the names inside stand out.
     */
    static final TextColor PUNCTUATION = NamedTextColor.DARK_GRAY;
    /**
     * Explanatory prose.
     */
    static final TextColor DESCRIPTION = NamedTextColor.GRAY;
    /**
     * A field's value in an info card.
     */
    static final TextColor VALUE = NamedTextColor.WHITE;
    /**
     * A region id, wherever it appears.
     */
    static final TextColor REGION = NamedTextColor.AQUA;

    private CommandText() {}

    /**
     * Colours one command's syntax: literals, {@code <required>} and {@code [optional]} arguments each
     * get their own colour, with the brackets dimmed so the argument names read cleanly.
     */
    static Component syntax(final String raw) {
        Component out = Component.empty();
        boolean first = true;
        for (final String token : raw.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            if (!first) {
                out = out.append(Component.space());
            }
            first = false;
            out = out.append(token(token));
        }
        return out;
    }

    private static Component token(final String token) {
        final char open = token.charAt(0);
        final char close = token.charAt(token.length() - 1);
        if (open == '<' && close == '>') {
            return bracketed('<', token.substring(1, token.length() - 1), '>', REQUIRED);
        }
        if (open == '[' && close == ']') {
            return bracketed('[', token.substring(1, token.length() - 1), ']', OPTIONAL);
        }

        // note: this was done because cloud renders aliases as "a|b|c", dim the separators so the first name reads as the command.
        if (token.indexOf('|') >= 0) {
            Component out = Component.empty();
            final String[] parts = token.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    out = out.append(Component.text("|", PUNCTUATION));
                }
                out = out.append(Component.text(parts[i], LITERAL));
            }
            return out;
        }
        return Component.text(token, LITERAL);
    }

    private static Component bracketed(
        final char open, final String name, final char close, final TextColor colour
    ) {
        return Component.text()
            .append(Component.text(open, PUNCTUATION))
            .append(Component.text(name, colour))
            .append(Component.text(close, PUNCTUATION))
            .build();
    }

    /**
     * A label/value row for an info card, padded so values line up in a column.
     */
    static Component field(final String label, final Component value) {
        return Component.text()
            .append(Component.text("  " + pad(label), DESCRIPTION))
            .append(value)
            .build();
    }

    static Component field(final String label, final String value) {
        return field(label, Component.text(value, VALUE));
    }

    private static String pad(final String label) {
        final StringBuilder sb = new StringBuilder(label);
        while (sb.length() < 10) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Makes a component run {@code command} when clicked and explain itself on hover.
     */
    static Component runnable(final Component text, final String command, final String hover) {
        return text
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, DESCRIPTION)));
    }

    /**
     * Makes a component pre-fill the chat box with {@code command} — for syntax that still needs
     * arguments filled in, where running it immediately would just error.
     */
    static Component suggestable(final Component text, final String command, final String hover) {
        return text
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, DESCRIPTION)));
    }

    /**
     * The bar that opens a section of output.
     */
    static Component header(final String title) {
        return Component.text()
            .append(Component.text("▎ ", NamedTextColor.AQUA))
            .append(Component.text(title, NamedTextColor.WHITE))
            .build();
    }
}
