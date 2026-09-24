package store.menkiestes.cdrwaystone;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CoreCommand implements CommandExecutor, TabCompleter {
    private final CdrWaystonePlugin plugin;

    public CoreCommand(CdrWaystonePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cdrwaystone.admin")) {
            plugin.feedback().command(sender, "§cNo permission");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            plugin.feedback().command(sender, "§d/cwscore give <player> [amount]");
            return true;
        }
        if (args.length < 2) {
            plugin.feedback().command(sender, "§cUsage: /cwscore give <player> [amount]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.feedback().command(sender, "§cPlayer not found");
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException ignored) { }
        }
        for (int i = 0; i < amount; i++) {
            var leftovers = target.getInventory().addItem(plugin.cores().createCore());
            leftovers.values().forEach(stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));
        }
        plugin.feedback().command(sender, "§aGave §f" + amount + "§a Waystone Core(s) to §f" + target.getName());
        if (!sender.equals(target)) plugin.feedback().action(target, "§dReceived §f" + amount + " §dWaystone Core(s)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cdrwaystone.admin")) return Collections.emptyList();
        if (args.length == 1) return filter(args[0], List.of("give"));
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filter(args[2], List.of("1", "2", "4", "8", "16", "32", "64"));
        return Collections.emptyList();
    }

    private List<String> filter(String input, List<String> choices) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String choice : choices) if (choice.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(choice);
        result.sort(String::compareToIgnoreCase);
        return result;
    }
}
