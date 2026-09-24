package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class WaystoneGui implements Listener {
    private static final int[] SKIN_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32};
    private final CdrWaystonePlugin plugin;
    private final NamespacedKey skinKey;

    public WaystoneGui(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.skinKey = new NamespacedKey(plugin, "gui_skin");
    }

    public void openMain(Player player, WaystoneData data) {
        GuiHolder holder = new GuiHolder(data.id(), Screen.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("✦ ", data.isAdmin() ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text(data.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(data.isAdmin() ? "  •  Admin Waystone" : "  •  Player Waystone", NamedTextColor.DARK_GRAY)));
        holder.inventory = inv;
        paintFrame(inv, data.isAdmin());

        inv.setItem(13, item(data.isAdmin() ? Material.NETHER_STAR : Material.LODESTONE,
                "✦ " + data.name(), data.isAdmin() ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE,
                "Type: " + data.type(), "Core: " + data.coreState(), "Tier: " + data.tier(),
                "Skin: " + pretty(data.skin()), "Waystone ID: " + data.id().toString().substring(0, 8)));

        if (data.isAdmin()) {
            inv.setItem(20, item(Material.COMMAND_BLOCK, "Server Authority", NamedTextColor.GOLD,
                    "Official server Waystone", "No player owner", data.publicAccess() ? "Public access" : "Administrative access only"));
        } else {
            OfflinePlayer owner = data.owner() == null ? null : Bukkit.getOfflinePlayer(data.owner());
            inv.setItem(20, ownerHead(owner, "Owner", data.owner() == null ? "Unknown" : displayName(owner),
                    plugin.access().canManage(player, data) ? "You can manage this Waystone" : "Owned by another player"));
        }

        DiscoveryService.State discoveryState = plugin.discovery().status(player, data);
        if (!data.coreActive() && !(data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true))) {
            inv.setItem(21, item(plugin.cores().coreMaterial(), "Dormant Core", NamedTextColor.LIGHT_PURPLE,
                    "This Waystone has not been awakened", "Install a Waystone Core to unlock attunement", "Owner/Admin: right-click with a Waystone Core"));
        } else {
            inv.setItem(21, discoveryItem(discoveryState));
        }

        boolean dormant = !data.coreActive() && !(data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true));
        boolean suppressed = plugin.teleports().isSuppressed(data);
        if (dormant) {
            inv.setItem(22, item(Material.CRYING_OBSIDIAN, "Dormant", NamedTextColor.LIGHT_PURPLE,
                    "Travel network is offline", "A Waystone Core is required", "Discovery can occur, but travel stays locked"));
        } else {
            inv.setItem(22, item(suppressed ? Material.REDSTONE_TORCH : Material.LIME_DYE,
                    suppressed ? "Suppressed" : (data.alwaysActive() ? "Always Active" : "Online"),
                    suppressed ? NamedTextColor.RED : NamedTextColor.GREEN,
                    data.alwaysActive() ? "Server override keeps this Waystone online" : (suppressed ? "Travel to this Waystone is disabled" : "Waystone is operational"),
                    data.alwaysActive() ? "Suppression and power requirements bypassed" : (suppressed ? "Remove the suppressor block below it" : "Activation is tracked per player")));
        }

        WaystoneData.Tier effectiveTier = plugin.tiers().effectiveTier(data);
        inv.setItem(23, item(tierMaterial(effectiveTier), "Tier Progression", tierColor(effectiveTier),
                "Stored tier: " + data.tier(),
                data.isAdmin() && effectiveTier != data.tier() ? "Effective tier: " + effectiveTier + " (Admin bypass)" : "Effective tier: " + effectiveTier,
                "Range: " + rangeLabel(data),
                "Cross-world: " + yesNo(plugin.tiers().allowsCrossWorld(data)),
                plugin.access().canManage(player, data) ? "Click to view and upgrade" : "Click to view progression"));

        inv.setItem(24, powerItem(data));
        inv.setItem(29, item(Material.AMETHYST_SHARD, "Skin Gallery", NamedTextColor.LIGHT_PURPLE,
                "Current: " + pretty(data.skin()), canManageSkin(player, data) ? "Click to browse 19 skins" : "Owner/Admin only"));
        inv.setItem(31, item(Material.NAME_TAG, "Rename Waystone", NamedTextColor.AQUA,
                "Rename a Name Tag in an Anvil", "then right-click this Waystone", plugin.access().canManage(player, data) ? "You may rename this Waystone" : "Owner/Admin only"));
        inv.setItem(33, item(Material.COMPASS, "Waystone Key", NamedTextColor.YELLOW,
                dormant ? "Waystone Core required before Key binding" : (discoveryState == DiscoveryService.State.ACTIVATED ? "This Waystone is ready for Key binding" : "Activate this Waystone before binding a Key"),
                "Sneak + right-click to relink", "Use the bound key elsewhere to warp"));
        inv.setItem(38, item(Material.MAP, "Coordinates", NamedTextColor.AQUA,
                data.worldName(), "X " + data.x() + "  Y " + data.y() + "  Z " + data.z()));
        inv.setItem(40, item(Material.CLOCK, "Travel Rules", NamedTextColor.YELLOW,
                "Core: " + data.coreState(), "Tier: " + effectiveTier,
                "Countdown: " + plugin.getConfig().getInt("warp.delay-seconds", 5) + " seconds",
                "Damage cancel: " + enabled(plugin.getConfig().getBoolean("warp.damage-cancels", true)),
                "Cross-world: " + (plugin.tiers().allowsCrossWorld(data) ? enabled(plugin.getConfig().getBoolean("warp.allow-cross-world", true)) : "Locked by tier"),
                data.freeTravel() ? "Travel cost: FREE" : "Tier cost multiplier: x" + formatMultiplier(plugin.tiers().costMultiplier(data))));

        if (data.isAdmin()) {
            inv.setItem(42, item(Material.BEACON, "Admin Waystone Controls", NamedTextColor.GOLD,
                    "Public: " + yesNo(data.publicAccess()), "Free: " + yesNo(data.freeTravel()),
                    "Permanent: " + yesNo(data.permanent()), "Always Active: " + yesNo(data.alwaysActive()),
                    "Global Discovery: " + yesNo(data.globallyDiscovered()),
                    player.hasPermission("cdrwaystone.admin") ? "Click to configure" : "Administrator only"));
        } else {
            inv.setItem(42, item(accessMaterial(data.accessMode()), "Ownership & Access", NamedTextColor.LIGHT_PURPLE,
                    "Mode: " + data.accessMode(), "Trusted players: " + data.trustedPlayers().size(),
                    plugin.access().canManage(player, data) ? "Click to manage access" : accessDescription(data.accessMode())));
        }

        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu"));
        player.openInventory(inv);
        click(player);
    }

    private void openTier(Player player, WaystoneData data) {
        GuiHolder holder = new GuiHolder(data.id(), Screen.TIER);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("✦ WAYSTONE TIER PROGRESSION", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inv;
        paintFrame(inv, data.isAdmin());

        inv.setItem(4, item(tierMaterial(plugin.tiers().effectiveTier(data)), data.name(), tierColor(plugin.tiers().effectiveTier(data)),
                "Core: " + data.coreState(), "Stored Tier: " + data.tier(), "Effective Tier: " + plugin.tiers().effectiveTier(data),
                data.isAdmin() && plugin.getConfig().getBoolean("tier.admin-bypass", true) ? "Admin route bypass is enabled" : "Tier progression controls route power"));

        inv.setItem(19, tierCard(data, WaystoneData.Tier.AWAKENED));
        inv.setItem(21, tierCard(data, WaystoneData.Tier.EMPOWERED));
        inv.setItem(23, tierCard(data, WaystoneData.Tier.ANCIENT));
        inv.setItem(25, tierCard(data, WaystoneData.Tier.ASCENDED));

        WaystoneData.Tier next = plugin.tiers().next(data);
        if (!data.coreActive() && !(data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true))) {
            inv.setItem(31, item(plugin.cores().coreMaterial(), "Core Required", NamedTextColor.LIGHT_PURPLE,
                    "This Waystone is still DORMANT", "Install a Waystone Core before tier upgrades"));
            inv.setItem(33, item(Material.BARRIER, "Upgrade Locked", NamedTextColor.RED, "Awaken the Waystone Core first"));
        } else if (next == null) {
            inv.setItem(31, item(Material.NETHER_STAR, "Maximum Tier Reached", NamedTextColor.GOLD,
                    "ASCENDED Waystone", "No further tier upgrades are available"));
            inv.setItem(33, item(Material.BEACON, "ASCENDED", NamedTextColor.AQUA,
                    "Maximum route range", "Cross-world enabled", "Lowest travel cost and cooldown multiplier"));
        } else {
            List<String> requirements = upgradeRequirements(next);
            inv.setItem(31, item(Material.WRITABLE_BOOK, "Upgrade to " + next, NamedTextColor.YELLOW,
                    requirements.toArray(String[]::new)));
            boolean manage = plugin.access().canManage(player, data);
            boolean affordable = plugin.tiers().canAfford(player, next);
            inv.setItem(33, item(affordable ? Material.LIME_DYE : Material.RED_DYE,
                    "Upgrade: " + next,
                    affordable ? NamedTextColor.GREEN : NamedTextColor.RED,
                    manage ? (affordable ? "Click to perform upgrade" : "Missing required resources") : "Owner/Admin only",
                    "Resources are consumed atomically"));
        }

        inv.setItem(36, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Return to Waystone menu"));
        inv.setItem(40, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu"));
        player.openInventory(inv);
        click(player);
    }

    private void openOwnership(Player player, WaystoneData data) {
        GuiHolder holder = new GuiHolder(data.id(), Screen.OWNERSHIP);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("✦ Waystone Ownership", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inv;
        paintFrame(inv, false);

        OfflinePlayer owner = data.owner() == null ? null : Bukkit.getOfflinePlayer(data.owner());
        inv.setItem(4, ownerHead(owner, "Waystone Owner", data.owner() == null ? "Unknown" : displayName(owner),
                "Owned Waystones: " + (data.owner() == null ? 0 : plugin.registry().countOwned(data.owner()))));

        inv.setItem(20, accessChoice(Material.IRON_DOOR, "Private", WaystoneData.AccessMode.PRIVATE, data,
                "Only the owner can discover or use it"));
        inv.setItem(22, accessChoice(Material.ENDER_CHEST, "Trusted", WaystoneData.AccessMode.TRUSTED, data,
                "Owner + trusted players can use it"));
        inv.setItem(24, accessChoice(Material.OAK_DOOR, "Public", WaystoneData.AccessMode.PUBLIC, data,
                "Any player can discover and activate it"));

        List<String> trustedLore = new ArrayList<>();
        trustedLore.add("Trusted players: " + data.trustedPlayers().size());
        int shown = 0;
        for (UUID id : data.trustedPlayers()) {
            if (shown++ >= 7) { trustedLore.add("...and more"); break; }
            trustedLore.add("• " + displayName(Bukkit.getOfflinePlayer(id)));
        }
        if (data.trustedPlayers().isEmpty()) trustedLore.add("No trusted players yet");
        trustedLore.add("Use /cws trust <player>");
        trustedLore.add("Use /cws untrust <player>");
        inv.setItem(30, item(Material.WRITABLE_BOOK, "Trusted Players", NamedTextColor.AQUA, trustedLore.toArray(String[]::new)));

        inv.setItem(32, item(Material.PLAYER_HEAD, "Transfer Ownership", NamedTextColor.YELLOW,
                "Use /cws transfer <online-player>", "Transfer checks the new owner's limit", "Access resets to PRIVATE", "Trusted list is cleared"));
        inv.setItem(36, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Return to Waystone menu"));
        inv.setItem(40, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu"));
        player.openInventory(inv);
        click(player);
    }

    private void openAdmin(Player player, WaystoneData data) {
        GuiHolder holder = new GuiHolder(data.id(), Screen.ADMIN);
        Inventory inv = Bukkit.createInventory(holder, 45, Component.text("✦ Admin Waystone Control", NamedTextColor.GOLD, TextDecoration.BOLD));
        holder.inventory = inv;
        paintFrame(inv, true);
        inv.setItem(4, item(Material.NETHER_STAR, data.name(), NamedTextColor.GOLD,
                "Official server infrastructure", "Core: " + data.coreState(), "Tier: " + data.tier(), "Changes save instantly"));
        inv.setItem(20, item(data.publicAccess() ? Material.LIME_DYE : Material.RED_DYE, "Public Access", data.publicAccess() ? NamedTextColor.GREEN : NamedTextColor.RED,
                "Current: " + yesNo(data.publicAccess()), "Allow normal players to discover, bind and travel", "Click to toggle"));
        inv.setItem(22, item(data.freeTravel() ? Material.EMERALD : Material.COAL, "Free Travel", data.freeTravel() ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                "Current: " + yesNo(data.freeTravel()), "Marks this Waystone as cost-free", "Click to toggle"));
        inv.setItem(24, item(data.permanent() ? Material.BEDROCK : Material.IRON_PICKAXE, "Permanent", data.permanent() ? NamedTextColor.GOLD : NamedTextColor.GRAY,
                "Current: " + yesNo(data.permanent()), "Permanent Waystones cannot be broken", "Click to toggle"));
        inv.setItem(28, item(data.globallyDiscovered() ? Material.ENDER_EYE : Material.ENDER_PEARL, "Globally Discovered", data.globallyDiscovered() ? NamedTextColor.AQUA : NamedTextColor.GRAY,
                "Current: " + yesNo(data.globallyDiscovered()), "YES makes this Waystone discovered for everyone", "Players still activate it individually", "Click to toggle"));
        inv.setItem(30, item(data.alwaysActive() ? Material.BEACON : Material.REDSTONE_LAMP, "Always Active", data.alwaysActive() ? NamedTextColor.AQUA : NamedTextColor.GRAY,
                "Current: " + yesNo(data.alwaysActive()), "Bypass suppression and power requirement", "Click to toggle"));
        inv.setItem(32, item(Material.AMETHYST_SHARD, "Skin Gallery", NamedTextColor.LIGHT_PURPLE, "Current: " + pretty(data.skin()), "Click to choose a model"));
        inv.setItem(36, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Return to Waystone menu"));
        inv.setItem(40, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close this menu"));
        player.openInventory(inv);
        click(player);
    }

    private void openSkins(Player player, WaystoneData data) {
        GuiHolder holder = new GuiHolder(data.id(), Screen.SKINS);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("✦ Waystone Skin Gallery", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inv;
        paintFrame(inv, data.isAdmin());
        int i = 0;
        for (String skin : plugin.skins().keySet()) {
            if (i >= SKIN_SLOTS.length) break;
            boolean active = skin.equalsIgnoreCase(data.skin());
            ItemStack stack = item(skinMaterial(skin), pretty(skin), active ? NamedTextColor.GREEN : NamedTextColor.LIGHT_PURPLE,
                    active ? "✓ Currently equipped" : "Click to apply this skin");
            ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skin);
            stack.setItemMeta(meta);
            inv.setItem(SKIN_SLOTS[i++], stack);
        }
        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.YELLOW, "Return to Waystone menu"));
        inv.setItem(49, item(Material.LODESTONE, "Current: " + pretty(data.skin()), NamedTextColor.GREEN, "19 Vephilim skins supported"));
        player.openInventory(inv);
        click(player);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        WaystoneData data = plugin.registry().get(holder.waystoneId);
        if (data == null) {
            player.closeInventory();
            player.sendMessage("§cThat Waystone no longer exists.");
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (holder.screen == Screen.MAIN) {
            switch (event.getRawSlot()) {
                case 21 -> {
                    if (!data.coreActive() && !(data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true))) {
                        player.sendMessage("§5This Waystone is Dormant. §7Right-click it with a §dWaystone Core§7 to awaken it.");
                        click(player);
                        return;
                    }
                    DiscoveryService.State state = plugin.discovery().status(player, data);
                    if (state == DiscoveryService.State.DISCOVERED) {
                        plugin.discovery().activate(player, data);
                        openMain(player, data);
                    } else if (state == DiscoveryService.State.ACTIVATED) {
                        player.sendMessage("§aThis Waystone is already activated."); click(player);
                    } else {
                        player.sendMessage("§7Discover this Waystone before activating it."); click(player);
                    }
                }
                case 23 -> openTier(player, data);
                case 29 -> { if (canManageSkin(player, data)) openSkins(player, data); else denied(player); }
                case 31 -> {
                    player.closeInventory();
                    if (plugin.access().canManage(player, data)) player.sendMessage("§bRename: §frename a Name Tag in an Anvil, then right-click this Waystone with it.");
                    else player.sendMessage("§cOnly the owner/admin can rename this Waystone.");
                }
                case 33 -> {
                    player.closeInventory();
                    player.sendMessage("§eWaystone Key: §fawaken the Waystone Core, activate the Waystone, right-click with a Key to bind, sneak + right-click to relink, then use it elsewhere to warp.");
                }
                case 42 -> {
                    if (data.isAdmin() && player.hasPermission("cdrwaystone.admin")) openAdmin(player, data);
                    else if (!data.isAdmin() && plugin.access().canManage(player, data)) openOwnership(player, data);
                    else denied(player);
                }
                case 49 -> player.closeInventory();
                default -> click(player);
            }
            return;
        }

        if (holder.screen == Screen.TIER) {
            switch (event.getRawSlot()) {
                case 33 -> {
                    if (!plugin.access().canManage(player, data)) { denied(player); return; }
                    plugin.tiers().upgrade(player, data);
                    openTier(player, data);
                }
                case 36 -> openMain(player, data);
                case 40 -> player.closeInventory();
                default -> click(player);
            }
            return;
        }

        if (holder.screen == Screen.OWNERSHIP) {
            if (data.isAdmin() || !plugin.access().canManage(player, data)) { player.closeInventory(); return; }
            switch (event.getRawSlot()) {
                case 20 -> data.accessMode(WaystoneData.AccessMode.PRIVATE);
                case 22 -> data.accessMode(WaystoneData.AccessMode.TRUSTED);
                case 24 -> data.accessMode(WaystoneData.AccessMode.PUBLIC);
                case 30 -> { player.closeInventory(); player.sendMessage("§bTrusted players: §f/cws trust <player>§7, §f/cws untrust <player>§7, §f/cws trusted"); return; }
                case 32 -> { player.closeInventory(); player.sendMessage("§eTransfer ownership: §f/cws transfer <online-player>"); return; }
                case 36 -> { openMain(player, data); return; }
                case 40 -> { player.closeInventory(); return; }
                default -> { click(player); return; }
            }
            plugin.registry().save();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
            openOwnership(player, data);
            return;
        }

        if (holder.screen == Screen.ADMIN) {
            if (!player.hasPermission("cdrwaystone.admin") || !data.isAdmin()) { player.closeInventory(); return; }
            switch (event.getRawSlot()) {
                case 20 -> data.publicAccess(!data.publicAccess());
                case 22 -> data.freeTravel(!data.freeTravel());
                case 24 -> data.permanent(!data.permanent());
                case 28 -> data.globallyDiscovered(!data.globallyDiscovered());
                case 30 -> data.alwaysActive(!data.alwaysActive());
                case 32 -> { openSkins(player, data); return; }
                case 36 -> { openMain(player, data); return; }
                case 40 -> { player.closeInventory(); return; }
                default -> { click(player); return; }
            }
            plugin.registry().save();
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.25f);
            openAdmin(player, data);
            return;
        }

        if (event.getRawSlot() == 45) { openMain(player, data); return; }
        String skin = clicked.hasItemMeta() ? clicked.getItemMeta().getPersistentDataContainer().get(skinKey, PersistentDataType.STRING) : null;
        if (skin == null || !plugin.skins().containsKey(skin)) return;
        if (!canManageSkin(player, data)) { denied(player); return; }
        data.skin(skin);
        plugin.registry().save();
        plugin.visuals().spawn(data);
        player.sendMessage("§dWaystone skin changed to §f" + pretty(skin) + "§d.");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.25f);
        openSkins(player, data);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) event.setCancelled(true);
    }

    private ItemStack tierCard(WaystoneData data, WaystoneData.Tier tier) {
        boolean current = data.tier() == tier;
        double max = Math.max(0.0, plugin.getConfig().getDouble("tier.perks." + tier.name() + ".max-distance", 0.0));
        boolean crossWorld = plugin.getConfig().getBoolean("tier.perks." + tier.name() + ".cross-world", true);
        double cost = Math.max(0.0, plugin.getConfig().getDouble("tier.perks." + tier.name() + ".cost-multiplier", 1.0));
        double cooldown = Math.max(0.0, plugin.getConfig().getDouble("tier.perks." + tier.name() + ".cooldown-multiplier", 1.0));
        return item(tierMaterial(tier), (current ? "✓ " : "") + tier.name(), current ? NamedTextColor.GREEN : tierColor(tier),
                current ? "Current stored tier" : "Progression tier",
                "Range: " + (max <= 0 ? "Unlimited" : (int) Math.ceil(max) + " blocks"),
                "Cross-world: " + yesNo(crossWorld),
                "Travel cost: x" + formatMultiplier(cost),
                "Cooldown: x" + formatMultiplier(cooldown));
    }

    private List<String> upgradeRequirements(WaystoneData.Tier next) {
        List<String> lore = new ArrayList<>();
        lore.add("Required resources:");
        Map<Material, Integer> items = plugin.tiers().requirements(next);
        if (items.isEmpty()) lore.add("No item requirements");
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            lore.add(entry.getValue() + "x " + pretty(entry.getKey().name()));
        }
        int xp = plugin.tiers().xpRequirement(next);
        if (xp > 0) lore.add(xp + " XP Levels");
        lore.add("Resources are consumed on upgrade");
        return lore;
    }

    private Material tierMaterial(WaystoneData.Tier tier) {
        return switch (tier) {
            case AWAKENED -> Material.AMETHYST_SHARD;
            case EMPOWERED -> Material.ECHO_SHARD;
            case ANCIENT -> Material.HEART_OF_THE_SEA;
            case ASCENDED -> Material.NETHER_STAR;
        };
    }

    private NamedTextColor tierColor(WaystoneData.Tier tier) {
        return switch (tier) {
            case AWAKENED -> NamedTextColor.LIGHT_PURPLE;
            case EMPOWERED -> NamedTextColor.AQUA;
            case ANCIENT -> NamedTextColor.YELLOW;
            case ASCENDED -> NamedTextColor.GOLD;
        };
    }

    private String rangeLabel(WaystoneData data) {
        double max = plugin.tiers().maxDistance(data);
        return max <= 0 ? "Unlimited" : (int) Math.ceil(max) + " blocks";
    }

    private String formatMultiplier(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private ItemStack discoveryItem(DiscoveryService.State state) {
        return switch (state) {
            case UNKNOWN -> item(Material.GRAY_DYE, "Unknown", NamedTextColor.DARK_GRAY,
                    "This Waystone has not been discovered", "Move close to it to reveal it");
            case DISCOVERED -> item(Material.AMETHYST_SHARD, "Discovered", NamedTextColor.LIGHT_PURPLE,
                    "You have found this Waystone", "Click to ACTIVATE it", "Activation unlocks Key binding and travel");
            case ACTIVATED -> item(Material.ENDER_EYE, "Activated", NamedTextColor.GREEN,
                    "This Waystone is attuned to you", "Key binding and travel unlocked");
        };
    }

    private ItemStack accessChoice(Material material, String label, WaystoneData.AccessMode mode, WaystoneData data, String description) {
        boolean selected = data.accessMode() == mode;
        return item(material, (selected ? "✓ " : "") + label, selected ? NamedTextColor.GREEN : NamedTextColor.LIGHT_PURPLE,
                description, selected ? "Currently selected" : "Click to select");
    }

    private Material accessMaterial(WaystoneData.AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> Material.IRON_DOOR;
            case TRUSTED -> Material.ENDER_CHEST;
            case PUBLIC -> Material.OAK_DOOR;
        };
    }

    private String accessDescription(WaystoneData.AccessMode mode) {
        return switch (mode) {
            case PRIVATE -> "Only the owner has access";
            case TRUSTED -> "Restricted to trusted players";
            case PUBLIC -> "Open to all players";
        };
    }

    private boolean canManageSkin(Player player, WaystoneData data) {
        return player.hasPermission("cdrwaystone.skin") && plugin.access().canManage(player, data);
    }

    private void denied(Player player) {
        player.sendMessage("§cYou cannot manage this Waystone.");
        click(player);
    }

    private ItemStack powerItem(WaystoneData data) {
        if (!data.coreActive() && !(data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true)))
            return item(plugin.cores().coreMaterial(), "Waystone Core", NamedTextColor.LIGHT_PURPLE,
                    "Core state: DORMANT", "Install a Waystone Core before travel power matters");
        if (data.alwaysActive()) return item(Material.BEACON, "Dimensional Power", NamedTextColor.AQUA, "Always Active override", "No Respawn Anchor charge required");
        Block below = data.location() == null ? null : data.location().clone().add(0,-1,0).getBlock();
        if (below != null && below.getType() == Material.RESPAWN_ANCHOR && below.getBlockData() instanceof RespawnAnchor anchor)
            return item(Material.RESPAWN_ANCHOR, "Dimensional Power", anchor.getCharges() > 0 ? NamedTextColor.AQUA : NamedTextColor.RED,
                    "Respawn Anchor charge: " + anchor.getCharges() + "/4", "Used when cross-world power is required");
        return item(Material.GLOWSTONE_DUST, "Dimensional Power", NamedTextColor.GRAY,
                "No Respawn Anchor detected below", "Same-world travel can still work", "depending on power.require");
    }

    private void paintFrame(Inventory inv, boolean adminTheme) {
        ItemStack dark = filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = filler(adminTheme ? Material.ORANGE_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE);
        int rows = inv.getSize() / 9;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9, col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) inv.setItem(slot, slot % 2 == 0 ? accent : dark);
        }
    }

    private ItemStack ownerHead(OfflinePlayer owner, String label, String... lines) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (owner != null) meta.setOwningPlayer(owner);
        meta.displayName(name(label, NamedTextColor.GOLD));
        meta.lore(lore(lines));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack filler(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String label, NamedTextColor color, String... lines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name(label, color));
        meta.lore(lore(lines));
        stack.setItemMeta(meta);
        return stack;
    }

    private Component name(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) if (line != null && !line.isBlank())
            out.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        return out;
    }

    private Material skinMaterial(String skin) {
        return switch (skin.toLowerCase(Locale.ROOT)) {
            case "blackstone" -> Material.POLISHED_BLACKSTONE;
            case "calcite" -> Material.CALCITE;
            case "deepslate" -> Material.POLISHED_DEEPSLATE;
            case "divine" -> Material.QUARTZ_BLOCK;
            case "divine_bricks" -> Material.QUARTZ_BRICKS;
            case "end_stone" -> Material.END_STONE_BRICKS;
            case "ice" -> Material.PACKED_ICE;
            case "mossy" -> Material.MOSS_BLOCK;
            case "mud_bricks" -> Material.MUD_BRICKS;
            case "nether_bricks" -> Material.NETHER_BRICKS;
            case "polished_calcite" -> Material.SMOOTH_QUARTZ;
            case "portstone" -> Material.CRYING_OBSIDIAN;
            case "sandy" -> Material.CHISELED_SANDSTONE;
            case "sculk" -> Material.SCULK;
            case "sea_stone" -> Material.PRISMARINE_BRICKS;
            case "sharestone" -> Material.ENDER_CHEST;
            case "tuff" -> Material.TUFF;
            case "tuff_bricks" -> Material.TUFF_BRICKS;
            default -> Material.ANDESITE;
        };
    }

    private String pretty(String input) {
        StringBuilder out = new StringBuilder();
        for (String part : input.replace('-', '_').split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private String displayName(OfflinePlayer player) {
        if (player == null) return "Unknown";
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private String enabled(boolean value) { return value ? "Enabled" : "Disabled"; }
    private String yesNo(boolean value) { return value ? "YES" : "NO"; }
    private void click(Player player) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.2f); }

    private enum Screen { MAIN, SKINS, ADMIN, OWNERSHIP, TIER }

    private static final class GuiHolder implements InventoryHolder {
        private final UUID waystoneId;
        private final Screen screen;
        private Inventory inventory;
        private GuiHolder(UUID waystoneId, Screen screen) { this.waystoneId = waystoneId; this.screen = screen; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
