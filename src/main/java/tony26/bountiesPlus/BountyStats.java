package tony26.bountiesPlus;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import tony26.bountiesPlus.utils.MessageUtils;
import java.util.UUID;

public class BountyStats {

    private final BountiesPlus plugin;

    public BountyStats(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    public boolean handleStatsCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("bountiesplus.bounty.stats")) {
            MessageUtils.sendFormattedMessage(player, "no-permission");
            return true;
        }

        UUID playerUUID = player.getUniqueId();

        // Get stats data
        int bountiesClaimed = plugin.getStatsConfig().getInt("players." + playerUUID + ".claimed", 0);
        int bountiesSurvived = plugin.getStatsConfig().getInt("players." + playerUUID + ".survived", 0);
        double moneyEarned = plugin.getStatsConfig().getDouble("players." + playerUUID + ".money_earned", 0.0);
        int reputation = plugin.getStatsConfig().getInt("players." + playerUUID + ".reputation", 0);

        MessageUtils.sendFormattedMessage(player, "bounty-stats");
        return true;
    }
}