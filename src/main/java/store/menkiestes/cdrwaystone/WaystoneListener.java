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
            event.getPlayer().sendMessage("§cYou reached your Player Waystone limit (§f" + limit + "§c). Remove or transfer one first.");
            return;
        }

        String skin = plugin.getConfig().getString("visuals.default-skin", "andesite");
        String name = plugin.getConfig().getString("waystones.default-name", "Waystone");
        WaystoneData data = plugin.registry().create(event.getPlayer().getUniqueId(), event.getBlockPlaced().getLocation(), name, skin);
        plugin.discovery().setState(event.getPlayer().getUniqueId(), data.id(), DiscoveryService.State.ACTIVATED, true);
        plugin.visuals().ensureCollision(data);
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.visuals().spawn(data));
        long owned = plugin.registry().countOwned(event.getPlayer().getUniqueId());
        int limit = plugin.access().limit(event.getPlayer());
        String count = limit < 0 ? owned + "/∞" : owned + "/" + limit;
        event.getPlayer().sendMessage("§aCdrWaystone created and activated. §7Owned: §f" + count + " §7Access: §f" + data.accessMode());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        WaystoneData barrierWaystone = waystoneFromClickedBlock(block);
        if (block.getType() == Material.BARRIER && barrierWaystone != null && barrierWaystone.collisionOwned()) {
            event.setCancelled(true); return;
        }
        WaystoneData data = plugin.registry().find(block.getLocation());
        if (data == null) return;

        if (data.isAdmin()) {
            if (!event.getPlayer().hasPermission("cdrwaystone.admin")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cThis is a server Admin Waystone and cannot be broken.");
                return;
            }
            if (data.permanent()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§6This Admin Waystone is permanent. Use §f/cws admin remove§6 or disable Permanent first.");
                return;
            }
        } else if (!plugin.access().canManage(event.getPlayer(), data)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cOnly the owner can break this Player Waystone.");
            return;
        }

        plugin.visuals().remove(data);
        plugin.registry().remove(data);
        plugin.discovery().forgetWaystone(data.id());
        event.getPlayer().sendMessage("§eCdrWaystone removed.");
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
                if (clickedWaystone.isAdmin()) player.sendMessage("§cThis Admin Waystone is not public.");
                else if (clickedWaystone.accessMode() == WaystoneData.AccessMode.TRUSTED) player.sendMessage("§cThis Waystone is restricted to trusted players.");
                else player.sendMessage("§cThis is a private Player Waystone.");
                return;
            }

            plugin.discovery().discover(player, clickedWaystone, true);

            if (hand.getType() == Material.NAME_TAG && hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
                if (!plugin.access().canManage(player, clickedWaystone)) {
                    player.sendMessage("§cYou cannot rename this Waystone."); event.setCancelled(true); return;
                }
                String newName = PlainTextComponentSerializer.plainText().serialize(hand.getItemMeta().displayName());
                if (!newName.isBlank()) {
                    clickedWaystone.name(newName); plugin.registry().save();
                    player.sendMessage("§aWaystone renamed to §f" + newName);
                }
                event.setCancelled(true); return;
            }

            if (plugin.keys().isKey(hand)) {
                if (!player.hasPermission("cdrwaystone.use")) return;
                if (!plugin.discovery().canUse(player, clickedWaystone)) {
                    player.sendMessage("§dThis Waystone must be activated first. §7Right-click it normally and use the Activate button.");
                    event.setCancelled(true); return;
                }
                if (plugin.teleports().isSuppressed(clickedWaystone)) {
                    player.sendMessage("§cThis Waystone is suppressed."); event.setCancelled(true); return;
                }
                java.util.UUID existing = plugin.keys().target(hand);
                boolean relinkable = plugin.getConfig().getBoolean("key.relinkable", true);
                if (existing == null || (relinkable && player.isSneaking())) {
                    plugin.keys().bind(hand, clickedWaystone);
                    player.sendMessage("§dWaystone Key bound to §f" + clickedWaystone.name());
                } else if (existing.equals(clickedWaystone.id())) player.sendMessage("§7This key is already bound here.");
                else player.sendMessage("§7Sneak + right-click a Waystone to relink this key.");
                event.setCancelled(true); return;
            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && plugin.getConfig().getBoolean("gui.enabled", true)) {
                event.setCancelled(true);
                if (player.isSneaking() && hand.getType().isAir() && plugin.getConfig().getBoolean("network.enabled", true)) {
                    plugin.networkGui().open(player, clickedWaystone);
                } else {
                    plugin.gui().openMain(player, clickedWaystone);
                }
            }
            return;
        }

        if (plugin.keys().isKey(hand)) {
            java.util.UUID targetId = plugin.keys().target(hand);
            if (targetId == null) { player.sendMessage("§7This Waystone Key is not bound yet."); return; }
            WaystoneData target = plugin.registry().get(targetId);
            if (target == null) { player.sendMessage("§cThe linked Waystone no longer exists."); return; }
            plugin.teleports().start(player, target); event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("anti-abuse.combat.enabled", true)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player direct) {
            attacker = direct;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        plugin.guards().tagCombat(victim);
        plugin.guards().tagCombat(attacker);
        if (plugin.getConfig().getBoolean("anti-abuse.combat.cancel-active-warp", true)) {
            plugin.teleports().cancel(victim, "entered combat");
            plugin.teleports().cancel(attacker, "entered combat");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getConfig().getBoolean("warp.damage-cancels", true)) plugin.teleports().cancel(player, "damaged");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("warp.movement-cancels", false) || !plugin.teleports().isWarping(event.getPlayer())) return;
        Location start = plugin.teleports().startLocation(event.getPlayer());
        if (start == null || event.getTo() == null) return;
        if (start.getBlockX()!=event.getTo().getBlockX() || start.getBlockY()!=event.getTo().getBlockY() || start.getBlockZ()!=event.getTo().getBlockZ()) plugin.teleports().cancel(event.getPlayer(), "moved");
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.teleports().cancel(event.getPlayer(), "disconnected"); }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        List<WaystoneData> stale = new ArrayList<>();
        for (WaystoneData data : plugin.registry().inChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            Location location = data.location();
            if (location == null || location.getBlock().getType() != Material.LODESTONE) { plugin.visuals().remove(data); stale.add(data); continue; }
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH) public void onEntityExplode(EntityExplodeEvent event) { handleExplosion(event.blockList()); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH) public void onBlockExplode(BlockExplodeEvent event) { handleExplosion(event.blockList()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.getConfig().getBoolean("protection.pistons", true) && event.getBlocks().stream().anyMatch(this::isManagedWaystoneBlock)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (plugin.getConfig().getBoolean("protection.pistons", true) && event.getBlocks().stream().anyMatch(this::isManagedWaystoneBlock)) event.setCancelled(true);
    }

    private void handleExplosion(List<Block> blocks) {
        blocks.removeIf(block -> {
            WaystoneData data = dataFromManagedBlock(block);
            return data != null && data.isAdmin() && data.permanent();
        });
        if (plugin.getConfig().getBoolean("protection.explosions", true)) { blocks.removeIf(this::isManagedWaystoneBlock); return; }
        List<WaystoneData> affected = blocks.stream().map(block -> plugin.registry().find(block.getLocation())).filter(java.util.Objects::nonNull).distinct().toList();
        if (affected.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean changed = false;
            for (WaystoneData data : affected) {
                Location location = data.location();
                if (location != null && location.getBlock().getType() != Material.LODESTONE) {
                    plugin.visuals().remove(data); plugin.registry().remove(data, false); plugin.discovery().forgetWaystone(data.id()); changed = true;
                }
            }
            if (changed) plugin.registry().save();
        });
    }

    private WaystoneData dataFromManagedBlock(Block block) {
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) {
            WaystoneData below = plugin.registry().find(block.getRelative(0,-1,0).getLocation());
            if (below != null && below.collisionOwned()) return below;
        }
        return null;
    }

    private boolean isManagedWaystoneBlock(Block block) { return dataFromManagedBlock(block) != null; }
    private WaystoneData waystoneFromClickedBlock(Block block) { return dataFromManagedBlock(block); }
}
