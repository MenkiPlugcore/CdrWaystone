# Changelog

## 0.5.0 - Travel Cost & Economy Engine

- Added configurable travel pricing for Waystone Network and Waystone Key teleports.
- Added economy providers: `AUTO`, `VAULT`, `XP_LEVELS`, `ITEM`, and `NONE`.
- `AUTO` uses Vault when an economy provider is registered and falls back to XP Levels when Vault is unavailable.
- Vault integration is runtime-only and optional; CdrWaystone does not hard-depend on a specific Vault API build.
- Added configurable base cost, free-distance threshold, cost per 1000 blocks, cross-world flat cost, minimum/maximum cost, and rounding mode.
- Added per-destination-category price multipliers for CAPITAL, CITY, VILLAGE, DUNGEON, KINGDOM, PLAYER, EVENT, and OTHER.
- Added XP conversion value so monetary cost units can map cleanly to experience levels.
- Added ITEM economy with configurable material, display name, and value-per-item.
- Admin Waystones with `Free Travel = true` bypass travel cost automatically.
- Added `cdrwaystone.cost.bypass`, default OP, for staff/rank travel-cost bypass.
- Network destination cards now display live travel cost and insufficient-funds route status.
- Network pricing is calculated from the origin Waystone to the destination.
- Waystone Key pricing is calculated from the player's position when the warp begins.
- Travel cost is locked when countdown starts, affordability is checked again when countdown completes, and payment is charged immediately before teleport.
- Failed Bukkit teleports automatically refund the travel payment and do not consume Respawn Anchor power.
- Existing access, discovery, suppression, dimensional power, countdown, damage cancellation, safe-arrival, and Portal Sickness rules remain enforced.
- Added optional Vault soft-load ordering while preserving startup without Vault.

## 0.4.1 - Waystone Categories & Network Filters

- Added persistent Waystone categories: `CAPITAL`, `CITY`, `VILLAGE`, `DUNGEON`, `KINGDOM`, `PLAYER`, `EVENT`, and `OTHER`.
- Existing Player Waystones migrate to `PLAYER`; existing Admin Waystones migrate to `CITY` when no category was previously stored.
- Added configurable default categories for new Player and Admin Waystones.
- Added `/cws category <category>` for Player Waystone owners.
- Added `/cws admin category <category>` for Admin Waystones.
- `/cws info` now includes category metadata.
- Added category-specific icons and colors to Network destination cards.
- Added a top-row Network filter bar for ALL and all eight categories.
- Category filters preserve pagination and live route validation.
- Categories are organization metadata only and do not bypass access, discovery, power, or teleport restrictions.

## 0.4.0 - Waystone Network Travel

- Added a premium paginated Waystone Network GUI with up to 28 destinations per page.
- Sneak + right-click an activated Waystone with an empty hand to open its Network.
- The Network can only be opened from an accessible, activated, non-suppressed origin Waystone while the player remains nearby.
- Only destinations the player can currently access and use are shown.
- Player Waystone `PRIVATE`, `TRUSTED`, and `PUBLIC` access rules are respected automatically.
- Admin Waystone public/private policy is respected automatically.
- Network destinations are sorted with Admin Waystones first, then Player Waystones, alphabetically within each group.
- Destination cards show type, world, route distance, skin, and live route status.
- Added structured route states for ready, unavailable world, missing Waystone, access denied, not discovered, not activated, suppressed, cross-world disabled, and missing dimensional power.
- Unavailable destinations remain visible when already activated/access-eligible, but are clearly marked and cannot start travel.
- Route access and availability are revalidated when a destination is clicked and again when the normal warp countdown completes.
- Network Travel reuses the existing countdown, damage cancellation, cross-world rules, power consumption, safe-arrival search, and Portal Sickness systems.
- Network GUI refreshes dynamically from live registry/discovery/access data and requires no additional database file.
- Added configurable `network.enabled`, `network.origin-radius`, and `network.destinations-per-page` settings.

## 0.3.1 - Player Waystone Ownership & Access

- Added first-class Player Waystone access modes: `PRIVATE`, `TRUSTED`, and `PUBLIC`.
- Existing Player Waystones migrate safely to configurable `ownership.default-access` (PRIVATE by default).
- Added persistent per-Waystone trusted-player UUID lists in `waystones.yml`.
- Added centralized access validation used by discovery, activation, Key binding, GUI interaction, and teleport.
- Player Waystones can now only be broken by their owner or an administrator.
- Added configurable Player Waystone ownership limit, defaulting to 3 per player.
- Added `cdrwaystone.limit.bypass` permission for unlimited placement.
- Placement is rejected cleanly when a player reaches their Waystone limit.
- Added `/cws access <private|trusted|public>`.
- Added `/cws trust <player>`, `/cws untrust <player>`, and `/cws trusted`.
- Trusting the first player automatically upgrades a PRIVATE Waystone to TRUSTED for easier setup.
- Added `/cws transfer <online-player>` with target ownership-limit validation.
- Ownership transfer resets access to PRIVATE, clears the old trusted list, activates the Waystone for the new owner, and removes the old owner's privileged access.
- Added `/cws limit` to show current owned Waystones versus the configured limit.
- Added a premium Player Ownership & Access GUI panel with direct PRIVATE/TRUSTED/PUBLIC selection.
- Added trusted-player summaries and transfer/trust command guidance inside the GUI.
- Admin Waystones keep their independent public/private server policy and are unaffected by Player access modes.
- Promoting a Player Waystone to an Admin Waystone clears Player ownership/trusted access metadata.
- Previously activated Keys can no longer bypass access after an owner changes a Waystone to PRIVATE/TRUSTED or removes trust.

## 0.3.0 - Discovery & Activation System

- Added per-player Waystone progression: `UNKNOWN -> DISCOVERED -> ACTIVATED`.
- Added automatic proximity discovery with configurable radius and scan interval.
- Added first-discovery title, sound and chat feedback.
- Added `discoveries.yml` persistence with atomic writes and `discoveries.yml.bak` backup.
- Existing Player Waystone owners remain effectively activated for backward compatibility.
- Newly placed Player Waystones are explicitly activated for their owner.
- Added a dedicated Discovery/Activation status item to the premium Waystone GUI.
- Added one-click activation from the GUI after discovery.
- Waystone Keys can no longer bind to a Waystone that the player has not activated.
- Teleporting to a Waystone now requires that player to have activated it, unless the player has admin bypass.
- Added `globally-discovered` metadata for Admin Waystones.
- Added `admin-waystones.default-globally-discovered` configuration.
- Added `/cws admin setglobal <true|false>` and a matching Admin GUI toggle.
- Globally discovered Admin Waystones appear as `DISCOVERED` to all players but still require individual activation.
- Private Admin Waystones are excluded from normal-player discovery.
- Discovery data is cleaned when a Waystone is removed or pruned as stale.
- `/cws info` now shows global-discovery policy and the viewer's personal discovery state.

## 0.2.1 - Admin Waystone System

- Added first-class `PLAYER` and `ADMIN` Waystone types with backward-compatible migration for existing data.
- Added official server-owned Admin Waystones with no player owner requirement.
- Added `/cws admin create <name>` to create or promote the targeted Lodestone/Waystone into an Admin Waystone.
- Added `/cws admin remove`, `setpublic`, `setfree`, `setpermanent`, `setactive`, `skin`, and `info` controls.
- Added default Admin Waystone settings in `config.yml`: Divine skin, public, free, permanent and always-active.
- Added permanent Admin Waystone protection against ordinary block breaking and explosions.
- Added private Admin Waystones that reject normal-player key binding and travel when public access is disabled.
- Added Always Active mode which bypasses suppression and Respawn Anchor power requirements.
- Added persistent public/free/permanent/always-active metadata to `waystones.yml`.
- Added gold/orange Admin Waystone GUI presentation and a dedicated interactive Admin Control panel.
- Admin GUI toggles save instantly for Public Access, Free Travel, Permanent and Always Active.
- Admin Waystones can still use the full 19-skin gallery.

## 0.2.0 - Waystone GUI System

- Added a premium 6-row Waystone management GUI opened by right-clicking a Waystone without a Key/Name Tag.
- Added visual frame styling, Waystone preview, owner head, active/suppressed status, power state, coordinates and travel rules.
- Added an interactive 19-skin gallery with themed material icons.
- Owners/admins can apply skins directly from the GUI; visuals refresh immediately and persist.
- Added rename guidance and Waystone Key usage help inside the UI.
- Added player/admin presentation state and a placeholder for the upcoming Admin Waystone system.
- Added GUI input protection against item movement/drag exploits.
- Added `gui.enabled` configuration toggle.

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
