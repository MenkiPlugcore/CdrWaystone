package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WaystoneListener implements Listener {
    private final CdrWaystonePlugin plugin;

    public WaystoneListener(CdrWaystonePlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("waystones.auto-register-lodestones", true)) return;
        if (event.getBlockPlaced().getType() != Material.LODESTONE) return;
        if (!event.getPlayer().hasPermission("cdrwaystone.use")) return;
        if (plugin.registry().find(event.getBlockPlaced().getLocation()) != null) return;

        String skin = plugin.getConfig().getString("visuals.default-skin", "andesite");
        String name = plugin.getConfig().getString("waystones.default-name", "Waystone");
        WaystoneData data = plugin.registry().create(event.getPlayer().getUniqueId(), event.getBlockPlaced().getLocation(), name, skin);
        plugin.visuals().ensureCollision(data);
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.visuals().spawn(data));
        event.getPlayer().sendMessage("§aCdrWaystone created. Skin: §f" + skin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        WaystoneData barrierWaystone = waystoneFromClickedBlock(block);
        if (block.getType() == Material.BARRIER && barrierWaystone != null) {
            event.setCancelled(true);
            return;
        }
        WaystoneData data = plugin.registry().find(block.getLocation());
        if (data == null) return;
        plugin.visuals().remove(data);
        plugin.registry().remove(data);
        event.getPlayer().sendMessage("§eCdrWaystone removed.");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        WaystoneData clickedWaystone = event.getClickedBlock() == null ? null : waystoneFromClickedBlock(event.getClickedBlock());
        if (clickedWaystone != null) {
            if (hand.getType() == Material.NAME_TAG && hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
                if (!isOwnerOrAdmin(player, clickedWaystone)) {
                    player.sendMessage("§cYou do not own this Waystone.");
                    event.setCancelled(true); return;
                }
                String newName = PlainTextComponentSerializer.plainText().serialize(hand.getItemMeta().displayName());
                if (!newName.isBlank()) {
                    clickedWaystone.name(newName);
                    plugin.registry().save();
                    player.sendMessage("§aWaystone renamed to §f" + newName);
                }
                event.setCancelled(true); return;
            }

            if (plugin.keys().isKey(hand)) {
                if (!player.hasPermission("cdrwaystone.use")) return;
                if (plugin.teleports().isSuppressed(clickedWaystone)) {
                    player.sendMessage("§cThis Waystone is suppressed.");
                    event.setCancelled(true); return;
                }
                java.util.UUID existing = plugin.keys().target(hand);
                boolean relinkable = plugin.getConfig().getBoolean("key.relinkable", true);
                if (existing == null || (relinkable && player.isSneaking())) {
                    plugin.keys().bind(hand, clickedWaystone);
                    player.sendMessage("§dWaystone Key bound to §f" + clickedWaystone.name());
                } else if (existing.equals(clickedWaystone.id())) {
                    player.sendMessage("§7This key is already bound here.");
                } else {
                    player.sendMessage("§7Sneak + right-click a Waystone to relink this key.");
                }
                event.setCancelled(true); return;
            }
            return;
        }

        if (plugin.keys().isKey(hand)) {
            java.util.UUID targetId = plugin.keys().target(hand);
            if (targetId == null) {
                player.sendMessage("§7This Waystone Key is not bound yet.");
                return;
            }
            WaystoneData target = plugin.registry().get(targetId);
            if (target == null) {
                player.sendMessage("§cThe linked Waystone no longer exists.");
                return;
            }
            plugin.teleports().start(player, target);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getConfig().getBoolean("warp.damage-cancels", true)) plugin.teleports().cancel(player, "damaged");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warp.movement-cancels", false)) return;
        if (!plugin.teleports().isWarping(event.getPlayer())) return;
        Location start = plugin.teleports().startLocation(event.getPlayer());
        if (start == null) return;
        if (start.getBlockX() != event.getTo().getBlockX() || start.getBlockY() != event.getTo().getBlockY() || start.getBlockZ() != event.getTo().getBlockZ()) {
            plugin.teleports().cancel(event.getPlayer(), "moved");
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.teleports().cancel(event.getPlayer(), "disconnected"); }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        for (WaystoneData data : plugin.registry().inChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            plugin.visuals().ensureCollision(data);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.visuals().spawn(data));
        }
    }

    private WaystoneData waystoneFromClickedBlock(Block block) {
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) {
            return plugin.registry().find(block.getRelative(0, -1, 0).getLocation());
        }
        return null;
    }

    private boolean isOwnerOrAdmin(Player player, WaystoneData data) {
        return data.owner().equals(player.getUniqueId()) || player.hasPermission("cdrwaystone.admin");
    }
}
