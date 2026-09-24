package store.menkiestes.cdrwaystone;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TeleportEffectService {
    private final CdrWaystonePlugin plugin;
    private final Map<UUID, BukkitTask> chargingTasks = new HashMap<>();
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
                base.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_BEACON_ACTIVATE, 0.95f, 1.12f));

        final double height = Math.max(1.5, plugin.getConfig().getDouble("teleport-effects.beam-height", 5.0));
        final double radius = Math.max(0.4, plugin.getConfig().getDouble("teleport-effects.ring-radius", 1.15));
        final int ringPoints = Math.max(8, Math.min(40,
                plugin.getConfig().getInt("teleport-effects.density.ring-points", 20)));
        final double beamStep = Math.max(0.20, Math.min(0.80,
                plugin.getConfig().getDouble("teleport-effects.density.beam-step", 0.32)));
        final int beamThickness = Math.max(1, Math.min(6,
                plugin.getConfig().getInt("teleport-effects.density.beam-thickness", 3)));
        final int spiralPoints = Math.max(0, Math.min(24,
                plugin.getConfig().getInt("teleport-effects.density.spiral-points", 10)));
        final int sparkCount = Math.max(0, Math.min(20,
                plugin.getConfig().getInt("teleport-effects.density.spark-count", 6)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                try {
                    if (!player.isOnline()) {
                        stop(player);
                        return;
                    }
                    Location current = origin.location();
                    if (current == null || current.getWorld() == null) {
                        stop(player);
                        return;
                    }

                    World world = current.getWorld();
                    Location center = current.clone().add(0.5, 0.25, 0.5);
                    double spin = tick * 0.28;

                    // Dense outer ring plus an inner portal ring.
                    for (int i = 0; i < ringPoints; i++) {
                        double angle = spin + (Math.PI * 2.0 * i / ringPoints);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        world.spawnParticle(Particle.ENCHANT, center.clone().add(x, 0.20, z), 2,
                                0.025, 0.045, 0.025, 0.0);

                        if ((i & 1) == 0) {
                            world.spawnParticle(Particle.PORTAL,
                                    center.clone().add(x * 0.78, 0.45, z * 0.78), 2,
                                    0.025, 0.10, 0.025, 0.0);
                        }
                    }

                    // Thick vertical beam. Multiple END_ROD particles at each height
                    // produce a filled beacon-like column instead of a dotted line.
                    for (double y = 0.30; y <= height; y += beamStep) {
                        world.spawnParticle(Particle.END_ROD,
                                center.clone().add(0.0, y, 0.0), beamThickness,
                                0.065, 0.07, 0.065, 0.004);
                    }

                    // A small helix makes the beam feel energized without widening
                    // the entire visual too much.
                    if (spiralPoints > 0) {
                        for (int i = 0; i < spiralPoints; i++) {
                            double progress = i / (double) spiralPoints;
                            double y = 0.45 + progress * Math.max(1.0, height - 0.45);
                            double angle = (spin * 1.55) + progress * Math.PI * 4.0;
                            double spiralRadius = radius * 0.30;
                            Location point = center.clone().add(
                                    Math.cos(angle) * spiralRadius,
                                    y,
                                    Math.sin(angle) * spiralRadius);
                            world.spawnParticle(Particle.PORTAL, point, 1,
                                    0.015, 0.025, 0.015, 0.0);
                        }
                    }

                    if (sparkCount > 0 && tick % 4 == 0) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK,
                                center.clone().add(0, 1.0, 0), sparkCount,
                                0.48, 0.85, 0.48, 0.025);
                    }
                } catch (RuntimeException ex) {
                    warnEffectOnce("charging particles", ex);
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
                0.62f, 0.98f + (float) (Math.random() * 0.18)));
    }

    public void depart(Player player, WaystoneData origin) {
        stop(player);
        if (!enabled() || origin == null) return;
        burst(origin.location(), true);
    }

    public void arrive(Player player, WaystoneData target) {
        if (!enabled() || target == null) return;
        burst(target.location(), false);
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
        BukkitTask task = chargingTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    public void stopAll() {
        for (BukkitTask task : chargingTasks.values()) task.cancel();
        chargingTasks.clear();
    }

    private void burst(Location base, boolean departure) {
        if (base == null || base.getWorld() == null) return;
        World world = base.getWorld();
        Location center = base.clone().add(0.5, 1.0, 0.5);

        int burstMultiplier = Math.max(1, Math.min(3,
                plugin.getConfig().getInt("teleport-effects.density.burst-multiplier", 2)));

        safeEffect("flash", () -> world.spawnParticle(Particle.FLASH, center, 1, Color.WHITE));
        safeEffect("end rod burst", () -> world.spawnParticle(
                Particle.END_ROD, center, 36 * burstMultiplier,
                0.78, 1.28, 0.78, 0.060));
        safeEffect("portal burst", () -> world.spawnParticle(
                Particle.PORTAL, center, 52 * burstMultiplier,
                0.95, 1.15, 0.95, 0.20));
        safeEffect("spark burst", () -> world.spawnParticle(
                Particle.ELECTRIC_SPARK, center, 16 * burstMultiplier,
                0.82, 1.05, 0.82, 0.075));

        // Short dense vertical pop at the Waystone itself.
        safeEffect("beam burst", () -> {
            for (double y = 0.15; y <= 3.2; y += 0.28) {
                world.spawnParticle(Particle.END_ROD,
                        base.clone().add(0.5, y, 0.5), 3,
                        0.08, 0.08, 0.08, 0.008);
            }
        });

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
