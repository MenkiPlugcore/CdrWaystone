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
            0,1,2,3,4,5,6,7,8,
            9,10,11,12,13,14,15,16,17,
            18,19,20,21,22,23,24,25,26,
            27,28,29,30,31,32,33,34,35,
            36,37,38,39,40,41,42,43,44
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
            plugin.feedback().action(player, "§cWaystone Network disabled");
            return;
        }
        if (!canUseOrigin(player, origin, true)) return;

        List<WaystoneData> destinations = destinations(player, origin);
        int perPage = Math.max(9, Math.min(DESTINATION_SLOTS.length,
                plugin.getConfig().getInt("network.destinations-per-page", DESTINATION_SLOTS.length)));
        int pages = Math.max(1, (int) Math.ceil(destinations.size() / (double) perPage));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        NetworkHolder holder = new NetworkHolder(origin.id(), page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("✦ " + origin.name() + " → WAYSTONES", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        holder.inventory = inv;

        int start = page * perPage;
        int end = Math.min(destinations.size(), start + perPage);
        for (int index = start; index < end; index++) {
            WaystoneData target = destinations.get(index);
            inv.setItem(DESTINATION_SLOTS[index - start], destinationItem(player, origin, target));
        }

        if (destinations.isEmpty()) {
            inv.setItem(22, item(Material.GRAY_DYE, "No Active Destinations", NamedTextColor.GRAY,
                    "No other activated Waystones are available"));
        }

        ItemStack filler = filler();
        for (int slot = 45; slot < 54; slot++) inv.setItem(slot, filler);
        if (page > 0) inv.setItem(45, item(Material.ARROW, "Previous", NamedTextColor.YELLOW, "Page " + page + " / " + pages));
        inv.setItem(47, item(Material.NETHER_STAR, origin.name(), NamedTextColor.GOLD,
                "Origin Waystone", origin.worldName(), "Stay within " + formatRadius() + " blocks while charging"));
        inv.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED, "Close network"));
        inv.setItem(50, item(Material.ENDER_EYE, "Refresh", NamedTextColor.GREEN, "Refresh routes and prices"));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "Next", NamedTextColor.YELLOW, "Page " + (page + 2) + " / " + pages));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof NetworkHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;

        WaystoneData origin = plugin.registry().get(holder.originId);
        if (!canUseOrigin(player, origin, true)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 45 && holder.page > 0) { open(player, origin, holder.page - 1); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 50) { open(player, origin, holder.page); return; }
        if (slot == 53) { open(player, origin, holder.page + 1); return; }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        String raw = clicked.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (raw == null) return;

        UUID targetId;
        try {
            targetId = UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            plugin.feedback().action(player, "§cDestination unavailable");
            return;
        }

        WaystoneData target = plugin.registry().get(targetId);
        if (target == null || target.id().equals(origin.id()) || !isActiveDestination(player, target)) {
            plugin.feedback().action(player, "§cDestination no longer active");
            open(player, origin, holder.page);
            return;
        }

        TeleportService.TravelStatus status = plugin.teleports().travelStatus(player, target, origin);
        EconomyService.Quote quote = plugin.economy().quote(player, origin, target);
        if (status != TeleportService.TravelStatus.READY) {
            String message = "§c" + plugin.teleports().statusLabel(status);
            if (status == TeleportService.TravelStatus.INSUFFICIENT_FUNDS) message += " §7• Need §f" + quote.formatted();
            plugin.feedback().action(player, message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.55f, 0.85f);
            return;
        }

        player.closeInventory();
        plugin.teleports().start(player, target, origin);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof NetworkHolder) event.setCancelled(true);
    }

    private List<WaystoneData> destinations(Player player, WaystoneData origin) {
        List<WaystoneData> result = new ArrayList<>();
        for (WaystoneData data : plugin.registry().all()) {
            if (data.id().equals(origin.id())) continue;
            if (isActiveDestination(player, data)) result.add(data);
        }
        result.sort(Comparator
                .comparing((WaystoneData data) -> !data.isAdmin())
                .thenComparing(data -> data.name().toLowerCase(Locale.ROOT))
                .thenComparing(data -> data.id().toString()));
        return result;
    }

    private boolean isActiveDestination(Player player, WaystoneData data) {
        if (data == null || !plugin.visuals().isAnchorValid(data)) return false;
        if (!coreOperational(data)) return false;
        if (!plugin.access().canAccess(player, data)) return false;
        if (!plugin.discovery().canUse(player, data)) return false;
        return data.alwaysActive() || !plugin.teleports().isSuppressed(data);
    }

    private boolean canUseOrigin(Player player, WaystoneData origin, boolean notify) {
        String error = null;
        if (origin == null || !plugin.visuals().isAnchorValid(origin)) error = "Origin Waystone unavailable";
        else if (!coreOperational(origin)) error = "Dormant Waystone • Core required";
        else if (!plugin.access().canAccess(player, origin)) error = "Access denied";
        else if (!plugin.discovery().canUse(player, origin)) error = "Activate this Waystone first";
        else if (!origin.alwaysActive() && plugin.teleports().isSuppressed(origin)) error = "Waystone Network suppressed";
        else if (!nearOrigin(player, origin)) error = "Stay closer to " + origin.name();

        if (error != null && notify) plugin.feedback().action(player, "§c" + error);
        return error == null;
    }

    private boolean nearOrigin(Player player, WaystoneData origin) {
        Location location = origin.location();
        if (location == null || !player.getWorld().getUID().equals(origin.worldId())) return false;
        double radius = Math.max(2.0, plugin.getConfig().getDouble("network.origin-radius", 6.5));
        return player.getLocation().distanceSquared(location.clone().add(0.5, 0.5, 0.5)) <= radius * radius;
    }

    private boolean coreOperational(WaystoneData data) {
        return data.coreActive() || (data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true));
    }

    private ItemStack destinationItem(Player player, WaystoneData origin, WaystoneData target) {
        TeleportService.TravelStatus status = plugin.teleports().travelStatus(player, target, origin);
        EconomyService.Quote quote = plugin.economy().quote(player, origin, target);
        boolean ready = status == TeleportService.TravelStatus.READY;
        Material material = ready ? categoryMaterial(target.category()) : Material.RED_STAINED_GLASS_PANE;
        NamedTextColor color = ready ? NamedTextColor.GOLD : NamedTextColor.RED;
        String price = quote.free() ? "FREE" : quote.formatted();

        ItemStack stack = item(material, target.name(), color,
                "Distance: " + distance(origin, target),
                "Cost: " + price,
                "Status: " + plugin.teleports().statusLabel(status),
                ready ? "Click to travel" : "Route unavailable");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.id().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    private Material categoryMaterial(WaystoneData.Category category) {
        return switch (category) {
            case CAPITAL -> Material.BEACON;
            case CITY -> Material.BELL;
            case VILLAGE -> Material.EMERALD;
            case DUNGEON -> Material.SPAWNER;
            case KINGDOM -> Material.GOLDEN_HELMET;
            case PLAYER -> Material.ENDER_PEARL;
            case EVENT -> Material.FIREWORK_ROCKET;
            case OTHER -> Material.AMETHYST_SHARD;
        };
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

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String label, NamedTextColor color, String... lines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class NetworkHolder implements InventoryHolder {
        private final UUID originId;
        private final int page;
        private Inventory inventory;

        private NetworkHolder(UUID originId, int page) {
            this.originId = originId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
