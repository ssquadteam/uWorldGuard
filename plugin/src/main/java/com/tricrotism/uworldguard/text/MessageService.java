package com.tricrotism.uworldguard.text;

import com.tricrotism.uworldguard.flags.Flag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Configurable, cooldown-throttled messages backed by {@code messages.yml}. Each entry can be
 * recoloured, given PlaceholderAPI placeholders, or disabled (empty / {@code false}). A per-player,
 * per-key cooldown (default 3s) suppresses spam from rapidly repeated denials.
 *
 * <p>Reloadable and editable at runtime ({@link #reload()}, {@link #setMessage}, {@link
 * #setCooldownSeconds}) so the settings GUI and {@code /uwg reload} can change messages live.
 * Thread-safe: the template and cooldown maps are concurrent.
 */
@NullMarked
public final class MessageService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String DENY_KEY = "no-permission";
    private static final String DENY_PREFIX = DENY_KEY + "-";
    private static final Function<UUID, Map<String, AtomicLong>> NEW_MAP = k -> new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final File file;
    private final Map<String, String> templates = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, AtomicLong>> lastSent = new ConcurrentHashMap<>();
    private volatile long cooldownMillis;
    /**
     * Whether any {@code no-permission-<flag>} entry exists; lets {@link #sendDeny} skip building
     * a per-flag key when nobody has configured one.
     */
    private volatile boolean denyOverrides;
    private final boolean placeholderApi;

    public MessageService(final Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.placeholderApi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        load();
    }

    private void load() {
        final FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        this.cooldownMillis = Math.max(0L, cfg.getLong("cooldown-seconds", 3L)) * 1000L;
        templates.clear();
        final ConfigurationSection section = cfg.getConfigurationSection("messages");
        boolean overrides = false;
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                templates.put(key, section.getString(key, ""));
                overrides |= key.startsWith(DENY_PREFIX);
            }
        }
        this.denyOverrides = overrides;
    }

    /**
     * Re-read messages.yml from disk.
     */
    public void reload() {
        load();
    }

    public Set<String> keys() {
        return new TreeSet<>(templates.keySet());
    }

    public @Nullable String raw(final String key) {
        return templates.get(key);
    }

    public long cooldownSeconds() {
        return cooldownMillis / 1000L;
    }

    public void setCooldownSeconds(final long seconds) {
        this.cooldownMillis = Math.max(0L, seconds) * 1000L;
        save();
    }

    public void setMessage(final String key, final String value) {
        templates.put(key, value);
        if (key.startsWith(DENY_PREFIX)) {
            this.denyOverrides = true;
        }
        save();
    }

    private void save() {
        final YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("cooldown-seconds", cooldownMillis / 1000L);
        for (final Map.Entry<String, String> entry : templates.entrySet()) {
            cfg.set("messages." + entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (final IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save messages.yml", e);
        }
    }

    /**
     * Deserialize a template, expanding PlaceholderAPI placeholders against {@code player} when the
     * plugin is present. No cooldown — for greetings/farewells that should always show.
     */
    public Component render(final String template, final @Nullable Player player, final TagResolver... resolvers) {
        if (resolvers.length == 0
            && (player == null || !placeholderApi || template.indexOf('%') < 0)) {
            return Messages.format(template);
        }
        final String text = placeholderApi && player != null ? PlaceholderSupport.expand(player, template) : template;
        return MM.deserialize(text, resolvers);
    }

    /**
     * Send a configurable message by key, honouring its cooldown. No-op when the entry is disabled
     * or the player is still on cooldown for that key.
     */
    public void send(final Player player, final String key, final TagResolver... resolvers) {
        dispatch(player, key, templates.get(key), resolvers);
    }

    /**
     * Send the denial message for {@code flag}: the per-flag override {@code no-permission-<flag>}
     * when messages.yml defines one, else the shared {@code no-permission} entry. The two levels
     * disable independently — a per-flag entry of {@code false} silences just that flag while the
     * shared message keeps working elsewhere, and disabling {@code no-permission} silences every
     * flag that has no override of its own.
     *
     * <p>Cooldown is keyed on whichever entry is used, so overriding a flag also gives it its own
     * throttle instead of sharing one with every other denial.
     */
    public void sendDeny(final Player player, final Flag<?> flag) {
        if (denyOverrides) {
            final String key = DENY_PREFIX + flag.getName();
            final String override = templates.get(key);
            if (override != null) {
                dispatch(player, key, override);
                return;
            }
        }
        dispatch(player, DENY_KEY, templates.get(DENY_KEY));
    }

    /**
     * Send a region-supplied message ({@code custom}) if set, else the configurable {@code fallbackKey}.
     * Cooldown is keyed on {@code fallbackKey} so a custom per-region message still can't spam.
     */
    public void sendFlag(final Player player, final @Nullable String custom, final String fallbackKey,
                         final TagResolver... resolvers) {
        final String template = custom != null && !custom.isBlank() ? custom : templates.get(fallbackKey);
        dispatch(player, fallbackKey, template, resolvers);
    }

    private void dispatch(final Player player, final String cooldownKey, final @Nullable String template,
                          final TagResolver... resolvers) {
        if (template == null || template.isBlank() || "false".equalsIgnoreCase(template)) {
            return;
        }
        if (onCooldown(player.getUniqueId(), cooldownKey)) {
            return;
        }
        player.sendMessage(render(template, player, resolvers));
    }

    private boolean onCooldown(final UUID uuid, final String key) {
        if (cooldownMillis <= 0L) {
            return false;
        }
        final long now = System.currentTimeMillis();
        final Map<String, AtomicLong> perPlayer = lastSent.computeIfAbsent(uuid, NEW_MAP);
        final AtomicLong slot = perPlayer.get(key);
        if (slot == null) {
            perPlayer.putIfAbsent(key, new AtomicLong(now));
            return false;
        }
        if (now - slot.get() < cooldownMillis) {
            return true;
        }
        slot.set(now);
        return false;
    }

    /**
     * Expand PlaceholderAPI placeholders against {@code player} when the plugin is present; returns
     * {@code text} unchanged otherwise. For non-message uses (commands, location/level flag values).
     */
    public String expand(final Player player, final String text) {
        return placeholderApi ? PlaceholderSupport.expand(player, text) : text;
    }

    /**
     * Drop a player's cooldown records (call on quit to avoid unbounded growth).
     */
    public void clear(final UUID uuid) {
        lastSent.remove(uuid);
    }
}
