package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public final class KeyService {
    private final CdrWaystonePlugin plugin;
    private final NamespacedKey keyMarker;
    private final NamespacedKey targetMarker;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public KeyService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.keyMarker = new NamespacedKey(plugin, "waystone_key");
        this.targetMarker = new NamespacedKey(plugin, "target_waystone");
    }

    public ItemStack createKey() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("key.material", "COMPASS"));
        if (material == null) material = Material.COMPASS;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(plugin.getConfig().getString("key.name", "<light_purple>Waystone Network Key</light_purple>")));
        meta.lore(List.of(
                Component.text("Use at an activated CdrWaystone"),
                Component.text("to open the Waystone Network"),
                Component.text("Travel only works Waystone to Waystone")
        ));
        meta.getPersistentDataContainer().set(keyMarker, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().remove(targetMarker);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isKey(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(keyMarker, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    /**
     * Legacy target reader retained so old v0.6.1 keys remain recognizable.
     * Remote-target teleporting is intentionally disabled in v0.6.2.
     */
    public UUID target(ItemStack item) {
        if (!isKey(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(targetMarker, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    /**
     * Converts an existing key into the v0.6.2 network-only form.
     */
    public void clearLegacyBinding(ItemStack item) {
        if (!isKey(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(targetMarker);
        meta.lore(List.of(
                Component.text("Use at an activated CdrWaystone"),
                Component.text("to open the Waystone Network"),
                Component.text("Travel only works Waystone to Waystone")
        ));
        item.setItemMeta(meta);
    }
}
