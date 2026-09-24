package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class CoreService {
    private final CdrWaystonePlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey recipeKey;

    public CoreService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "waystone_core");
        this.recipeKey = new NamespacedKey(plugin, "waystone_core_recipe");
    }

    public ItemStack createCore() {
        Material material = coreMaterial();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("✦ Waystone Core", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("A condensed heart of dimensional energy.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click a Dormant Player Waystone", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("to awaken its travel network.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isCore(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        Byte marker = stack.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean activate(Player player, WaystoneData data, ItemStack held) {
        if (player == null || data == null || !isCore(held)) return false;
        if (data.coreActive()) {
            player.sendMessage("§7This Waystone Core is already active.");
            return false;
        }
        if (!plugin.access().canManage(player, data)) {
            player.sendMessage("§cOnly the Waystone owner or an administrator can install its Core.");
            return false;
        }

        data.coreState(WaystoneData.CoreState.ACTIVE);
        plugin.registry().save();

        if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.hasPermission("cdrwaystone.core.consume.bypass")) {
            held.setAmount(held.getAmount() - 1);
        }

        plugin.discovery().setState(player.getUniqueId(), data.id(), DiscoveryService.State.ACTIVATED, true);
        Location center = data.location();
        if (center != null) {
            center = center.clone().add(0.5, 1.0, 0.5);
            center.getWorld().spawnParticle(Particle.END_ROD, center, 36, 0.55, 0.9, 0.55, 0.03);
            center.getWorld().spawnParticle(Particle.ENCHANT, center, 60, 0.8, 0.8, 0.8, 0.1);
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.9f);
            center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.25f);
        }
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("WAYSTONE AWAKENED", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text(data.name(), NamedTextColor.WHITE)));
        player.sendMessage("§d✦ §f" + data.name() + " §dhas awakened. Network travel is now available.");
        return true;
    }

    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        if (!plugin.getConfig().getBoolean("core.recipe-enabled", true)) return;

        Material corner = material("core.recipe.corner", Material.AMETHYST_SHARD);
        Material edge = material("core.recipe.edge", Material.ENDER_PEARL);
        Material center = material("core.recipe.center", Material.HEART_OF_THE_SEA);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createCore());
        recipe.shape("AEA", "EHE", "AEA");
        recipe.setIngredient('A', corner);
        recipe.setIngredient('E', edge);
        recipe.setIngredient('H', center);
        Bukkit.addRecipe(recipe);
    }

    public Material coreMaterial() {
        return material("core.item-material", Material.ECHO_SHARD);
    }

    private Material material(String path, Material fallback) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name()));
        return material == null || material.isAir() ? fallback : material;
    }
}
