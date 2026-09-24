package store.menkiestes.cdrwaystone;

import org.bukkit.entity.Player;

import java.util.UUID;

public final class AccessService {
    private final CdrWaystonePlugin plugin;

    public AccessService(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOwner(Player player, WaystoneData data) {
        return player != null && data != null && data.owner() != null && data.owner().equals(player.getUniqueId());
    }

    public boolean canManage(Player player, WaystoneData data) {
        if (player == null || data == null) return false;
        if (player.hasPermission("cdrwaystone.admin")) return true;
        return !data.isAdmin() && isOwner(player, data);
    }

    public boolean canAccess(Player player, WaystoneData data) {
        if (player == null || data == null) return false;
        if (player.hasPermission("cdrwaystone.admin")) return true;

        if (data.isAdmin()) return data.publicAccess();
        if (isOwner(player, data)) return true;

        return switch (data.accessMode()) {
            case PUBLIC -> true;
            case TRUSTED -> data.isTrusted(player.getUniqueId());
            case PRIVATE -> false;
        };
    }

    public boolean canCreate(Player player) {
        if (player == null) return false;
        if (player.hasPermission("cdrwaystone.admin") || player.hasPermission("cdrwaystone.limit.bypass")) return true;
        int limit = Math.max(0, plugin.getConfig().getInt("ownership.max-waystones-per-player", 3));
        return plugin.registry().countOwned(player.getUniqueId()) < limit;
    }

    public int limit(Player player) {
        if (player != null && (player.hasPermission("cdrwaystone.admin") || player.hasPermission("cdrwaystone.limit.bypass"))) return -1;
        return Math.max(0, plugin.getConfig().getInt("ownership.max-waystones-per-player", 3));
    }

    public boolean canReceiveTransfer(Player target, WaystoneData data) {
        if (target == null || data == null || data.isAdmin()) return false;
        UUID currentOwner = data.owner();
        if (currentOwner != null && currentOwner.equals(target.getUniqueId())) return true;
        return canCreate(target);
    }
}
