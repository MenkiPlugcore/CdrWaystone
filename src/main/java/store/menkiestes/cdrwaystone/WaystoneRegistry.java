package store.menkiestes.cdrwaystone;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class WaystoneRegistry {
    private final CdrWaystonePlugin plugin;
    private final File file;
    private final Map<UUID, WaystoneData> byId = new LinkedHashMap<>();
    private final Map<String, UUID> byLocation = new HashMap<>();

    public WaystoneRegistry(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "waystones.yml");
    }

    public void load() {
        byId.clear();
        byLocation.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("waystones");
        if (root == null) return;

        for (String rawId : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(rawId);
                String path = "waystones." + rawId;
                UUID owner = UUID.fromString(yaml.getString(path + ".owner"));
                UUID worldId = UUID.fromString(yaml.getString(path + ".world-id"));
                String worldName = yaml.getString(path + ".world-name", "world");
                int x = yaml.getInt(path + ".x");
                int y = yaml.getInt(path + ".y");
                int z = yaml.getInt(path + ".z");
                String name = yaml.getString(path + ".name", "Waystone");
                String skin = yaml.getString(path + ".skin", "andesite");
                WaystoneData data = new WaystoneData(id, owner, worldId, worldName, x, y, z, name, skin);
                byId.put(id, data);
                byLocation.put(locationKey(worldId, x, y, z), id);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid waystone entry " + rawId + ": " + ex.getMessage());
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (WaystoneData data : byId.values()) {
            String path = "waystones." + data.id();
            yaml.set(path + ".owner", data.owner().toString());
            yaml.set(path + ".world-id", data.worldId().toString());
            yaml.set(path + ".world-name", data.worldName());
            yaml.set(path + ".x", data.x());
            yaml.set(path + ".y", data.y());
            yaml.set(path + ".z", data.z());
            yaml.set(path + ".name", data.name());
            yaml.set(path + ".skin", data.skin());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save waystones.yml: " + ex.getMessage());
        }
    }

    public WaystoneData create(UUID owner, Location location, String name, String skin) {
        UUID id = UUID.randomUUID();
        WaystoneData data = new WaystoneData(id, owner, location.getWorld().getUID(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), name, skin);
        byId.put(id, data);
        byLocation.put(locationKey(location), id);
        save();
        return data;
    }

    public WaystoneData find(Location location) {
        UUID id = byLocation.get(locationKey(location));
        return id == null ? null : byId.get(id);
    }

    public WaystoneData get(UUID id) { return byId.get(id); }
    public Collection<WaystoneData> all() { return Collections.unmodifiableCollection(byId.values()); }

    public void remove(WaystoneData data) {
        byId.remove(data.id());
        byLocation.remove(locationKey(data.worldId(), data.x(), data.y(), data.z()));
        save();
    }

    public List<WaystoneData> inChunk(World world, int chunkX, int chunkZ) {
        List<WaystoneData> list = new ArrayList<>();
        for (WaystoneData data : byId.values()) {
            if (!data.worldId().equals(world.getUID())) continue;
            if ((data.x() >> 4) == chunkX && (data.z() >> 4) == chunkZ) list.add(data);
        }
        return list;
    }

    private String locationKey(Location location) {
        return locationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private String locationKey(UUID worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
