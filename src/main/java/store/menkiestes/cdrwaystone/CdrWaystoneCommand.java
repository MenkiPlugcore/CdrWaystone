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
            sender.sendMessage("§dCdrWaystone §7v" + plugin.getPluginMeta().getVersion() + " §8| §f/cws skins, /cws info");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "skins" -> sender.sendMessage("§dSkins: §f" + String.join(", ", plugin.skins().keySet()));
            case "getkey" -> getKey(sender, args);
            case "skin" -> setSkin(sender, args);
            case "refresh" -> {
                if (!admin(sender)) return true;
                plugin.visuals().refreshAllLoaded();
                sender.sendMessage("§aWaystone visuals refreshed.");
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.reloadPlugin();
                sender.sendMessage("§aCdrWaystone configuration reloaded.");
            }
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
        if (args.length >= 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) {}
        }
        if (target == null) { sender.sendMessage("§cPlayer not found. Usage: /cws getkey <player> [amount]"); return; }
        for (int i = 0; i < amount; i++) target.getInventory().addItem(plugin.keys().createKey());
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
        if (!data.owner().equals(player.getUniqueId()) && !player.hasPermission("cdrwaystone.admin")) { sender.sendMessage("§cYou do not own this Waystone."); return; }
        data.skin(skin);
        plugin.registry().save();
        plugin.visuals().spawn(data);
        sender.sendMessage("§aWaystone skin changed to §f" + skin + ".");
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayer only."); return; }
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        sender.sendMessage("§d" + data.name() + " §7[" + data.skin() + "]");
        sender.sendMessage("§7ID: §f" + data.id());
        sender.sendMessage("§7Location: §f" + data.worldName() + " " + data.x() + ", " + data.y() + ", " + data.z());
        sender.sendMessage("§7Suppressed: §f" + plugin.teleports().isSuppressed(data));
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayer only."); return; }
        if (!admin(sender)) return;
        WaystoneData data = targeted(player);
        if (data == null) { sender.sendMessage("§cLook directly at a CdrWaystone within 6 blocks."); return; }
        plugin.visuals().remove(data);
        plugin.registry().remove(data);
        sender.sendMessage("§eRegistry entry and visual removed. Lodestone was left in place.");
    }

    private WaystoneData targeted(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) return null;
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) return plugin.registry().find(block.getRelative(0, -1, 0).getLocation());
        return null;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("cdrwaystone.admin")) return true;
        sender.sendMessage("§cNo permission."); return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(args[0], List.of("getkey", "skin", "skins", "refresh", "reload", "info", "remove"));
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) return filter(args[1], new ArrayList<>(plugin.skins().keySet()));
        if (args.length == 2 && args[0].equalsIgnoreCase("getkey")) return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        return Collections.emptyList();
    }

    private List<String> filter(String input, List<String> choices) {
        String lower = input.toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
