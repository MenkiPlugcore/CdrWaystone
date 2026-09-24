package store.menkiestes.cdrwaystone;

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
        ORIGIN_REQUIRED,
        ORIGIN_TOO_FAR,
        SAME_WAYSTONE,
        WORLD_UNAVAILABLE,
        MISSING,
        DORMANT,
        NO_ACCESS,
        NOT_DISCOVERED,
        NOT_ACTIVATED,
        SUPPRESSED,
        CROSS_WORLD_DISABLED,
        NO_POWER,
        ECONOMY_UNAVAILABLE,
        INSUFFICIENT_FUNDS,
        COOLDOWN,
        COMBAT_LOCKED,
        KNOCKED_OUT
    }

    private final CdrWaystonePlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();
    private final Map<UUID, WaystoneData> activeOrigins = new HashMap<>();

    public TeleportService(CdrWaystonePlugin plugin) { this.plugin = plugin; }
    public boolean isWarping(Player player) { return active.containsKey(player.getUniqueId()); }

    public void start(Player player, WaystoneData target) {
        plugin.feedback().action(player, "§cTravel must begin at a Waystone");
    }

    public void start(Player player, WaystoneData target, WaystoneData origin) {
        if (isWarping(player)) {
            plugin.feedback().action(player, "§eWaystone is already charging");
            return;
        }

        TravelStatus initialStatus = travelStatus(player, target, origin);
        if (!validateStatus(player, target, initialStatus)) return;

        EconomyService.Quote quote = plugin.economy().quote(player, origin, target);
        if (!quote.providerAvailable()) {
            plugin.feedback().action(player, "§cTravel economy unavailable");
            return;
        }
        if (!plugin.economy().canAfford(player, quote)) {
            plugin.feedback().action(player, "§cInsufficient funds §7• Need §f" + quote.formatted());
            return;
        }

        int delay = Math.max(0, plugin.getConfig().getInt("warp.delay-seconds", 5));
        startLocations.put(player.getUniqueId(), player.getLocation().clone());
        activeOrigins.put(player.getUniqueId(), origin);
        plugin.effects().startCharging(player, origin);

        BukkitTask task = new BukkitRunnable() {
            int remaining = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    plugin.effects().stop(player);
                    cleanup(player);
                    cancel();
                    return;
                }

                TravelGuardService.GuardStatus guardStatus = plugin.guards().status(player);
                if (guardStatus != TravelGuardService.GuardStatus.READY) {
                    fail(player, origin, plugin.guards().label(player, guardStatus));
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    TravelStatus routeStatus = travelStatusCore(player, target, origin);
                    if (routeStatus != TravelStatus.READY) {
                        plugin.effects().cancel(player, origin);
                        validateStatus(player, target, routeStatus);
                        cleanup(player);
                        cancel();
                        return;
                    }
                    if (!quote.providerAvailable()) {
                        fail(player, origin, "Economy unavailable");
                        cancel();
                        return;
                    }
                    if (!plugin.economy().canAfford(player, quote)) {
                        fail(player, origin, "Insufficient funds • Need " + quote.formatted());
                        cancel();
                        return;
                    }

                    Location destination = safeDestination(target);
                    if (destination == null) {
                        fail(player, origin, "No safe arrival point");
                        cancel();
                        return;
                    }

                    boolean crossWorld = !origin.worldId().equals(target.worldId());
                    EconomyService.Payment payment = plugin.economy().charge(player, quote);
                    if (payment == null) {
                        fail(player, origin, "Payment failed");
                        cancel();
                        return;
                    }

                    plugin.effects().depart(player, origin);
                    cleanup(player);
                    cancel();

                    if (!player.teleport(destination)) {
                        plugin.economy().refund(player, payment);
                        plugin.feedback().action(player, "§cTeleport failed §7• Payment refunded");
                        return;
                    }

                    consumePowerIfNeeded(target, crossWorld);
                    plugin.guards().markTravelSuccess(player);
                    plugin.effects().arrive(player, target);
                    plugin.feedback().title(player, "§d§lTRAVEL COMPLETE", "§f" + target.name(), 120, 900, 300);
                    if (payment.charged()) plugin.feedback().action(player, "§7Paid §f" + quote.formatted() + " §8• §d" + target.name());
                    applyPortalSickness(player);
                    return;
                }

                String action = "§dCharging §f" + origin.name() + " §8→ §f" + target.name() + " §8• §f" + remaining + "s";
                if (!quote.free()) action += " §8• §f" + quote.formatted();
                plugin.feedback().action(player, action);
                plugin.effects().pulse(player, origin);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        active.put(player.getUniqueId(), task);
    }

    public void cancel(Player player, String reason) {
        BukkitTask task = active.remove(player.getUniqueId());
        WaystoneData origin = activeOrigins.remove(player.getUniqueId());
        startLocations.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            plugin.effects().cancel(player, origin);
            if (player.isOnline()) plugin.feedback().action(player, "§cTeleport cancelled §7• §f" + reason);
        }
    }

    public void cancelAll() {
        for (BukkitTask task : active.values()) task.cancel();
        active.clear();
        startLocations.clear();
        activeOrigins.clear();
    }

    public Location startLocation(Player player) { return startLocations.get(player.getUniqueId()); }

    private void cleanup(Player player) {
        active.remove(player.getUniqueId());
        startLocations.remove(player.getUniqueId());
        activeOrigins.remove(player.getUniqueId());
        plugin.effects().stop(player);
    }

    private void fail(Player player, WaystoneData origin, String reason) {
        plugin.effects().cancel(player, origin);
        cleanup(player);
        plugin.feedback().action(player, "§cTeleport cancelled §7• §f" + reason);
    }

    public TravelStatus travelStatus(Player player, WaystoneData target) {
        return TravelStatus.ORIGIN_REQUIRED;
    }

    public TravelStatus travelStatus(Player player, WaystoneData target, WaystoneData origin) {
        TravelStatus core = travelStatusCore(player, target, origin);
        if (core != TravelStatus.READY) return core;

        EconomyService.Quote quote = plugin.economy().quote(player, origin, target);
        if (!quote.providerAvailable()) return TravelStatus.ECONOMY_UNAVAILABLE;
        if (!plugin.economy().canAfford(player, quote)) return TravelStatus.INSUFFICIENT_FUNDS;
        return TravelStatus.READY;
    }

    private TravelStatus travelStatusCore(Player player, WaystoneData target, WaystoneData origin) {
        if (player == null || target == null) return TravelStatus.MISSING;
        if (origin == null) return TravelStatus.ORIGIN_REQUIRED;
        if (origin.id().equals(target.id())) return TravelStatus.SAME_WAYSTONE;

        TravelGuardService.GuardStatus guard = plugin.guards().status(player);
        if (guard != TravelGuardService.GuardStatus.READY) {
            return switch (guard) {
                case COOLDOWN -> TravelStatus.COOLDOWN;
                case COMBAT_LOCKED -> TravelStatus.COMBAT_LOCKED;
                case KNOCKED_OUT -> TravelStatus.KNOCKED_OUT;
                default -> TravelStatus.READY;
            };
        }

        Location originLoc = origin.location();
        Location targetLoc = target.location();
        if (originLoc == null || targetLoc == null || originLoc.getWorld() == null || targetLoc.getWorld() == null) return TravelStatus.WORLD_UNAVAILABLE;
        if (originLoc.getBlock().getType() != Material.LODESTONE || targetLoc.getBlock().getType() != Material.LODESTONE) return TravelStatus.MISSING;
        if (!coreOperational(origin) || !coreOperational(target)) return TravelStatus.DORMANT;

        if (!plugin.access().canAccess(player, origin)) return TravelStatus.NO_ACCESS;
        if (!plugin.discovery().canUse(player, origin)) return TravelStatus.NOT_ACTIVATED;
        if (!origin.alwaysActive() && isSuppressed(origin)) return TravelStatus.SUPPRESSED;
        if (!nearOrigin(player, origin)) return TravelStatus.ORIGIN_TOO_FAR;

        if (!plugin.access().canAccess(player, target)) return TravelStatus.NO_ACCESS;
        if (!plugin.discovery().canUse(player, target)) {
            DiscoveryService.State state = plugin.discovery().status(player, target);
            return state == DiscoveryService.State.UNKNOWN ? TravelStatus.NOT_DISCOVERED : TravelStatus.NOT_ACTIVATED;
        }
        if (!target.alwaysActive() && isSuppressed(target)) return TravelStatus.SUPPRESSED;

        boolean crossWorld = !origin.worldId().equals(target.worldId());
        if (crossWorld && !plugin.getConfig().getBoolean("warp.allow-cross-world", true)) return TravelStatus.CROSS_WORLD_DISABLED;
        if (!target.alwaysActive() && requiresPower(crossWorld) && !hasPower(target)) return TravelStatus.NO_POWER;
        return TravelStatus.READY;
    }

    public String statusLabel(TravelStatus status) {
        return switch (status) {
            case READY -> "Ready";
            case ORIGIN_REQUIRED -> "Origin Waystone required";
            case ORIGIN_TOO_FAR -> "Too far from origin";
            case SAME_WAYSTONE -> "Already at this Waystone";
            case WORLD_UNAVAILABLE -> "World unavailable";
            case MISSING -> "Waystone missing";
            case DORMANT -> "Dormant • Core required";
            case NO_ACCESS -> "Access denied";
            case NOT_DISCOVERED -> "Not discovered";
            case NOT_ACTIVATED -> "Not activated";
            case SUPPRESSED -> "Suppressed";
            case CROSS_WORLD_DISABLED -> "Cross-world disabled";
            case NO_POWER -> "No dimensional power";
            case ECONOMY_UNAVAILABLE -> "Economy unavailable";
            case INSUFFICIENT_FUNDS -> "Insufficient funds";
            case COOLDOWN -> "Travel cooldown";
            case COMBAT_LOCKED -> "Combat locked";
            case KNOCKED_OUT -> "Knocked out";
        };
    }

    private boolean validateStatus(Player player, WaystoneData target, TravelStatus status) {
        if (status == TravelStatus.READY) return true;
        String text = switch (status) {
            case ORIGIN_REQUIRED -> "Travel must begin at a Waystone";
            case ORIGIN_TOO_FAR -> "Stay near the origin Waystone";
            case SAME_WAYSTONE -> "Already at this Waystone";
            case WORLD_UNAVAILABLE -> "Waystone world unavailable";
            case MISSING -> "Waystone no longer exists";
            case DORMANT -> "Dormant Waystone • Core required";
            case NO_ACCESS -> "Access denied";
            case NOT_DISCOVERED -> "Waystone not discovered";
            case NOT_ACTIVATED -> "Waystone not activated";
            case SUPPRESSED -> "Waystone route suppressed";
            case CROSS_WORLD_DISABLED -> "Cross-world travel disabled";
            case NO_POWER -> "Destination needs dimensional power";
            case ECONOMY_UNAVAILABLE -> "Travel economy unavailable";
            case INSUFFICIENT_FUNDS -> "Insufficient funds";
            case COOLDOWN -> "Cooldown • " + plugin.guards().cooldownRemainingSeconds(player) + "s";
            case COMBAT_LOCKED -> "Combat locked • " + plugin.guards().combatRemainingSeconds(player) + "s";
            case KNOCKED_OUT -> "Waystone unavailable while knocked out";
            default -> "Route unavailable";
        };
        plugin.feedback().action(player, "§c" + text);
        return false;
    }

    private boolean coreOperational(WaystoneData data) {
        return data.coreActive() || (data.isAdmin() && plugin.getConfig().getBoolean("core.admin-bypass", true));
    }

    private boolean nearOrigin(Player player, WaystoneData origin) {
        Location location = origin.location();
        if (location == null || location.getWorld() == null) return false;
        if (!player.getWorld().getUID().equals(origin.worldId())) return false;
        double radius = Math.max(2.0, plugin.getConfig().getDouble("network.origin-radius", 6.5));
        return player.getLocation().distanceSquared(location.clone().add(0.5, 0.5, 0.5)) <= radius * radius;
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
        return switch (mode) {
            case "ALL" -> true;
            case "INTER_DIMENSION" -> crossWorld;
            default -> false;
        };
    }

    private boolean hasPower(WaystoneData target) {
        Location loc = target.location();
        if (loc == null) return false;
        Block block = loc.clone().add(0, -1, 0).getBlock();
        if (block.getType() != Material.RESPAWN_ANCHOR) return false;
        BlockData data = block.getBlockData();
        if (!(data instanceof RespawnAnchor anchor)) return false;
        int cost = Math.max(0, plugin.getConfig().getInt("power.respawn-anchor-cost", 1));
        return anchor.getCharges() >= cost;
    }

    private void consumePowerIfNeeded(WaystoneData target, boolean crossWorld) {
        if (target.alwaysActive() || !requiresPower(crossWorld)) return;
        Location loc = target.location();
        if (loc == null) return;
        Block block = loc.clone().add(0, -1, 0).getBlock();
        if (!(block.getBlockData() instanceof RespawnAnchor anchor)) return;
        int cost = Math.max(0, plugin.getConfig().getInt("power.respawn-anchor-cost", 1));
        anchor.setCharges(Math.max(0, anchor.getCharges() - cost));
        block.setBlockData(anchor, false);
    }

    private Location safeDestination(WaystoneData target) {
        Location base = target.location();
        if (base == null) return null;
        int radius = Math.max(1, plugin.getConfig().getInt("warp.safe-radius", 3));
        World world = base.getWorld();
        if (world == null) return null;

        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    Block feet = world.getBlockAt(base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
                    Block head = feet.getRelative(0, 1, 0);
                    Block floor = feet.getRelative(0, -1, 0);
                    if (feet.isPassable() && head.isPassable() && floor.getType().isSolid()) {
                        return new Location(world, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, base.getYaw(), 0f);
                    }
                }
            }
        }
        return base.clone().add(0.5, 2.05, 0.5);
    }

    private void applyPortalSickness(Player player) {
        if (!plugin.getConfig().getBoolean("portal-sickness.enabled", true)) return;
        double chance = plugin.getConfig().getDouble("portal-sickness.chance", 0.05);
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        PotionEffectType nausea = PotionEffectType.getByName("NAUSEA");
        PotionEffectType blindness = PotionEffectType.getByName("BLINDNESS");
        if (nausea != null) player.addPotionEffect(new PotionEffect(nausea, plugin.getConfig().getInt("portal-sickness.nausea-seconds", 15) * 20, 0));
        if (blindness != null) player.addPotionEffect(new PotionEffect(blindness, plugin.getConfig().getInt("portal-sickness.blindness-seconds", 3) * 20, 0));
        double damage = plugin.getConfig().getDouble("portal-sickness.damage", 5.0);
        if (damage > 0) player.damage(damage);
        plugin.feedback().action(player, "§5Portal sickness");
    }
}
