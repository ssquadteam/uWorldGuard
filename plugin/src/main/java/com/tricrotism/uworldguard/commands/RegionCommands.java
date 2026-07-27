package com.tricrotism.uworldguard.commands;

import com.tricrotism.uworldguard.UWorldGuard;
import com.tricrotism.uworldguard.config.Bypass;
import com.tricrotism.uworldguard.domain.DefaultDomain;
import com.tricrotism.uworldguard.flags.*;
import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.gui.ChatInputService;
import com.tricrotism.uworldguard.gui.FlagMenu;
import com.tricrotism.uworldguard.gui.RegionMenu;
import com.tricrotism.uworldguard.gui.SettingsMenu;
import com.tricrotism.uworldguard.region.*;
import com.tricrotism.uworldguard.selection.Selection;
import com.tricrotism.uworldguard.selection.SelectionService;
import com.tricrotism.uworldguard.text.MessageService;
import com.tricrotism.uworldguard.text.Messages;
import com.tricrotism.uworldguard.util.BlockVector3;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.*;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Region management commands, registered through Cloud's annotation parser.
 */
@NullMarked
public final class RegionCommands {

    private final UWorldGuard plugin;
    private final RegionContainerImpl container;
    private final SelectionService selection;
    private final ChatInputService chatInput;
    private final MessageService messages;
    private @Nullable PaperCommandManager<Source> manager;

    public RegionCommands(
        final UWorldGuard plugin, final RegionContainerImpl container, final SelectionService selection,
        final ChatInputService chatInput, final MessageService messages
    ) {
        this.plugin = plugin;
        this.container = container;
        this.selection = selection;
        this.chatInput = chatInput;
        this.messages = messages;
    }

    public void register(final Object... extraHandlers) {
        this.manager = PaperCommandManager
            .builder(PaperSimpleSenderMapper.simpleSenderMapper())
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(plugin);
        final AnnotationParser<Source> parser = new AnnotationParser<>(manager, Source.class);
        parser.parse(this);
        for (final Object handler : extraHandlers) {
            parser.parse(handler);
        }
    }

    /**
     * Which section of the help output a command belongs to, keyed by its first literal. Anything not
     * listed falls into "Other", so a newly added command still shows up without touching this map.
     */
    private static final Map<String, String> HELP_GROUPS = Map.ofEntries(
        Map.entry("define", "Creating regions"),
        Map.entry("define-cylinder", "Creating regions"),
        Map.entry("define-sphere", "Creating regions"),
        Map.entry("define-polygon", "Creating regions"),
        Map.entry("remove", "Creating regions"),
        Map.entry("list", "Inspecting"),
        Map.entry("info", "Inspecting"),
        Map.entry("here", "Inspecting"),
        Map.entry("bypass", "Administration"),
        Map.entry("flag", "Configuring"),
        Map.entry("priority", "Configuring"),
        Map.entry("setparent", "Configuring"),
        Map.entry("removeparent", "Configuring"),
        Map.entry("addowner", "Owners & members"),
        Map.entry("removeowner", "Owners & members"),
        Map.entry("addmember", "Owners & members"),
        Map.entry("removemember", "Owners & members"),
        Map.entry("menu", "Menus"),
        Map.entry("settings", "Menus"),
        Map.entry("reload", "Administration"),
        Map.entry("migrate", "Administration"));

    private static final List<String> GROUP_ORDER = List.of(
        "Creating regions", "Inspecting", "Configuring", "Owners & members",
        "Menus", "Administration", "Other");

    @Command("uworldguard|uwg|worldguard|wg")
    @CommandDescription("Show this command list")
    public void help(final Source sender) {
        final PaperCommandManager<Source> mgr = this.manager;
        if (mgr == null) return;

        final Map<String, List<Component>> grouped = new HashMap<>();
        int total = 0;
        for (final org.incendo.cloud.Command<Source> command : mgr.commands()) {
            final List<CommandComponent<Source>> components = command.components();
            if (components.size() < 2) continue;
            if (!mgr.testPermission(sender, command.commandPermission()).allowed()) continue;

            final List<CommandComponent<Source>> arguments = components.subList(1, components.size());
            final String rendered = mgr.commandSyntaxFormatter().apply(sender, arguments, null);
            final String root = arguments.getFirst().name();
            final String syntax = "/uwg " + rendered;

            Component line = CommandText.suggestable(
                CommandText.syntax(syntax), syntax + " ", "Click to put this in your chat box");
            final org.incendo.cloud.description.CommandDescription description = command.commandDescription();
            if (!description.isEmpty()) {
                line = line
                    .append(Component.text("  —  ", CommandText.PUNCTUATION))
                    .append(Component.text(
                        description.description().textDescription(), CommandText.DESCRIPTION));
            }
            grouped.computeIfAbsent(HELP_GROUPS.getOrDefault(root, "Other"), _ -> new ArrayList<>())
                .add(line);
            total++;
        }

        Component message = Component.text()
            .append(Component.text("uWorldGuard", NamedTextColor.AQUA))
            .append(Component.text(" — " + total + " command" + (total == 1 ? "" : "s") + " available",
                CommandText.DESCRIPTION))
            .build();

        for (final String group : GROUP_ORDER) {
            final List<Component> lines = grouped.get(group);
            if (lines == null) continue;

            lines.sort(Comparator.comparing(line -> PlainTextComponentSerializer.plainText().serialize(line)));
            message = message.append(Component.newline()).append(CommandText.header(group));
            for (final Component line : lines) {
                message = message.append(Component.newline()).append(line);
            }
        }

        message = message.append(Component.newline()).append(
            Component.text("Colours: ", CommandText.DESCRIPTION)
                .append(Component.text("command", CommandText.LITERAL))
                .append(Component.text("  <", CommandText.PUNCTUATION))
                .append(Component.text("required", CommandText.REQUIRED))
                .append(Component.text(">  [", CommandText.PUNCTUATION))
                .append(Component.text("optional", CommandText.OPTIONAL))
                .append(Component.text("]", CommandText.PUNCTUATION)));

        sender.source().sendMessage(message);
    }

    @Command("uworldguard|uwg|worldguard|wg define <id>")
    @CommandDescription("Define a cuboid region from your selection")
    @Permission("uworldguard.region.define")
    public void define(final Source sender, @Argument("id") final String id) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final Selection sel = selection.getSelection(player);
        if (sel == null) {
            error(sender, "Make a selection first.");
            return;
        }

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
            return;
        }

        if (regionManager.hasRegion(id)) {
            error(sender, "A region named <aqua>" + id + "</aqua> already exists.");
            return;
        }

        create(sender, regionManager, new ProtectedCuboidRegion(id, sel.min(), sel.max()));
    }

    @Command("uworldguard|uwg|worldguard|wg define-cylinder <id> <radiusX> <radiusZ> <minY> <maxY>")
    @CommandDescription("Define a cylinder region at your location")
    @Permission("uworldguard.region.define")
    public void defineCylinder(
        final Source sender,
        @Argument("id") final String id,
        @Argument("radiusX") final int radiusX,
        @Argument("radiusZ") final int radiusZ,
        @Argument("minY") final int minY,
        @Argument("maxY") final int maxY
    ) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
            return;
        }

        @NotNull final Location loc = player.getLocation();
        try {
            create(sender, regionManager, new ProtectedCylinderRegion(
                id, loc.getBlockX(), loc.getBlockZ(), radiusX, radiusZ, minY, maxY));
        } catch (final IllegalArgumentException e) {
            error(sender, e.getMessage());
        }
    }

    @Command("uworldguard|uwg|worldguard|wg define-sphere <id> <radiusX> <radiusY> <radiusZ>")
    @CommandDescription("Define a sphere region at your location")
    @Permission("uworldguard.region.define")
    public void defineSphere(
        final Source sender,
        @Argument("id") final String id,
        @Argument("radiusX") final int radiusX,
        @Argument("radiusY") final int radiusY,
        @Argument("radiusZ") final int radiusZ
    ) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
            return;
        }

        final Location loc = player.getLocation();
        try {
            create(sender, regionManager, new ProtectedSphereRegion(
                id, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), radiusX, radiusY, radiusZ));
        } catch (final IllegalArgumentException e) {
            error(sender, e.getMessage());
        }
    }

    @Command("uworldguard|uwg|worldguard|wg define-polygon <id> <minY> <maxY>")
    @CommandDescription("Define a polygon region from your WorldEdit selection")
    @Permission("uworldguard.region.define")
    public void definePolygon(
        final Source sender,
        @Argument("id") final String id,
        @Argument("minY") final int minY,
        @Argument("maxY") final int maxY
    ) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
            return;
        }

        final List<BlockVector3> points = selection.getPolygon(player);
        if (points == null || points.size() < 3) {
            error(sender, "Select a polygon with WorldEdit first (//sel poly).");
            return;
        }

        try {
            create(sender, regionManager, new ProtectedPolygonRegion(id, points, minY, maxY));
        } catch (final IllegalArgumentException e) {
            error(sender, e.getMessage());
        }
    }

    private void create(
        final Source sender, final RegionManager regionManager, final ProtectedRegion region
    ) {
        if (regionManager.hasRegion(region.getId())) {
            error(sender, "A region named <aqua>" + region.getId() + "</aqua> already exists.");
            return;
        }

        regionManager.addRegion(region);
        success(sender, "Created region <aqua>" + region.getId() + "</aqua>.");
    }

    @Command("uworldguard|uwg|worldguard|wg remove <id>")
    @CommandDescription("Remove a region")
    @Permission("uworldguard.region.remove")
    public void remove(final Source sender, @Argument(value = "id", suggestions = "region-ids") final String id) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        if (GlobalProtectedRegion.ID.equalsIgnoreCase(id)) {
            error(sender, "The global region cannot be removed.");
            return;
        }

        if (regionManager.removeRegion(id) == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        success(sender, "Removed region <aqua>" + id + "</aqua>.");
    }

    /**
     * Regions shown per page of {@code /uwg list}, sized to leave the chat history readable.
     */
    private static final int PAGE_SIZE = 8;

    @Command("uworldguard|uwg|worldguard|wg list [page]")
    @CommandDescription("List regions in this world, a page at a time")
    @Permission("uworldguard.region.list")
    public void list(final Source sender, @Argument("page") final @Nullable Integer pageArg) {
        final int page = pageArg == null ? 1 : pageArg;
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        if (regionManager.size() == 0) {
            note(sender, "No regions in this world yet. Make a selection, then run /uwg define <name>.");
            return;
        }

        final List<ProtectedRegion> regions = new ArrayList<>(regionManager.getRegions());
        regions.sort(Comparator.comparing(ProtectedRegion::getId, String.CASE_INSENSITIVE_ORDER));

        final int pages = (regions.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        final int index = Math.clamp(page, 1, pages);
        final int from = (index - 1) * PAGE_SIZE;
        final int to = Math.min(from + PAGE_SIZE, regions.size());

        Component message = CommandText.header(regions.size()
                + (regions.size() == 1 ? " region in " : " regions in ") + world(sender))
            .append(Component.text("   page " + index + "/" + pages, CommandText.PUNCTUATION));

        for (int i = from; i < to; i++) {
            final ProtectedRegion region = regions.get(i);
            message = message.append(Component.newline()).append(CommandText.runnable(
                Component.text()
                    .append(Component.text("  • ", CommandText.PUNCTUATION))
                    .append(Component.text(region.getId(), CommandText.REGION))
                    .append(Component.text("  " + region.getType().name().toLowerCase(Locale.ROOT)
                            + ", priority " + region.getPriority()
                            + ", " + (region.getOwners().size() + region.getMembers().size()) + " trusted",
                        CommandText.DESCRIPTION))
                    .build(),
                "/uwg info " + region.getId(),
                "Click for details about " + region.getId()));
        }

        if (pages > 1) {
            message = message.append(Component.newline()).append(pager(index, pages));
        }
        sender.source().sendMessage(message);
    }

    /**
     * Previous/next controls for a paged listing. Both stay in place when unavailable but render dim
     * and inert, so the row does not jump around as you page through it.
     */
    private static Component pager(final int page, final int pages) {
        final Component previous = page > 1
            ? CommandText.runnable(Component.text("‹ prev", CommandText.LITERAL),
            "/uwg list " + (page - 1), "Page " + (page - 1))
            : Component.text("‹ prev", CommandText.PUNCTUATION);
        final Component next = page < pages
            ? CommandText.runnable(Component.text("next ›", CommandText.LITERAL),
            "/uwg list " + (page + 1), "Page " + (page + 1))
            : Component.text("next ›", CommandText.PUNCTUATION);
        return Component.text()
            .append(Component.text("  ", CommandText.DESCRIPTION))
            .append(previous)
            .append(Component.text("   ·   ", CommandText.PUNCTUATION))
            .append(next)
            .build();
    }

    private static String world(final Source sender) {
        return sender.source() instanceof Player player ? player.getWorld().getName() : "this world";
    }

    @Command("uworldguard|uwg|worldguard|wg here")
    @CommandDescription("Show the regions you are standing in")
    @Permission("uworldguard.region.info")
    public void here(final Source sender) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final ApplicableRegionSet set = container.createQuery().getApplicableRegions(player);
        final List<ProtectedRegion> regions = set.getRegions();
        if (regions.isEmpty()) {
            note(sender, "You are not standing in any region.");
            return;
        }

        final UUID uuid = player.getUniqueId();
        Component message = CommandText.header(regions.size()
            + (regions.size() == 1 ? " region here" : " regions here, strongest first"));
        for (final ProtectedRegion region : regions) {
            final String standing = region.isOwner(uuid) ? "owner"
                : region.isMember(uuid) ? "member" : "visitor";
            message = message.append(Component.newline()).append(CommandText.runnable(
                Component.text()
                    .append(Component.text("  • ", CommandText.PUNCTUATION))
                    .append(Component.text(region.getId(), CommandText.REGION))
                    .append(Component.text("  priority " + region.getPriority() + ", you are ",
                        CommandText.DESCRIPTION))
                    .append(Component.text(standing, "visitor".equals(standing)
                        ? CommandText.PUNCTUATION : CommandText.REQUIRED))
                    .build(),
                "/uwg info " + region.getId(),
                "Click for details about " + region.getId()));
        }
        message = message.append(Component.newline()).append(Component.text(
                "  You may build here: ", CommandText.DESCRIPTION))
            .append(set.canBuild(uuid)
                ? Component.text("yes", NamedTextColor.GREEN)
                : Component.text("no", NamedTextColor.RED));
        sender.source().sendMessage(message);
    }

    @Command("uworldguard|uwg|worldguard|wg bypass")
    @CommandDescription("Toggle your own region bypass off or on")
    @Permission(Bypass.NODE)
    public void bypass(final Source sender) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        if (Bypass.toggle(player)) {
            success(sender, "Bypass <green>on</green> — region protections no longer apply to you.");
        } else {
            note(sender, "Bypass <red>off</red> — you are treated as an ordinary player. "
                + "Run <aqua>/uwg bypass</aqua> again to restore it.");
        }
    }

    @Command("uworldguard|uwg|worldguard|wg info <id>")
    @CommandDescription("Show details about a region")
    @Permission("uworldguard.region.info")
    public void info(final Source sender, @Argument(value = "id", suggestions = "region-ids") final String id) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        final ProtectedRegion parent = region.getParent();
        final int flagCount = region.getFlags().size();

        Component card = CommandText.header("Region " + region.getId())
            .append(Component.newline())
            .append(CommandText.field("Type", region.getType().name().toLowerCase(Locale.ROOT)))
            .append(Component.newline())
            .append(CommandText.field("Priority", String.valueOf(region.getPriority())))
            .append(Component.newline())
            .append(CommandText.field("Parent", parent == null
                ? Component.text("none", CommandText.PUNCTUATION)
                : CommandText.runnable(Component.text(parent.getId(), CommandText.REGION),
                "/uwg info " + parent.getId(), "Click for details about " + parent.getId())))
            .append(Component.newline())
            .append(CommandText.field("Owners", String.valueOf(region.getOwners().size())))
            .append(Component.newline())
            .append(CommandText.field("Members", String.valueOf(region.getMembers().size())));

        card = card.append(Component.newline()).append(CommandText.field("Flags",
            CommandText.runnable(
                Component.text(flagCount + (flagCount == 0 ? " set" : " set — click to edit"),
                    CommandText.VALUE),
                "/uwg menu " + region.getId(),
                "Open the flag menu for " + region.getId())));

        sender.source().sendMessage(card);
    }

    @Command("uworldguard|uwg|worldguard|wg flag <id> <flag> [value]")
    @CommandDescription("Set or clear a flag on a region (-g to limit who it applies to)")
    @Permission("uworldguard.region.flag")
    public void flag(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument(value = "flag", suggestions = "flags") final String flagName,
        @org.incendo.cloud.annotations.Flag(value = "group", aliases = "g",
            suggestions = "flag-groups") final @Nullable String groupName,
        @Argument(value = "value", suggestions = "flag-values") @Greedy final @Nullable String value
    ) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        final Flag<?> flag = Flags.get(flagName);
        if (flag == null) {
            error(sender, "Unknown flag <aqua>" + flagName + "</aqua>.");
            return;
        }

        if (value == null) {
            region.setFlag(flag, null);
            region.setFlagGroup(flag, null);
            regionManager.markDirty();
            success(sender, "Cleared flag <aqua>" + flag.getName() + "</aqua>.");
            return;
        }

        RegionGroup group = null;
        if (groupName != null) {
            group = RegionGroup.parse(groupName);
            if (group == null) {
                error(sender, "Unknown group <aqua>" + groupName + "</aqua>. Use one of: "
                    + "all, members, owners, nonmembers, nonowners, none.");
                return;
            }
        }

        if (!applyFlag(region, flag, value)) {
            error(sender, "Invalid value for flag <aqua>" + flag.getName() + "</aqua>.");
            return;
        }
        if (groupName != null) {
            region.setFlagGroup(flag, group);
        }

        regionManager.markDirty();
        success(sender, "Set flag <aqua>" + flag.getName() + "</aqua> to <aqua>" + value + "</aqua>"
            + (group != null && group != RegionGroup.ALL
            ? " for <aqua>" + group.serialized() + "</aqua>" : "") + ".");
    }

    @Suggestions("flag-groups")
    public List<String> suggestFlagGroups(final CommandContext<Source> ctx, final String input) {
        return List.of("all", "members", "owners", "nonmembers", "nonowners", "none");
    }

    @Command("uworldguard|uwg|worldguard|wg priority <id> <priority>")
    @CommandDescription("Set a region's priority")
    @Permission("uworldguard.region.priority")
    public void priority(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument("priority") final int priority
    ) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        region.setPriority(priority);
        regionManager.markDirty();
        success(sender, "Set priority of <aqua>" + id + "</aqua> to <aqua>" + priority + "</aqua>.");
    }

    @Command("uworldguard|uwg|worldguard|wg setparent <id> [parent]")
    @CommandDescription("Set or clear a region's parent")
    @Permission("uworldguard.region.setparent")
    public void setParent(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument(value = "parent", suggestions = "region-ids") final @Nullable String parentId
    ) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        if (parentId == null) {
            region.setParent(null);
            regionManager.markDirty();
            success(sender, "Cleared the parent of <aqua>" + id + "</aqua>.");
            return;
        }

        final ProtectedRegion parent = regionManager.getRegion(parentId);
        if (parent == null) {
            error(sender, "No region named <aqua>" + parentId + "</aqua>.");
            return;
        }

        try {
            region.setParent(parent);
        } catch (final IllegalArgumentException _) {
            error(sender, "That would create a circular parent relationship.");
            return;
        }

        regionManager.markDirty();
        success(sender, "Set parent of <aqua>" + id + "</aqua> to <aqua>" + parentId + "</aqua>.");
    }

    @Command("uworldguard|uwg|worldguard|wg removeparent|unsetparent <id>")
    @CommandDescription("Remove a region's parent")
    @Permission("uworldguard.region.setparent")
    public void removeParent(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id
    ) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        if (region.getParent() == null) {
            error(sender, "Region <aqua>" + id + "</aqua> has no parent.");
            return;
        }

        region.setParent(null);
        regionManager.markDirty();
        success(sender, "Cleared the parent of <aqua>" + id + "</aqua>.");
    }

    @Command("uworldguard|uwg|worldguard|wg menu")
    @CommandDescription("Open the region menu")
    @Permission("uworldguard.menu")
    public void menu(final Source sender) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
            return;
        }

        new RegionMenu(plugin, player.getWorld(), regionManager, selection, chatInput).open(player);
    }

    @Command("uworldguard|uwg|worldguard|wg settings")
    @CommandDescription("Open the settings menu")
    @Permission("uworldguard.settings")
    public void settings(final Source sender) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        new SettingsMenu(plugin, messages, chatInput).open(player);
    }

    @Command("uworldguard|uwg|worldguard|wg reload")
    @CommandDescription("Reload messages and config")
    @Permission("uworldguard.reload")
    public void reload(final Source sender) {
        messages.reload();
        plugin.reloadSettings();
        success(sender, "Reloaded messages, config, and movement mode. "
            + "<gray>(Storage backend and wand item still need a restart.)");
    }

    @Command("uworldguard|uwg|worldguard|wg menu <id>")
    @CommandDescription("Open the flag menu for a region")
    @Permission("uworldguard.menu")
    public void menu(final Source sender, @Argument(value = "id", suggestions = "region-ids") final String id) {
        final Player player = asPlayer(sender);
        if (player == null) return;

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
            return;
        }

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        new FlagMenu(regionManager, region, chatInput).open(player);
    }

    @Command("uworldguard|uwg|worldguard|wg addowner <id> <player>")
    @CommandDescription("Add an owner to a region")
    @Permission("uworldguard.region.members")
    public void addOwner(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument(value = "player", suggestions = "players") final String playerName
    ) {
        member(sender, id, playerName, true, true);
    }

    @Command("uworldguard|uwg|worldguard|wg removeowner <id> <player>")
    @CommandDescription("Remove an owner from a region")
    @Permission("uworldguard.region.members")
    public void removeOwner(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument(value = "player", suggestions = "players") final String playerName
    ) {
        member(sender, id, playerName, true, false);
    }

    @Command("uworldguard|uwg|worldguard|wg addmember <id> <player>")
    @CommandDescription("Add a member to a region")
    @Permission("uworldguard.region.members")
    public void addMember(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument(value = "player", suggestions = "players") final String playerName
    ) {
        member(sender, id, playerName, false, true);
    }

    @Command("uworldguard|uwg|worldguard|wg removemember <id> <player>")
    @CommandDescription("Remove a member from a region")
    @Permission("uworldguard.region.members")
    public void removeMember(
        final Source sender,
        @Argument(value = "id", suggestions = "region-ids") final String id,
        @Argument(value = "player", suggestions = "players") final String playerName
    ) {
        member(sender, id, playerName, false, false);
    }

    private void member(
        final Source sender, final String id, final String playerName, final boolean owner, final boolean add
    ) {
        final RegionManager regionManager = managerFor(sender);
        if (regionManager == null) return;

        final ProtectedRegion region = regionManager.getRegion(id);
        if (region == null) {
            error(sender, "No region named <aqua>" + id + "</aqua>.");
            return;
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            final OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            final UUID uuid = target.getUniqueId();
            final DefaultDomain domain = owner ? region.getOwners() : region.getMembers();
            if (add) {
                domain.addPlayer(uuid);
            } else {
                domain.removePlayer(uuid);
            }

            regionManager.markDirty();
            success(sender, (add ? "Added " : "Removed ") + "<aqua>" + playerName + "</aqua> "
                + (add ? "to" : "from") + " " + (owner ? "owners" : "members") + " of <aqua>" + id + "</aqua>.");
        });
    }

    @Suggestions("region-ids")
    public List<String> suggestRegionIds(final CommandContext<Source> ctx, final String input) {
        if (!(ctx.sender().source() instanceof Player player)) return List.of();

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) return List.of();

        final List<String> ids = new ArrayList<>();
        for (final ProtectedRegion region : regionManager.getRegions()) {
            ids.add(region.getId());
        }
        return ids;
    }

    @Suggestions("flags")
    public List<String> suggestFlags(final CommandContext<Source> ctx, final String input) {
        final List<String> names = new ArrayList<>(Flags.all().size());
        for (final Flag<?> flag : Flags.all()) {
            names.add(flag.getName());
        }
        return names;
    }

    @Suggestions("players")
    public List<String> suggestPlayers(final CommandContext<Source> ctx, final String input) {
        final List<String> names = new ArrayList<>();
        for (final Player online : plugin.getServer().getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    /**
     * Value suggestions for {@code /uwg flag}. Where a flag has no closed set of values, the
     * suggestion is a shaped example rather than nothing, so the expected format is discoverable from
     * the command line instead of only from the menu's "Accepts:" line.
     */
    @Suggestions("flag-values")
    public List<String> suggestFlagValues(final CommandContext<Source> ctx, final String input) {
        final Flag<?> flag = Flags.get(ctx.getOrDefault("flag", ""));
        if (flag instanceof StateFlag) return List.of("allow", "deny");
        if (flag instanceof BooleanFlag) return List.of("true", "false");
        if (flag == Flags.GAME_MODE) return List.of("survival", "creative", "adventure", "spectator");
        if (flag instanceof StringSetFlag) return List.of("home,tp,spawn");
        if (flag instanceof PotionEffectSetFlag) return List.of("SPEED:1,NIGHT_VISION");
        if (flag instanceof MaterialSetFlag) return List.of("DIAMOND_SWORD,BOW");
        if (flag instanceof IntegerFlag || flag instanceof DoubleFlag) return List.of("1");
        if (flag == Flags.TELEPORT_ON_ENTRY || flag == Flags.TELEPORT_ON_EXIT
            || flag == Flags.RESPAWN_LOCATION || flag == Flags.JOIN_LOCATION) {
            return List.of("world,0,64,0");
        }

        return List.of();
    }

    private static <T> boolean applyFlag(final ProtectedRegion region, final Flag<T> flag, final String value) {
        final T parsed = flag.parse(value);
        if (parsed == null) return false;

        region.setFlag(flag, parsed);
        return true;
    }

    private @Nullable RegionManager managerFor(final Source sender) {
        final Player player = asPlayer(sender);
        if (player == null) return null;

        final RegionManager regionManager = container.get(player.getWorld());
        if (regionManager == null) {
            error(sender, "Regions are not loaded for this world.");
        }
        return regionManager;
    }

    private @Nullable Player asPlayer(final Source sender) {
        if (sender.source() instanceof Player player) return player;

        error(sender, "Only players can use this command.");
        return null;
    }

    private static void error(final Source sender, final String message) {
        sender.source().sendMessage(Messages.format("<red>" + message));
    }

    private static void success(final Source sender, final String message) {
        sender.source().sendMessage(Messages.format("<green>" + message));
    }

    private static void note(final Source sender, final String message) {
        sender.source().sendMessage(Messages.format("<gray>" + message));
    }
}
