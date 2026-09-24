package store.menkiestes.cdrwaystone;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TierCommand implements CommandExecutor, TabCompleter {
    private final CdrWaystonePlugin plugin;

    public TierCommand(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command must be used in-game while looking at a Waystone.");
            return true;
        }
        if (!player.hasPermission("cdrwaystone.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        WaystoneData data = targeted(player);
        if (data == null) {
            player.sendMessage("§cLook at a Waystone within 6 blocks.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            player.sendMessage("§dWaystone Tier: §f" + data.tier());
            player.sendMessage("§7Effective route tier: §f" + plugin.tiers().effectiveTier(data));
            player.sendMessage("§7" + plugin.tiers().summary(data));
            return true;
        }

        if (!args[0].equalsIgnoreCase("set") || args.length < 2) {
            player.sendMessage("§dUsage: §f/cwstier info §7or §f/cwstier set <awakened|empowered|ancient|ascended>");
            return true;
        }

        WaystoneData.Tier tier;
        try { tier = WaystoneData.Tier.valueOf(args[1].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) {
            player.sendMessage("§cInvalid tier. Use AWAKENED, EMPOWERED, ANCIENT, or ASCENDED.");
            return true;
        }

        plugin.tiers().setTier(data, tier);
        player.sendMessage("§aWaystone tier set to §f" + tier + "§a.");
        plugin.gui().openMain(player, data);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cdrwaystone.admin")) return Collections.emptyList();
        if (args.length == 1) return filter(args[0], List.of("info", "set"));
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(args[1], List.of("awakened", "empowered", "ancient", "ascended"));
        }
        return Collections.emptyList();
    }

    private WaystoneData targeted(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) return null;
        WaystoneData direct = plugin.registry().find(block.getLocation());
        if (direct != null) return direct;
        if (block.getType() == Material.BARRIER) {
            return plugin.registry().find(block.getRelative(0, -1, 0).getLocation());
        }
        return null;
    }

    private List<String> filter(String input, List<String> choices) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String choice : choices) if (choice.startsWith(lower)) result.add(choice);
        return result;
    }
}
