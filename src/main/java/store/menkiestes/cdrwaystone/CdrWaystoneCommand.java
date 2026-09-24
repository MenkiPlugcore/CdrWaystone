package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
            sender.sendMessage("§dCdrWaystone §7v" + plugin.getPluginMeta().getVersion() + " §8| §f/cws skins, /cws info, /cws admin");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "skins" -> sender.sendMessage("§dSkins: §f" + String.join(", ", plugin.skins().keySet()));
            case "getkey" -> getKey(sender, args);
            case "skin" -> setSkin(sender, args);
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
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayer only."); return; }
        if (!player.hasPermission("cdrwaystone.skin")) { sender.sendMessage("§cNo permission."); return; }
        if (args.length < 2) { sender.sendMessage("§cUsage: /cws skin <skin>"); return; }
        String skin = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.skins().containsKey(skin)) { sender.sendMessage("§cUnknown skin. Use /cws skins"); return; }
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        if (data.isAdmin() && !player.hasPermission("cdrwaystone.admin")) { sender.sendMessage("§cAdmin Waystones can only be changed by administrators."); return; }
        if (!data.isAdmin() && (data.owner()==null || !data.owner().equals(player.getUniqueId())) && !player.hasPermission("cdrwaystone.admin")) { sender.sendMessage("§cYou do not own this Waystone."); return; }
        data.skin(skin); plugin.registry().save(); plugin.visuals().spawn(data);
        sender.sendMessage("§aWaystone skin changed to §f" + skin + ".");
    }

    private void adminWaystone(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (!(sender instanceof Player player)) { sender.sendMessage("§cAdmin Waystone editing is player-only."); return; }
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
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayer only."); return; }
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        showInfo(sender, data);
    }

    private void showInfo(CommandSender sender, WaystoneData data) {
        sender.sendMessage("§d" + data.name() + " §7[" + data.type() + " / " + data.skin() + "]");
        sender.sendMessage("§7ID: §f" + data.id());
        sender.sendMessage("§7Location: §f" + data.worldName() + " " + data.x() + ", " + data.y() + ", " + data.z());
        sender.sendMessage("§7Public: §f" + data.publicAccess() + " §7Free: §f" + data.freeTravel() + " §7Permanent: §f" + data.permanent() + " §7Always Active: §f" + data.alwaysActive());
        sender.sendMessage("§7Globally Discovered: §f" + data.globallyDiscovered() + " §7Suppressed: §f" + plugin.teleports().isSuppressed(data));
        if (sender instanceof Player player) sender.sendMessage("§7Your Discovery State: §f" + plugin.discovery().status(player, data));
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayer only."); return; }
        if (!admin(sender)) return;
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        if (data.isAdmin() && data.permanent()) { sender.sendMessage("§6Use §f/cws admin remove§6 for a permanent Admin Waystone."); return; }
        plugin.visuals().remove(data); plugin.registry().remove(data); plugin.discovery().forgetWaystone(data.id());
        sender.sendMessage("§eRegistry entry and visual removed. Lodestone was left in place.");
    }

    private WaystoneData targeted(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) return null;
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) return plugin.registry().find(block.getRelative(0,-1,0).getLocation());
        return null;
    }
    private boolean admin(CommandSender sender) { if (sender.hasPermission("cdrwaystone.admin")) return true; sender.sendMessage("§cNo permission."); return false; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], List.of("getkey","skin","skins","admin","refresh","reload","info","remove"));
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) return filter(args[1], new ArrayList<>(plugin.skins().keySet()));
        if (args.length == 2 && args[0].equalsIgnoreCase("getkey")) return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return filter(args[1], List.of("create","remove","setpublic","setfree","setpermanent","setactive","setglobal","skin","info"));
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && List.of("setpublic","setfree","setpermanent","setactive","setglobal").contains(args[1].toLowerCase(Locale.ROOT))) return filter(args[2], List.of("true","false"));
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("skin")) return filter(args[2], new ArrayList<>(plugin.skins().keySet()));
        return Collections.emptyList();
    }
    private List<String> filter(String input, List<String> choices) { String lower=input.toLowerCase(Locale.ROOT); return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList(); }
}
