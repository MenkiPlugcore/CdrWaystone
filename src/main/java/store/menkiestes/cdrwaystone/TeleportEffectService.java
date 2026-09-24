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
                base.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.15f));

        final double height = Math.max(1.5, plugin.getConfig().getDouble("teleport-effects.beam-height", 5.0));
        final double radius = Math.max(0.4, plugin.getConfig().getDouble("teleport-effects.ring-radius", 1.15));

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
                    for (int i = 0; i < 12; i++) {
                        double angle = spin + (Math.PI * 2.0 * i / 12.0);
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        world.spawnParticle(Particle.ENCHANT, center.clone().add(x, 0.2, z), 1,
                                0.02, 0.04, 0.02, 0.0);
                        if ((i & 1) == 0) {
                            world.spawnParticle(Particle.PORTAL, center.clone().add(x * 0.8, 0.45, z * 0.8), 1,
                                    0.01, 0.08, 0.01, 0.0);
                        }
                    }

                    for (double y = 0.35; y <= height; y += 0.55) {
                        world.spawnParticle(Particle.END_ROD, center.clone().add(0.0, y, 0.0), 1,
                                0.025, 0.06, 0.025, 0.0);
                    }

                    if (tick % 5 == 0) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1.0, 0), 3,
                                0.35, 0.65, 0.35, 0.02);
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
                0.5f, 1.0f + (float) (Math.random() * 0.18)));
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
                Particle.ASH, center, 18, 0.55, 0.45, 0.55, 0.02));
        safeEffect("cancel sound", () -> base.getWorld().playSound(
                center, Sound.BLOCK_BEACON_DEACTIVATE, 0.55f, 0.9f));
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

        // Paper 1.21.11 requires org.bukkit.Color data for FLASH.
        safeEffect("flash", () -> world.spawnParticle(Particle.FLASH, center, 1, Color.WHITE));
        safeEffect("end rod burst", () -> world.spawnParticle(
                Particle.END_ROD, center, 42, 0.65, 1.1, 0.65, 0.055));
        safeEffect("portal burst", () -> world.spawnParticle(
                Particle.PORTAL, center, 64, 0.8, 1.0, 0.8, 0.18));
        safeEffect("spark burst", () -> world.spawnParticle(
                Particle.ELECTRIC_SPARK, center, 20, 0.65, 0.9, 0.65, 0.07));
        safeEffect("travel sound", () -> world.playSound(
                center,
                departure ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_PORTAL_TRAVEL,
                departure ? 0.9f : 0.65f,
                departure ? 1.35f : 1.15f));
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
