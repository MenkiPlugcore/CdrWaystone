package store.menkiestes.cdrwaystone;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

public final class VisualService {
    private final CdrWaystonePlugin plugin;
    private final NamespacedKey visualMarker;
    private Method customStackGetInstance;
    private Method customStackGetItemStack;

    public VisualService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.visualMarker = new NamespacedKey(plugin, "waystone_visual");
    }

    public void refreshAllLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isOurVisual(entity)) entity.remove();
            }
        }
        for (WaystoneData data : plugin.registry().all()) {
            Location location = data.location();
            if (location != null && isChunkLoaded(location)) {
                ensureCollision(data);
                spawn(data);
            }
        }
    }

    public void spawn(WaystoneData data) {
        if (!plugin.getConfig().getBoolean("visuals.enabled", true)) return;
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        if (base.getBlock().getType() != Material.LODESTONE) return;

        removeNear(base);
        String itemId = plugin.skins().get(data.skin());
        if (itemId == null) itemId = plugin.skins().get(plugin.getConfig().getString("visuals.default-skin", "andesite"));
        ItemStack modelItem = getItemsAdderItem(itemId);
        if (modelItem == null) {
            plugin.getLogger().warning("ItemsAdder model not available: " + itemId + " (waystone " + data.id() + ")");
            return;
        }

        double yOffset = plugin.getConfig().getDouble("visuals.y-offset", 0.0);
        float rotation = (float) Math.toRadians(plugin.getConfig().getDouble("visuals.rotation-degrees", 0.0));
        float viewRange = (float) plugin.getConfig().getDouble("visuals.view-range", 1.5);
        Location spawnAt = base.clone().add(0.5, yOffset, 0.5);

        base.getWorld().spawn(spawnAt, ItemDisplay.class, display -> {
            display.setItemStack(modelItem);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            display.setViewRange(viewRange);
            display.setPersistent(false);
            display.getPersistentDataContainer().set(visualMarker, PersistentDataType.BYTE, (byte) 1);
            Transformation old = display.getTransformation();
            display.setTransformation(new Transformation(
                    old.getTranslation(),
                    new Quaternionf(new AxisAngle4f(rotation, 0f, 1f, 0f)),
                    new Vector3f(1f, 1f, 1f),
                    old.getRightRotation()
            ));
        });
    }

    public void remove(WaystoneData data) {
        Location location = data.location();
        if (location == null) return;
        removeNear(location);
        removeCollision(data);
    }

    public void removeNear(Location base) {
        if (base == null || !isChunkLoaded(base)) return;
        for (Entity entity : base.getWorld().getNearbyEntities(base.clone().add(0.5, 1.0, 0.5), 2.0, 3.0, 2.0)) {
            if (isOurVisual(entity)) entity.remove();
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
        if (!plugin.getConfig().getBoolean("collision.enabled", true)) return;
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        Material current = base.clone().add(0, 1, 0).getBlock().getType();
        if (current == Material.AIR || current == Material.BARRIER) {
            base.clone().add(0, 1, 0).getBlock().setType(Material.BARRIER, false);
        }
    }

    public void removeCollision(WaystoneData data) {
        Location base = data.location();
        if (base == null || !isChunkLoaded(base)) return;
        if (base.clone().add(0, 1, 0).getBlock().getType() == Material.BARRIER) {
            base.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
        }
    }

    private ItemStack getItemsAdderItem(String id) {
        if (id == null || Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return null;
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
