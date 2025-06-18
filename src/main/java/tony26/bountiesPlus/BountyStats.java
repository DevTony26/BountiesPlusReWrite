package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import tony26.bountiesPlus.utils.MessageUtils;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BountyStats {

    private final BountiesPlus plugin;

    public BountyStats(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the /bounty stats command
     * // note: Displays a player's bounty statistics
     */
    /**
     * Executes the /bounty stats command
     * // note: Displays a player's bounty statistics
     */
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (!player.hasPermission("bountiesplus.stats")) {
            MessageUtils.sendFormattedMessage(player, "no-permission");
            return true;
        }

        CompletableFuture.runAsync(() -> {
            try {
                int claimed = plugin.getMySQL().getClaimed(playerUUID).get();
                int survived = plugin.getMySQL().getSurvived(playerUUID).get();
                double moneyEarned = plugin.getMySQL().getMoneyEarned(playerUUID).get();
                int xpEarned = plugin.getMySQL().getXPEarned(playerUUID).get();
                double totalValueEarned = plugin.getMySQL().getTotalValueEarned(playerUUID).get();

                List<String> messages = plugin.getMessagesConfig().getStringList("stats-message");
                if (messages.isEmpty()) {
                    messages = Arrays.asList(
                            "&6&lYour Bounty Statistics:",
                            "&7Bounties Claimed: &e%claimed%",
                            "&7Bounties Survived: &e%survived%",
                            "&7Money Earned: &a$%money_earned%",
                            "&7XP Earned: &e%xp_earned% Levels",
                            "&7Total Value Earned: &a$%total_value_earned%"
                    );
                }

                PlaceholderContext context = PlaceholderContext.create()
                        .player(player)
                        .bountyCount(claimed)
                        .bountyCount(survived)
                        .withAmount(moneyEarned)
                        .expValue(xpEarned)
                        .totalBountyAmount(totalValueEarned);

                List<String> processedMessages = Placeholders.apply(messages, context);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String message : processedMessages) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch stats for " + playerUUID + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(ChatColor.RED + "Failed to retrieve your stats!"));
            }
        });

        return true;
    }
}