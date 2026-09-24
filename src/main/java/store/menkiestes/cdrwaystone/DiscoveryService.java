package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

public final class DiscoveryService {
    public enum State { UNKNOWN, DISCOVERED, ACTIVATED }

    private final CdrWaystonePlugin plugin;
    private final File file;
    private final File backupFile;
    private final Map<UUID, Map<UUID, State>> playerStates = new HashMap<>();
    private BukkitTask scanTask;

    public DiscoveryService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "discoveries.yml");
        this.backupFile = new File(plugin.getDataFolder(), "discoveries.yml.bak");
    }

    public synchronized void load() {
        playerStates.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String rawPlayer : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawPlayer);
                ConfigurationSection entries = players.getConfigurationSection(rawPlayer);
                if (entries == null) continue;
                Map<UUID, State> states = new HashMap<>();
                for (String rawWaystone : entries.getKeys(false)) {
                    try {
                        UUID waystoneId = UUID.fromString(rawWaystone);
                        State state = State.valueOf(entries.getString(rawWaystone, "UNKNOWN").toUpperCase(Locale.ROOT));
                        if (state != State.UNKNOWN) states.put(waystoneId, state);
                    } catch (Exception ignored) {}
                }
                if (!states.isEmpty()) playerStates.put(playerId, states);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid player UUID in discoveries.yml: " + rawPlayer);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<UUID, State>> playerEntry : playerStates.entrySet()) {
            for (Map.Entry<UUID, State> stateEntry : playerEntry.getValue().entrySet()) {
                if (stateEntry.getValue() == State.UNKNOWN) continue;
                yaml.set("players." + playerEntry.getKey() + "." + stateEntry.getKey(), stateEntry.getValue().name());
            }
        }

        File tempFile = new File(plugin.getDataFolder(), "discoveries.yml.tmp");
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            yaml.save(tempFile);
            if (file.exists()) Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save discoveries.yml safely: " + ex.getMessage());
            if (tempFile.exists() && !tempFile.delete()) tempFile.deleteOnExit();
        }
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("discovery.enabled", true)) return;
        long interval = Math.max(20L, plugin.getConfig().getLong("discovery.scan-interval-ticks", 20L));
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanOnlinePlayers, interval, interval);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    public State status(Player player, WaystoneData data) {
        if (!plugin.getConfig().getBoolean("discovery.enabled", true)) return State.ACTIVATED;
        if (data.owner() != null && data.owner().equals(player.getUniqueId())) return State.ACTIVATED;
        State stored = storedState(player.getUniqueId(), data.id());
        if (stored != State.UNKNOWN) return stored;
        if (data.isAdmin() && data.globallyDiscovered() && plugin.access().canAccess(player, data)) return State.DISCOVERED;
        return State.UNKNOWN;
    }

    public boolean canUse(Player player, WaystoneData data) {
        if (player.hasPermission("cdrwaystone.admin")) return true;
        return plugin.access().canAccess(player, data) && status(player, data) == State.ACTIVATED;
    }

    public boolean discover(Player player, WaystoneData data, boolean notify) {
        if (!plugin.getConfig().getBoolean("discovery.enabled", true)) return false;
        if (!canDiscover(player, data)) return false;
        if (status(player, data) != State.UNKNOWN) return false;
        setState(player.getUniqueId(), data.id(), State.DISCOVERED, true);
        if (notify) notifyDiscovered(player, data);
        return true;
    }

    public boolean activate(Player player, WaystoneData data) {
        if (!plugin.getConfig().getBoolean("discovery.enabled", true)) return true;
        if (!plugin.access().canAccess(player, data)) {
            player.sendMessage("§cYou do not have access to this Waystone.");
            return false;
        }
        if (!player.hasPermission("cdrwaystone.admin") && status(player, data) == State.UNKNOWN) {
            player.sendMessage("§7You have not discovered this Waystone yet.");
            return false;
        }
        if (status(player, data) == State.ACTIVATED) return true;
        setState(player.getUniqueId(), data.id(), State.ACTIVATED, true);
        player.showTitle(Title.title(
                Component.text("✦ WAYSTONE ACTIVATED ✦"),
                Component.text(data.name()),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1600), Duration.ofMillis(450))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.15f);
        player.sendMessage("§dWaystone activated: §f" + data.name());
        return true;
    }

    public synchronized void forgetWaystone(UUID waystoneId) {
        boolean changed = false;
        for (Iterator<Map.Entry<UUID, Map<UUID, State>>> it = playerStates.entrySet().iterator(); it.hasNext();) {
            Map<UUID, State> states = it.next().getValue();
            if (states.remove(waystoneId) != null) changed = true;
            if (states.isEmpty()) it.remove();
        }
        if (changed) save();
    }

    public synchronized void setState(UUID playerId, UUID waystoneId, State state, boolean saveNow) {
        if (state == State.UNKNOWN) {
            Map<UUID, State> states = playerStates.get(playerId);
            if (states != null) {
                states.remove(waystoneId);
                if (states.isEmpty()) playerStates.remove(playerId);
            }
        } else {
            playerStates.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(waystoneId, state);
        }
        if (saveNow) save();
    }

    private State storedState(UUID playerId, UUID waystoneId) {
        Map<UUID, State> states = playerStates.get(playerId);
        return states == null ? State.UNKNOWN : states.getOrDefault(waystoneId, State.UNKNOWN);
    }

    private boolean canDiscover(Player player, WaystoneData data) {
        if (!plugin.access().canAccess(player, data)) return false;
        Location location = data.location();
        return location != null && location.getWorld() != null && location.getWorld().equals(player.getWorld());
    }

    private void scanOnlinePlayers() {
        double radius = Math.max(1.0, plugin.getConfig().getDouble("discovery.radius", 8.0));
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (WaystoneData data : plugin.registry().all()) {
                if (status(player, data) != State.UNKNOWN || !canDiscover(player, data)) continue;
                Location location = data.location();
                if (location == null || !location.getWorld().equals(player.getWorld())) continue;
                Location center = location.clone().add(0.5, 0.5, 0.5);
                if (player.getLocation().distanceSquared(center) <= radiusSquared) {
                    discover(player, data, true);
                }
            }
        }
    }

    private void notifyDiscovered(Player player, WaystoneData data) {
        player.showTitle(Title.title(
                Component.text("✦ WAYSTONE DISCOVERED ✦"),
                Component.text(data.name()),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(450))
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.85f);
        player.sendMessage("§5Discovered §f" + data.name() + "§5. Right-click it to activate.");
    }
}
