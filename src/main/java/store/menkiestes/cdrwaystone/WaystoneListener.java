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

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WaystoneListener implements Listener {
    private final CdrWaystonePlugin plugin;

    public WaystoneListener(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Staff placement rule:
     * - cdrwaystone.create => Lodestone is consumed as the placement trigger for an official Waystone.
     * - everyone else => completely normal vanilla Lodestone, never registered by CdrWaystone.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("waystones.auto-register-lodestones", true)) return;
        if (event.getBlockPlaced().getType() != Material.LODESTONE) return;
        if (!event.getPlayer().hasPermission("cdrwaystone.create")) return;
        if (plugin.registry().find(event.getBlockPlaced().getLocation()) != null) return;

        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        String skin = plugin.getConfig().getString("visuals.default-skin", "andesite");
        if (!plugin.skins().containsKey(skin)) skin = "andesite";
        String name = plugin.getConfig().getString("waystones.default-name", "Waystone");
        WaystoneData.Category category = parseCategory(
                plugin.getConfig().getString("staff-placement.category", "CITY"),
                WaystoneData.Category.CITY);

        WaystoneData data = plugin.registry().create(
                null,
                location,
                name,
                skin,
                WaystoneData.Type.ADMIN,
                plugin.getConfig().getBoolean("staff-placement.public", true),
                plugin.getConfig().getBoolean("staff-placement.free", false),
                plugin.getConfig().getBoolean("staff-placement.permanent", false),
                plugin.getConfig().getBoolean("staff-placement.always-active", true),
                plugin.getConfig().getBoolean("staff-placement.globally-discovered", true),
                WaystoneData.AccessMode.PRIVATE,
                Set.of(),
                category,
                WaystoneData.CoreState.ACTIVE
        );
        plugin.discovery().setState(player.getUniqueId(), data.id(), DiscoveryService.State.ACTIVATED, true);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.visuals().materialize(data)) {
                plugin.registry().remove(data);
                plugin.discovery().forgetWaystone(data.id());
                plugin.feedback().action(player, "§cWaystone model unavailable §7• Lodestone kept vanilla");
                return;
            }
            plugin.feedback().title(player, "§d§lWAYSTONE CREATED", "§f" + data.name(), 120, 900, 300);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        WaystoneData data = waystoneFromClickedBlock(block);
        if (data == null) return;

        Player player = event.getPlayer();

        // Official Waystones use invisible Barrier anchors. Never let Bukkit
        // break only one anchor and leave a ghost node. Staff destruction is
        // converted into one atomic Waystone removal instead.
        if (block.getType() == Material.BARRIER) {
            event.setCancelled(true);
            if (!player.hasPermission("cdrwaystone.admin")) {
                plugin.feedback().action(player, "§cOfficial Waystone is protected");
                return;
            }
            if (data.permanent()) {
                plugin.feedback().action(player, "§6Permanent Waystone");
                return;
            }
            removeWaystone(data, player);
            return;
        }

        if (data.isAdmin()) {
            if (!player.hasPermission("cdrwaystone.admin")) {
                event.setCancelled(true);
                plugin.feedback().action(player, "§cServer Waystone cannot be broken");
                return;
            }
            if (data.permanent()) {
                event.setCancelled(true);
                plugin.feedback().action(player, "§6Permanent Waystone");
                return;
            }
        } else if (!plugin.access().canManage(player, data)) {
            event.setCancelled(true);
            plugin.feedback().action(player, "§cOnly the owner can break this Waystone");
            return;
        }

        // Legacy visible registered blocks are also removed atomically.
        event.setCancelled(true);
        removeWaystone(data, player);
    }

    private void removeWaystone(WaystoneData data, Player player) {
        if (plugin.registry().get(data.id()) == null) return;
        plugin.visuals().remove(data);
        plugin.registry().remove(data);
        plugin.discovery().forgetWaystone(data.id());
        plugin.feedback().action(player, "§eWaystone removed");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) {
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (plugin.keys().isKey(hand)) {
                event.setCancelled(true);
                plugin.keys().clearLegacyBinding(hand);
                plugin.feedback().action(event.getPlayer(), "§dWaystone Key §7• Use it on an active Waystone");
            }
            return;
        }

        WaystoneData clickedWaystone = waystoneFromClickedBlock(event.getClickedBlock());
        if (clickedWaystone == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!plugin.access().canAccess(player, clickedWaystone)) {
            event.setCancelled(true);
            plugin.feedback().action(player, "§cWaystone access denied");
            return;
        }

        plugin.discovery().discover(player, clickedWaystone, false);

        if (plugin.cores().isCore(hand)) {
            event.setCancelled(true);
            plugin.cores().activate(player, clickedWaystone, hand);
            return;
        }

        if (isNamedNameTag(hand)) {
            event.setCancelled(true);
            renameWaystone(player, clickedWaystone, hand);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        openNetwork(player, clickedWaystone, hand);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFurnitureInteract(PlayerInteractAtEntityEvent event) {
        WaystoneData data = plugin.visuals().waystoneFromEntity(event.getRightClicked());
        if (data == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!plugin.access().canAccess(player, data)) {
            plugin.feedback().action(player, "§cWaystone access denied");
            return;
        }

        plugin.discovery().discover(player, data, false);

        if (plugin.cores().isCore(hand)) {
            plugin.cores().activate(player, data, hand);
            return;
        }

        if (isNamedNameTag(hand)) {
            renameWaystone(player, data, hand);
            return;
        }

        openNetwork(player, data, hand);
    }

    private boolean isNamedNameTag(ItemStack hand) {
        return hand != null
                && hand.getType() == Material.NAME_TAG
                && hand.hasItemMeta()
                && hand.getItemMeta().hasDisplayName();
    }

    private void renameWaystone(Player player, WaystoneData waystone, ItemStack hand) {
        if (!plugin.access().canManage(player, waystone) && !player.hasPermission("cdrwaystone.admin")) {
            plugin.feedback().action(player, "§cYou cannot rename this Waystone");
            return;
        }
        String newName = PlainTextComponentSerializer.plainText().serialize(hand.getItemMeta().displayName());
        if (newName.isBlank()) return;
        waystone.name(newName);
        plugin.registry().save();
        plugin.feedback().action(player, "§aWaystone renamed §7• §f" + newName);
    }

    private void openNetwork(Player player, WaystoneData waystone, ItemStack hand) {
        if (!plugin.access().canAccess(player, waystone)) {
            plugin.feedback().action(player, "§cWaystone access denied");
            return;
        }
        if (!player.hasPermission("cdrwaystone.use")) {
            plugin.feedback().action(player, "§cNo permission to use Waystones");
            return;
        }
        if (!coreOperational(waystone)) {
            plugin.feedback().action(player, "§5Dormant Waystone §7• §dCore required");
            return;
        }
        if (plugin.teleports().isSuppressed(waystone)) {
            plugin.feedback().action(player, "§cWaystone Network suppressed");
            return;
        }
        if (!plugin.getConfig().getBoolean("network.enabled", true)) {
            plugin.feedback().action(player, "§cWaystone Network disabled");
            return;
        }

        plugin.discovery().discover(player, waystone, false);
        if (plugin.discovery().status(player, waystone) != DiscoveryService.State.ACTIVATED) {
            if (!plugin.discovery().activate(player, waystone)) return;
        }

        if (plugin.keys().isKey(hand)) plugin.keys().clearLegacyBinding(hand);
        plugin.networkGui().open(player, waystone);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        WaystoneData furniture = plugin.visuals().waystoneFromEntity(event.getEntity());
        if (furniture != null) {
            event.setCancelled(true);
            return;
        }

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
        if (plugin.visuals().waystoneFromEntity(event.getEntity()) != null) {
            event.setCancelled(true);
            return;
        }
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
        for (WaystoneData data : plugin.registry().inChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            Location location = data.location();
            if (location == null) continue;
            Material type = location.getBlock().getType();
            if (type != Material.BARRIER && type != Material.LODESTONE && type != Material.AIR) {
                plugin.getLogger().warning("Waystone " + data.id() + " is obstructed by " + type + "; keeping registry entry.");
                continue;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.visuals().materialize(data));
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
        if (plugin.getConfig().getBoolean("protection.explosions", true)) {
            blocks.removeIf(this::isManagedWaystoneBlock);
            return;
        }
        blocks.removeIf(block -> {
            WaystoneData data = dataFromManagedBlock(block);
            return data != null && data.isAdmin() && data.permanent();
        });
    }

    private WaystoneData.Category parseCategory(String raw, WaystoneData.Category fallback) {
        if (raw == null) return fallback;
        try {
            return WaystoneData.Category.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
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

    private boolean isManagedWaystoneBlock(Block block) {
        return dataFromManagedBlock(block) != null;
    }

    private WaystoneData waystoneFromClickedBlock(Block block) {
        return dataFromManagedBlock(block);
    }
}
