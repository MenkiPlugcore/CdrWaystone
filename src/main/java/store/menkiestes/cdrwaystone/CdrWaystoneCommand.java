package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class CdrWaystoneCommand implements CommandExecutor, TabCompleter {
    private final CdrWaystonePlugin plugin;
    public CdrWaystoneCommand(CdrWaystonePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§dCdrWaystone §7v" + plugin.getPluginMeta().getVersion() + " §8| §f/cws info, access, trust, transfer, admin");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skins" -> sender.sendMessage("§dSkins: §f" + String.join(", ", plugin.skins().keySet()));
            case "getkey" -> getKey(sender, args);
            case "skin" -> setSkin(sender, args);
            case "access" -> setAccess(sender, args);
            case "trust" -> trust(sender, args, true);
            case "untrust" -> trust(sender, args, false);
            case "trusted" -> trusted(sender);
            case "transfer" -> transfer(sender, args);
            case "limit" -> limit(sender);
            case "admin" -> adminWaystone(sender, args);
            case "refresh" -> { if (admin(sender)) { plugin.visuals().refreshAllLoaded(); sender.sendMessage("§aWaystone visuals refreshed."); } }
            case "reload" -> { if (admin(sender)) { plugin.reloadPlugin(); sender.sendMessage("§aCdrWaystone configuration reloaded."); } }
            case "info" -> info(sender);
            case "remove" -> remove(sender);
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void getKey(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player target = sender instanceof Player p ? p : null;
        int amount = 1;
        if (args.length >= 2) target = Bukkit.getPlayerExact(args[1]);
        if (args.length >= 3) try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) {}
        if (target == null) { sender.sendMessage("§cPlayer not found. Usage: /cws getkey <player> [amount]"); return; }
        for (int i=0;i<amount;i++) target.getInventory().addItem(plugin.keys().createKey());
        sender.sendMessage("§aGave " + amount + " Waystone Key(s) to " + target.getName() + ".");
    }

    private void setSkin(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!player.hasPermission("cdrwaystone.skin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 2) { sender.sendMessage("§cUsage: /cws skin <skin>"); return; }
        String skin = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.skins().containsKey(skin)) { sender.sendMessage("§cUnknown skin. Use /cws skins"); return; }
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        if (!plugin.access().canManage(player, data)) { sender.sendMessage("§cYou cannot manage this Waystone."); return; }
        data.skin(skin); plugin.registry().save(); plugin.visuals().spawn(data);
        sender.sendMessage("§aWaystone skin changed to §f" + skin + ".");
    }

    private void setAccess(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) {
            sender.sendMessage("§dAccess: §f" + data.accessMode() + " §7| Trusted: §f" + data.trustedPlayers().size());
            sender.sendMessage("§7Use /cws access <private|trusted|public>");
            return;
        }
        try {
            WaystoneData.AccessMode mode = WaystoneData.AccessMode.valueOf(args[1].toUpperCase(Locale.ROOT));
            data.accessMode(mode);
            plugin.registry().save();
            sender.sendMessage("§aWaystone access changed to §f" + mode + ".");
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("§cAccess must be PRIVATE, TRUSTED, or PUBLIC.");
        }
    }

    private void trust(CommandSender sender, String[] args, boolean add) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) { sender.sendMessage("§cUsage: /cws " + (add ? "trust" : "untrust") + " <player>"); return; }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null || target.getUniqueId().equals(data.owner())) {
            sender.sendMessage("§cPlayer not found, or that player is already the owner."); return;
        }
        boolean changed = add ? data.trust(target.getUniqueId()) : data.untrust(target.getUniqueId());
        if (!changed) {
            sender.sendMessage(add ? "§7That player is already trusted." : "§7That player is not trusted."); return;
        }
        if (add && data.accessMode() == WaystoneData.AccessMode.PRIVATE) data.accessMode(WaystoneData.AccessMode.TRUSTED);
        plugin.registry().save();
        sender.sendMessage((add ? "§aTrusted §f" : "§eRemoved trust for §f") + displayName(target) + "§7. Access mode: §f" + data.accessMode());
    }

    private void trusted(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (data.trustedPlayers().isEmpty()) { sender.sendMessage("§7This Waystone has no trusted players."); return; }
        List<String> names = new ArrayList<>();
        for (UUID id : data.trustedPlayers()) names.add(displayName(Bukkit.getOfflinePlayer(id)));
        sender.sendMessage("§dTrusted players (§f" + names.size() + "§d): §f" + String.join(", ", names));
    }

    private void transfer(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) { sender.sendMessage("§cUsage: /cws transfer <online-player>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage("§cThe new owner must be online."); return; }
        if (data.owner() != null && data.owner().equals(target.getUniqueId())) { sender.sendMessage("§7That player already owns this Waystone."); return; }
        if (!plugin.access().canReceiveTransfer(target, data)) {
            sender.sendMessage("§c" + target.getName() + " has reached their Player Waystone limit."); return;
        }

        UUID oldOwner = data.owner();
        data.owner(target.getUniqueId());
        data.accessMode(WaystoneData.AccessMode.PRIVATE);
        data.clearTrusted();
        plugin.registry().save();
        if (oldOwner != null) plugin.discovery().setState(oldOwner, data.id(), DiscoveryService.State.DISCOVERED, false);
        plugin.discovery().setState(target.getUniqueId(), data.id(), DiscoveryService.State.ACTIVATED, true);

        sender.sendMessage("§aOwnership transferred to §f" + target.getName() + "§a. Access reset to PRIVATE and trusted list cleared.");
        target.sendMessage("§dYou now own Waystone §f" + data.name() + "§d. It has been activated for you.");
    }

    private void limit(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        long count = plugin.registry().countOwned(player.getUniqueId());
        int limit = plugin.access().limit(player);
        sender.sendMessage("§dPlayer Waystones: §f" + count + "§7/§f" + (limit < 0 ? "∞" : limit));
    }

    private void adminWaystone(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player player = requirePlayer(sender); if (player == null) return;
        if (args.length < 2) {
            sender.sendMessage("§6Admin Waystone: §f/cws admin create <name>, remove, setpublic, setfree, setpermanent, setactive, setglobal, skin, info");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        WaystoneData data = targeted(player);

        if (action.equals("create")) {
            Block block = player.getTargetBlockExact(6);
            if (block == null || block.getType() != Material.LODESTONE) { sender.sendMessage("§cLook at a Lodestone/CdrWaystone within 6 blocks."); return; }
            String name = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Server Waystone";
            String skin = plugin.getConfig().getString("admin-waystones.default-skin", "divine");
            if (!plugin.skins().containsKey(skin)) skin = "andesite";
            boolean global = plugin.getConfig().getBoolean("admin-waystones.default-globally-discovered", true);
            if (data == null) {
                data = plugin.registry().create(null, block.getLocation(), name, skin, WaystoneData.Type.ADMIN,
                        plugin.getConfig().getBoolean("admin-waystones.default-public", true),
                        plugin.getConfig().getBoolean("admin-waystones.default-free", true),
                        plugin.getConfig().getBoolean("admin-waystones.default-permanent", true),
                        plugin.getConfig().getBoolean("admin-waystones.default-always-active", true), global);
            } else {
                data.type(WaystoneData.Type.ADMIN); data.owner(null); data.name(name); data.skin(skin);
                data.publicAccess(plugin.getConfig().getBoolean("admin-waystones.default-public", true));
                data.freeTravel(plugin.getConfig().getBoolean("admin-waystones.default-free", true));
                data.permanent(plugin.getConfig().getBoolean("admin-waystones.default-permanent", true));
                data.alwaysActive(plugin.getConfig().getBoolean("admin-waystones.default-always-active", true));
                data.globallyDiscovered(global);
                data.accessMode(WaystoneData.AccessMode.PRIVATE);
                data.clearTrusted();
                plugin.registry().save();
            }
            plugin.discovery().setState(player.getUniqueId(), data.id(), DiscoveryService.State.ACTIVATED, true);
            plugin.visuals().ensureCollision(data); plugin.visuals().spawn(data);
            sender.sendMessage("§6Admin Waystone created: §f" + data.name() + " §7(Global Discovery: " + data.globallyDiscovered() + ")");
            return;
        }

        if (data == null || !data.isAdmin()) { sender.sendMessage("§cLook directly at an Admin Waystone."); return; }
        switch (action) {
            case "remove" -> {
                plugin.visuals().remove(data); plugin.registry().remove(data); plugin.discovery().forgetWaystone(data.id());
                sender.sendMessage("§eAdmin Waystone registry entry removed. Lodestone left in place.");
            }
            case "setpublic" -> toggle(sender, args, "public", data);
            case "setfree" -> toggle(sender, args, "free", data);
            case "setpermanent" -> toggle(sender, args, "permanent", data);
            case "setactive" -> toggle(sender, args, "active", data);
            case "setglobal" -> toggle(sender, args, "global", data);
            case "skin" -> {
                if (args.length < 3 || !plugin.skins().containsKey(args[2].toLowerCase(Locale.ROOT))) { sender.sendMessage("§cUsage: /cws admin skin <skin>"); return; }
                data.skin(args[2].toLowerCase(Locale.ROOT)); plugin.registry().save(); plugin.visuals().spawn(data);
                sender.sendMessage("§aAdmin Waystone skin updated.");
            }
            case "info" -> showInfo(sender, data);
            default -> sender.sendMessage("§cUnknown admin action.");
        }
    }

    private void toggle(CommandSender sender, String[] args, String field, WaystoneData data) {
        if (args.length < 3 || !(args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("false"))) {
            sender.sendMessage("§cUsage: /cws admin set" + field + " <true|false>"); return;
        }
        boolean value = Boolean.parseBoolean(args[2]);
        switch (field) {
            case "public" -> data.publicAccess(value);
            case "free" -> data.freeTravel(value);
            case "permanent" -> data.permanent(value);
            case "active" -> data.alwaysActive(value);
            case "global" -> data.globallyDiscovered(value);
        }
        plugin.registry().save();
        sender.sendMessage("§aAdmin Waystone " + field + " = §f" + value);
    }

    private void info(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        if (!plugin.access().canAccess(player, data) && !plugin.access().canManage(player, data)) { sender.sendMessage("§cYou do not have access to this Waystone."); return; }
        showInfo(sender, data);
    }

    private void showInfo(CommandSender sender, WaystoneData data) {
        sender.sendMessage("§d" + data.name() + " §7[" + data.type() + " / " + data.skin() + "]");
        sender.sendMessage("§7ID: §f" + data.id());
        sender.sendMessage("§7Location: §f" + data.worldName() + " " + data.x() + ", " + data.y() + ", " + data.z());
        if (data.isAdmin()) {
            sender.sendMessage("§7Public: §f" + data.publicAccess() + " §7Free: §f" + data.freeTravel() + " §7Permanent: §f" + data.permanent() + " §7Always Active: §f" + data.alwaysActive());
            sender.sendMessage("§7Globally Discovered: §f" + data.globallyDiscovered());
        } else {
            sender.sendMessage("§7Access: §f" + data.accessMode() + " §7Trusted: §f" + data.trustedPlayers().size());
            sender.sendMessage("§7Owner: §f" + (data.owner() == null ? "Unknown" : displayName(Bukkit.getOfflinePlayer(data.owner()))));
        }
        sender.sendMessage("§7Suppressed: §f" + plugin.teleports().isSuppressed(data));
        if (sender instanceof Player player) sender.sendMessage("§7Your Discovery State: §f" + plugin.discovery().status(player, data));
    }

    private void remove(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!admin(sender)) return;
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        if (data.isAdmin() && data.permanent()) { sender.sendMessage("§6Use §f/cws admin remove§6 for a permanent Admin Waystone."); return; }
        plugin.visuals().remove(data); plugin.registry().remove(data); plugin.discovery().forgetWaystone(data.id());
        sender.sendMessage("§eRegistry entry and visual removed. Lodestone was left in place.");
    }

    private WaystoneData ownedTarget(Player player) {
        WaystoneData data = targeted(player);
        if (data == null) { player.sendMessage("§cLook directly at a Player Waystone within 6 blocks."); return null; }
        if (data.isAdmin()) { player.sendMessage("§cThis command is for Player Waystones."); return null; }
        if (!plugin.access().canManage(player, data)) { player.sendMessage("§cOnly the owner can manage this Waystone."); return null; }
        return data;
    }

    private WaystoneData targeted(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) return null;
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) return plugin.registry().find(block.getRelative(0,-1,0).getLocation());
        return null;
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) return offline;
        }
        return null;
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage("§cPlayer only."); return null;
    }

    private boolean admin(CommandSender sender) { if (sender.hasPermission("cdrwaystone.admin")) return true; sender.sendMessage("§cNo permission."); return false; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], List.of("getkey","skin","skins","access","trust","untrust","trusted","transfer","limit","admin","refresh","reload","info","remove"));
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) return filter(args[1], new ArrayList<>(plugin.skins().keySet()));
        if (args.length == 2 && args[0].equalsIgnoreCase("getkey")) return filter(args[1], onlineNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("access")) return filter(args[1], List.of("private","trusted","public"));
        if (args.length == 2 && List.of("trust","untrust","transfer").contains(args[0].toLowerCase(Locale.ROOT))) return filter(args[1], onlineNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return filter(args[1], List.of("create","remove","setpublic","setfree","setpermanent","setactive","setglobal","skin","info"));
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && List.of("setpublic","setfree","setpermanent","setactive","setglobal").contains(args[1].toLowerCase(Locale.ROOT))) return filter(args[2], List.of("true","false"));
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("skin")) return filter(args[2], new ArrayList<>(plugin.skins().keySet()));
        return Collections.emptyList();
    }

    private List<String> onlineNames() { return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); }
    private List<String> filter(String input, List<String> choices) { String lower=input.toLowerCase(Locale.ROOT); return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }
}
