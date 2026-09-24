package store.menkiestes.cdrwaystone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TeleportEffectService {
    private final CdrWaystonePlugin plugin;
    private final Map<UUID, BukkitTask> chargingTasks = new HashMap<>();
    private final Map<UUID, List<BlockDisplay>> chargingLasers = new HashMap<>();
    private final List<BlockDisplay> transientLasers = new ArrayList<>();
    private boolean effectWarningLogged;

    public TeleportEffectService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    public void startCharging(Player player, WaystoneData origin) {
        stop(player);
        if (!enabled() || player == null || origin == null) return;
        Location base = origin.location();
        if (base == null || base.getWorld() == null) return;

        safeEffect("charging sound", () -> player.playSound(
                base.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.08f));

        if (plugin.getConfig().getBoolean("teleport-effects.laser.enabled", true)) {
            List<BlockDisplay> laser = spawnLaser(base);
            if (!laser.isEmpty()) chargingLasers.put(player.getUniqueId(), laser);
        }

        final double auraRadius = Math.max(0.45, Math.min(1.60,
                plugin.getConfig().getDouble("teleport-effects.aura.radius", 0.90)));
        final int ringPoints = Math.max(10, Math.min(36,
                plugin.getConfig().getInt("teleport-effects.aura.ring-points", 22)));
        final int spiralPoints = Math.max(0, Math.min(28,
                plugin.getConfig().getInt("teleport-effects.aura.spiral-points", 14)));
        final int sparkCount = Math.max(0, Math.min(16,
                plugin.getConfig().getInt("teleport-effects.aura.spark-count", 5)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                try {
                    if (!player.isOnline()) {
                        stop(player);
                        return;
                    }

                    Location playerBase = player.getLocation().clone();
                    World world = playerBase.getWorld();
                    if (world == null) {
                        stop(player);
                        return;
                    }

                    double spin = tick * 0.34;

                    // Thick lower ring rotating clockwise around the travelling player.
                    for (int i = 0; i < ringPoints; i++) {
                        double angle = spin + (Math.PI * 2.0 * i / ringPoints);
                        double x = Math.cos(angle) * auraRadius;
                        double z = Math.sin(angle) * auraRadius;
                        world.spawnParticle(
                                Particle.PORTAL,
                                playerBase.clone().add(x, 0.22, z),
                                2,
                                0.025, 0.055, 0.025, 0.0);
                    }

                    // Second ring rotates in the opposite direction at torso level.
                    int upperPoints = Math.max(8, ringPoints - 4);
                    for (int i = 0; i < upperPoints; i++) {
                        double angle = (-spin * 1.12) + (Math.PI * 2.0 * i / upperPoints);
                        double x = Math.cos(angle) * auraRadius * 0.72;
                        double z = Math.sin(angle) * auraRadius * 0.72;
                        world.spawnParticle(
                                Particle.ENCHANT,
                                playerBase.clone().add(x, 1.05, z),
                                2,
                                0.025, 0.055, 0.025, 0.0);
                    }

                    // Vertical helix wraps the player's body while the Waystone laser is active.
                    if (spiralPoints > 0) {
                        for (int i = 0; i < spiralPoints; i++) {
                            double progress = i / (double) spiralPoints;
                            double y = 0.12 + progress * 1.95;
                            double angle = (spin * 1.45) + progress * Math.PI * 4.0;
                            double radius = auraRadius * (0.46 - 0.12 * progress);
                            Location point = playerBase.clone().add(
                                    Math.cos(angle) * radius,
                                    y,
                                    Math.sin(angle) * radius);
                            world.spawnParticle(
                                    Particle.END_ROD,
                                    point,
                                    2,
                                    0.025, 0.035, 0.025, 0.002);
                        }
                    }

                    if (sparkCount > 0 && tick % 3 == 0) {
                        world.spawnParticle(
                                Particle.ELECTRIC_SPARK,
                                playerBase.clone().add(0.0, 1.0, 0.0),
                                sparkCount,
                                0.58, 0.95, 0.58, 0.025);
                    }
                } catch (RuntimeException ex) {
                    warnEffectOnce("player charging aura", ex);
                } finally {
                    tick++;
                }
            }
        }, 0L, 4L);
        chargingTasks.put(player.getUniqueId(), task);
    }

    public void pulse(Player player, WaystoneData origin) {
        if (!enabled() || player == null || origin == null) return;
        Location base = origin.location();
        if (base == null || base.getWorld() == null) return;
        safeEffect("charging pulse", () -> base.getWorld().playSound(
                base.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                0.65f, 0.94f + (float) (Math.random() * 0.18)));
    }

    public void depart(Player player, WaystoneData origin) {
        stop(player);
        if (!enabled() || origin == null) return;
        burst(origin.location(), true);
    }

    public void arrive(Player player, WaystoneData target) {
        if (!enabled() || target == null) return;
        Location base = target.location();
        burst(base, false);
        if (plugin.getConfig().getBoolean("teleport-effects.laser.enabled", true)) {
            spawnTemporaryLaser(base);
        }
    }

    public void cancel(Player player, WaystoneData origin) {
        stop(player);
        if (!enabled() || origin == null) return;
        Location base = origin.location();
        if (base == null || base.getWorld() == null) return;
        Location center = base.clone().add(0.5, 0.8, 0.5);
        safeEffect("cancel particles", () -> base.getWorld().spawnParticle(
                Particle.ASH, center, 26, 0.65, 0.55, 0.65, 0.025));
        safeEffect("cancel sound", () -> base.getWorld().playSound(
                center, Sound.BLOCK_BEACON_DEACTIVATE, 0.65f, 0.88f));
    }

    public void stop(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        BukkitTask task = chargingTasks.remove(id);
        if (task != null) task.cancel();
        removeDisplays(chargingLasers.remove(id));
    }

    public void stopAll() {
        for (BukkitTask task : chargingTasks.values()) task.cancel();
        chargingTasks.clear();

        for (List<BlockDisplay> displays : chargingLasers.values()) removeDisplays(displays);
        chargingLasers.clear();

        removeDisplays(new ArrayList<>(transientLasers));
        transientLasers.clear();
    }

    private List<BlockDisplay> spawnLaser(Location base) {
        List<BlockDisplay> displays = new ArrayList<>();
        if (base == null || base.getWorld() == null) return displays;

        try {
            double height = Math.max(2.0, Math.min(32.0,
                    plugin.getConfig().getDouble("teleport-effects.beam-height", 7.0)));
            double outerWidth = Math.max(0.10, Math.min(0.50,
                    plugin.getConfig().getDouble("teleport-effects.laser.outer-width", 0.24)));
            double innerWidth = Math.max(0.04, Math.min(outerWidth * 0.75,
                    plugin.getConfig().getDouble("teleport-effects.laser.inner-width", 0.085)));

            displays.add(spawnLaserLayer(
                    base,
                    Material.LIGHT_BLUE_STAINED_GLASS,
                    outerWidth,
                    height,
                    0.02));
            displays.add(spawnLaserLayer(
                    base,
                    Material.WHITE_STAINED_GLASS,
                    innerWidth,
                    height,
                    0.025));
        } catch (RuntimeException ex) {
            removeDisplays(displays);
            displays.clear();
            warnEffectOnce("laser display", ex);
        }
        return displays;
    }

    private BlockDisplay spawnLaserLayer(Location base, Material material, double width, double height, double yOffset) {
        World world = base.getWorld();
        Location spawn = base.clone().add(0.5 - width / 2.0, yOffset, 0.5 - width / 2.0);

        return world.spawn(spawn, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf(),
                    new Vector3f((float) width, (float) height, (float) width),
                    new Quaternionf()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setBillboard(Display.Billboard.FIXED);
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
            display.setViewRange(2.0f);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
        });
    }

    private void spawnTemporaryLaser(Location base) {
        if (base == null || base.getWorld() == null) return;
        List<BlockDisplay> displays = spawnLaser(base);
        if (displays.isEmpty()) return;

        transientLasers.addAll(displays);
        long duration = Math.max(5L, Math.min(100L,
                plugin.getConfig().getLong("teleport-effects.laser.arrival-duration-ticks", 24L)));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeDisplays(displays);
            transientLasers.removeAll(displays);
        }, duration);
    }

    private void removeDisplays(List<BlockDisplay> displays) {
        if (displays == null) return;
        for (BlockDisplay display : displays) {
            if (display != null && display.isValid()) display.remove();
        }
    }

    private void burst(Location base, boolean departure) {
        if (base == null || base.getWorld() == null) return;
        World world = base.getWorld();
        Location center = base.clone().add(0.5, 1.0, 0.5);

        int burstMultiplier = Math.max(1, Math.min(3,
                plugin.getConfig().getInt("teleport-effects.density.burst-multiplier", 2)));

        safeEffect("flash", () -> world.spawnParticle(Particle.FLASH, center, 1, Color.WHITE));
        safeEffect("portal burst", () -> world.spawnParticle(
                Particle.PORTAL, center, 52 * burstMultiplier,
                0.95, 1.15, 0.95, 0.20));
        safeEffect("spark burst", () -> world.spawnParticle(
                Particle.ELECTRIC_SPARK, center, 16 * burstMultiplier,
                0.82, 1.05, 0.82, 0.075));
        safeEffect("end rod burst", () -> world.spawnParticle(
                Particle.END_ROD, center, 22 * burstMultiplier,
                0.72, 1.05, 0.72, 0.045));

        safeEffect("travel sound", () -> world.playSound(
                center,
                departure ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_PORTAL_TRAVEL,
                departure ? 1.0f : 0.78f,
                departure ? 1.30f : 1.10f));
    }

    private void safeEffect(String context, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            warnEffectOnce(context, ex);
        }
    }

    private void warnEffectOnce(String context, RuntimeException ex) {
        if (effectWarningLogged) return;
        effectWarningLogged = true;
        plugin.getLogger().warning("Teleport visual effect '" + context
                + "' failed; travel will continue without that visual: "
                + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("teleport-effects.enabled", true);
    }
}
