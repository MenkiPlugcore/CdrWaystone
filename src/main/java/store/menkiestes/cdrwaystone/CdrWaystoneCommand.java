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
            reply(sender, "§dCdrWaystone §7v" + plugin.getPluginMeta().getVersion() + " §8• §f/cws info | skin | access | admin");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skins" -> reply(sender, "§dSkins §7• §f" + String.join(", ", plugin.skins().keySet()));
            case "getkey" -> getKey(sender, args);
            case "skin" -> setSkin(sender, args);
            case "category" -> setCategory(sender, args);
            case "access" -> setAccess(sender, args);
            case "trust" -> trust(sender, args, true);
            case "untrust" -> trust(sender, args, false);
            case "trusted" -> trusted(sender);
            case "transfer" -> transfer(sender, args);
            case "limit" -> limit(sender);
            case "admin" -> adminWaystone(sender, args);
            case "refresh" -> { if (admin(sender)) { plugin.visuals().refreshAllLoaded(); reply(sender, "§aWaystone visuals refreshed"); } }
            case "reload" -> { if (admin(sender)) { plugin.reloadPlugin(); reply(sender, "§aCdrWaystone reloaded"); } }
            case "info" -> info(sender);
            case "remove" -> remove(sender);
            default -> reply(sender, "§cUnknown subcommand");
        }
        return true;
    }

    private void getKey(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player target = sender instanceof Player p ? p : null;
        int amount = 1;
        if (args.length >= 2) target = Bukkit.getPlayerExact(args[1]);
        if (args.length >= 3) try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) {}
        if (target == null) { reply(sender, "§cPlayer not found §7• /cws getkey <player> [amount]"); return; }
        for (int i = 0; i < amount; i++) target.getInventory().addItem(plugin.keys().createKey());
        reply(sender, "§aGave §f" + amount + "§a Waystone Key(s) to §f" + target.getName());
        if (!sender.equals(target)) plugin.feedback().action(target, "§dReceived §f" + amount + " §dWaystone Key(s)");
    }

    private void setSkin(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!player.hasPermission("cdrwaystone.skin")) { reply(sender, "§cNo permission"); return; }
        if (args.length < 2) { reply(sender, "§c/cws skin <skin>"); return; }
        String skin = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.skins().containsKey(skin)) { reply(sender, "§cUnknown skin §7• §f/cws skins"); return; }
        WaystoneData data = targeted(player);
        if (data == null) { reply(sender, "§cLook at a Waystone within 6 blocks"); return; }
        if (!plugin.access().canManage(player, data)) { reply(sender, "§cYou cannot manage this Waystone"); return; }
        data.skin(skin);
        plugin.registry().save();
        plugin.visuals().spawn(data);
        reply(sender, "§aSkin changed §7• §f" + skin);
    }

    private void setCategory(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) { reply(sender, "§dCategory §7• §f" + data.category()); return; }
        WaystoneData.Category category = parseCategory(args[1]);
        if (category == null) { reply(sender, "§cUnknown category"); return; }
        data.category(category);
        plugin.registry().save();
        reply(sender, "§aCategory §7• §f" + category);
    }

    private void setAccess(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) { reply(sender, "§dAccess §7• §f" + data.accessMode() + " §8| §7Trusted §f" + data.trustedPlayers().size()); return; }
        try {
            WaystoneData.AccessMode mode = WaystoneData.AccessMode.valueOf(args[1].toUpperCase(Locale.ROOT));
            data.accessMode(mode);
            plugin.registry().save();
            reply(sender, "§aAccess changed §7• §f" + mode);
        } catch (IllegalArgumentException ex) {
            reply(sender, "§cUse PRIVATE, TRUSTED, or PUBLIC");
        }
    }

    private void trust(CommandSender sender, String[] args, boolean add) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) { reply(sender, "§c/cws " + (add ? "trust" : "untrust") + " <player>"); return; }
        OfflinePlayer target = resolveKnownPlayer(args[1]);
        if (target == null || target.getUniqueId().equals(data.owner())) { reply(sender, "§cInvalid player"); return; }
        boolean changed = add ? data.trust(target.getUniqueId()) : data.untrust(target.getUniqueId());
        if (!changed) { reply(sender, add ? "§7Player already trusted" : "§7Player is not trusted"); return; }
        if (add && data.accessMode() == WaystoneData.AccessMode.PRIVATE) data.accessMode(WaystoneData.AccessMode.TRUSTED);
        plugin.registry().save();
        reply(sender, (add ? "§aTrusted §f" : "§eUntrusted §f") + displayName(target));
    }

    private void trusted(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (data.trustedPlayers().isEmpty()) { reply(sender, "§7No trusted players"); return; }
        List<String> names = new ArrayList<>();
        for (UUID id : data.trustedPlayers()) names.add(displayName(Bukkit.getOfflinePlayer(id)));
        reply(sender, "§dTrusted §7• §f" + String.join(", ", names));
    }

    private void transfer(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = ownedTarget(player); if (data == null) return;
        if (args.length < 2) { reply(sender, "§c/cws transfer <online-player>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { reply(sender, "§cNew owner must be online"); return; }
        if (data.owner() != null && data.owner().equals(target.getUniqueId())) { reply(sender, "§7That player already owns this Waystone"); return; }
        if (!plugin.access().canReceiveTransfer(target, data)) { reply(sender, "§c" + target.getName() + " reached their Waystone limit"); return; }
        UUID oldOwner = data.owner();
        data.owner(target.getUniqueId());
        data.accessMode(WaystoneData.AccessMode.PRIVATE);
        data.category(WaystoneData.Category.PLAYER);
        data.clearTrusted();
        plugin.registry().save();
        if (oldOwner != null) plugin.discovery().setState(oldOwner, data.id(), DiscoveryService.State.DISCOVERED, false);
        plugin.discovery().setState(target.getUniqueId(), data.id(), DiscoveryService.State.ACTIVATED, true);
        reply(sender, "§aOwnership transferred §7• §f" + target.getName());
        plugin.feedback().action(target, "§dYou now own §f" + data.name());
    }

    private void limit(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        long count = plugin.registry().countOwned(player.getUniqueId());
        int limit = plugin.access().limit(player);
        reply(sender, "§dWaystones §7• §f" + count + "§7/§f" + (limit < 0 ? "∞" : limit));
    }

    private void adminWaystone(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        Player player = requirePlayer(sender); if (player == null) return;
        if (args.length < 2) { reply(sender, "§6/cws admin create <name> | remove | setfree | skin | info"); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        WaystoneData data = targeted(player);

        if (action.equals("create")) {
            Block block = player.getTargetBlockExact(6);
            if (block != null && block.getType() == Material.BARRIER) {
                WaystoneData below = plugin.registry().find(block.getRelative(0, -1, 0).getLocation());
                if (below != null && below.collisionOwned()) block = block.getRelative(0, -1, 0);
            }
            if (block == null || block.getType() != Material.LODESTONE) { reply(sender, "§cLook at a Lodestone within 6 blocks"); return; }
            data = plugin.registry().find(block.getLocation());
            String name = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Server Waystone";
            String skin = plugin.getConfig().getString("admin-waystones.default-skin", "divine");
            if (!plugin.skins().containsKey(skin)) skin = "andesite";
            boolean global = plugin.getConfig().getBoolean("admin-waystones.default-globally-discovered", true);
            WaystoneData.Category category = parseCategory(plugin.getConfig().getString("categories.default-admin", "CITY"));
            if (category == null) category = WaystoneData.Category.CITY;
            if (data == null) {
                data = plugin.registry().create(null, block.getLocation(), name, skin, WaystoneData.Type.ADMIN,
                        plugin.getConfig().getBoolean("admin-waystones.default-public", true),
                        plugin.getConfig().getBoolean("admin-waystones.default-free", true),
                        plugin.getConfig().getBoolean("admin-waystones.default-permanent", true),
                        plugin.getConfig().getBoolean("admin-waystones.default-always-active", true), global,
                        WaystoneData.AccessMode.PRIVATE, Set.of(), category);
            } else {
                data.type(WaystoneData.Type.ADMIN);
                data.owner(null);
                data.name(name);
                data.skin(skin);
                data.category(category);
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
            plugin.visuals().ensureCollision(data);
            plugin.visuals().spawn(data);
            reply(sender, "§6Admin Waystone created §7• §f" + data.name());
            return;
        }

        if (data == null || !data.isAdmin()) { reply(sender, "§cLook at an Admin Waystone"); return; }
        switch (action) {
            case "remove" -> {
                plugin.visuals().remove(data);
                plugin.registry().remove(data);
                plugin.discovery().forgetWaystone(data.id());
                reply(sender, "§eAdmin Waystone removed");
            }
            case "category" -> {
                if (args.length < 3) { reply(sender, "§c/cws admin category <category>"); return; }
                WaystoneData.Category category = parseCategory(args[2]);
                if (category == null) { reply(sender, "§cUnknown category"); return; }
                data.category(category);
                plugin.registry().save();
                reply(sender, "§aCategory §7• §f" + category);
            }
            case "setpublic" -> toggle(sender, args, "public", data);
            case "setfree" -> toggle(sender, args, "free", data);
            case "setpermanent" -> toggle(sender, args, "permanent", data);
            case "setactive" -> toggle(sender, args, "active", data);
            case "setglobal" -> toggle(sender, args, "global", data);
            case "skin" -> {
                if (args.length < 3 || !plugin.skins().containsKey(args[2].toLowerCase(Locale.ROOT))) { reply(sender, "§c/cws admin skin <skin>"); return; }
                data.skin(args[2].toLowerCase(Locale.ROOT));
                plugin.registry().save();
                plugin.visuals().spawn(data);
                reply(sender, "§aAdmin skin §7• §f" + data.skin());
            }
            case "info" -> showInfo(sender, data);
            default -> reply(sender, "§cUnknown admin action");
        }
    }

    private void toggle(CommandSender sender, String[] args, String field, WaystoneData data) {
        if (args.length < 3 || !(args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("false"))) {
            reply(sender, "§c/cws admin set" + field + " <true|false>");
            return;
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
        reply(sender, "§a" + field + " §7• §f" + value);
    }

    private void info(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        WaystoneData data = targeted(player);
        if (data == null) { reply(sender, "§cLook at a Waystone within 6 blocks"); return; }
        if (!plugin.access().canAccess(player, data) && !plugin.access().canManage(player, data)) { reply(sender, "§cAccess denied"); return; }
        showInfo(sender, data);
    }

    private void showInfo(CommandSender sender, WaystoneData data) {
        String access = data.isAdmin() ? (data.publicAccess() ? "PUBLIC" : "ADMIN") : data.accessMode().name();
        reply(sender, "§d" + data.name() + " §8• §f" + data.category() + " §8• §f" + data.coreState() + " §8• §f" + access + " §8• §f" + data.skin());
    }

    private void remove(CommandSender sender) {
        Player player = requirePlayer(sender); if (player == null) return;
        if (!admin(sender)) return;
        WaystoneData data = targeted(player);
        if (data == null) { reply(sender, "§cLook at a Waystone within 6 blocks"); return; }
        if (data.isAdmin() && data.permanent()) { reply(sender, "§6Use /cws admin remove for a permanent Admin Waystone"); return; }
        plugin.visuals().remove(data);
        plugin.registry().remove(data);
        plugin.discovery().forgetWaystone(data.id());
        reply(sender, "§eWaystone registry entry removed");
    }

    private WaystoneData ownedTarget(Player player) {
        WaystoneData data = targeted(player);
        if (data == null) { plugin.feedback().action(player, "§cLook at a Player Waystone"); return null; }
        if (data.isAdmin()) { plugin.feedback().action(player, "§cThis is an Admin Waystone"); return null; }
        if (!plugin.access().canManage(player, data)) { plugin.feedback().action(player, "§cOnly the owner can manage this Waystone"); return null; }
        return data;
    }

    private WaystoneData targeted(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) return null;
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) return plugin.registry().find(block.getRelative(0, -1, 0).getLocation());
        return null;
    }

    private WaystoneData.Category parseCategory(String raw) {
        if (raw == null) return null;
        try { return WaystoneData.Category.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) return offline;
        }
        return null;
    }

    private String displayName(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString() : player.getName(); }
    private void reply(CommandSender sender, String text) { plugin.feedback().command(sender, text); }
    private Player requirePlayer(CommandSender sender) { if (sender instanceof Player player) return player; reply(sender, "§cPlayer only"); return null; }
    private boolean admin(CommandSender sender) { if (sender.hasPermission("cdrwaystone.admin")) return true; reply(sender, "§cNo permission"); return false; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], List.of("getkey","skin","skins","category","access","trust","untrust","trusted","transfer","limit","admin","refresh","reload","info","remove"));
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) return filter(args[1], new ArrayList<>(plugin.skins().keySet()));
        if (args.length == 2 && args[0].equalsIgnoreCase("category")) return filter(args[1], categoryNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("getkey")) return filter(args[1], onlineNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("access")) return filter(args[1], List.of("private","trusted","public"));
        if (args.length == 2 && List.of("trust","untrust","transfer").contains(args[0].toLowerCase(Locale.ROOT))) return filter(args[1], onlineNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return filter(args[1], List.of("create","remove","category","setpublic","setfree","setpermanent","setactive","setglobal","skin","info"));
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("category")) return filter(args[2], categoryNames());
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && List.of("setpublic","setfree","setpermanent","setactive","setglobal").contains(args[1].toLowerCase(Locale.ROOT))) return filter(args[2], List.of("true","false"));
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("skin")) return filter(args[2], new ArrayList<>(plugin.skins().keySet()));
        return Collections.emptyList();
    }

    private List<String> categoryNames() { return Arrays.stream(WaystoneData.Category.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList(); }
    private List<String> onlineNames() { return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()); }
    private List<String> filter(String input, List<String> choices) { String lower = input.toLowerCase(Locale.ROOT); return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }
}
