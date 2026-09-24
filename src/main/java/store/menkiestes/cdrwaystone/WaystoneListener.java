package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class WaystoneListener implements Listener {
    private final CdrWaystonePlugin plugin;
    public WaystoneListener(CdrWaystonePlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("waystones.auto-register-lodestones", true)) return;
        if (event.getBlockPlaced().getType() != Material.LODESTONE) return;
        if (!event.getPlayer().hasPermission("cdrwaystone.use")) return;
        if (plugin.registry().find(event.getBlockPlaced().getLocation()) != null) return;

        if (!plugin.access().canCreate(event.getPlayer())) {
            event.setCancelled(true);
            int limit = plugin.access().limit(event.getPlayer());
            plugin.feedback().action(event.getPlayer(), "§cWaystone limit reached §7(" + limit + ")");
            return;
        }

        String skin = plugin.getConfig().getString("visuals.default-skin", "andesite");
        String name = plugin.getConfig().getString("waystones.default-name", "Waystone");
        WaystoneData data = plugin.registry().create(event.getPlayer().getUniqueId(), event.getBlockPlaced().getLocation(), name, skin);
        DiscoveryService.State ownerState = data.coreActive() ? DiscoveryService.State.ACTIVATED : DiscoveryService.State.DISCOVERED;
        plugin.discovery().setState(event.getPlayer().getUniqueId(), data.id(), ownerState, true);
        plugin.visuals().ensureCollision(data);
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.visuals().spawn(data));

        if (data.coreActive()) {
            plugin.feedback().title(event.getPlayer(), "§aWAYSTONE READY", "§f" + data.name());
        } else {
            plugin.feedback().title(event.getPlayer(), "§5DORMANT WAYSTONE", "§dInstall a Waystone Core");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        WaystoneData barrierWaystone = waystoneFromClickedBlock(block);
        if (block.getType() == Material.BARRIER && barrierWaystone != null && barrierWaystone.collisionOwned()) {
            event.setCancelled(true);
            return;
        }
        WaystoneData data = plugin.registry().find(block.getLocation());
        if (data == null) return;

        if (data.isAdmin()) {
            if (!event.getPlayer().hasPermission("cdrwaystone.admin")) {
                event.setCancelled(true);
                plugin.feedback().action(event.getPlayer(), "§cServer Waystone cannot be broken");
                return;
            }
            if (data.permanent()) {
                event.setCancelled(true);
                plugin.feedback().action(event.getPlayer(), "§6Permanent Admin Waystone");
                return;
            }
        } else if (!plugin.access().canManage(event.getPlayer(), data)) {
            event.setCancelled(true);
            plugin.feedback().action(event.getPlayer(), "§cOnly the owner can break this Waystone");
            return;
        }

        plugin.visuals().remove(data);
        plugin.registry().remove(data);
        plugin.discovery().forgetWaystone(data.id());
        plugin.feedback().action(event.getPlayer(), "§eWaystone removed");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        WaystoneData clickedWaystone = event.getClickedBlock() == null ? null : waystoneFromClickedBlock(event.getClickedBlock());

        if (clickedWaystone != null) {
            if (!plugin.access().canAccess(player, clickedWaystone)) {
                event.setCancelled(true);
                String reason = clickedWaystone.isAdmin() ? "Server Waystone is restricted"
                        : clickedWaystone.accessMode() == WaystoneData.AccessMode.TRUSTED ? "Trusted players only"
                        : "Private Waystone";
                plugin.feedback().action(player, "§c" + reason);
                return;
            }

            plugin.discovery().discover(player, clickedWaystone, false);

            if (plugin.cores().isCore(hand)) {
                event.setCancelled(true);
                plugin.cores().activate(player, clickedWaystone, hand);
                return;
            }

            if (hand.getType() == Material.NAME_TAG && hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
                event.setCancelled(true);
                if (!plugin.access().canManage(player, clickedWaystone)) {
                    plugin.feedback().action(player, "§cYou cannot rename this Waystone");
                    return;
                }
                String newName = PlainTextComponentSerializer.plainText().serialize(hand.getItemMeta().displayName());
                if (!newName.isBlank()) {
                    clickedWaystone.name(newName);
                    plugin.registry().save();
                    plugin.feedback().action(player, "§aWaystone renamed §7• §f" + newName);
                }
                return;
            }

            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            event.setCancelled(true);

            if (!player.hasPermission("cdrwaystone.use")) {
                plugin.feedback().action(player, "§cNo permission to use Waystones");
                return;
            }
            if (!coreOperational(clickedWaystone)) {
                plugin.feedback().action(player, "§5Dormant Waystone §7• §dCore required");
                return;
            }
            if (plugin.teleports().isSuppressed(clickedWaystone)) {
                plugin.feedback().action(player, "§cWaystone Network suppressed");
                return;
            }
            if (!plugin.getConfig().getBoolean("network.enabled", true)) {
                plugin.feedback().action(player, "§cWaystone Network disabled");
                return;
            }

            if (plugin.discovery().status(player, clickedWaystone) != DiscoveryService.State.ACTIVATED) {
                if (!plugin.discovery().activate(player, clickedWaystone)) return;
            }

            if (plugin.keys().isKey(hand)) plugin.keys().clearLegacyBinding(hand);
            plugin.networkGui().open(player, clickedWaystone);
            return;
        }

        if (plugin.keys().isKey(hand)) {
            event.setCancelled(true);
            plugin.keys().clearLegacyBinding(hand);
            plugin.feedback().action(player, "§dWaystone Key §7• Use it on an active Waystone");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("anti-abuse.combat.enabled", true)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player direct) attacker = direct;
        else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) attacker = shooter;
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        plugin.guards().tagCombat(victim);
        plugin.guards().tagCombat(attacker);
        if (plugin.getConfig().getBoolean("anti-abuse.combat.cancel-active-warp", true)) {
            plugin.teleports().cancel(victim, "Combat locked");
            plugin.teleports().cancel(attacker, "Combat locked");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getConfig().getBoolean("warp.damage-cancels", true)) {
            plugin.teleports().cancel(player, "Damaged");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warp.movement-cancels", false) || !plugin.teleports().isWarping(event.getPlayer())) return;
        Location start = plugin.teleports().startLocation(event.getPlayer());
        if (start == null || event.getTo() == null) return;
        if (start.getBlockX() != event.getTo().getBlockX()
                || start.getBlockY() != event.getTo().getBlockY()
                || start.getBlockZ() != event.getTo().getBlockZ()) {
            plugin.teleports().cancel(event.getPlayer(), "Moved");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.teleports().cancel(event.getPlayer(), "Disconnected");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        List<WaystoneData> stale = new ArrayList<>();
        for (WaystoneData data : plugin.registry().inChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            Location location = data.location();
            if (location == null || location.getBlock().getType() != Material.LODESTONE) {
                plugin.visuals().remove(data);
                stale.add(data);
                continue;
            }
            plugin.visuals().ensureCollision(data);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.visuals().spawn(data));
        }
        if (!stale.isEmpty()) {
            for (WaystoneData data : stale) {
                plugin.registry().remove(data, false);
                plugin.discovery().forgetWaystone(data.id());
            }
            plugin.registry().save();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) { handleExplosion(event.blockList()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) { handleExplosion(event.blockList()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.getConfig().getBoolean("protection.pistons", true)
                && event.getBlocks().stream().anyMatch(this::isManagedWaystoneBlock)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (plugin.getConfig().getBoolean("protection.pistons", true)
                && event.getBlocks().stream().anyMatch(this::isManagedWaystoneBlock)) event.setCancelled(true);
    }

    private void handleExplosion(List<Block> blocks) {
        blocks.removeIf(block -> {
            WaystoneData data = dataFromManagedBlock(block);
            return data != null && data.isAdmin() && data.permanent();
        });
        if (plugin.getConfig().getBoolean("protection.explosions", true)) {
            blocks.removeIf(this::isManagedWaystoneBlock);
            return;
        }

        List<WaystoneData> affected = blocks.stream()
                .map(block -> plugin.registry().find(block.getLocation()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (affected.isEmpty()) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean changed = false;
            for (WaystoneData data : affected) {
                Location location = data.location();
                if (location != null && location.getBlock().getType() != Material.LODESTONE) {
                    plugin.visuals().remove(data);
                    plugin.registry().remove(data, false);
                    plugin.discovery().forgetWaystone(data.id());
                    changed = true;
                }
            }
            if (changed) plugin.registry().save();
        });
    }

    private boolean coreOperational(WaystoneData data) {
        return data.coreActive() || (data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true));
    }

    private WaystoneData dataFromManagedBlock(Block block) {
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) {
            WaystoneData below = plugin.registry().find(block.getRelative(0, -1, 0).getLocation());
            if (below != null && below.collisionOwned()) return below;
        }
        return null;
    }

    private boolean isManagedWaystoneBlock(Block block) { return dataFromManagedBlock(block) != null; }
    private WaystoneData waystoneFromClickedBlock(Block block) { return dataFromManagedBlock(block); }
}
