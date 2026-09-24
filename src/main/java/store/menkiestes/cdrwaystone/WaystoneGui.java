package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
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
        Component title = Component.text("✦ ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(data.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  •  Waystone", NamedTextColor.DARK_GRAY));
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inv;
        paintFrame(inv);

        inv.setItem(13, item(Material.LODESTONE, "✦ " + data.name(), NamedTextColor.LIGHT_PURPLE,
                List.of("Skin: " + pretty(data.skin()), "Waystone ID: " + shortId(data.id()))));

        OfflinePlayer owner = Bukkit.getOfflinePlayer(data.owner());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skull = (SkullMeta) head.getItemMeta();
        skull.setOwningPlayer(owner);
        skull.displayName(Component.text("Owner", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        skull.lore(lore(List.of(owner.getName() == null ? data.owner().toString() : owner.getName(),
                isOwnerOrAdmin(player, data) ? "You can manage this Waystone" : "View only"), NamedTextColor.GRAY));
        head.setItemMeta(skull);
        inv.setItem(20, head);

        boolean suppressed = plugin.teleports().isSuppressed(data);
        inv.setItem(22, item(suppressed ? Material.REDSTONE_TORCH : Material.LIME_DYE,
                suppressed ? "Suppressed" : "Active",
                suppressed ? NamedTextColor.RED : NamedTextColor.GREEN,
                suppressed ? List.of("Travel to this Waystone is disabled", "Remove the suppressor block below it")
                        : List.of("Waystone is online", "Ready for binding and travel")));

        inv.setItem(24, powerItem(data));
        inv.setItem(29, item(Material.AMETHYST_SHARD, "Skin Gallery", NamedTextColor.LIGHT_PURPLE,
                List.of("Current: " + pretty(data.skin()), isOwnerOrAdmin(player, data) ? "Click to browse 19 skins" : "Owner/Admin only")));
        inv.setItem(31, item(Material.NAME_TAG, "Rename Waystone", NamedTextColor.AQUA,
                List.of("Rename a Name Tag in an Anvil", "then right-click this Waystone", isOwnerOrAdmin(player, data) ? "You may rename this Waystone" : "Owner/Admin only")));
        inv.setItem(33, item(Material.COMPASS, "Waystone Key", NamedTextColor.YELLOW,
                List.of("Right-click with an unbound key to bind", "Sneak + right-click to relink", "Use the bound key elsewhere to warp")));

        inv.setItem(38, item(Material.MAP, "Coordinates", NamedTextColor.AQUA,
                List.of(data.worldName(), "X " + data.x() + "  Y " + data.y() + "  Z " + data.z())));
        inv.setItem(40, item(Material.CLOCK, "Travel Rules", NamedTextColor.YELLOW,
                List.of("Countdown: " + plugin.getConfig().getInt("warp.delay-seconds", 5) + " seconds",
                        "Damage cancel: " + yesNo(plugin.getConfig().getBoolean("warp.damage-cancels", true)),
                        "Cross-world: " + yesNo(plugin.getConfig().getBoolean("warp.allow-cross-world", true)))));
        inv.setItem(42, item(player.hasPermission("cdrwaystone.admin") ? Material.NETHER_STAR : Material.IRON_BARS,
                player.hasPermission("cdrwaystone.admin") ? "Administrator" : "Player Waystone",
                player.hasPermission("cdrwaystone.admin") ? NamedTextColor.GOLD : NamedTextColor.GRAY,
                player.hasPermission("cdrwaystone.admin") ? List.of("Admin controls unlocked", "Admin Waystones arrive in v0.2.1") : List.of("Managed by its owner")));

        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of("Close this menu")));
        player.openInventory(inv);
        clickSound(player);
    }

    private void openSkins(Player player, WaystoneData data) {
        GuiHolder holder = new GuiHolder(data.id(), Screen.SKINS);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("✦ Waystone Skin Gallery", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inv;
        paintFrame(inv);

        int index = 0;
        for (String skin : plugin.skins().keySet()) {
            if (index >= SKIN_SLOTS.length) break;
            Material icon = skinMaterial(skin);
            ItemStack stack = item(icon, pretty(skin), skin.equalsIgnoreCase(data.skin()) ? NamedTextColor.GREEN : NamedTextColor.LIGHT_PURPLE,
                    skin.equalsIgnoreCase(data.skin()) ? List.of("✓ Currently equipped", "Click to keep this skin") : List.of("Click to apply this skin"));
            ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skin);
            stack.setItemMeta(meta);
            inv.setItem(SKIN_SLOTS[index++], stack);
        }
        inv.setItem(45, item(Material.ARROW, "Back", NamedTextColor.YELLOW, List.of("Return to Waystone menu")));
        inv.setItem(49, item(Material.LODESTONE, "Current: " + pretty(data.skin()), NamedTextColor.GREEN, List.of("19 Vephilim skins supported")));
        player.openInventory(inv);
        clickSound(player);
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
                case 29 -> {
                    if (!canManage(player, data)) return;
                    openSkins(player, data);
                }
                case 31 -> {
                    player.closeInventory();
                    if (!canManage(player, data)) return;
                    player.sendMessage("§bRename: §frename a Name Tag in an Anvil, then right-click this Waystone with it.");
                }
                case 33 -> {
                    player.closeInventory();
                    player.sendMessage("§eWaystone Key: §fright-click to bind, sneak + right-click to relink, then use the key elsewhere to warp.");
                }
                case 49 -> player.closeInventory();
                default -> clickSound(player);
            }
            return;
        }

        if (event.getRawSlot() == 45) {
            openMain(player, data);
            return;
        }

        String skin = clicked.hasItemMeta() ? clicked.getItemMeta().getPersistentDataContainer().get(skinKey, PersistentDataType.STRING) : null;
        if (skin == null) return;
        if (!canManage(player, data)) return;
        if (!plugin.skins().containsKey(skin)) return;

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

    private boolean canManage(Player player, WaystoneData data) {
        if (!player.hasPermission("cdrwaystone.skin") || !isOwnerOrAdmin(player, data)) {
            player.sendMessage("§cYou cannot manage this Waystone.");
            clickSound(player);
            return false;
        }
        return true;
    }

    private boolean isOwnerOrAdmin(Player player, WaystoneData data) {
        return data.owner().equals(player.getUniqueId()) || player.hasPermission("cdrwaystone.admin");
    }

    private ItemStack powerItem(WaystoneData data) {
        Block below = data.location() == null ? null : data.location().clone().add(0, -1, 0).getBlock();
        if (below != null && below.getType() == Material.RESPAWN_ANCHOR && below.getBlockData() instanceof RespawnAnchor anchor) {
            return item(Material.RESPAWN_ANCHOR, "Dimensional Power", anchor.getCharges() > 0 ? NamedTextColor.AQUA : NamedTextColor.RED,
                    List.of("Respawn Anchor charge: " + anchor.getCharges() + "/4", "Used when cross-world power is required"));
        }
        return item(Material.GLOWSTONE_DUST, "Dimensional Power", NamedTextColor.GRAY,
                List.of("No Respawn Anchor detected below", "Same-world travel can still work", "depending on power.require"));
    }

    private void paintFrame(Inventory inv) {
        ItemStack dark = filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack purple = filler(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) inv.setItem(slot, (slot % 2 == 0) ? purple : dark);
        }
        for (int slot : new int[]{9,17,18,26,27,35,36,44}) inv.setItem(slot, purple);
    }

    private ItemStack filler(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore(lines, NamedTextColor.GRAY));
        stack.setItemMeta(meta);
        return stack;
    }

    private List<Component> lore(List<String> lines, NamedTextColor color) {
        return lines.stream().map(line -> Component.text(line, color).decoration(TextDecoration.ITALIC, false)).toList();
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
        String[] parts = input.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private String shortId(UUID id) { return id.toString().substring(0, 8); }
    private String yesNo(boolean value) { return value ? "Enabled" : "Disabled"; }
    private void clickSound(Player player) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 1.2f); }

    private enum Screen { MAIN, SKINS }

    private static final class GuiHolder implements InventoryHolder {
        private final UUID waystoneId;
        private final Screen screen;
        private Inventory inventory;

        private GuiHolder(UUID waystoneId, Screen screen) {
            this.waystoneId = waystoneId;
            this.screen = screen;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
