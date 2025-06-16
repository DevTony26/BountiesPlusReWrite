package tony26.bountiesPlus;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ParticleCommand implements CommandExecutor {

    private final BountiesPlus plugin;

    public ParticleCommand(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("bountiesplus.particle")) {
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }

        boolean currentState = plugin.getParticleVisibility(player);
        boolean newState = !currentState;
        plugin.setParticleVisibility(player, newState);

        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        String messageKey = newState ? "particle-enabled" : "particle-disabled";
        String message = messagesConfig.getString(messageKey, newState ? "%prefix%&aBounty particles enabled." : "%prefix%&cBounty particles disabled.");
        message = message.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        return true;
    }
}