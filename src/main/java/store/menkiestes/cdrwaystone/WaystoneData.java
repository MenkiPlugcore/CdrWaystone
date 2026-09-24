package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class WaystoneData {
    public enum Type { PLAYER, ADMIN }

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

    public WaystoneData(UUID id, UUID owner, UUID worldId, String worldName,
                        int x, int y, int z, String name, String skin, boolean collisionOwned,
                        Type type, boolean publicAccess, boolean freeTravel,
                        boolean permanent, boolean alwaysActive) {
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

    public void owner(UUID value) { this.owner = value; }
    public void name(String value) { this.name = value; }
    public void skin(String value) { this.skin = value; }
    public void collisionOwned(boolean value) { this.collisionOwned = value; }
    public void type(Type value) { this.type = value; }
    public void publicAccess(boolean value) { this.publicAccess = value; }
    public void freeTravel(boolean value) { this.freeTravel = value; }
    public void permanent(boolean value) { this.permanent = value; }
    public void alwaysActive(boolean value) { this.alwaysActive = value; }

    public World world() {
        World world = Bukkit.getWorld(worldId);
        return world != null ? world : Bukkit.getWorld(worldName);
    }

    public Location location() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z);
    }
}
