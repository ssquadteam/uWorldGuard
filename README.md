# uWorldGuard

World Guard... but out of this world.

Region protection for Paper and Folia. Claim an area, decide what's allowed inside it, and the
plugin enforces it. Nearly 100 flags, a GUI for people who don't like commands, and an API for
people who do.

|               |                          |
|---------------|--------------------------|
| **Minecraft** | 26.2                     |
| **Server**    | Paper (Folia-compatible) |
| **Java**      | 25 or newer              |
| **Version**   | 1.0.5                    |

---

## For server owners

### Installing

1. Drop `uWorldGuard-1.0.5.jar` into your `plugins/` folder.
2. Restart the server. **Not** `/reload` — this plugin loads at startup.
3. On first start it downloads a few libraries it needs. That's normal and only happens once.

That's it. Nothing else is required — no database to set up, no other plugins to install.

> **Java 25 is required.** If the server won't start and the log mentions `UnsupportedClassVersionError`,
> your Java is too old. Nothing else will fix it.

### Your first region in 60 seconds

```
/wg define spawn
```

...but first you need to select the area. Hold a **wooden axe** (the default wand), then:

- **Left-click** a block for corner 1
- **Right-click** a block for corner 2
- Run `/wg define spawn`

Now nobody but you can build there. To let a friend build too:

```
/wg addmember spawn Steve
```

To change what's allowed, open the menu instead of memorising flag names:

```
/wg menu spawn
```

> If you have **WorldEdit** installed, uWorldGuard uses your WorldEdit selection instead and the
> wooden axe wand is ignored. Both work — you don't have to choose.

### How regions actually decide things

Three ideas, and everything else follows from them:

**Owners and members.** Owners can do everything in the region and manage it. Members can build.
Everyone else is an outsider. Outsiders are the ones flags usually restrict.

**Flags.** Each flag is one rule — `pvp`, `mob-spawning`, `chest-access`. Set it and the plugin
enforces it. Unset flags fall back to normal server behaviour.

**Priority.** Regions overlap. The one with the **highest priority** wins.
A `pvp deny` shop inside a `pvp allow` arena works — give the shop a higher priority:

```
/wg priority shop 10
```

**Parents** let a region inherit its parent's flags, so you set a rule once and every child gets it:

```
/wg setparent shop mall
```

### Bypassing your own rules

Give yourself `uworldguard.bypass` and toggle it while you build:

```
/wg bypass
```

### Commands

Every command works as `/uworldguard`, `/uwg`, `/worldguard`, or `/wg`. Use whichever you like.

| Command                                                      | What it does                                         |
|--------------------------------------------------------------|------------------------------------------------------|
| `/wg`                                                        | List all commands                                    |
| `/wg define <id>`                                            | Create a box-shaped region from your selection       |
| `/wg define-cylinder <id> <radiusX> <radiusZ> <minY> <maxY>` | Create a cylinder where you're standing              |
| `/wg define-sphere <id> <radiusX> <radiusY> <radiusZ>`       | Create a sphere where you're standing                |
| `/wg define-polygon <id> <minY> <maxY>`                      | Create a polygon from a WorldEdit selection          |
| `/wg remove <id>`                                            | Delete a region                                      |
| `/wg list [page]`                                            | List regions in this world                           |
| `/wg here`                                                   | What region am I standing in?                        |
| `/wg info <id>`                                              | Show a region's owners, members, flags, priority     |
| `/wg flag <id> <flag> [value]`                               | Set a flag (leave value blank to clear it)           |
| `/wg priority <id> <priority>`                               | Set which region wins when they overlap              |
| `/wg setparent <id> [parent]`                                | Inherit flags from another region                    |
| `/wg removeparent <id>`                                      | Stop inheriting                                      |
| `/wg addowner` / `removeowner <id> <player>`                 | Manage owners                                        |
| `/wg addmember` / `removemember <id> <player>`               | Manage members                                       |
| `/wg menu [id]`                                              | Open the region browser, or one region's flag editor |
| `/wg settings`                                               | Edit messages and cooldowns in-game                  |
| `/wg bypass`                                                 | Toggle your own bypass                               |
| `/wg reload`                                                 | Reload config and messages                           |

### Permissions

| Node                           | Grants                       |
|--------------------------------|------------------------------|
| `uworldguard.region.define`    | All four `define-*` commands |
| `uworldguard.region.remove`    | `remove`                     |
| `uworldguard.region.list`      | `list`                       |
| `uworldguard.region.info`      | `info`, `here`               |
| `uworldguard.region.flag`      | `flag`                       |
| `uworldguard.region.priority`  | `priority`                   |
| `uworldguard.region.setparent` | `setparent`, `removeparent`  |
| `uworldguard.region.members`   | Owner and member commands    |
| `uworldguard.menu`             | `menu`                       |
| `uworldguard.settings`         | `settings`                   |
| `uworldguard.reload`           | `reload`                     |
| `uworldguard.bypass`           | Ignore region protection     |

Commands you lack permission for don't appear in tab-completion.

### Flags

Set with `/wg flag <region> <flag> <value>`, or through `/wg menu <region>` if you'd rather click.
Clear a flag by leaving the value off.

Value types you'll see:

| Type    | Accepts                                       | Example                                                  |
|---------|-----------------------------------------------|----------------------------------------------------------|
| State   | `allow` / `deny`                              | `/wg flag spawn pvp deny`                                |
| Boolean | `true` / `false`                              | `/wg flag spawn fly true`                                |
| Number  | a number                                      | `/wg flag spawn walk-speed 0.3`                          |
| Text    | any string (MiniMessage where it's a message) | `/wg flag spawn greeting "<green>Welcome!"`              |
| List    | comma-separated                               | `/wg flag spawn deny-item-drops DIAMOND,NETHERITE_INGOT` |

State flags also take a **group** with `-g`, limiting who the rule applies to:

```
/wg flag spawn pvp deny -g nonmembers
```

Groups: `all` · `members` · `owners` · `nonmembers` · `nonowners` · `none`.
WorldGuard's spellings work too, so `non-members` and `non_members` are both accepted.

<details>
<summary><b>Protection</b> — who can touch what (20 flags)</summary>

`build` · `block-break` · `block-place` · `interact` · `use` · `chest-access` · `pvp` ·
`damage-animals` · `fall-damage` · `ride` · `sleep` · `tnt` · `lighter` · `end-crystal-place` ·
`end-crystal-interact` · `worldedit` · `pistons` · `passthrough` · `entity-item-frame-destroy` ·
`entity-painting-destroy`

`passthrough` is the odd one: it makes a region *not* apply protection, letting lower-priority
regions decide instead.
</details>

<details>
<summary><b>Entry &amp; exit</b> — who gets in, and what happens when they do (13 flags)</summary>

`entry` · `exit` · `entry-min-level` · `entry-max-level` · `player-count-limit` ·
`teleport-on-entry` · `teleport-on-exit` · `command-on-entry` · `command-on-exit` ·
`console-command-on-entry` · `console-command-on-exit` · `respawn-location` · `join-location`

`command-on-*` runs as the player; `console-command-on-*` runs as the server.
`entry-min-level` / `entry-max-level` accept a number, or a PlaceholderAPI `%placeholder%` if you
have PAPI installed — useful for gating on a level from another plugin.
</details>

<details>
<summary><b>Mobs &amp; explosions</b> (8 flags)</summary>

`mob-spawning` · `mob-damage` · `creeper-explosion` · `other-explosion` · `enderman-grief` ·
`ghast-fireball` · `mob-drops` · `exp-drops`
</details>

<details>
<summary><b>Environment</b> — fire, fluids, growth, decay (14 flags)</summary>

`fire-spread` · `lava-fire` · `lava-flow` · `water-flow` · `snow-fall` · `snow-melt` · `ice-form` ·
`ice-melt` · `leaf-decay` · `crop-growth` · `vine-growth` · `crop-trample` · `frostwalker` ·
`chunk-unload`
</details>

<details>
<summary><b>Movement</b> (5 flags)</summary>

`enderpearl` · `chorus-fruit-teleport` · `glide` · `nether-portals` · `chambered-enderpearl`

With **GSit** installed, four more appear here: `sit`, `playersit`, `pose`, `crawl`.
</details>

<details>
<summary><b>Messages &amp; sound</b> (7 flags)</summary>

`greeting` · `farewell` · `chat-prefix` · `chat-suffix` · `entry-deny-message` ·
`exit-deny-message` · `play-sounds`

All message flags are [MiniMessage](https://docs.advntr.dev/minimessage/) — `<red>`, `<bold>`,
gradients, the lot. `chat-prefix` / `chat-suffix` wrap what a player says while they're inside.
</details>

<details>
<summary><b>Player state</b> — applied continuously while inside (15 flags)</summary>

`invincible` · `godmode` · `heal-amount` · `heal-min-health` · `heal-max-health` · `game-mode` ·
`give-effects` · `blocked-effects` · `hide-players` · `walk-speed` · `fly-speed` · `fly` ·
`keep-inventory` · `keep-exp` · `disable-collision`
</details>

<details>
<summary><b>Items &amp; commands</b> (15 flags)</summary>

`disable-completely` · `disable-throw` · `wind-charge` · `villager-trade` · `permit-workbenches` ·
`inventory-craft` · `deny-item-drops` · `deny-item-pickup` · `item-durability` ·
`allow-block-place` · `deny-block-place` · `allow-block-break` · `deny-block-break` ·
`blocked-cmds` · `allowed-cmds`

The `allow-*` / `deny-*` block lists are the fine-grained escape hatch: `deny-block-break` blocks
those materials **even for members**, and `allow-block-break` permits them **even for outsiders**.
Handy for a survival spawn where anyone may harvest crops but nobody may break stone.

`blocked-cmds` is a deny-list. `allowed-cmds`, if you set it, is exclusive — anything not on the
list is refused.
</details>

### Configuration

`config.yml` ships with comments explaining every option. The three that matter:

**Storage.** YAML by default — one file per world, no setup. Switch to SQL if you'd rather:

```yaml
storage:
    type: sql
    auto-save-minutes: 5
    sql:
        enabled: true
        url: "jdbc:sqlite:plugins/uWorldGuard/regions.db"
```

**Movement detection.** This is the setting to reach for if your server is struggling.
`PlayerMoveEvent` fires many times per second per player, and it's the most expensive thing any
region plugin does.

```yaml
movement:
    mode: EVENT          # or TASK
    task-interval-ticks: 4
```

| Mode              | Cost scales with          | Trade-off                                                                                                                         |
|-------------------|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `EVENT` (default) | how much players move     | Exact — a denied entry is cancelled before the player moves                                                                       |
| `TASK`            | player count and interval | Cheaper and predictable, but a denied crossing rubber-bands the player back afterwards, and effects can lag by up to one interval |

`TASK` also sweeps roughly once a second for anyone standing inside a region that refuses them —
which catches logging in inside a no-entry region, or a region being created around someone.
Neither is a crossing, so neither mode would otherwise notice.

**Per-world event skipping.** If another plugin owns interactions in a creative or minigame world,
tell uWorldGuard to stay out of the way there:

```yaml
worlds:
    creative:
        events:
            whitelist-mode: false     # false = skip the listed events
            disabled:
                - BlockBreakEvent
                - BlockPlaceEvent
```

Events are named by their Bukkit class. Apply changes with `/wg reload`.

### Messages

`messages.yml` controls what players are told. Every entry is MiniMessage, and every entry can be
turned off by setting it to `false` or `""`.

```yaml
cooldown-seconds: 3

messages:
    no-permission: "<red>You don't have permission to do that here."
    entry-denied: "<red>You cannot enter this area."
    exit-denied: "<red>You cannot leave this area."
```

`no-permission` is the shared "you can't do that" message. To word it differently for one specific
flag, add `no-permission-<flag>`:

```yaml
messages:
    no-permission: "<red>You don't have permission to do that here."
    no-permission-chest-access: "<red>This container is locked."
    no-permission-block-break: false          # silence just this one
```

The two levels disable independently — a per-flag `false` silences only that flag, while disabling
`no-permission` silences everything that has no override of its own. `cooldown-seconds` stops the
same message repeating at a player who's spam-clicking.

Flags that can send a denial: `block-break`, `block-place`, `interact`, `chest-access`,
`end-crystal-place`, `end-crystal-interact`, `villager-trade`, `permit-workbenches`,
`inventory-craft`, `disable-completely`, `disable-throw`, `deny-item-drops`, `blocked-cmds`.

> Editing a message through `/wg settings` rewrites `messages.yml` and **drops the comments** in it.
> Your values are safe; the explanatory comments aren't.

### Optional integrations

All optional — install them or don't, nothing breaks either way.

| Plugin             | What it adds                                                                                                                   |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------|
| **WorldEdit**      | Use WorldEdit selections instead of the built-in wand; required for `define-polygon`                                           |
| **PlaceholderAPI** | `%placeholders%` in message flags and in `entry-min-level` / `entry-max-level`                                                 |
| **GSit**           | Four extra flags — `sit`, `playersit`, `pose`, `crawl` — enforced by uWorldGuard, since GSit can't see region flags on its own |

---

## For developers

### Getting the API

The API module is published to your local Maven repo:

```bash
./gradlew :api:publishToMavenLocal
```

```kotlin
repositories { mavenLocal() }

dependencies {
    compileOnly("com.tricrotism:uworldguard-api:1.0.5")
}
```

`compileOnly` is correct — the API classes ship **inside** the uWorldGuard jar and are loaded from
its classloader at runtime. Don't shade them.

Declare the dependency so load order is guaranteed:

```yaml
# paper-plugin.yml
dependencies:
    server:
        uWorldGuard:
            load: BEFORE
            required: true
            join-classpath: true
```

### Querying regions

```java
RegionQuery query = UWorldGuardApi.createQuery();

// Can this player build here?
boolean canBuild = query.testBuild(location, player);

// Is a state flag allowed here? (respects group qualifiers when you pass the player)
boolean pvp = query.testState(location, Flags.PVP, player);

// Read a typed flag value — null when unset
String greeting = query.queryValue(location, Flags.GREETING);
```

`UWorldGuardApi.isAvailable()` tells you whether the plugin is enabled; `regionContainer()` and
`createQuery()` throw `IllegalStateException` if it isn't. The container is also registered with
Bukkit's services manager if you prefer that lookup:

```java
RegionContainer container = getServer().getServicesManager().load(RegionContainer.class);
```

For repeated checks at one spot, resolve the set once instead of calling `query` per flag:

```java
ApplicableRegionSet set = query.getApplicableRegions(block);
if(!set.

testState(Flags.BLOCK_BREAK, player.getUniqueId())){
        // denied
        }
```

`RegionQuery` overloads accept `Location`, `Block`, `Entity`, or raw `(World, x, y, z)` — prefer the
raw form in hot paths to skip constructing a `Location`.

### Registering your own flags

Flags are a registry, so your plugin can add its own and they'll show up in commands, tab-completion
and the GUI like any built-in:

```java
public static final StateFlag MY_FLAG =
        Flags.register(FlagCategory.PROTECTION, new StateFlag("my-flag", true));
```

Register during your plugin's load/enable, before regions are queried. Registering a name that's
already taken throws `IllegalStateException`.

Flag types available: `StateFlag` (allow/deny + group), `BooleanFlag`, `IntegerFlag`, `DoubleFlag`,
`StringFlag`, `StringSetFlag`, `MaterialSetFlag`, `PotionEffectSetFlag`. Subclass `Flag<T>` for
anything else — you implement `parse`, `marshal` and `unmarshal`.

Each registered flag gets a dense `getIndex()`, stable for the JVM's lifetime, so you can key a
bitset on it rather than hashing.

### Region types

`ProtectedCuboidRegion`, `ProtectedCylinderRegion`, `ProtectedSphereRegion`,
`ProtectedPolygonRegion`, and `GlobalProtectedRegion` (a whole world, priority-wise below
everything). All extend `ProtectedRegion`. Get a world's `RegionManager` from
`RegionContainer.get(world)` — it returns `null` if that world's regions aren't loaded, so check.

### Threading

**This plugin is Folia-compatible, which constrains how you call it.**

- Region queries are thread-safe and allocation-light; call them from any thread.
- Anything you *do* with the answer — moving a player, changing a block, opening an inventory —
  belongs on the region thread that owns that location, via `RegionScheduler` or the entity's own
  `EntityScheduler`.
- There is no single main thread. `Bukkit.getScheduler()` and `BukkitRunnable` are broken under
  Folia; don't reach for them.

### Building

```bash
./gradlew shadowJar
```

Output: `plugin/build/libs/uWorldGuard-<version>.jar`. Requires JDK 25.

```bash
./gradlew runServer     # test server on the target Minecraft version
```

The project is two modules: `api/` (public, no NMS, published as `uworldguard-api`) and `plugin/`
(implementation). Cloud, InvUI, Caffeine and the SQLite driver are **not** shaded — they're
downloaded at boot by `UWorldGuardLoader`. Only bStats is shaded, relocated out of the way.

> **InvUI is version-coupled.** It's the one dependency tied to a specific Minecraft release, so a
> given uWorldGuard build targets one Minecraft version. Bumping Minecraft means bumping
> `invui` in `gradle/libs.versions.toml`, the coordinate in `UWorldGuardLoader`, the Paper
> dev bundle, and `api-version` in `paper-plugin.yml` together.

---

## License

See [LICENSE.md](LICENSE.md).
