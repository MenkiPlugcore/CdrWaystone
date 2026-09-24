package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class WaystoneData {
    public enum Type { PLAYER, ADMIN }
    public enum AccessMode { PRIVATE, TRUSTED, PUBLIC }
    public enum Category { CAPITAL, CITY, VILLAGE, DUNGEON, KINGDOM, PLAYER, EVENT, OTHER }

    private final UUID id;
    private UUID owner;
    private final UUID worldId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private String name;
    private String skin;
    private boolean collisionOwned;
    private Type type;
    private boolean publicAccess;
    private boolean freeTravel;
    private boolean permanent;
    private boolean alwaysActive;
    private boolean globallyDiscovered;
    private AccessMode accessMode;
    private Category category;
    private final Set<UUID> trustedPlayers;

    public WaystoneData(UUID id, UUID owner, UUID worldId, String worldName,
                        int x, int y, int z, String name, String skin, boolean collisionOwned,
                        Type type, boolean publicAccess, boolean freeTravel,
                        boolean permanent, boolean alwaysActive, boolean globallyDiscovered,
                        AccessMode accessMode, Set<UUID> trustedPlayers, Category category) {
        this.id = id;
        this.owner = owner;
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
        this.skin = skin;
        this.collisionOwned = collisionOwned;
        this.type = type;
        this.publicAccess = publicAccess;
        this.freeTravel = freeTravel;
        this.permanent = permanent;
        this.alwaysActive = alwaysActive;
        this.globallyDiscovered = globallyDiscovered;
        this.accessMode = accessMode == null ? AccessMode.PRIVATE : accessMode;
        this.trustedPlayers = new LinkedHashSet<>(trustedPlayers == null ? Set.of() : trustedPlayers);
        this.category = category == null ? (type == Type.ADMIN ? Category.CITY : Category.PLAYER) : category;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public UUID worldId() { return worldId; }
    public String worldName() { return worldName; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String name() { return name; }
    public String skin() { return skin; }
    public boolean collisionOwned() { return collisionOwned; }
    public Type type() { return type; }
    public boolean isAdmin() { return type == Type.ADMIN; }
    public boolean publicAccess() { return publicAccess; }
    public boolean freeTravel() { return freeTravel; }
    public boolean permanent() { return permanent; }
    public boolean alwaysActive() { return alwaysActive; }
    public boolean globallyDiscovered() { return globallyDiscovered; }
    public AccessMode accessMode() { return accessMode; }
    public Category category() { return category; }
    public Set<UUID> trustedPlayers() { return Collections.unmodifiableSet(trustedPlayers); }
    public boolean isTrusted(UUID playerId) { return playerId != null && trustedPlayers.contains(playerId); }

    public void owner(UUID value) { this.owner = value; }
    public void name(String value) { this.name = value; }
    public void skin(String value) { this.skin = value; }
    public void collisionOwned(boolean value) { this.collisionOwned = value; }
    public void type(Type value) { this.type = value; }
    public void publicAccess(boolean value) { this.publicAccess = value; }
    public void freeTravel(boolean value) { this.freeTravel = value; }
    public void permanent(boolean value) { this.permanent = value; }
    public void alwaysActive(boolean value) { this.alwaysActive = value; }
    public void globallyDiscovered(boolean value) { this.globallyDiscovered = value; }
    public void accessMode(AccessMode value) { this.accessMode = value == null ? AccessMode.PRIVATE : value; }
    public void category(Category value) { this.category = value == null ? (isAdmin() ? Category.CITY : Category.PLAYER) : value; }
    public boolean trust(UUID playerId) { return playerId != null && trustedPlayers.add(playerId); }
    public boolean untrust(UUID playerId) { return playerId != null && trustedPlayers.remove(playerId); }
    public void clearTrusted() { trustedPlayers.clear(); }

    public World world() {
        World world = Bukkit.getWorld(worldId);
        return world != null ? world : Bukkit.getWorld(worldName);
    }

    public Location location() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z);
    }
}
