# wg-compat — WorldGuard 7 API compatibility layer

An independent, clean-room reimplementation of WorldGuard 7's **public API surface**
(`com.sk89q.worldguard.*`) that delegates to uWorldGuard's engine, so plugins compiled against real WorldGuard link and
work at runtime.

**License: LGPL-3.0-or-later** (see [COPYING](COPYING) and [COPYING.LESSER](COPYING.LESSER); both also ship inside the
plugin jar at `META-INF/licenses/wg-compat/`). The rest of uWorldGuard keeps its own license. WorldGuard is a project of
the EngineHub team; this module is not affiliated with or endorsed by EngineHub or sk89q — "WorldGuard" is used only to
describe compatibility.

## Clean-room policy

Every file in this module is written without reference to WorldGuard's source code.

**Allowed sources, and what we actually used:**

- **WorldGuard's published javadoc jar** (`worldguard-core` / `worldguard-bukkit` 7.0.18, classifier `javadoc`, from
  `maven.enginehub.org`) — mechanically distilled into
  [`src/test/resources/worldguard-7.0.18-api.txt`](src/test/resources/worldguard-7.0.18-api.txt), which lists type kinds
  and member signatures only. Declaring code, nothing more.
- The published documentation at worldguard.enginehub.org — flag names are retyped from the
  [flags reference](https://worldguard.enginehub.org/en/latest/regions/flags/), and resolution semantics come from the
  priorities / flag-calculation pages.
- Observed *usage* in third-party consumer plugins (what they call, what shapes they expect).

**Forbidden, and not used:** WorldGuard's source code in any form, and decompilation of its binary jars. No
implementation was read; every method body here was written from the declarations and the documented behaviour. The
binary artifact is not a build input at all.

WorldEdit is an ordinary compile-only dependency (provided at runtime by the real WorldEdit/FAWE plugin) — its types are
used, never reimplemented.

## Structure

- `com.sk89q.worldguard.**` — the API surface consumers link against.
- `com.tricrotism.uworldguard.wgcompat.**` — bridge internals (binding, flag bridging, region adapters, diagnostics). A
  source file imports either `com.sk89q.*` or `com.tricrotism.*`, never both; the other side is referenced by
  fully-qualified name (three simple-name collisions:
  `BlockVector3`, `Flags`, `ProtectedRegion`).

## Activation

The layer activates only when uWorldGuard enables **and** WorldEdit/FAWE is installed **and** no other plugin claims the
name "WorldGuard". Inactive, every entry point throws
`IllegalStateException` with a clear message; the `com.sk89q` classes still load safely.

## Documented behavioural deltas vs real WorldGuard

| Area                     | Delta                                                                                                                                                                                                                                                                                                                                                   |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Version string           | `getPlugin("WorldGuard").getDescription().getVersion()` reports uWorldGuard's version (1.x), never "7.x". Consumers that gate on `startsWith("7")` will decline to hook.                                                                                                                                                                                |
| Name-based domains       | The engine stores UUIDs and groups only. `DefaultDomain.getPlayers()` / `toPlayersString()` resolve names from the server's offline-player cache and omit unresolvable entries; `addPlayer(String name)` resolves via the cache and warns (once) when it cannot.                                                                                        |
| Null-default state flags | Engine `StateFlag` defaults are always ALLOW or DENY. Custom flags registered with a WG null default are backed by a DENY-default engine flag and resolved with WG null-default semantics shim-side.                                                                                                                                                    |
| Event bridge             | Consumers cannot force-ALLOW an action uWorldGuard denied (WG's `Result.ALLOW` override); DENY works. (Phase C)                                                                                                                                                                                                                                         |
| MoveType fidelity        | `EMBARK`/`GLIDE`/`SWIM` style move types map to `MOVE`; session handler ticks run at 20-tick cadence on the player's region thread (Folia). (Phase C)                                                                                                                                                                                                   |
| getPoints()              | Cuboids answer with their 4 footprint corners; uWorldGuard-only shapes (cylinder/sphere) answer with bounding-box corners and type `POLYGON`.                                                                                                                                                                                                           |
| Unshipped surface        | `commands.*`, storage drivers, blacklist, migration, reports are not present — consumers touching them get `NoClassDefFoundError`/`NoSuchMethodError`, and stub hits are visible in `/uwg compat`. The full list of omitted members is in [`API-NOTES.md`](API-NOTES.md) and is enforced by a baseline in `src/test/resources/known-api-omissions.txt`. |
| Sessions                 | Session objects and handlers are created and registered, but nothing drives them yet — no movement/tick dispatch. Handlers will not fire until a later release.                                                                                                                                                                                         |
| `BukkitPlayer`           | Not shipped as a class. `LocalPlayer` is implemented by a dynamic proxy over WorldEdit's adapted player, so `new BukkitPlayer(...)` will not link; `wrapPlayer(...)` is the supported path.                                                                                                                                                             |
