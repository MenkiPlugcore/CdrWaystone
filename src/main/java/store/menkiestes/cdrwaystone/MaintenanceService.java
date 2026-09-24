package store.menkiestes.cdrwaystone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight self-healing pass for changes that bypass Bukkit block events
 * (commands, WorldEdit-like tools, crash leftovers, or external plugins).
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
        List<WaystoneData> stale = new ArrayList<>();

        for (WaystoneData data : plugin.registry().all()) {
            Location location = data.location();
            if (location == null || location.getWorld() == null) continue;
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;

            if (location.getBlock().getType() != Material.LODESTONE) {
                plugin.visuals().remove(data);
                stale.add(data);
                continue;
            }

            plugin.visuals().ensureCollision(data);
            if (plugin.getConfig().getBoolean("visuals.enabled", true) && !plugin.visuals().hasVisual(data)) {
                plugin.visuals().spawn(data);
            }
        }

        if (!stale.isEmpty()) {
            for (WaystoneData data : stale) plugin.registry().remove(data, false);
            plugin.registry().save();
            plugin.getLogger().warning("Stability check removed " + stale.size() + " stale Waystone entr" + (stale.size() == 1 ? "y." : "ies."));
        }
    }
}
