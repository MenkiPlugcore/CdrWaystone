package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TravelGuardService {
    public enum GuardStatus { READY, COOLDOWN, COMBAT_LOCKED, KNOCKED_OUT }

    private final CdrWaystonePlugin plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private Class<?> knockoutApiClass;
    private Object knockoutApi;

    public TravelGuardService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        knockoutApiClass = null;
        knockoutApi = null;
    }

    public GuardStatus status(Player player) {
        if (player == null) return GuardStatus.READY;

        if (plugin.getConfig().getBoolean("anti-abuse.knockout.enabled", true)
                && !player.hasPermission("cdrwaystone.knockout.bypass")
                && isKnockoutBlocked(player)) {
            return GuardStatus.KNOCKED_OUT;
        }

        if (plugin.getConfig().getBoolean("anti-abuse.combat.enabled", true)
                && !player.hasPermission("cdrwaystone.combat.bypass")
                && combatRemainingSeconds(player) > 0) {
            return GuardStatus.COMBAT_LOCKED;
        }

        if (plugin.getConfig().getBoolean("anti-abuse.cooldown.enabled", true)
                && !player.hasPermission("cdrwaystone.cooldown.bypass")
                && cooldownRemainingSeconds(player) > 0) {
            return GuardStatus.COOLDOWN;
        }

        return GuardStatus.READY;
    }

    public void markTravelSuccess(Player player) {
        markTravelSuccess(player, null);
    }

    public void markTravelSuccess(Player player, WaystoneData routeNode) {
        if (player == null || !plugin.getConfig().getBoolean("anti-abuse.cooldown.enabled", true)
                || player.hasPermission("cdrwaystone.cooldown.bypass")) return;
        double baseSeconds = Math.max(0, plugin.getConfig().getInt("anti-abuse.cooldown.seconds", 30));
        double multiplier = routeNode == null ? 1.0 : plugin.tiers().cooldownMultiplier(routeNode);
        long millis = (long) Math.ceil(baseSeconds * Math.max(0.0, multiplier) * 1000.0);
        if (millis <= 0) return;
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public void tagCombat(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("anti-abuse.combat.enabled", true)
                || player.hasPermission("cdrwaystone.combat.bypass")) return;
        int seconds = Math.max(0, plugin.getConfig().getInt("anti-abuse.combat.tag-seconds", 15));
        if (seconds <= 0) return;
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public long cooldownRemainingSeconds(Player player) {
        if (player == null || player.hasPermission("cdrwaystone.cooldown.bypass")) return 0;
        return remaining(cooldownUntil, player.getUniqueId());
    }

    public long combatRemainingSeconds(Player player) {
        if (player == null || player.hasPermission("cdrwaystone.combat.bypass")) return 0;
        return remaining(combatUntil, player.getUniqueId());
    }

    public String label(Player player, GuardStatus status) {
        return switch (status) {
            case READY -> "Ready";
            case COOLDOWN -> "Travel cooldown (" + cooldownRemainingSeconds(player) + "s)";
            case COMBAT_LOCKED -> "Combat locked (" + combatRemainingSeconds(player) + "s)";
            case KNOCKED_OUT -> "Knocked out";
        };
    }

    private long remaining(Map<UUID, Long> map, UUID playerId) {
        Long until = map.get(playerId);
        if (until == null) return 0;
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0) {
            map.remove(playerId);
            return 0;
        }
        return Math.max(1L, (remainingMs + 999L) / 1000L);
    }

    private boolean isKnockoutBlocked(Player player) {
        Object api = resolveKnockoutApi();
        if (api == null || knockoutApiClass == null) return false;
        try {
            if (plugin.getConfig().getBoolean("anti-abuse.knockout.block-knocked", true)) {
                Method isKnocked = knockoutApiClass.getMethod("isKnocked", Player.class);
                if (Boolean.TRUE.equals(isKnocked.invoke(api, player))) return true;
            }
            if (plugin.getConfig().getBoolean("anti-abuse.knockout.block-death-in-progress", true)) {
                Method isDeathInProgress = knockoutApiClass.getMethod("isDeathInProgress", Player.class);
                if (Boolean.TRUE.equals(isDeathInProgress.invoke(api, player))) return true;
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("CdrKnockout travel guard check failed: " + ex.getMessage());
            knockoutApi = null;
            knockoutApiClass = null;
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveKnockoutApi() {
        if (knockoutApi != null && knockoutApiClass != null) return knockoutApi;
        if (Bukkit.getPluginManager().getPlugin("CdrKnockout") == null) return null;
        try {
            Class<?> apiClass = Class.forName("dev.cadera.cdrknockout.api.CdrKnockoutApi");
            Object service = Bukkit.getServicesManager().load((Class) apiClass);
            if (service == null) return null;
            knockoutApiClass = apiClass;
            knockoutApi = service;
            return service;
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }
}
