package com.tricrotism.uworldguard.migration;

import com.tricrotism.uworldguard.domain.DefaultDomain;
import com.tricrotism.uworldguard.flags.Flag;
import com.tricrotism.uworldguard.flags.Flags;
import com.tricrotism.uworldguard.flags.RegionGroup;
import com.tricrotism.uworldguard.region.*;
import com.tricrotism.uworldguard.util.BlockVector3;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reads an existing WorldGuard installation's YAML region files
 * ({@code plugins/WorldGuard/worlds/<world>/regions.yml}) and imports them into a
 * {@link RegionManager}. WorldGuard's flag names are mirrored by {@link Flags}, so flags
 * round-trip by name; cuboid, poly2d and global regions are supported (cylinder/sphere are
 * uWorldGuard-only and never appear in WorldGuard data). Per-flag region groups
 * ({@code <flag>-group}) and legacy name-based domain entries are not migrated.
 *
 * <p>Pure parsing + in-memory population — no Bukkit world access — so it is safe to run on
 * the async scheduler. The caller decides persistence (mark dirty, save on cycle).
 */
@NullMarked
public final class WorldGuardImporter {

    /**
     * WorldGuard flag names that uWorldGuard implements under a different name. Only exact
     * behavioural equivalents belong here — a flag whose uWorldGuard counterpart is broader or
     * narrower is deliberately left to be reported as unmapped rather than silently widened.
     */
    private static final Map<String, String> FLAG_ALIASES = Map.of(
        "block-trampling", "crop-trample",
        "wind-charge-burst", "wind-charge",
        "frosted-ice-form", "frostwalker"
    );

    /**
     * Matches a legacy colour/format code, so conversion only runs on values that carry one.
     */
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");

    /**
     * Outcome of importing one world. {@code conflicts} lists the ids that already existed and
     * were left untouched (only populated when not overwriting); {@code skipped} counts regions
     * whose shape WorldGuard supports but uWorldGuard does not parse from this format;
     * {@code unmappedFlags} maps each WorldGuard flag name with no uWorldGuard equivalent to how
     * many regions used it, and {@code groupQualifiers} counts {@code <flag>-group} entries, which
     * uWorldGuard has no concept of. The last two exist so a migration cannot quietly lose
     * configuration — previously any unknown flag was dropped with no trace.
     *
     * <p>{@code warnings} carries the same idea one level down: an entry per individual thing that
     * was understood but could not be applied — a flag whose value will not parse, a domain entry
     * that is not a UUID, a parent that never arrived. Those are per-region and hand-fixable, so
     * they name the region rather than being counted, and the caller logs them to console.
     */
    public record Result(
        int imported, List<String> conflicts, int skipped,
        Map<String, Integer> unmappedFlags, int groupQualifiers, List<String> warnings
    ) {}

    private final File worldsDir;

    /**
     * @param pluginsDir the server's {@code plugins} directory (the data folder's parent)
     */
    public WorldGuardImporter(final File pluginsDir) {
        this.worldsDir = new File(new File(pluginsDir, "WorldGuard"), "worlds");
    }

    public boolean hasData(final String worldName) {
        return regionsFile(worldName).exists();
    }

    /**
     * Import the WorldGuard regions for {@code worldName} into {@code manager}.
     *
     * @param overwrite replace regions whose id already exists; otherwise existing ids are
     *                  reported as conflicts and left untouched
     * @return the per-world outcome, or {@code null} if WorldGuard has no data for this world
     */
    public @Nullable Result importWorld(
        final String worldName, final RegionManager manager, final boolean overwrite
    ) throws Exception {
        final File file = regionsFile(worldName);
        if (!file.exists()) {
            return null;
        }

        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(file.toPath()));
        final ConfigurationSection root = yaml.getConfigurationSection("regions");
        if (root == null) {
            return new Result(0, List.of(), 0, Map.of(), 0, List.of());
        }

        int imported = 0;
        int skipped = 0;
        final List<String> conflicts = new ArrayList<>();
        final Map<String, String> parents = new HashMap<>();
        final Map<String, Integer> unmapped = new TreeMap<>();
        final List<String> warnings = new ArrayList<>();
        final int[] groupQualifiers = {0};

        for (final String id : root.getKeys(false)) {
            final ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }

            final ProtectedRegion region = buildRegion(id, sec);
            if (region == null) {
                skipped++;
                warnings.add("region '" + id + "': shape type '" + sec.getString("type", "cuboid")
                    + "' is unsupported or its coordinates are malformed — region not imported");
                continue;
            }

            final ProtectedRegion existing = manager.getRegion(region.getId());
            if (existing != null && !overwrite && !isUntouchedGlobal(existing)) {
                conflicts.add(region.getId());
                continue;
            }

            region.setPriority(sec.getInt("priority", 0));
            readDomain(sec.getConfigurationSection("owners"), region.getOwners(), id, "owner", warnings);
            readDomain(sec.getConfigurationSection("members"), region.getMembers(), id, "member", warnings);
            readFlags(sec.getConfigurationSection("flags"), region, unmapped, groupQualifiers, warnings);
            manager.addRegion(region);
            imported++;

            final String parent = sec.getString("parent");
            if (parent != null) {
                parents.put(region.getId().toLowerCase(Locale.ROOT), parent);
            }
        }

        for (final Map.Entry<String, String> entry : parents.entrySet()) {
            final ProtectedRegion child = manager.getRegion(entry.getKey());
            if (child == null) {
                continue;
            }
            final ProtectedRegion parent = manager.getRegion(entry.getValue());
            if (parent == null) {
                warnings.add("region '" + entry.getKey() + "': parent '" + entry.getValue()
                    + "' does not exist here (not imported, or in another world) — parent not set");
                continue;
            }
            try {
                child.setParent(parent);
            } catch (final IllegalArgumentException e) {
                warnings.add("region '" + entry.getKey() + "': parent '" + entry.getValue()
                    + "' was rejected (" + e.getMessage() + ") — parent not set");
            }
        }

        return new Result(imported, conflicts, skipped, unmapped, groupQualifiers[0], warnings);
    }

    /**
     * Whether an existing region is only the placeholder global that every world gets at load.
     *
     * <p>Without this the global region is a guaranteed conflict on every migration — it always
     * already exists — so WorldGuard's {@code __global__} flags were silently dropped unless the
     * admin happened to pass {@code --overwrite}. That is the one region whose flags apply
     * server-wide, so losing it quietly is the worst possible default. A global that already carries
     * flags or members is real configuration and is still treated as a conflict.
     */
    private static boolean isUntouchedGlobal(final ProtectedRegion existing) {
        return existing instanceof GlobalProtectedRegion
            && existing.getFlags().isEmpty()
            && existing.getOwners().isEmpty()
            && existing.getMembers().isEmpty();
    }

    private @Nullable ProtectedRegion buildRegion(final String id, final ConfigurationSection sec) {
        final String type = sec.getString("type", "cuboid").toLowerCase(Locale.ROOT);
        switch (type) {
            case "cuboid" -> {
                final BlockVector3 min = readVec(sec.getConfigurationSection("min"));
                final BlockVector3 max = readVec(sec.getConfigurationSection("max"));
                if (min == null || max == null) {
                    return null;
                }
                return new ProtectedCuboidRegion(id, min, max);
            }
            case "poly2d" -> {
                final List<BlockVector3> points = readPoints(sec);
                if (points.size() < 3) {
                    return null;
                }
                return new ProtectedPolygonRegion(
                    id, points, floor(sec.getDouble("min-y")), floor(sec.getDouble("max-y")));
            }
            case "global" -> {
                return new GlobalProtectedRegion();
            }
            default -> {
                return null;
            }
        }
    }

    private static @Nullable BlockVector3 readVec(final @Nullable ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        return BlockVector3.at(floor(sec.getDouble("x")), floor(sec.getDouble("y")), floor(sec.getDouble("z")));
    }

    private static List<BlockVector3> readPoints(final ConfigurationSection sec) {
        final List<BlockVector3> points = new ArrayList<>();
        for (final Map<?, ?> point : sec.getMapList("points")) {
            if (point.get("x") instanceof Number x && point.get("z") instanceof Number z) {
                points.add(BlockVector3.at(floor(x.doubleValue()), 0, floor(z.doubleValue())));
            }
        }
        return points;
    }

    private static void readDomain(
        final @Nullable ConfigurationSection sec, final DefaultDomain domain,
        final String id, final String role, final List<String> warnings
    ) {
        if (sec == null) {
            return;
        }
        for (final String raw : sec.getStringList("unique-ids")) {
            try {
                domain.addPlayer(UUID.fromString(raw));
            } catch (final IllegalArgumentException _) {
                warnings.add("region '" + id + "': " + role + " entry '" + raw
                    + "' is not a valid UUID — not added");
            }
        }
        for (final String group : sec.getStringList("groups")) {
            domain.addGroup(group);
        }
        // Pre-UUID WorldGuard installs stored bare player names here. There is nothing to resolve
        // them against offline, so they are dropped — but silently dropping a region's owners is
        // exactly the kind of loss an admin needs told about.
        final List<String> names = sec.getStringList("players");
        if (!names.isEmpty()) {
            warnings.add("region '" + id + "': " + names.size() + " legacy name-based " + role
                + "(s) cannot be migrated (" + String.join(", ", names) + ") — re-add them by hand");
        }
    }

    /**
     * Copies one region's flags across, resolving WorldGuard's name to ours directly, then through
     * {@link #FLAG_ALIASES}. Anything still unresolved is counted rather than dropped in silence, so
     * the caller can tell the admin exactly what did not come over.
     */
    private static void readFlags(
        final @Nullable ConfigurationSection sec, final ProtectedRegion region,
        final Map<String, Integer> unmapped, final int[] groupQualifiers, final List<String> warnings
    ) {
        if (sec == null) {
            return;
        }
        for (final String key : sec.getKeys(false)) {
            if (key.endsWith("-group")) {
                final Flag<?> target = resolve(key.substring(0, key.length() - "-group".length()));
                final RegionGroup group = RegionGroup.parse(String.valueOf(sec.get(key)));
                if (target != null && group != null) {
                    region.setFlagGroup(target, group);
                } else {
                    groupQualifiers[0]++;
                    warnings.add("region '" + region.getId() + "': group qualifier '" + key + "' = '"
                        + sec.get(key) + "' could not be read — that flag now applies to everyone");
                }
                continue;
            }
            final Flag<?> flag = resolve(key);
            if (flag == null) {
                unmapped.merge(key, 1, Integer::sum);
                continue;
            }
            applyFlag(region, flag, sec.get(key), warnings);
        }
    }

    /**
     * Resolves a WorldGuard flag name to ours, directly then through {@link #FLAG_ALIASES}.
     */
    private static @Nullable Flag<?> resolve(final String name) {
        final Flag<?> direct = Flags.get(name);
        if (direct != null) {
            return direct;
        }
        final String alias = FLAG_ALIASES.get(name.toLowerCase(Locale.ROOT));
        return alias == null ? null : Flags.get(alias);
    }

    private static <T> void applyFlag(
        final ProtectedRegion region, final Flag<T> flag, final @Nullable Object stored,
        final List<String> warnings
    ) {
        if (stored == null) {
            return;
        }
        final T value = flag.unmarshal(convertLegacyColours(stored));
        // The flag name matched, so this is a value uWorldGuard's type could not read — a material
        // that no longer exists, a number where a state belongs. Worth naming individually: the
        // region keeps every other flag, so the admin only has to fix this one.
        if (value == null) {
            warnings.add("region '" + region.getId() + "': flag '" + flag.getName() + "' value '"
                + stored + "' could not be read — flag not set");
            return;
        }
        region.setFlag(flag, value);
    }

    /**
     * WorldGuard stores message flags (greeting, farewell, deny messages) with legacy {@code &}/{@code §}
     * colour codes, which uWorldGuard renders through MiniMessage and would otherwise print verbatim —
     * an imported "&aWelcome" showing up as literal "&aWelcome" rather than green text. Values that
     * carry no legacy code are returned untouched, so MiniMessage input already in the file is safe.
     */
    private static Object convertLegacyColours(final Object stored) {
        if (!(stored instanceof String text) || !LEGACY_CODE.matcher(text).find()) {
            return stored;
        }
        return MiniMessage.miniMessage().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace('§', '&')));
    }

    private static int floor(final double value) {
        return (int) Math.floor(value);
    }

    private File regionsFile(final String worldName) {
        return new File(new File(worldsDir, worldName), "regions.yml");
    }
}
