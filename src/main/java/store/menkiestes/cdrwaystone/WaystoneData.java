package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class WaystoneData {
    private final UUID id;
    private final UUID owner;
    private final UUID worldId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private String name;
    private String skin;
    private boolean collisionOwned;

    public WaystoneData(UUID id, UUID owner, UUID worldId, String worldName,
                        int x, int y, int z, String name, String skin, boolean collisionOwned) {
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
    public void name(String value) { this.name = value; }
    public void skin(String value) { this.skin = value; }
    public void collisionOwned(boolean value) { this.collisionOwned = value; }

    public World world() {
        World world = Bukkit.getWorld(worldId);
        return world != null ? world : Bukkit.getWorld(worldName);
    }

    public Location location() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z);
    }
}
