# CdrWaystone

Native Waystone plugin for **Vephilim**, built for Paper 1.21.11 and Java 21.

CdrWaystone is a clean-room implementation. It does not copy source code from the previous Waystones plugin. ItemsAdder is used only as the visual asset provider for the Vephilim Waystone models.

## v0.1.1 Stability Patch

Core gameplay from v0.1.0 remains intact, with production-safety improvements:

- Vanilla Lodestone automatically becomes a CdrWaystone.
- 19 Vephilim ItemsAdder skins supported.
- Native `ItemDisplay` visual renderer using ItemsAdder `CustomStack` at runtime.
- Per-Waystone visual UUID markers prevent nearby models from deleting each other.
- Two-block collision: Lodestone base + plugin-owned Barrier top collision.
- Collision ownership is persisted so cleanup only touches CdrWaystone-managed barriers.
- Waystone Key with PDC identity and persistent target UUID.
- Right-click a Waystone with an unbound key to bind it.
- Sneak + right-click another Waystone to relink a bound key.
- Right-click elsewhere with a bound key to warp after a configurable countdown.
- Damage cancellation and optional movement cancellation.
- Safe-arrival search around the destination.
- Obsidian below a Waystone suppresses it.
- Respawn Anchor below a Waystone powers travel when configured.
- Optional cross-world travel.
- Portal sickness chance.
- Name Tag renaming.
- Atomic `waystones.yml` writes with `waystones.yml.bak` backup.
- Chunk-load restoration plus periodic self-healing maintenance.
- Explosion and piston protection to prevent registry/world desync.
- Stale Waystone records are automatically pruned when their Lodestone is gone.

## Requirements

- Paper 1.21.11
- Java 21
- ItemsAdder for 3D visuals
- The `vephilim_waystones` ItemsAdder content pack

The plugin intentionally uses ItemsAdder through runtime reflection. This keeps the CdrWaystone JAR from having a hard compile-time dependency on a specific ItemsAdder API release.

## Installation / Upgrade

1. Remove the old third-party Waystones plugin JAR before testing. Two Waystone systems must not listen to the same Lodestone interactions.
2. Install `CdrWaystone-0.1.1.jar` into `plugins/`.
3. Keep the `vephilim_waystones` content folder in `plugins/ItemsAdder/contents/`.
4. Run `/iazip` and make sure the resource pack is applied.
5. Restart the server.
6. Existing CdrWaystone v0.1.0 data is migrated automatically; new config defaults are merged into the existing config.
7. Use `/cws getkey <player>` for the first test key.

## Commands

- `/cws getkey [player] [amount]`
- `/cws skin <skin>`
- `/cws skins`
- `/cws refresh` — force a visual/collision refresh and prune loaded stale entries
- `/cws reload`
- `/cws info`
- `/cws remove`

## Permissions

- `cdrwaystone.use` default `true`
- `cdrwaystone.skin` default `true`
- `cdrwaystone.admin` default `op`

## Stability controls

`config.yml` exposes:

- `protection.explosions`
- `protection.pistons`
- `stability.health-check-seconds`
- `collision.enabled`

The health check only inspects loaded chunks, so unloaded worlds/chunks are not force-loaded just to maintain Waystones.

## Supported skins

`andesite`, `blackstone`, `calcite`, `deepslate`, `divine`, `divine_bricks`, `end_stone`, `ice`, `mossy`, `mud_bricks`, `nether_bricks`, `polished_calcite`, `portstone`, `sandy`, `sculk`, `sea_stone`, `sharestone`, `tuff`, `tuff_bricks`.

## Important asset note

The Java code in this repository is original CADERA work. Third-party visual assets remain under their own license. Do not assume the MENKIESTES license relicenses third-party textures/models.
