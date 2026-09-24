package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class NetworkGui implements Listener {
    private static final int[] DESTINATION_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final CdrWaystonePlugin plugin;
    private final NamespacedKey targetKey;

    public NetworkGui(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.targetKey = new NamespacedKey(plugin, "network_target");
    }

    public void open(Player player, WaystoneData origin) {
        open(player, origin, 0);
    }

    public void open(Player player, WaystoneData origin, int requestedPage) {
        if (!plugin.getConfig().getBoolean("network.enabled", true)) {
            player.sendMessage("§cWaystone Network Travel is disabled.");
            return;
        }
        if (!canUseOrigin(player, origin, true)) return;

        List<WaystoneData> destinations = destinations(player, origin);
        int perPage = Math.max(7, Math.min(DESTINATION_SLOTS.length,
                plugin.getConfig().getInt("network.destinations-per-page", DESTINATION_SLOTS.length)));
        int pages = Math.max(1, (int) Math.ceil(destinations.size() / (double) perPage));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        NetworkHolder holder = new NetworkHolder(origin.id(), page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("✦ WAYSTONE NETWORK", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                        .append(Component.text("  " + (page + 1) + "/" + pages, NamedTextColor.DARK_GRAY)));
        holder.inventory = inv;
        paintFrame(inv);

        int start = page * perPage;
        int end = Math.min(destinations.size(), start + perPage);
        for (int index = start; index < end; index++) {
            int slot = DESTINATION_SLOTS[index - start];
            WaystoneData target = destinations.get(index);
            inv.setItem(slot, destinationItem(player, origin, target));
        }

        if (destinations.isEmpty()) {
            inv.setItem(22, item(Material.GRAY_DYE, "No Activated Destinations", NamedTextColor.GRAY,
                    "Discover and activate more Waystones", "to make them appear in this network."));
        }

        if (page > 0) inv.setItem(45, item(Material.ARROW, "Previous Page", NamedTextColor.YELLOW, "Page " + page + " of " + pages));
        inv.setItem(47, item(Material.COMPASS, "Network Rules", NamedTextColor.AQUA,
                "Only ACTIVATED Waystones are listed",
                "PRIVATE/TRUSTED access is respected",
                "Route status is checked again on click",
                "Travel uses the normal warp countdown"));
        inv.setItem(48, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close the Waystone Network"));
        inv.setItem(49, item(origin.isAdmin() ? Material.NETHER_STAR : Material.LODESTONE,
                "Origin: " + origin.name(), origin.isAdmin() ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE,
                origin.worldName(), "Destinations: " + destinations.size(),
                "Stay within " + formatRadius() + " blocks of this Waystone"));
        inv.setItem(50, item(Material.ENDER_EYE, "Refresh Network", NamedTextColor.GREEN,
                "Refresh access, activation and route status"));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "Next Page", NamedTextColor.YELLOW, "Page " + (page + 2) + " of " + pages));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.55f, 1.15f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NetworkHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        WaystoneData origin = plugin.registry().get(holder.originId);
        if (!canUseOrigin(player, origin, true)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 45 && holder.page > 0) {
            open(player, origin, holder.page - 1);
            return;
        }
        if (slot == 48) {
            player.closeInventory();
            return;
        }
        if (slot == 50) {
            open(player, origin, holder.page);
            return;
        }
        if (slot == 53) {
            open(player, origin, holder.page + 1);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String rawTarget = clicked.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (rawTarget == null) return;

        UUID targetId;
        try {
            targetId = UUID.fromString(rawTarget);
        } catch (IllegalArgumentException ex) {
            player.sendMessage("§cInvalid Waystone Network destination.");
            return;
        }

        WaystoneData target = plugin.registry().get(targetId);
        if (target == null || target.id().equals(origin.id())) {
            player.sendMessage("§cThat destination is no longer available.");
            open(player, origin, holder.page);
            return;
        }
        if (!plugin.access().canAccess(player, target) || !plugin.discovery().canUse(player, target)) {
            player.sendMessage("§cYou no longer have access to that activated destination.");
            open(player, origin, holder.page);
            return;
        }

        TeleportService.TravelStatus status = plugin.teleports().travelStatus(player, target);
        if (status != TeleportService.TravelStatus.READY) {
            player.sendMessage("§cRoute unavailable: §f" + plugin.teleports().statusLabel(status));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            open(player, origin, holder.page);
            return;
        }

        player.closeInventory();
        player.sendMessage("§5Routing Waystone Network: §f" + origin.name() + " §8→ §f" + target.name());
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.15f);
        plugin.teleports().start(player, target);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NetworkHolder) event.setCancelled(true);
    }

    private List<WaystoneData> destinations(Player player, WaystoneData origin) {
        List<WaystoneData> result = new ArrayList<>();
        for (WaystoneData data : plugin.registry().all()) {
            if (data.id().equals(origin.id())) continue;
            if (!plugin.access().canAccess(player, data)) continue;
            if (!plugin.discovery().canUse(player, data)) continue;
            result.add(data);
        }
        result.sort(Comparator
                .comparing((WaystoneData data) -> !data.isAdmin())
                .thenComparing(data -> data.name().toLowerCase(Locale.ROOT))
                .thenComparing(data -> data.id().toString()));
        return result;
    }

    private boolean canUseOrigin(Player player, WaystoneData origin, boolean notify) {
        if (origin == null) {
            if (notify) player.sendMessage("§cThe origin Waystone no longer exists.");
            return false;
        }
        Location location = origin.location();
        if (location == null || location.getBlock().getType() != Material.LODESTONE) {
            if (notify) player.sendMessage("§cThe origin Waystone is unavailable.");
            return false;
        }
        if (!plugin.access().canAccess(player, origin)) {
            if (notify) player.sendMessage("§cYou do not have access to this Waystone Network node.");
            return false;
        }
        if (!plugin.discovery().canUse(player, origin)) {
            if (notify) player.sendMessage("§dActivate this Waystone before opening its Network.");
            return false;
        }
        if (plugin.teleports().isSuppressed(origin)) {
            if (notify) player.sendMessage("§cThis Waystone is suppressed, so its Network is offline.");
            return false;
        }
        if (!nearOrigin(player, origin)) {
            if (notify) player.sendMessage("§cStay close to the origin Waystone to use its Network.");
            return false;
        }
        return true;
    }

    private boolean nearOrigin(Player player, WaystoneData origin) {
        Location location = origin.location();
        if (location == null || !player.getWorld().getUID().equals(origin.worldId())) return false;
        double radius = Math.max(2.0, plugin.getConfig().getDouble("network.origin-radius", 6.5));
        return player.getLocation().distanceSquared(location.clone().add(0.5, 0.5, 0.5)) <= radius * radius;
    }

    private ItemStack destinationItem(Player player, WaystoneData origin, WaystoneData target) {
        TeleportService.TravelStatus status = plugin.teleports().travelStatus(player, target);
        boolean ready = status == TeleportService.TravelStatus.READY;
        Material material = ready
                ? (target.isAdmin() ? Material.NETHER_STAR : Material.LODESTONE)
                : Material.RED_STAINED_GLASS_PANE;
        NamedTextColor color = ready
                ? (target.isAdmin() ? NamedTextColor.GOLD : NamedTextColor.LIGHT_PURPLE)
                : NamedTextColor.RED;

        ItemStack stack = item(material, target.name(), color,
                target.isAdmin() ? "ADMIN WAYSTONE" : "PLAYER WAYSTONE • " + target.accessMode(),
                "World: " + target.worldName(),
                "Distance: " + distance(origin, target),
                "Skin: " + pretty(target.skin()),
                "Route: " + plugin.teleports().statusLabel(status),
                ready ? "Click to begin network travel" : "Route cannot be used right now");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.id().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    private String distance(WaystoneData origin, WaystoneData target) {
        Location from = origin.location();
        Location to = target.location();
        if (from == null || to == null) return "Unknown";
        if (!origin.worldId().equals(target.worldId())) return "Cross-world";
        return Math.round(from.distance(to)) + " blocks";
    }

    private String formatRadius() {
        double radius = Math.max(2.0, plugin.getConfig().getDouble("network.origin-radius", 6.5));
        return radius == Math.rint(radius) ? String.valueOf((int) radius) : String.format(Locale.US, "%.1f", radius);
    }

    private void paintFrame(Inventory inv) {
        ItemStack dark = filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = filler(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inv.setItem(slot, slot % 2 == 0 ? accent : dark);
            }
        }
    }

    private ItemStack filler(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String label, NamedTextColor color, String... loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            if (line == null || line.isBlank()) continue;
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String pretty(String input) {
        StringBuilder output = new StringBuilder();
        for (String part : input.replace('-', '_').split("_")) {
            if (part.isEmpty()) continue;
            if (output.length() > 0) output.append(' ');
            output.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return output.toString();
    }

    private static final class NetworkHolder implements InventoryHolder {
        private final UUID originId;
        private final int page;
        private Inventory inventory;

        private NetworkHolder(UUID originId, int page) {
            this.originId = originId;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
