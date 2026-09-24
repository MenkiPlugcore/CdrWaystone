# Changelog

## 0.6.4.2 - Skin & Auto-Remove Hotfix

- Kept Waystone skin management command-based so the travel GUI stays minimal.
- `/cws skin <skin>` and `/cws admin skin <skin>` rebuild the native ItemsAdder furniture in place while preserving the Waystone node data.
- Player command feedback remains screen-only through Action Bar; normal CdrWaystone commands do not spam player chat.
- Added staff destroy handling for official ItemsAdder Waystone furniture.
- Staff with `cdrwaystone.admin` can destroy a non-permanent Waystone and CdrWaystone automatically removes the furniture, invisible Barrier anchors, registry entry, and per-player discovery data.
- Added a runtime bridge for ItemsAdder `FurnitureBreakEvent` when available, without making ItemsAdder a hard compile dependency.
- Added a direct furniture-hit fallback so staff cleanup still works when the furniture damage event itself is protected/cancelled.
- Breaking a managed Barrier anchor as staff now performs the same atomic Waystone removal instead of instructing staff to use a removal command.
- Normal members still cannot destroy official Waystones, and permanent Waystones remain protected.

## 0.6.4 - Staff Furniture Waystones

- Added `cdrwaystone.create`, default OP, as the only permission that converts a placed Lodestone into an official CdrWaystone.
- Players without `cdrwaystone.create` can place and use normal vanilla Lodestones without CdrWaystone registering or changing them.
- Replaced the manual `CustomStack -> Bukkit ItemDisplay` visual path with native ItemsAdder `CustomFurniture` spawning through a runtime adapter.
- Staff-placed Lodestones are now placement triggers only. After the furniture is created successfully, the visible Lodestone is replaced by invisible Barrier interaction/collision anchors.
- Existing registered v0.6.3 Lodestone Waystones are migrated automatically into furniture-backed Waystones when loaded/refreshed.
- If ItemsAdder furniture cannot be created for a new staff placement, the registry entry is removed and the Lodestone is kept vanilla instead of creating a ghost Waystone.
- Added persistent Waystone UUID markers to spawned furniture entities so interaction, protection and cleanup can map back to the correct registry node.
- Added direct interaction and damage protection for registered Waystone furniture.
- Network GUI and teleport validation now use registered Waystone anchors instead of requiring a visible Lodestone block.
- Maintenance and chunk-load recovery now rebuild missing furniture while preserving registered anchors.
- New staff-created Waystones are official ADMIN network nodes, active and public by default, paid by default, and removable by default while map setup is in progress.
- Existing paid travel, Vault economy, simplified Network GUI, cooldown/combat/KO guards, teleport effects and screen-only feedback remain active.

## 0.6.3 - Clean Network & Teleport Effects

- Simplified normal Waystone interaction so it opens the destination Network directly instead of the legacy multi-menu management GUI.
- Removed the legacy `WaystoneGui` runtime path from the plugin.
- Expanded the clean Network list to up to 45 destinations per page with destination name, distance, travel cost and route status shown directly.
- Added `FeedbackService` so normal gameplay state is presented through Action Bar and Title instead of chat spam.
- Added `TeleportEffectService` with beacon-like END_ROD columns, rotating particle rings, departure bursts, arrival bursts and charging/deactivation sounds.
- Added charging/cancel/arrival effects to the paid Waystone-to-Waystone teleport lifecycle.
- Added configurable teleport-effect beam height and ring radius.
- Added an ItemDisplay transform/scale attempt for the Vephilim skin models; this renderer is superseded by native ItemsAdder furniture in v0.6.4.

## 0.6.2 - Simplified Waystone Travel

- Retired the v0.6.1 Waystone Tier & Upgrade Progression system.
- Removed TierService, TierCommand, tier command registration, tier permissions, tier GUI, tier route limits, tier cooldown modifiers, and tier travel-cost modifiers.
- Removed tier metadata from the runtime data model and registry persistence.
- Legacy `tier` fields from v0.6.1 are safely ignored on load and disappear the next time `waystones.yml` is saved.
- Travel is now strictly Waystone-to-Waystone. A valid origin Waystone is required for every player teleport route.
- Added origin proximity validation at route start and again immediately before teleport commit.
- Players must remain within `network.origin-radius` of the origin Waystone until the countdown completes.
- Portable/direct Waystone Key teleporting has been removed.
- Waystone Keys now act as Network Keys: use one on an activated Waystone to open that Waystone's Network.
- Existing bound Keys automatically have their legacy destination binding cleared when used.
- Using a Waystone Key away from a Waystone now explains that travel must begin at a Waystone node.
- Economy quotes now require both an origin Waystone and a destination Waystone.
- Same-world pricing is always calculated from Waystone coordinates, never from the player's arbitrary position.
- Default production economy provider changed to `VAULT` so travel uses server money instead of silently falling back to XP.
- Default same-world price is 500 base cost plus 500 per 1,000 blocks.
- Default cross-world surcharge is 5,000 in addition to the base cost.
- Admin Waystones with `Free Travel = true` bypass travel cost automatically.
- Failed teleports still refund payment and do not consume dimensional power.
- Premium Waystone GUI now focuses on Core state, activation, Network travel, ownership/access, economy, power, and skins.
- Core activation, discovery, category filters, ownership, combat lock, cooldown, CdrKnockout integration, suppression, cross-world power, and Portal Sickness remain supported.

## 0.6.1 - Waystone Tier & Upgrade Progression

- Added experimental Waystone tiers: `AWAKENED`, `EMPOWERED`, `ANCIENT`, and `ASCENDED`.
- Added tier-based travel range, cross-world access, cost multipliers, cooldown multipliers, GUI progression, upgrade requirements, and admin tier controls.
- This system was intentionally retired in v0.6.2 after the project direction was simplified to paid Waystone-to-Waystone travel.

## 0.6.0 - Waystone Core & Activation Progression

- Added persistent Waystone Core states: `DORMANT` and `ACTIVE`.
- New Player Waystones now default to `DORMANT` and cannot be used for Key binding, Network Travel, or destination travel until awakened.
- Existing Waystones from pre-v0.6.0 installations migrate safely as `ACTIVE` so upgrades do not disable established travel infrastructure.
- Added the PDC-authenticated `Waystone Core` item using Echo Shard as its default visual material.
- Added a configurable Waystone Core recipe using Amethyst Shards, Ender Pearls, and a Heart of the Sea by default.
- Right-clicking a Dormant Waystone with a valid Waystone Core awakens it, consumes one Core, saves the new state, and automatically attunes the installer.
- Creative/admin Core consumption bypass is available through `cdrwaystone.core.consume.bypass`.
- Added awakening particles, sounds, title feedback, and chat confirmation.
- Added Core-state checks to discovery activation, Key binding, Network opening, and teleport route validation.
- Added `DORMANT` as a first-class route status with dedicated player feedback.
- Premium Waystone GUI now shows Core state, Dormant status, Core requirement help, and prevents misleading Online/Activated messaging before awakening.
- Admin Waystones default to `ACTIVE`; optional `core.admin-bypass` can keep server infrastructure operational regardless of stored Core state.
- Added `/cwscore give <player> [amount]` for administrator QA and event distribution.
- Added configurable Core item material and recipe ingredients.
- Existing economy, cooldown, combat lock, CdrKnockout integration, ownership, discovery, category, power, suppression, and stability systems remain enforced.

## 0.5.1 - Cooldown & Anti-Abuse

- Added configurable post-teleport travel cooldown, starting only after a successful teleport.
- Added PvP combat tagging for both attacker and victim.
- Projectile attacks from players now trigger combat tagging as well as melee attacks.
- Added configurable combat-tag duration and immediate active-warp cancellation when combat starts.
- Combat state is timestamp-based, so relogging during the same server session does not instantly clear the tag.
- Added travel guard revalidation when a warp begins, during warmup, and immediately before teleport commit.
- Added `COOLDOWN`, `COMBAT_LOCKED`, and `KNOCKED_OUT` route states to the existing travel validation engine.
- Added optional runtime integration with the public CdrKnockout Bukkit service API.
- Knocked players and players with death-in-progress can be blocked from Waystone travel without a hard dependency on CdrKnockout.
- Added `cdrwaystone.cooldown.bypass`, `cdrwaystone.combat.bypass`, and `cdrwaystone.knockout.bypass` permissions, default OP.
- Added `CdrKnockout` as an optional soft dependency for correct service load ordering.
- Existing economy, access, discovery, suppression, dimensional power, safe-arrival, refund, and Portal Sickness rules remain enforced.

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
