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
    private final Map<UUID, LaserRig> chargingLasers = new HashMap<>();
    private final List<LaserRig> transientLasers = new ArrayList<>();
    private final List<BukkitTask> transientLaserTasks = new ArrayList<>();
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
                base.clone().add(0.5, 1.0, 0.5), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.08f));

        LaserRig rig = null;
        if (plugin.getConfig().getBoolean("teleport-effects.laser.enabled", true)) {
            rig = spawnLaserRig(base);
            if (rig != null) chargingLasers.put(player.getUniqueId(), rig);
        }
        final LaserRig activeRig = rig;

        final double auraMinRadius = Math.max(0.08, Math.min(0.50,
                plugin.getConfig().getDouble("teleport-effects.aura.min-radius", 0.16)));
        final double auraMaxRadius = Math.max(auraMinRadius + 0.10, Math.min(1.25,
                plugin.getConfig().getDouble("teleport-effects.aura.max-radius", 0.72)));
        final int spiralPoints = Math.max(8, Math.min(24,
                plugin.getConfig().getInt("teleport-effects.aura.spiral-points", 14)));
        final int ringPoints = Math.max(8, Math.min(24,
                plugin.getConfig().getInt("teleport-effects.aura.ring-points", 14)));
        final int sparkCount = Math.max(0, Math.min(8,
                plugin.getConfig().getInt("teleport-effects.aura.spark-count", 2)));

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                try {
                    if (!player.isOnline()) {
                        stop(player);
                        return;
                    }

                    Location currentWaystone = origin.location();
                    if (activeRig != null && currentWaystone != null && currentWaystone.getWorld() != null) {
                        updateLaserRig(activeRig, currentWaystone, tick);
                    }

                    Location playerBase = player.getLocation().clone();
                    World world = playerBase.getWorld();
                    if (world == null) {
                        stop(player);
                        return;
                    }

                    double spin = tick * 0.30;

                    // Light ground ring so the player remains visually readable.
                    for (int i = 0; i < ringPoints; i++) {
                        double angle = -spin + (Math.PI * 2.0 * i / ringPoints);
                        double x = Math.cos(angle) * auraMaxRadius * 0.82;
                        double z = Math.sin(angle) * auraMaxRadius * 0.82;
                        world.spawnParticle(
                                Particle.PORTAL,
                                playerBase.clone().add(x, 0.10, z),
                                1,
                                0.012, 0.025, 0.012, 0.0);
                    }

                    // Expanding conical spiral: tight at the feet, wider toward the head.
                    for (int i = 0; i < spiralPoints; i++) {
                        double progress = i / (double) Math.max(1, spiralPoints - 1);
                        double y = 0.08 + progress * 1.95;
                        double radius = auraMinRadius + (auraMaxRadius - auraMinRadius) * progress;
                        double angle = (spin * 1.35) + progress * Math.PI * 4.6;
                        Location point = playerBase.clone().add(
                                Math.cos(angle) * radius,
                                y,
                                Math.sin(angle) * radius);
                        world.spawnParticle(
                                Particle.END_ROD,
                                point,
                                1,
                                0.012, 0.018, 0.012, 0.001);
                    }

                    // A faint counter-spiral gives depth without hiding the skin.
                    int counterPoints = Math.max(6, spiralPoints / 2);
                    for (int i = 0; i < counterPoints; i++) {
                        double progress = i / (double) Math.max(1, counterPoints - 1);
                        double y = 0.20 + progress * 1.65;
                        double radius = auraMinRadius + (auraMaxRadius * 0.78 - auraMinRadius) * progress;
                        double angle = (-spin * 1.05) + progress * Math.PI * 3.7 + Math.PI;
                        Location point = playerBase.clone().add(
                                Math.cos(angle) * radius,
                                y,
                                Math.sin(angle) * radius);
                        world.spawnParticle(
                                Particle.ENCHANT,
                                point,
                                1,
                                0.010, 0.016, 0.010, 0.0);
                    }

                    if (sparkCount > 0 && tick % 4 == 0) {
                        world.spawnParticle(
                                Particle.ELECTRIC_SPARK,
                                playerBase.clone().add(0.0, 0.95, 0.0),
                                sparkCount,
                                0.35, 0.65, 0.35, 0.015);
                    }
                } catch (RuntimeException ex) {
                    warnEffectOnce("rotating charging visuals", ex);
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
                0.55f, 0.94f + (float) (Math.random() * 0.18)));
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
                Particle.ASH, center, 18, 0.52, 0.42, 0.52, 0.018));
        safeEffect("cancel sound", () -> base.getWorld().playSound(
                center, Sound.BLOCK_BEACON_DEACTIVATE, 0.55f, 0.88f));
    }

    public void stop(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        BukkitTask task = chargingTasks.remove(id);
        if (task != null) task.cancel();
        removeRig(chargingLasers.remove(id));
    }

    public void stopAll() {
        for (BukkitTask task : chargingTasks.values()) task.cancel();
        chargingTasks.clear();

        for (LaserRig rig : chargingLasers.values()) removeRig(rig);
        chargingLasers.clear();

        for (BukkitTask task : transientLaserTasks) {
            if (task != null) task.cancel();
        }
        transientLaserTasks.clear();

        for (LaserRig rig : new ArrayList<>(transientLasers)) removeRig(rig);
        transientLasers.clear();
    }

    private LaserRig spawnLaserRig(Location base) {
        if (base == null || base.getWorld() == null) return null;

        try {
            double height = Math.max(2.0, Math.min(24.0,
                    plugin.getConfig().getDouble("teleport-effects.beam-height", 6.0)));
            double coreWidth = Math.max(0.025, Math.min(0.14,
                    plugin.getConfig().getDouble("teleport-effects.laser.core-width", 0.060)));
            double strandWidth = Math.max(0.020, Math.min(0.12,
                    plugin.getConfig().getDouble("teleport-effects.laser.strand-width", 0.050)));
            int strands = Math.max(1, Math.min(3,
                    plugin.getConfig().getInt("teleport-effects.laser.strands", 2)));
            int segments = Math.max(4, Math.min(10,
                    plugin.getConfig().getInt("teleport-effects.laser.segments", 6)));

            BlockDisplay core = spawnLaserSegment(
                    base.clone().add(0.5 - coreWidth / 2.0, 0.02, 0.5 - coreWidth / 2.0),
                    Material.WHITE_STAINED_GLASS,
                    coreWidth,
                    height);

            List<BlockDisplay> orbiting = new ArrayList<>();
            double segmentHeight = height / segments * 0.88;
            for (int strand = 0; strand < strands; strand++) {
                for (int segment = 0; segment < segments; segment++) {
                    Location spawn = base.clone().add(0.5 - strandWidth / 2.0,
                            segment * (height / segments) + 0.03,
                            0.5 - strandWidth / 2.0);
                    orbiting.add(spawnLaserSegment(
                            spawn,
                            strand % 2 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.CYAN_STAINED_GLASS,
                            strandWidth,
                            segmentHeight));
                }
            }
            return new LaserRig(core, orbiting, strands, segments, height, strandWidth);
        } catch (RuntimeException ex) {
            warnEffectOnce("rotating laser rig", ex);
            return null;
        }
    }

    private void updateLaserRig(LaserRig rig, Location base, int tick) {
        if (rig == null || base == null || base.getWorld() == null) return;
        double minRadius = Math.max(0.06, Math.min(0.40,
                plugin.getConfig().getDouble("teleport-effects.laser.min-radius", 0.10)));
        double maxRadius = Math.max(minRadius + 0.05, Math.min(0.75,
                plugin.getConfig().getDouble("teleport-effects.laser.max-radius", 0.34)));
        double turns = Math.max(0.5, Math.min(4.0,
                plugin.getConfig().getDouble("teleport-effects.laser.turns", 1.65)));
        double speed = Math.max(0.05, Math.min(0.80,
                plugin.getConfig().getDouble("teleport-effects.laser.rotation-speed", 0.22)));

        int index = 0;
        for (int strand = 0; strand < rig.strands; strand++) {
            double strandPhase = Math.PI * 2.0 * strand / rig.strands;
            for (int segment = 0; segment < rig.segments; segment++) {
                if (index >= rig.orbiting.size()) return;
                BlockDisplay display = rig.orbiting.get(index++);
                if (display == null || !display.isValid()) continue;

                double progress = rig.segments <= 1 ? 0.0 : segment / (double) (rig.segments - 1);
                double radius = minRadius + (maxRadius - minRadius) * progress;
                double angle = tick * speed + strandPhase + progress * turns * Math.PI * 2.0;
                double y = segment * (rig.height / rig.segments) + 0.03;
                Location next = base.clone().add(
                        0.5 + Math.cos(angle) * radius - rig.strandWidth / 2.0,
                        y,
                        0.5 + Math.sin(angle) * radius - rig.strandWidth / 2.0);
                display.teleport(next);
            }
        }
    }

    private BlockDisplay spawnLaserSegment(Location spawn, Material material, double width, double height) {
        World world = spawn.getWorld();
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
            display.setInterpolationDuration(4);
            display.setTeleportDuration(4);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(false);
        });
    }

    private void spawnTemporaryLaser(Location base) {
        if (base == null || base.getWorld() == null) return;
        LaserRig rig = spawnLaserRig(base);
        if (rig == null) return;

        transientLasers.add(rig);
        long duration = Math.max(8L, Math.min(100L,
                plugin.getConfig().getLong("teleport-effects.laser.arrival-duration-ticks", 24L)));

        BukkitTask animation = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick = 0;
            @Override
            public void run() {
                updateLaserRig(rig, base, tick++);
            }
        }, 0L, 4L);
        transientLaserTasks.add(animation);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            animation.cancel();
            transientLaserTasks.remove(animation);
            removeRig(rig);
            transientLasers.remove(rig);
        }, duration);
    }

    private void removeRig(LaserRig rig) {
        if (rig == null) return;
        if (rig.core != null && rig.core.isValid()) rig.core.remove();
        for (BlockDisplay display : rig.orbiting) {
            if (display != null && display.isValid()) display.remove();
        }
    }

    private void burst(Location base, boolean departure) {
        if (base == null || base.getWorld() == null) return;
        World world = base.getWorld();
        Location center = base.clone().add(0.5, 1.0, 0.5);

        int burstMultiplier = Math.max(1, Math.min(3,
                plugin.getConfig().getInt("teleport-effects.density.burst-multiplier", 1)));

        safeEffect("flash", () -> world.spawnParticle(Particle.FLASH, center, 1, Color.WHITE));
        safeEffect("portal burst", () -> world.spawnParticle(
                Particle.PORTAL, center, 30 * burstMultiplier,
                0.72, 0.92, 0.72, 0.13));
        safeEffect("spark burst", () -> world.spawnParticle(
                Particle.ELECTRIC_SPARK, center, 9 * burstMultiplier,
                0.58, 0.82, 0.58, 0.045));
        safeEffect("end rod burst", () -> world.spawnParticle(
                Particle.END_ROD, center, 12 * burstMultiplier,
                0.52, 0.82, 0.52, 0.025));

        safeEffect("travel sound", () -> world.playSound(
                center,
                departure ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_PORTAL_TRAVEL,
                departure ? 0.95f : 0.72f,
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

    private static final class LaserRig {
        private final BlockDisplay core;
        private final List<BlockDisplay> orbiting;
        private final int strands;
        private final int segments;
        private final double height;
        private final double strandWidth;

        private LaserRig(BlockDisplay core, List<BlockDisplay> orbiting, int strands,
                         int segments, double height, double strandWidth) {
            this.core = core;
            this.orbiting = orbiting;
            this.strands = strands;
            this.segments = segments;
            this.height = height;
            this.strandWidth = strandWidth;
        }
    }
}
