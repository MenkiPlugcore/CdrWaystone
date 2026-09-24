package store.menkiestes.cdrwaystone;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class WaystoneRegistry {
    private final CdrWaystonePlugin plugin;
    private final File file;
    private final File backupFile;
    private final Map<UUID, WaystoneData> byId = new LinkedHashMap<>();
    private final Map<String, UUID> byLocation = new HashMap<>();

    public WaystoneRegistry(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "waystones.yml");
        this.backupFile = new File(plugin.getDataFolder(), "waystones.yml.bak");
    }

    public synchronized void load() {
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
                String ownerRaw = yaml.getString(path + ".owner");
                UUID owner = ownerRaw == null || ownerRaw.isBlank() ? null : UUID.fromString(ownerRaw);
                UUID worldId = UUID.fromString(Objects.requireNonNull(yaml.getString(path + ".world-id")));
                String worldName = yaml.getString(path + ".world-name", "world");
                int x = yaml.getInt(path + ".x"), y = yaml.getInt(path + ".y"), z = yaml.getInt(path + ".z");
                String name = yaml.getString(path + ".name", "Waystone");
                String skin = yaml.getString(path + ".skin", "andesite");
                boolean collisionOwned = yaml.contains(path + ".collision-owned") ? yaml.getBoolean(path + ".collision-owned") : true;

                WaystoneData.Type type;
                try { type = WaystoneData.Type.valueOf(yaml.getString(path + ".type", "PLAYER").toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) { type = WaystoneData.Type.PLAYER; }

                boolean publicAccess = yaml.getBoolean(path + ".public", type == WaystoneData.Type.ADMIN);
                boolean freeTravel = yaml.getBoolean(path + ".free", type == WaystoneData.Type.ADMIN);
                boolean permanent = yaml.getBoolean(path + ".permanent", type == WaystoneData.Type.ADMIN);
                boolean alwaysActive = yaml.getBoolean(path + ".always-active", type == WaystoneData.Type.ADMIN);
                boolean globallyDiscovered = yaml.contains(path + ".globally-discovered")
                        ? yaml.getBoolean(path + ".globally-discovered")
                        : type == WaystoneData.Type.ADMIN && plugin.getConfig().getBoolean("admin-waystones.default-globally-discovered", true);

                WaystoneData.AccessMode accessMode;
                try {
                    String defaultMode = plugin.getConfig().getString("ownership.default-access", "PRIVATE");
                    accessMode = WaystoneData.AccessMode.valueOf(yaml.getString(path + ".access-mode", defaultMode).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    accessMode = WaystoneData.AccessMode.PRIVATE;
                }

                Set<UUID> trusted = new LinkedHashSet<>();
                for (String rawTrusted : yaml.getStringList(path + ".trusted")) {
                    try { trusted.add(UUID.fromString(rawTrusted)); } catch (IllegalArgumentException ignored) {}
                }

                WaystoneData.Category category = parseCategory(
                        yaml.getString(path + ".category"),
                        type == WaystoneData.Type.ADMIN ? defaultAdminCategory() : defaultPlayerCategory());

                WaystoneData data = new WaystoneData(id, owner, worldId, worldName, x, y, z, name, skin, collisionOwned,
                        type, publicAccess, freeTravel, permanent, alwaysActive, globallyDiscovered, accessMode, trusted, category);
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
            yaml.set(path + ".owner", data.owner() == null ? null : data.owner().toString());
            yaml.set(path + ".world-id", data.worldId().toString());
            yaml.set(path + ".world-name", data.worldName());
            yaml.set(path + ".x", data.x()); yaml.set(path + ".y", data.y()); yaml.set(path + ".z", data.z());
            yaml.set(path + ".name", data.name());
            yaml.set(path + ".skin", data.skin());
            yaml.set(path + ".collision-owned", data.collisionOwned());
            yaml.set(path + ".type", data.type().name());
            yaml.set(path + ".public", data.publicAccess());
            yaml.set(path + ".free", data.freeTravel());
            yaml.set(path + ".permanent", data.permanent());
            yaml.set(path + ".always-active", data.alwaysActive());
            yaml.set(path + ".globally-discovered", data.globallyDiscovered());
            yaml.set(path + ".access-mode", data.accessMode().name());
            yaml.set(path + ".trusted", data.trustedPlayers().stream().map(UUID::toString).toList());
            yaml.set(path + ".category", data.category().name());
        }
        File tempFile = new File(plugin.getDataFolder(), "waystones.yml.tmp");
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) throw new IOException("Could not create plugin data folder");
            yaml.save(tempFile);
            if (file.exists()) Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save waystones.yml safely: " + ex.getMessage());
            if (tempFile.exists() && !tempFile.delete()) tempFile.deleteOnExit();
        }
    }

    public synchronized WaystoneData create(UUID owner, Location location, String name, String skin) {
        WaystoneData.AccessMode mode;
        try { mode = WaystoneData.AccessMode.valueOf(plugin.getConfig().getString("ownership.default-access", "PRIVATE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { mode = WaystoneData.AccessMode.PRIVATE; }
        return create(owner, location, name, skin, WaystoneData.Type.PLAYER, false, false, false, false, false,
                mode, Set.of(), defaultPlayerCategory());
    }

    public synchronized WaystoneData create(UUID owner, Location location, String name, String skin, WaystoneData.Type type,
                                             boolean publicAccess, boolean freeTravel, boolean permanent, boolean alwaysActive) {
        return create(owner, location, name, skin, type, publicAccess, freeTravel, permanent, alwaysActive, false,
                WaystoneData.AccessMode.PRIVATE, Set.of(), type == WaystoneData.Type.ADMIN ? defaultAdminCategory() : defaultPlayerCategory());
    }

    public synchronized WaystoneData create(UUID owner, Location location, String name, String skin, WaystoneData.Type type,
                                             boolean publicAccess, boolean freeTravel, boolean permanent,
                                             boolean alwaysActive, boolean globallyDiscovered) {
        return create(owner, location, name, skin, type, publicAccess, freeTravel, permanent, alwaysActive, globallyDiscovered,
                WaystoneData.AccessMode.PRIVATE, Set.of(), type == WaystoneData.Type.ADMIN ? defaultAdminCategory() : defaultPlayerCategory());
    }

    public synchronized WaystoneData create(UUID owner, Location location, String name, String skin, WaystoneData.Type type,
                                             boolean publicAccess, boolean freeTravel, boolean permanent,
                                             boolean alwaysActive, boolean globallyDiscovered,
                                             WaystoneData.AccessMode accessMode, Set<UUID> trusted) {
        return create(owner, location, name, skin, type, publicAccess, freeTravel, permanent, alwaysActive, globallyDiscovered,
                accessMode, trusted, type == WaystoneData.Type.ADMIN ? defaultAdminCategory() : defaultPlayerCategory());
    }

    public synchronized WaystoneData create(UUID owner, Location location, String name, String skin, WaystoneData.Type type,
                                             boolean publicAccess, boolean freeTravel, boolean permanent,
                                             boolean alwaysActive, boolean globallyDiscovered,
                                             WaystoneData.AccessMode accessMode, Set<UUID> trusted, WaystoneData.Category category) {
        UUID id = UUID.randomUUID();
        WaystoneData data = new WaystoneData(id, owner, location.getWorld().getUID(), location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), name, skin, false,
                type, publicAccess, freeTravel, permanent, alwaysActive, globallyDiscovered, accessMode, trusted, category);
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
    public Collection<WaystoneData> all() { return Collections.unmodifiableCollection(new ArrayList<>(byId.values())); }

    public long countOwned(UUID owner) {
        if (owner == null) return 0;
        return byId.values().stream().filter(data -> !data.isAdmin() && owner.equals(data.owner())).count();
    }

    public List<WaystoneData> ownedBy(UUID owner) {
        if (owner == null) return List.of();
        return byId.values().stream().filter(data -> !data.isAdmin() && owner.equals(data.owner())).toList();
    }

    public synchronized void remove(WaystoneData data) { remove(data, true); }
    public synchronized void remove(WaystoneData data, boolean saveNow) {
        byId.remove(data.id());
        byLocation.remove(locationKey(data.worldId(), data.x(), data.y(), data.z()));
        if (saveNow) save();
    }

    public List<WaystoneData> inChunk(World world, int chunkX, int chunkZ) {
        List<WaystoneData> list = new ArrayList<>();
        for (WaystoneData data : byId.values()) {
            if (data.worldId().equals(world.getUID()) && (data.x() >> 4) == chunkX && (data.z() >> 4) == chunkZ) list.add(data);
        }
        return list;
    }

    private WaystoneData.Category defaultPlayerCategory() {
        return parseCategory(plugin.getConfig().getString("categories.default-player", "PLAYER"), WaystoneData.Category.PLAYER);
    }

    private WaystoneData.Category defaultAdminCategory() {
        return parseCategory(plugin.getConfig().getString("categories.default-admin", "CITY"), WaystoneData.Category.CITY);
    }

    private WaystoneData.Category parseCategory(String raw, WaystoneData.Category fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return WaystoneData.Category.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private String locationKey(Location location) { return locationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ()); }
    private String locationKey(UUID worldId, int x, int y, int z) { return worldId + ":" + x + ":" + y + ":" + z; }
}
