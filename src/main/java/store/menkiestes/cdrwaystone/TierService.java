package store.menkiestes.cdrwaystone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TierService {
    private final CdrWaystonePlugin plugin;

    public TierService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("tier.enabled", true);
    }

    public WaystoneData.Tier effectiveTier(WaystoneData data) {
        if (data == null) return WaystoneData.Tier.AWAKENED;
        if (data.isAdmin() && plugin.getConfig().getBoolean("tier.admin-bypass", true)) {
            return WaystoneData.Tier.ASCENDED;
        }
        return data.tier();
    }

    public WaystoneData.Tier next(WaystoneData data) {
        if (data == null) return null;
        return switch (data.tier()) {
            case AWAKENED -> WaystoneData.Tier.EMPOWERED;
            case EMPOWERED -> WaystoneData.Tier.ANCIENT;
            case ANCIENT -> WaystoneData.Tier.ASCENDED;
            case ASCENDED -> null;
        };
    }

    public double maxDistance(WaystoneData routeNode) {
        if (!enabled()) return 0.0;
        return Math.max(0.0, plugin.getConfig().getDouble(path(routeNode, "max-distance"), 0.0));
    }

    public boolean allowsCrossWorld(WaystoneData routeNode) {
        if (!enabled()) return true;
        return plugin.getConfig().getBoolean(path(routeNode, "cross-world"), true);
    }

    public double costMultiplier(WaystoneData routeNode) {
        if (!enabled()) return 1.0;
        return Math.max(0.0, plugin.getConfig().getDouble(path(routeNode, "cost-multiplier"), 1.0));
    }

    public double cooldownMultiplier(WaystoneData routeNode) {
        if (!enabled()) return 1.0;
        return Math.max(0.0, plugin.getConfig().getDouble(path(routeNode, "cooldown-multiplier"), 1.0));
    }

    public String summary(WaystoneData data) {
        WaystoneData.Tier tier = effectiveTier(data);
        double max = maxDistance(data);
        String range = max <= 0 ? "Unlimited" : ((int) Math.ceil(max)) + " blocks";
        return tier.name() + " • Range " + range + " • Cross-world " + (allowsCrossWorld(data) ? "YES" : "NO");
    }

    public Map<Material, Integer> requirements(WaystoneData.Tier targetTier) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        if (targetTier == null) return out;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tier.upgrades." + targetTier.name() + ".items");
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            int amount = Math.max(0, section.getInt(key, 0));
            if (material != null && !material.isAir() && amount > 0) out.put(material, amount);
        }
        return out;
    }

    public int xpRequirement(WaystoneData.Tier targetTier) {
        if (targetTier == null) return 0;
        return Math.max(0, plugin.getConfig().getInt("tier.upgrades." + targetTier.name() + ".xp-levels", 0));
    }

    public boolean canAfford(Player player, WaystoneData.Tier targetTier) {
        if (player == null || targetTier == null) return false;
        if (bypassCost(player)) return true;
        if (player.getLevel() < xpRequirement(targetTier)) return false;
        for (Map.Entry<Material, Integer> entry : requirements(targetTier).entrySet()) {
            if (count(player, entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public boolean upgrade(Player player, WaystoneData data) {
        if (player == null || data == null) return false;
        if (!enabled()) {
            player.sendMessage("§cWaystone tier progression is disabled.");
            return false;
        }
        if (!data.coreActive()) {
            player.sendMessage("§cAwaken this Waystone with a Waystone Core before upgrading its tier.");
            return false;
        }
        if (!plugin.access().canManage(player, data)) {
            player.sendMessage("§cOnly the owner or an administrator can upgrade this Waystone.");
            return false;
        }
        WaystoneData.Tier next = next(data);
        if (next == null) {
            player.sendMessage("§dThis Waystone is already ASCENDED.");
            return false;
        }
        if (!canAfford(player, next)) {
            player.sendMessage("§cYou do not have the required resources to upgrade to §f" + next + "§c.");
            return false;
        }

        if (!bypassCost(player)) {
            for (Map.Entry<Material, Integer> entry : requirements(next).entrySet()) {
                remove(player, entry.getKey(), entry.getValue());
            }
            int levels = xpRequirement(next);
            if (levels > 0) player.setLevel(Math.max(0, player.getLevel() - levels));
        }

        data.tier(next);
        plugin.registry().save();
        playUpgradeEffect(player, data, next);
        return true;
    }

    public void setTier(WaystoneData data, WaystoneData.Tier tier) {
        if (data == null || tier == null) return;
        data.tier(tier);
        plugin.registry().save();
    }

    private String path(WaystoneData routeNode, String setting) {
        return "tier.perks." + effectiveTier(routeNode).name() + "." + setting;
    }

    private boolean bypassCost(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.hasPermission("cdrwaystone.tier.cost.bypass");
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private void remove(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setStorageContents(contents);
    }

    private void playUpgradeEffect(Player player, WaystoneData data, WaystoneData.Tier tier) {
        Location location = data.location();
        if (location != null && location.getWorld() != null) {
            Location center = location.clone().add(0.5, 1.1, 0.5);
            int particles = switch (tier) {
                case AWAKENED -> 25;
                case EMPOWERED -> 45;
                case ANCIENT -> 70;
                case ASCENDED -> 100;
            };
            center.getWorld().spawnParticle(Particle.END_ROD, center, particles, 0.7, 1.0, 0.7, 0.04);
            center.getWorld().spawnParticle(Particle.ENCHANT, center, particles, 0.9, 0.9, 0.9, 0.12);
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f + tier.ordinal() * 0.12f);
            center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.0f + tier.ordinal() * 0.1f);
        }

        player.showTitle(Title.title(
                Component.text("WAYSTONE " + tier.name(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text(data.name(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1400), Duration.ofMillis(450))
        ));
        player.sendMessage("§d✦ §f" + data.name() + " §dadvanced to §f" + tier + "§d.");
    }
}
