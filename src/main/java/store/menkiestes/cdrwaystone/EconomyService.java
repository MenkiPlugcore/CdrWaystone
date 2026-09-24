package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Locale;

public final class EconomyService {
    public enum Provider { NONE, AUTO, VAULT, XP_LEVELS, ITEM }

    public record Quote(double rawCost, double chargeAmount, Provider provider, String formatted,
                        boolean free, boolean providerAvailable) {}

    public record Payment(Provider provider, double amount, Material material, boolean charged) {
        public static Payment free() { return new Payment(Provider.NONE, 0.0, null, false); }
    }

    private final CdrWaystonePlugin plugin;
    private Object vaultEconomy;
    private Class<?> vaultEconomyClass;

    public EconomyService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        vaultEconomy = null;
        vaultEconomyClass = null;
    }

    public Quote quote(Player player, WaystoneData origin, WaystoneData target) {
        if (target == null || target.freeTravel()
                || (player != null && player.hasPermission("cdrwaystone.cost.bypass"))
                || !plugin.getConfig().getBoolean("economy.enabled", true)) {
            return new Quote(0.0, 0.0, Provider.NONE, "FREE", true, true);
        }

        Provider configured = configuredProvider();
        Provider provider = resolveProvider(configured);
        if (provider == Provider.NONE) {
            return new Quote(0.0, 0.0, Provider.NONE, "FREE", true, true);
        }

        boolean providerAvailable = provider != Provider.VAULT || ensureVault();
        double raw = calculateRawCost(player, origin, target);
        if (raw <= 0.0) return new Quote(0.0, 0.0, provider, "FREE", true, providerAvailable);

        double charge = normalizeCharge(raw, provider);
        return new Quote(raw, charge, provider, format(provider, charge), false, providerAvailable);
    }

    public boolean canAfford(Player player, Quote quote) {
        if (player == null || quote == null) return false;
        if (quote.free()) return true;
        if (!quote.providerAvailable()) return false;

        try {
            return switch (quote.provider()) {
                case VAULT -> vaultHas(player, quote.chargeAmount());
                case XP_LEVELS -> player.getLevel() >= (int) Math.ceil(quote.chargeAmount());
                case ITEM -> countItem(player, itemMaterial()) >= (int) Math.ceil(quote.chargeAmount());
                case NONE, AUTO -> true;
            };
        } catch (Exception ex) {
            plugin.getLogger().warning("Economy affordability check failed: " + ex.getMessage());
            return false;
        }
    }

    public Payment charge(Player player, Quote quote) {
        if (player == null || quote == null || quote.free()) return Payment.free();
        if (!quote.providerAvailable() || !canAfford(player, quote)) return null;

        try {
            return switch (quote.provider()) {
                case VAULT -> vaultWithdraw(player, quote.chargeAmount())
                        ? new Payment(Provider.VAULT, quote.chargeAmount(), null, true) : null;
                case XP_LEVELS -> {
                    int levels = (int) Math.ceil(quote.chargeAmount());
                    player.setLevel(Math.max(0, player.getLevel() - levels));
                    yield new Payment(Provider.XP_LEVELS, levels, null, true);
                }
                case ITEM -> {
                    Material material = itemMaterial();
                    int amount = (int) Math.ceil(quote.chargeAmount());
                    if (!removeItem(player, material, amount)) yield null;
                    yield new Payment(Provider.ITEM, amount, material, true);
                }
                case NONE, AUTO -> Payment.free();
            };
        } catch (Exception ex) {
            plugin.getLogger().warning("Economy charge failed: " + ex.getMessage());
            return null;
        }
    }

    public void refund(Player player, Payment payment) {
        if (player == null || payment == null || !payment.charged() || payment.amount() <= 0) return;
        try {
            switch (payment.provider()) {
                case VAULT -> vaultDeposit(player, payment.amount());
                case XP_LEVELS -> player.giveExpLevels((int) Math.ceil(payment.amount()));
                case ITEM -> refundItems(player,
                        payment.material() == null ? itemMaterial() : payment.material(),
                        (int) Math.ceil(payment.amount()));
                default -> { }
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Economy refund failed for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public String providerLabel() {
        Provider configured = configuredProvider();
        Provider resolved = resolveProvider(configured);
        if (configured == Provider.AUTO) return "AUTO → " + resolved.name();
        return resolved.name();
    }

    private double calculateRawCost(Player player, WaystoneData origin, WaystoneData target) {
        double base = Math.max(0.0, plugin.getConfig().getDouble("economy.pricing.base-cost", 0.0));
        double cost = base;

        Location from = origin == null ? (player == null ? null : player.getLocation()) : origin.location();
        Location to = target.location();
        boolean crossWorld = from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID());

        if (crossWorld) {
            cost += Math.max(0.0, plugin.getConfig().getDouble("economy.pricing.cross-world-flat-cost", 1000.0));
        } else if (plugin.getConfig().getBoolean("economy.pricing.distance.enabled", true)) {
            double distance = from.distance(to);
            double freeDistance = Math.max(0.0, plugin.getConfig().getDouble("economy.pricing.distance.free-distance", 500.0));
            double billable = Math.max(0.0, distance - freeDistance);
            double per1000 = Math.max(0.0, plugin.getConfig().getDouble("economy.pricing.distance.cost-per-1000-blocks", 250.0));
            cost += (billable / 1000.0) * per1000;
        }

        double multiplier = Math.max(0.0, plugin.getConfig().getDouble(
                "economy.pricing.category-multipliers." + target.category().name(), 1.0));
        cost *= multiplier;

        double minimum = Math.max(0.0, plugin.getConfig().getDouble("economy.pricing.minimum-cost", 0.0));
        double maximum = Math.max(minimum, plugin.getConfig().getDouble("economy.pricing.maximum-cost", 1000000.0));
        cost = Math.max(minimum, Math.min(maximum, cost));

        String rounding = plugin.getConfig().getString("economy.pricing.rounding", "CEIL").toUpperCase(Locale.ROOT);
        return switch (rounding) {
            case "FLOOR" -> Math.floor(cost);
            case "ROUND" -> Math.round(cost);
            case "NONE" -> cost;
            default -> Math.ceil(cost);
        };
    }

    private double normalizeCharge(double raw, Provider provider) {
        if (provider == Provider.XP_LEVELS) {
            double levelValue = Math.max(0.0001, plugin.getConfig().getDouble("economy.xp.value-per-level", 250.0));
            return Math.ceil(raw / levelValue);
        }
        if (provider == Provider.ITEM) {
            double itemValue = Math.max(0.0001, plugin.getConfig().getDouble("economy.item.value", 250.0));
            return Math.ceil(raw / itemValue);
        }
        return raw;
    }

    private Provider configuredProvider() {
        try {
            return Provider.valueOf(plugin.getConfig().getString("economy.provider", "AUTO").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Provider.AUTO;
        }
    }

    private Provider resolveProvider(Provider configured) {
        if (configured != Provider.AUTO) return configured;
        return ensureVault() ? Provider.VAULT : Provider.XP_LEVELS;
    }

    private String format(Provider provider, double amount) {
        return switch (provider) {
            case VAULT -> vaultFormat(amount);
            case XP_LEVELS -> ((int) Math.ceil(amount)) + " XP Level" + (Math.ceil(amount) == 1 ? "" : "s");
            case ITEM -> {
                int count = (int) Math.ceil(amount);
                String itemName = plugin.getConfig().getString("economy.item.display-name", pretty(itemMaterial().name()));
                yield count + " " + itemName + (count == 1 ? "" : "s");
            }
            case NONE, AUTO -> "FREE";
        };
    }

    private Material itemMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("economy.item.material", "EMERALD"));
        return material == null || material.isAir() ? Material.EMERALD : material;
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    private boolean removeItem(Player player, Material material, int amount) {
        if (amount <= 0) return true;
        if (countItem(player, material) < amount) return false;
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
        return remaining == 0;
    }

    private void refundItems(Player player, Material material, int amount) {
        int remaining = amount;
        int max = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(max, remaining);
            var leftovers = player.getInventory().addItem(new ItemStack(material, stackAmount));
            leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            remaining -= stackAmount;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean ensureVault() {
        if (vaultEconomy != null && vaultEconomyClass != null) return true;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) return false;
            Method getProvider = registration.getClass().getMethod("getProvider");
            Object provider = getProvider.invoke(registration);
            if (provider == null) return false;
            vaultEconomyClass = economyClass;
            vaultEconomy = provider;
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    private boolean vaultHas(Player player, double amount) throws Exception {
        if (!ensureVault()) return false;
        Method method = vaultEconomyClass.getMethod("has", OfflinePlayer.class, double.class);
        return Boolean.TRUE.equals(method.invoke(vaultEconomy, player, amount));
    }

    private boolean vaultWithdraw(Player player, double amount) throws Exception {
        if (!ensureVault()) return false;
        Method method = vaultEconomyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        Object response = method.invoke(vaultEconomy, player, amount);
        Method success = response.getClass().getMethod("transactionSuccess");
        return Boolean.TRUE.equals(success.invoke(response));
    }

    private boolean vaultDeposit(Player player, double amount) throws Exception {
        if (!ensureVault()) return false;
        Method method = vaultEconomyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        Object response = method.invoke(vaultEconomy, player, amount);
        Method success = response.getClass().getMethod("transactionSuccess");
        return Boolean.TRUE.equals(success.invoke(response));
    }

    private String vaultFormat(double amount) {
        if (!ensureVault()) return String.format(Locale.US, "%.2f", amount);
        try {
            Method method = vaultEconomyClass.getMethod("format", double.class);
            Object value = method.invoke(vaultEconomy, amount);
            return value == null ? String.format(Locale.US, "%.2f", amount) : value.toString();
        } catch (Exception ex) {
            return String.format(Locale.US, "%.2f", amount);
        }
    }

    private String pretty(String input) {
        StringBuilder out = new StringBuilder();
        for (String part : input.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
