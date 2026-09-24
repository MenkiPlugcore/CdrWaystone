package store.menkiestes.cdrwaystone;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VisualService {
    private final CdrWaystonePlugin plugin;
    private final NamespacedKey visualMarker;
    private final NamespacedKey visualIdMarker;
    private Method customStackGetInstance;
    private Method customStackGetItemStack;

    public VisualService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.visualMarker = new NamespacedKey(plugin, "waystone_visual");
        this.visualIdMarker = new NamespacedKey(plugin, "waystone_visual_id");
    }

    public void refreshAllLoaded() {
        removeAllVisualEntities();
        List<WaystoneData> invalid = new ArrayList<>();

        for (WaystoneData data : plugin.registry().all()) {
            Location location = data.location();
            if (location == null || !isChunkLoaded(location)) continue;
            if (location.getBlock().getType() != Material.LODESTONE) {
                removeCollision(data);
                invalid.add(data);
                continue;
            }
            ensureCollision(data);
            spawn(data);
        }

        if (!invalid.isEmpty()) {
            for (WaystoneData data : invalid) plugin.registry().remove(data, false);
            plugin.registry().save();
            plugin.getLogger().warning("Pruned " + invalid.size() + " stale Waystone registry entr" + (invalid.size() == 1 ? "y." : "ies."));
        }
    }

    public void spawn(WaystoneData data) {
        if (!plugin.getConfig().getBoolean("visuals.enabled", true)) {
            removeVisual(data);
            return;
        }
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        if (base.getBlock().getType() != Material.LODESTONE) return;

        removeVisual(data);
        String itemId = plugin.skins().get(data.skin());
        if (itemId == null) itemId = plugin.skins().get(plugin.getConfig().getString("visuals.default-skin", "andesite"));
        ItemStack modelItem = getItemsAdderItem(itemId);
        if (modelItem == null) {
            plugin.getLogger().warning("ItemsAdder model not available: " + itemId + " (waystone " + data.id() + ")");
            return;
        }

        double yOffset = plugin.getConfig().getDouble("visuals.model-y-offset", 0.5);
        float rotation = (float) Math.toRadians(plugin.getConfig().getDouble("visuals.rotation-degrees", 0.0));
        float viewRange = (float) plugin.getConfig().getDouble("visuals.view-range", 1.5);
        float scale = (float) Math.max(0.05, plugin.getConfig().getDouble("visuals.scale", 1.0));
        ItemDisplay.ItemDisplayTransform transform = configuredTransform();
        Location spawnAt = base.clone().add(0.5, yOffset, 0.5);

        base.getWorld().spawn(spawnAt, ItemDisplay.class, display -> {
            display.setItemStack(modelItem);
            display.setItemDisplayTransform(transform);
            display.setBillboard(Display.Billboard.FIXED);
            display.setViewRange(viewRange);
            display.setPersistent(false);
            display.getPersistentDataContainer().set(visualMarker, PersistentDataType.BYTE, (byte) 1);
            display.getPersistentDataContainer().set(visualIdMarker, PersistentDataType.STRING, data.id().toString());

            Transformation old = display.getTransformation();
            display.setTransformation(new Transformation(
                    old.getTranslation(),
                    new Quaternionf(new AxisAngle4f(rotation, 0f, 1f, 0f)),
                    new Vector3f(scale, scale, scale),
                    old.getRightRotation()
            ));
        });
    }

    public boolean hasVisual(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return false;
        String id = data.id().toString();
        Location center = base.clone().add(0.5, 1.0, 0.5);
        for (Entity entity : base.getWorld().getNearbyEntities(center, 1.25, 3.0, 1.25)) {
            String visualId = entity.getPersistentDataContainer().get(visualIdMarker, PersistentDataType.STRING);
            if (id.equals(visualId) && isOurVisual(entity)) return true;
        }
        return false;
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
        for (Entity entity : base.getWorld().getNearbyEntities(center, 1.25, 3.0, 1.25)) {
            if (!isOurVisual(entity)) continue;
            String visualId = entity.getPersistentDataContainer().get(visualIdMarker, PersistentDataType.STRING);
            if (id.equals(visualId)) {
                entity.remove();
                continue;
            }
            if (visualId == null) {
                Location entityLoc = entity.getLocation();
                double dx = entityLoc.getX() - (base.getBlockX() + 0.5);
                double dz = entityLoc.getZ() - (base.getBlockZ() + 0.5);
                if ((dx * dx + dz * dz) <= 0.36) entity.remove();
            }
        }
    }

    public void removeAllVisualEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isOurVisual(entity)) entity.remove();
            }
        }
    }

    private boolean isChunkLoaded(Location location) {
        return location.getWorld() != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private boolean isOurVisual(Entity entity) {
        Byte value = entity.getPersistentDataContainer().get(visualMarker, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void ensureCollision(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        if (base.getBlock().getType() != Material.LODESTONE) return;

        if (!plugin.getConfig().getBoolean("collision.enabled", true)) {
            removeCollision(data);
            return;
        }

        Block top = base.clone().add(0, 1, 0).getBlock();
        Material current = top.getType();
        if (current == Material.AIR) {
            top.setType(Material.BARRIER, false);
            if (!data.collisionOwned()) {
                data.collisionOwned(true);
                plugin.registry().save();
            }
        } else if (current != Material.BARRIER && data.collisionOwned()) {
            data.collisionOwned(false);
            plugin.registry().save();
            plugin.getLogger().warning("Collision disabled for Waystone " + data.id() + " because the block above it is occupied by " + current + ".");
        }
    }

    public void removeCollision(WaystoneData data) {
        if (!data.collisionOwned()) return;
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        Block top = base.clone().add(0, 1, 0).getBlock();
        if (top.getType() == Material.BARRIER) top.setType(Material.AIR, false);
        data.collisionOwned(false);
    }

    private ItemDisplay.ItemDisplayTransform configuredTransform() {
        String raw = plugin.getConfig().getString("visuals.display-transform", "FIXED");
        if (raw == null) return ItemDisplay.ItemDisplayTransform.FIXED;
        try {
            return ItemDisplay.ItemDisplayTransform.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Unknown visuals.display-transform '" + raw + "'. Using FIXED.");
            return ItemDisplay.ItemDisplayTransform.FIXED;
        }
    }

    private ItemStack getItemsAdderItem(String id) {
        Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (id == null || itemsAdder == null || !itemsAdder.isEnabled()) return null;
        try {
            if (customStackGetInstance == null || customStackGetItemStack == null) {
                Class<?> clazz = Class.forName("dev.lone.itemsadder.api.CustomStack");
                customStackGetInstance = clazz.getMethod("getInstance", String.class);
                customStackGetItemStack = clazz.getMethod("getItemStack");
            }
            Object customStack = customStackGetInstance.invoke(null, id);
            if (customStack == null) return null;
            return ((ItemStack) customStackGetItemStack.invoke(customStack)).clone();
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Could not read ItemsAdder CustomStack " + id + ": " + ex.getMessage());
            return null;
        }
    }
}
