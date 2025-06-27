package tony26.bountiesPlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BountyTabCompleter implements TabCompleter {

    /**
     * Provides tab completion for the /bounty command
     * // note: Suggests subcommands, player names, and other parameters based on context
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Main subcommands
            if (sender.hasPermission("bountiesplus.bounty")) {
                completions.add("set");
            }
            if (sender.hasPermission("bountiesplus.bounty.boost")) {
                completions.add("boost");
            }
            if (sender.hasPermission("bountiesplus.bounty.check")) {
                completions.add("check");
            }
            if (sender.hasPermission("bountiesplus.bounty.cancel")) {
                completions.add("cancel");
            }
            if (sender.hasPermission("bountiesplus.bounty.stats")) {
                completions.add("stats");
            }
            if (sender.hasPermission("bountiesplus.bounty.status")) {
                completions.add("status");
            }
            if (sender.hasPermission("bountiesplus.bounty.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("bountiesplus.bounty.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("bountiesplus.admin.frenzy")) {
                completions.add("frenzy");
            }
            if (sender.hasPermission("bountiesplus.notify.toggle")) {
                completions.add("notify");
            }
        } else if (args.length == 2) {
            // Player names for set, check, boost, give commands
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("check") ||
                    args[0].equalsIgnoreCase("boost") || args[0].equalsIgnoreCase("give")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            } else if (args[0].equalsIgnoreCase("frenzy")) {
                // Duration suggestions for frenzy command (in seconds)
                completions.addAll(Arrays.asList("30", "60", "120", "300", "600"));
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("boost")) {
                // Boost type options
                completions.add("Money");
                completions.add("XP");
            } else if (args[0].equalsIgnoreCase("give")) {
                // Available items for give command
                completions.add("tracker");
                completions.add("jammer");
                completions.add("uav");
                completions.add("manual-boost");
                completions.add("manual-frenzy");
                completions.add("chronos-shard");
                completions.add("reverse-bounty");
            } else if (args[0].equalsIgnoreCase("frenzy")) {
                // Multiplier suggestions for frenzy command
                completions.addAll(Arrays.asList("1.5", "2.0", "2.5", "3.0", "4.0", "5.0"));
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("boost")) {
                // Multiplier suggestions
                completions.add("1.5");
                completions.add("2.0");
                completions.add("3.0");
                completions.add("5.0");
            } else if (args[0].equalsIgnoreCase("give")) {
                // Amount suggestions for give command
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("16");
                completions.add("32");
                completions.add("64");
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("boost")) {
                // Time suggestions (in minutes)
                completions.add("5");
                completions.add("10");
                completions.add("30");
                completions.add("60");
            } else if (args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("manual-frenzy")) {
                // Multiplier suggestions for manual-frenzy
                completions.add("1.5");
                completions.add("2.0");
                completions.add("2.5");
                completions.add("3.0");
                completions.add("5.0");
            }
        } else if (args.length == 6) {
            if (args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("manual-frenzy")) {
                // Time suggestions for manual-frenzy (in minutes)
                completions.add("5");
                completions.add("10");
                completions.add("15");
                completions.add("30");
                completions.add("60");
            }
        } else if (args.length == 7) {
            if (args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("manual-frenzy")) {
                // Failure chance suggestions for manual-frenzy
                completions.add("0.0");
                completions.add("0.1");
                completions.add("0.2");
                completions.add("0.3");
                completions.add("0.5");
            }
        }

        // Filter completions based on what the user has typed
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}