package store.menkiestes.cdrwaystone;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v0.6.4 furniture renderer.
 *
 * The Lodestone is only a staff placement trigger. Registered Waystones are
 * represented in-world by ItemsAdder CustomFurniture plus invisible Barrier
 * anchors for reliable Bukkit interaction/collision.
 */
public final class VisualService {
    private final CdrWaystonePlugin plugin;
    private final NamespacedKey visualMarker;
    private final NamespacedKey visualIdMarker;

    private Class<?> customFurnitureClass;
    private Method furnitureSpawnPrecise;
    private Method furnitureSpawnBlock;
    private Method furnitureByEntity;
    private Method furnitureGetEntity;
    private Method furnitureGetArmorstand;
    private Method furnitureRemove;

    public VisualService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.visualMarker = new NamespacedKey(plugin, "waystone_visual");
        this.visualIdMarker = new NamespacedKey(plugin, "waystone_visual_id");
    }

    /**
     * Rebuild all loaded Waystone furniture. Legacy registered Lodestones are
     * migrated automatically. Unregistered vanilla Lodestones are untouched.
     */
    public void refreshAllLoaded() {
        removeAllVisualEntities();
        List<WaystoneData> invalid = new ArrayList<>();

        for (WaystoneData data : plugin.registry().all()) {
            Location location = data.location();
            if (location == null || !isChunkLoaded(location)) continue;

            Material baseType = location.getBlock().getType();
            if (baseType != Material.LODESTONE && baseType != Material.BARRIER && baseType != Material.AIR) {
                removeCollision(data);
                invalid.add(data);
                continue;
            }

            if (!materialize(data)) {
                plugin.getLogger().warning("Could not materialize Waystone " + data.id()
                        + ". The registry entry was kept for a later retry.");
            }
        }

        if (!invalid.isEmpty()) {
            for (WaystoneData data : invalid) plugin.registry().remove(data, false);
            plugin.registry().save();
            plugin.getLogger().warning("Pruned " + invalid.size() + " obstructed Waystone registry entr"
                    + (invalid.size() == 1 ? "y." : "ies."));
        }
    }

    /**
     * Converts/rebuilds a registered Waystone into native ItemsAdder furniture.
     * Returns false without deleting the original Lodestone when ItemsAdder
     * cannot spawn the furniture.
     */
    public boolean materialize(WaystoneData data) {
        if (!plugin.getConfig().getBoolean("visuals.enabled", true)) return false;
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return false;

        Block baseBlock = base.getBlock();
        Block topBlock = baseBlock.getRelative(0, 1, 0);
        Material originalBase = baseBlock.getType();
        Material originalTop = topBlock.getType();

        if (originalBase != Material.LODESTONE && originalBase != Material.BARRIER && originalBase != Material.AIR) {
            return false;
        }
        if (originalTop != Material.AIR && originalTop != Material.BARRIER) {
            plugin.getLogger().warning("Cannot render Waystone " + data.id()
                    + " because its upper hitbox is occupied by " + originalTop + ".");
            return false;
        }

        String furnitureId = furnitureId(data);
        if (furnitureId == null || furnitureId.isBlank()) return false;

        removeVisual(data);

        // ItemsAdder needs the target space available while it creates the furniture.
        if (baseBlock.getType() == Material.LODESTONE || baseBlock.getType() == Material.BARRIER) {
            baseBlock.setType(Material.AIR, false);
        }
        if (topBlock.getType() == Material.BARRIER) topBlock.setType(Material.AIR, false);

        Entity entity = spawnFurniture(furnitureId, base);
        if (entity == null) {
            // Never eat the placement trigger when the visual provider is unavailable.
            baseBlock.setType(originalBase == Material.LODESTONE ? Material.LODESTONE : originalBase, false);
            if (originalTop == Material.BARRIER) topBlock.setType(Material.BARRIER, false);
            return false;
        }

        markFurniture(entity, data.id());
        ensureCollision(data);
        return true;
    }

    /** Existing callers (skin changes, chunk restoration) now rebuild furniture. */
    public void spawn(WaystoneData data) {
        materialize(data);
    }

    public boolean hasVisual(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return false;
        String id = data.id().toString();
        Location center = base.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : base.getWorld().getNearbyEntities(center, 2.0, 3.0, 2.0)) {
            String visualId = entity.getPersistentDataContainer().get(visualIdMarker, PersistentDataType.STRING);
            if (id.equals(visualId) && isOurVisual(entity)) return true;
        }
        return false;
    }

    public boolean isAnchorValid(WaystoneData data) {
        Location location = data == null ? null : data.location();
        if (location == null || location.getWorld() == null) return false;
        Material type = location.getBlock().getType();
        return type == Material.BARRIER || type == Material.LODESTONE;
    }

    public WaystoneData waystoneFromEntity(Entity entity) {
        if (entity == null || !isOurVisual(entity)) return null;
        String raw = entity.getPersistentDataContainer().get(visualIdMarker, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return plugin.registry().get(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void remove(WaystoneData data) {
        removeVisual(data);
        removeCollision(data);
    }

    public void removeVisual(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        String id = data.id().toString();
        Location center = base.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : new ArrayList<>(base.getWorld().getNearbyEntities(center, 2.0, 3.0, 2.0))) {
            if (!isOurVisual(entity)) continue;
            String visualId = entity.getPersistentDataContainer().get(visualIdMarker, PersistentDataType.STRING);
            if (id.equals(visualId)) removeFurnitureEntity(entity);
        }
    }

    public void removeAllVisualEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (isOurVisual(entity)) removeFurnitureEntity(entity);
            }
        }
    }

    public boolean isOurVisual(Entity entity) {
        if (entity == null) return false;
        Byte value = entity.getPersistentDataContainer().get(visualMarker, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    /**
     * The registered location itself and one block above it become invisible
     * anchors. The visible object is the ItemsAdder furniture only.
     */
    public void ensureCollision(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        if (!plugin.getConfig().getBoolean("collision.enabled", true)) {
            removeCollision(data);
            return;
        }

        Block bottom = base.getBlock();
        Block top = bottom.getRelative(0, 1, 0);

        if (bottom.getType() == Material.LODESTONE || bottom.getType() == Material.AIR) {
            bottom.setType(Material.BARRIER, false);
        }
        if (top.getType() == Material.AIR) top.setType(Material.BARRIER, false);

        boolean owned = bottom.getType() == Material.BARRIER && top.getType() == Material.BARRIER;
        if (data.collisionOwned() != owned) {
            data.collisionOwned(owned);
            plugin.registry().save();
        }
    }

    public void removeCollision(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        Block bottom = base.getBlock();
        Block top = bottom.getRelative(0, 1, 0);
        if (bottom.getType() == Material.BARRIER) bottom.setType(Material.AIR, false);
        if (top.getType() == Material.BARRIER) top.setType(Material.AIR, false);
        if (data.collisionOwned()) data.collisionOwned(false);
    }

    private String furnitureId(WaystoneData data) {
        String itemId = plugin.skins().get(data.skin());
        if (itemId == null) {
            itemId = plugin.skins().get(plugin.getConfig().getString("visuals.default-skin", "andesite"));
        }
        return itemId;
    }

    private Entity spawnFurniture(String id, Location base) {
        Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            plugin.getLogger().warning("ItemsAdder is not available; cannot spawn Waystone furniture " + id + ".");
            return null;
        }

        try {
            prepareFurnitureApi();
            Object furniture = null;

            // Preferred for our non-solid furniture definitions: exact floor-centred placement.
            if (furnitureSpawnPrecise != null) {
                Location precise = base.clone().add(0.5, 0.0, 0.5);
                precise.setYaw((float) plugin.getConfig().getDouble("visuals.rotation-degrees", 0.0));
                furniture = furnitureSpawnPrecise.invoke(null, id, precise);
            }

            // Compatibility fallback for ItemsAdder builds without precise spawning.
            if (furniture == null && furnitureSpawnBlock != null) {
                furniture = furnitureSpawnBlock.invoke(null, id, base.getBlock());
            }
            if (furniture == null) {
                plugin.getLogger().warning("ItemsAdder could not spawn furniture: " + id);
                return null;
            }

            Entity entity = null;
            if (furnitureGetEntity != null) {
                Object result = furnitureGetEntity.invoke(furniture);
                if (result instanceof Entity found) entity = found;
            }
            if (entity == null && furnitureGetArmorstand != null) {
                Object result = furnitureGetArmorstand.invoke(furniture);
                if (result instanceof Entity found) entity = found;
            }
            return entity;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not use ItemsAdder CustomFurniture API for " + id + ": " + ex.getMessage());
            return null;
        }
    }

    private void prepareFurnitureApi() throws ReflectiveOperationException {
        if (customFurnitureClass != null) return;
        customFurnitureClass = Class.forName("dev.lone.itemsadder.api.CustomFurniture");

        try {
            furnitureSpawnPrecise = customFurnitureClass.getMethod("spawnPreciseNonSolid", String.class, Location.class);
        } catch (NoSuchMethodException ignored) {
            furnitureSpawnPrecise = null;
        }
        try {
            furnitureSpawnBlock = customFurnitureClass.getMethod("spawn", String.class, Block.class);
        } catch (NoSuchMethodException ignored) {
            furnitureSpawnBlock = null;
        }
        furnitureByEntity = customFurnitureClass.getMethod("byAlreadySpawned", Entity.class);
        try {
            furnitureGetEntity = customFurnitureClass.getMethod("getEntity");
        } catch (NoSuchMethodException ignored) {
            furnitureGetEntity = null;
        }
        try {
            furnitureGetArmorstand = customFurnitureClass.getMethod("getArmorstand");
        } catch (NoSuchMethodException ignored) {
            furnitureGetArmorstand = null;
        }
        furnitureRemove = customFurnitureClass.getMethod("remove", boolean.class);
    }

    private void markFurniture(Entity entity, UUID waystoneId) {
        entity.getPersistentDataContainer().set(visualMarker, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(visualIdMarker, PersistentDataType.STRING, waystoneId.toString());
        entity.setInvulnerable(true);
        entity.setPersistent(false);
    }

    private void removeFurnitureEntity(Entity entity) {
        try {
            prepareFurnitureApi();
            Object furniture = furnitureByEntity.invoke(null, entity);
            if (furniture != null) {
                furnitureRemove.invoke(furniture, false);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback below also cleans legacy v0.6.3 Bukkit ItemDisplays.
        }
        entity.remove();
    }

    private boolean isChunkLoaded(Location location) {
        return location.getWorld() != null
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
