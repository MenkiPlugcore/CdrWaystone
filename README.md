# CdrWaystone

Native Waystone plugin for **Vephilim**, built for Paper 1.21.11 and Java 21.

CdrWaystone is a clean-room implementation. It does not copy source code from the previous Waystones plugin. ItemsAdder is used only as the visual asset provider for the Vephilim Waystone models.

## v0.1.0

- Vanilla Lodestone automatically becomes a CdrWaystone.
- 19 Vephilim ItemsAdder skins supported.
- Native `ItemDisplay` visual renderer using ItemsAdder `CustomStack` at runtime.
- Two-block collision: Lodestone base + redirected Barrier top collision.
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
- YAML persistence.
- Chunk-load visual restoration.

## Requirements

- Paper 1.21.11
- Java 21
- ItemsAdder for 3D visuals
- The `vephilim_waystones` ItemsAdder content pack

The plugin intentionally uses ItemsAdder through runtime reflection. This keeps the CdrWaystone JAR from having a hard compile-time dependency on a specific ItemsAdder API release.

## Installation

1. Remove the old Waystones plugin JAR before testing. Two Waystone plugins must not listen to the same Lodestone interactions.
2. Install `CdrWaystone-0.1.0.jar` into `plugins/`.
3. Install the `vephilim_waystones` content folder into `plugins/ItemsAdder/contents/`.
4. Run `/iazip` and make sure the resource pack is applied.
5. Restart the server.
6. Use `/cws getkey <player>` for the first test key.

## Commands

- `/cws getkey [player] [amount]`
- `/cws skin <skin>`
- `/cws skins`
- `/cws refresh`
- `/cws reload`
- `/cws info`
- `/cws remove`

## Permissions

- `cdrwaystone.use` default `true`
- `cdrwaystone.skin` default `true`
- `cdrwaystone.admin` default `op`

## Supported skins

`andesite`, `blackstone`, `calcite`, `deepslate`, `divine`, `divine_bricks`, `end_stone`, `ice`, `mossy`, `mud_bricks`, `nether_bricks`, `polished_calcite`, `portstone`, `sandy`, `sculk`, `sea_stone`, `sharestone`, `tuff`, `tuff_bricks`.

## Important asset note

The Java code in this repository is original CADERA work. Third-party visual assets remain under their own license. Do not assume the MENKIESTES license relicenses third-party textures/models.
