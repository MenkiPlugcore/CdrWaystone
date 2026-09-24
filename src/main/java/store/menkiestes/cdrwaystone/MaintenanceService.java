package store.menkiestes.cdrwaystone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

/**
 * Lightweight self-healing pass for changes that bypass Bukkit block events.
 * v0.6.4 treats Barrier/AIR/legacy Lodestone anchors as recoverable and lets
 * VisualService rebuild the native ItemsAdder furniture.
 */
public final class MaintenanceService {
    private final CdrWaystonePlugin plugin;
    private BukkitTask task;

    public MaintenanceService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long seconds = Math.max(30L, plugin.getConfig().getLong("stability.health-check-seconds", 300L));
        long period = seconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runOnce, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void runOnce() {
        for (WaystoneData data : plugin.registry().all()) {
            Location location = data.location();
            if (location == null || location.getWorld() == null) continue;
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;

            Material type = location.getBlock().getType();
            if (type != Material.BARRIER && type != Material.LODESTONE && type != Material.AIR) {
                plugin.getLogger().warning("Waystone " + data.id() + " anchor is obstructed by " + type + ".");
                continue;
            }

            if (!plugin.visuals().isAnchorValid(data) || !plugin.visuals().hasVisual(data)) {
                plugin.visuals().materialize(data);
            } else {
                plugin.visuals().ensureCollision(data);
            }
        }
    }
}
