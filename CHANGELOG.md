# Changelog

## 0.1.1 - Stability Patch

- Added per-Waystone visual UUID markers so nearby Waystones cannot delete each other's `ItemDisplay` model.
- Added collision ownership tracking and persistence.
- Fixed stale registry entries creating orphan Barrier collision blocks when the Lodestone no longer exists.
- Added automatic stale-entry pruning on startup refresh and chunk load.
- Added a periodic self-healing stability pass for command/external-plugin block changes.
- Added piston protection for registered Waystones and managed collision blocks.
- Added explosion protection with optional cleanup behavior when explosion protection is disabled.
- Changed shutdown behavior to remove runtime visuals without stripping managed collision blocks.
- Active warp tasks are now safely cancelled during plugin shutdown.
- Fixed completed teleports being cancelled by their own teleport movement event or Portal Sickness damage.
- Power is only consumed after Bukkit confirms the teleport succeeded.
- Registry writes are now atomic when supported and keep `waystones.yml.bak` as a previous-state backup.
- New config defaults are merged into existing installations during upgrade.
- Reload now safely refreshes the key recipe, visuals, collision state, and maintenance schedule.

## 0.1.0 - Foundation

- Initial clean-room CdrWaystone implementation.
- Paper 1.21.11 / Java 21 project foundation.
- Added persistent Lodestone Waystones.
- Added PDC Waystone Keys and bind/relink/warp flow.
- Added ItemsAdder runtime visual adapter and 19 skin mappings.
- Added two-block collision handling with Barrier interaction redirection.
- Added countdown, damage cancellation, optional movement cancellation and safe destination search.
- Added suppression and Respawn Anchor power mechanics.
- Added portal sickness.
- Added Name Tag rename flow.
- Added admin/player commands, permissions and build workflow.
