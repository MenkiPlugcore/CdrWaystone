package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportService {
    private final CdrWaystonePlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();

    public TeleportService(CdrWaystonePlugin plugin) { this.plugin = plugin; }
    public boolean isWarping(Player player) { return active.containsKey(player.getUniqueId()); }

    public void start(Player player, WaystoneData target) {
        if (isWarping(player)) { player.sendMessage("§cYou are already warping."); return; }
        if (!validateTarget(player, target)) return;
        int delay = Math.max(0, plugin.getConfig().getInt("warp.delay-seconds", 5));
        startLocations.put(player.getUniqueId(), player.getLocation().clone());

        BukkitTask task = new BukkitRunnable() {
            int remaining = delay;
            @Override public void run() {
                if (!player.isOnline()) { cleanup(player); cancel(); return; }
                if (remaining <= 0) {
                    if (!validateTarget(player, target)) { cleanup(player); cancel(); return; }
                    Location destination = safeDestination(target);
                    if (destination == null) { player.sendMessage("§cNo safe destination found near that Waystone."); cleanup(player); cancel(); return; }
                    boolean crossWorld = !player.getWorld().getUID().equals(target.worldId());
                    cleanup(player); cancel();
                    if (!player.teleport(destination)) { player.sendMessage("§cTeleport failed. No Waystone power was consumed."); return; }
                    consumePowerIfNeeded(target, crossWorld);
                    player.playSound(destination, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.2f);
                    applyPortalSickness(player);
                    return;
                }
                player.sendActionBar(Component.text("Warping in " + remaining + "s..."));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 1.2f);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        active.put(player.getUniqueId(), task);
    }

    public void cancel(Player player, String reason) {
        BukkitTask task = active.remove(player.getUniqueId());
        startLocations.remove(player.getUniqueId());
        if (task != null) { task.cancel(); if (player.isOnline()) player.sendActionBar(Component.text("Warp cancelled: " + reason)); }
    }
    public void cancelAll() { for (BukkitTask task : active.values()) task.cancel(); active.clear(); startLocations.clear(); }
    public Location startLocation(Player player) { return startLocations.get(player.getUniqueId()); }
    private void cleanup(Player player) { active.remove(player.getUniqueId()); startLocations.remove(player.getUniqueId()); }

    private boolean validateTarget(Player player, WaystoneData target) {
        Location targetLoc = target.location();
        if (targetLoc == null) { player.sendMessage("§cThat Waystone world is unavailable."); return false; }
        if (targetLoc.getBlock().getType() != Material.LODESTONE) { player.sendMessage("§cThat Waystone no longer exists."); return false; }
        if (target.isAdmin() && !target.publicAccess() && !player.hasPermission("cdrwaystone.admin")) {
            player.sendMessage("§cThat Admin Waystone is not public."); return false;
        }
        if (!target.alwaysActive() && isSuppressed(target)) { player.sendMessage("§cThat Waystone is suppressed."); return false; }
        boolean crossWorld = !player.getWorld().getUID().equals(target.worldId());
        if (crossWorld && !plugin.getConfig().getBoolean("warp.allow-cross-world", true)) {
            player.sendMessage("§cCross-world Waystone travel is disabled."); return false;
        }
        if (!target.alwaysActive() && requiresPower(crossWorld) && !hasPower(target)) {
            player.sendMessage("§cThat Waystone needs a charged Respawn Anchor below it."); return false;
        }
        return true;
    }

    public boolean isSuppressed(WaystoneData data) {
        if (data.alwaysActive()) return false;
        if (!plugin.getConfig().getBoolean("suppression.enabled", true)) return false;
        Material material = Material.matchMaterial(plugin.getConfig().getString("suppression.block", "OBSIDIAN"));
        Location loc = data.location();
        return material != null && loc != null && loc.clone().add(0, -1, 0).getBlock().getType() == material;
    }

    private boolean requiresPower(boolean crossWorld) {
        String mode = plugin.getConfig().getString("power.require", "INTER_DIMENSION").toUpperCase();
        return switch (mode) { case "ALL" -> true; case "INTER_DIMENSION" -> crossWorld; default -> false; };
    }

    private boolean hasPower(WaystoneData target) {
        Location loc = target.location(); if (loc == null) return false;
        Block block = loc.clone().add(0, -1, 0).getBlock();
        if (block.getType() != Material.RESPAWN_ANCHOR) return false;
        BlockData data = block.getBlockData();
        if (!(data instanceof RespawnAnchor anchor)) return false;
        int cost = Math.max(0, plugin.getConfig().getInt("power.respawn-anchor-cost", 1));
        return anchor.getCharges() >= cost;
    }

    private void consumePowerIfNeeded(WaystoneData target, boolean crossWorld) {
        if (target.alwaysActive() || !requiresPower(crossWorld)) return;
        Location loc = target.location(); if (loc == null) return;
        Block block = loc.clone().add(0, -1, 0).getBlock();
        if (!(block.getBlockData() instanceof RespawnAnchor anchor)) return;
        int cost = Math.max(0, plugin.getConfig().getInt("power.respawn-anchor-cost", 1));
        anchor.setCharges(Math.max(0, anchor.getCharges() - cost));
        block.setBlockData(anchor, false);
    }

    private Location safeDestination(WaystoneData target) {
        Location base = target.location(); if (base == null) return null;
        int radius = Math.max(1, plugin.getConfig().getInt("warp.safe-radius", 3));
        World world = base.getWorld();
        for (int r = 1; r <= radius; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
            Block feet = world.getBlockAt(base.getBlockX()+dx, base.getBlockY(), base.getBlockZ()+dz);
            Block head = feet.getRelative(0,1,0), floor = feet.getRelative(0,-1,0);
            if (feet.isPassable() && head.isPassable() && floor.getType().isSolid())
                return new Location(world, feet.getX()+0.5, feet.getY(), feet.getZ()+0.5, base.getYaw(), 0f);
        }
        return base.clone().add(0.5, 2.05, 0.5);
    }

    private void applyPortalSickness(Player player) {
        if (!plugin.getConfig().getBoolean("portal-sickness.enabled", true)) return;
        double chance = plugin.getConfig().getDouble("portal-sickness.chance", 0.05);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        PotionEffectType nausea = PotionEffectType.getByName("NAUSEA"), blindness = PotionEffectType.getByName("BLINDNESS");
        if (nausea != null) player.addPotionEffect(new PotionEffect(nausea, plugin.getConfig().getInt("portal-sickness.nausea-seconds",15)*20,0));
        if (blindness != null) player.addPotionEffect(new PotionEffect(blindness, plugin.getConfig().getInt("portal-sickness.blindness-seconds",3)*20,0));
        double damage = plugin.getConfig().getDouble("portal-sickness.damage",5.0); if (damage > 0) player.damage(damage);
        player.sendMessage("§5Portal sickness washes over you...");
    }
}
