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
    public enum TravelStatus {
        READY,
        WORLD_UNAVAILABLE,
        MISSING,
        NO_ACCESS,
        NOT_DISCOVERED,
        NOT_ACTIVATED,
        SUPPRESSED,
        CROSS_WORLD_DISABLED,
        NO_POWER,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS
    }

    private final CdrWaystonePlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();

    public TeleportService(CdrWaystonePlugin plugin) { this.plugin = plugin; }
    public boolean isWarping(Player player) { return active.containsKey(player.getUniqueId()); }

    public void start(Player player, WaystoneData target) {
        start(player, target, null);
    }

    public void start(Player player, WaystoneData target, WaystoneData origin) {
        if (isWarping(player)) { player.sendMessage("§cYou are already warping."); return; }

        TravelStatus initialStatus = travelStatus(player, target, origin);
        if (!validateStatus(player, target, initialStatus)) return;

        EconomyService.Quote quote = plugin.economy().quote(player, origin, target);
        if (!quote.providerAvailable()) {
            player.sendMessage("§cTravel economy provider is unavailable.");
            return;
        }
        if (!plugin.economy().canAfford(player, quote)) {
            player.sendMessage("§cYou need §f" + quote.formatted() + "§c to use this Waystone route.");
            return;
        }

        int delay = Math.max(0, plugin.getConfig().getInt("warp.delay-seconds", 5));
        startLocations.put(player.getUniqueId(), player.getLocation().clone());
        if (!quote.free()) player.sendMessage("§7Travel cost locked: §f" + quote.formatted());

        BukkitTask task = new BukkitRunnable() {
            int remaining = delay;
            @Override public void run() {
                if (!player.isOnline()) { cleanup(player); cancel(); return; }
                if (remaining <= 0) {
                    TravelStatus routeStatus = travelStatusCore(player, target);
                    if (!validateStatus(player, target, routeStatus)) { cleanup(player); cancel(); return; }
                    if (!quote.providerAvailable()) {
                        player.sendMessage("§cTravel economy provider became unavailable."); cleanup(player); cancel(); return;
                    }
                    if (!plugin.economy().canAfford(player, quote)) {
                        player.sendMessage("§cTravel cancelled. You no longer have §f" + quote.formatted() + "§c.");
                        cleanup(player); cancel(); return;
                    }

                    Location destination = safeDestination(target);
                    if (destination == null) {
                        player.sendMessage("§cNo safe destination found near that Waystone."); cleanup(player); cancel(); return;
                    }

                    boolean crossWorld = !player.getWorld().getUID().equals(target.worldId());
                    EconomyService.Payment payment = plugin.economy().charge(player, quote);
                    if (payment == null) {
                        player.sendMessage("§cTravel payment failed. No teleport was performed.");
                        cleanup(player); cancel(); return;
                    }

                    cleanup(player);
                    cancel();
                    if (!player.teleport(destination)) {
                        plugin.economy().refund(player, payment);
                        player.sendMessage("§cTeleport failed. Travel payment was refunded and no Waystone power was consumed.");
                        return;
                    }

                    consumePowerIfNeeded(target, crossWorld);
                    if (payment.charged()) player.sendMessage("§aTravel paid: §f" + quote.formatted());
                    player.playSound(destination, Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.2f);
                    applyPortalSickness(player);
                    return;
                }

                String action = "Warping in " + remaining + "s";
                if (!quote.free()) action += " • " + quote.formatted();
                player.sendActionBar(Component.text(action));
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

    public TravelStatus travelStatus(Player player, WaystoneData target) {
        return travelStatus(player, target, null);
    }

    public TravelStatus travelStatus(Player player, WaystoneData target, WaystoneData origin) {
        TravelStatus core = travelStatusCore(player, target);
        if (core != TravelStatus.READY) return core;

        EconomyService.Quote quote = plugin.economy().quote(player, origin, target);
        if (!quote.providerAvailable()) return TravelStatus.ECONOMY_UNAVAILABLE;
        if (!plugin.economy().canAfford(player, quote)) return TravelStatus.INSUFFICIENT_FUNDS;
        return TravelStatus.READY;
    }

    private TravelStatus travelStatusCore(Player player, WaystoneData target) {
        if (player == null || target == null) return TravelStatus.MISSING;
        Location targetLoc = target.location();
        if (targetLoc == null || targetLoc.getWorld() == null) return TravelStatus.WORLD_UNAVAILABLE;
        if (targetLoc.getBlock().getType() != Material.LODESTONE) return TravelStatus.MISSING;

        if (!plugin.access().canAccess(player, target)) return TravelStatus.NO_ACCESS;

        if (!plugin.discovery().canUse(player, target)) {
            DiscoveryService.State state = plugin.discovery().status(player, target);
            return state == DiscoveryService.State.UNKNOWN ? TravelStatus.NOT_DISCOVERED : TravelStatus.NOT_ACTIVATED;
        }

        if (!target.alwaysActive() && isSuppressed(target)) return TravelStatus.SUPPRESSED;

        boolean crossWorld = !player.getWorld().getUID().equals(target.worldId());
        if (crossWorld && !plugin.getConfig().getBoolean("warp.allow-cross-world", true)) return TravelStatus.CROSS_WORLD_DISABLED;
        if (!target.alwaysActive() && requiresPower(crossWorld) && !hasPower(target)) return TravelStatus.NO_POWER;
        return TravelStatus.READY;
    }

    public String statusLabel(TravelStatus status) {
        return switch (status) {
            case READY -> "Ready";
            case WORLD_UNAVAILABLE -> "World unavailable";
            case MISSING -> "Waystone missing";
            case NO_ACCESS -> "Access denied";
            case NOT_DISCOVERED -> "Not discovered";
            case NOT_ACTIVATED -> "Not activated";
            case SUPPRESSED -> "Suppressed";
            case CROSS_WORLD_DISABLED -> "Cross-world disabled";
            case NO_POWER -> "No dimensional power";
            case ECONOMY_UNAVAILABLE -> "Economy unavailable";
            case INSUFFICIENT_FUNDS -> "Insufficient funds";
        };
    }

    private boolean validateStatus(Player player, WaystoneData target, TravelStatus status) {
        if (status == TravelStatus.READY) return true;

        switch (status) {
            case WORLD_UNAVAILABLE -> player.sendMessage("§cThat Waystone world is unavailable.");
            case MISSING -> player.sendMessage("§cThat Waystone no longer exists.");
            case NO_ACCESS -> {
                if (target != null && target.isAdmin()) player.sendMessage("§cThat Admin Waystone is not public.");
                else player.sendMessage("§cYou no longer have access to that Player Waystone.");
            }
            case NOT_DISCOVERED -> player.sendMessage("§7You have not discovered that Waystone yet.");
            case NOT_ACTIVATED -> player.sendMessage("§dYou discovered that Waystone, but it has not been activated yet.");
            case SUPPRESSED -> player.sendMessage("§cThat Waystone is suppressed.");
            case CROSS_WORLD_DISABLED -> player.sendMessage("§cCross-world Waystone travel is disabled.");
            case NO_POWER -> player.sendMessage("§cThat Waystone needs a charged Respawn Anchor below it.");
            case ECONOMY_UNAVAILABLE -> player.sendMessage("§cTravel economy provider is unavailable.");
            case INSUFFICIENT_FUNDS -> player.sendMessage("§cYou cannot afford this Waystone route.");
            default -> player.sendMessage("§cThat Waystone cannot be used right now.");
        }
        return false;
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
