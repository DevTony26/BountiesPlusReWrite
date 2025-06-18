package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import tony26.bountiesPlus.utils.MessageUtils;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;

public class BountyTeamCheck {
    private final BountiesPlus plugin;

    public BountyTeamCheck(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if two players are in the same group based on settings in BountyTeamChecks.yml.
     * @param setter The player setting the bounty or claiming it.
     * @param target The target player.
     * @return true if they are in the same group and the action should be blocked, false otherwise.
     */
    public boolean arePlayersInSameGroup(Player setter, OfflinePlayer target) {
        if (!target.isOnline()) {
            // Skip checks for offline players; assume not in same group
            return false;
        }
        Player targetPlayer = target.getPlayer();
        FileConfiguration config = plugin.getTeamChecksConfig();
        ConfigurationSection checks = config.getConfigurationSection("group-checks");
        if (checks == null) return false;

        for (String checkName : checks.getKeys(false)) {
            ConfigurationSection check = checks.getConfigurationSection(checkName);
            String type = check.getString("type", "").toLowerCase();

            switch (type) {
                case "scoreboard":
                    if (checkScoreboard(setter, targetPlayer, check)) {
                        return true;
                    }
                    break;
                case "permission":
                    if (checkPermission(setter, targetPlayer, check)) {
                        return true;
                    }
                    break;
                case "command":
                    if (checkCommand(setter, targetPlayer, check)) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private boolean checkScoreboard(Player setter, Player target, ConfigurationSection check) {
        String prefix = check.getString("team-prefix", "");
        Scoreboard scoreboard = setter.getScoreboard();
        Team setterTeam = scoreboard.getEntryTeam(setter.getName());
        Team targetTeam = scoreboard.getEntryTeam(target.getName());

        if (setterTeam != null && targetTeam != null &&
                setterTeam.getName().startsWith(prefix) && setterTeam.equals(targetTeam)) {
            sendErrorMessage(setter, check.getString("error-message", "You cannot target a teammate!"), target);
            return true;
        }
        return false;
    }

    private boolean checkPermission(Player setter, Player target, ConfigurationSection check) {
        String node = check.getString("node", "");
        if (!node.isEmpty() && setter.hasPermission(node) && target.hasPermission(node)) {
            sendErrorMessage(setter, check.getString("error-message", "You cannot target someone with the same permission!"), target);
            return true;
        }
        return false;
    }

    private boolean checkCommand(Player setter, Player target, ConfigurationSection check) {
        String command = check.getString("command", "");
        String regex = check.getString("regex", "");
        if (command.isEmpty() || regex.isEmpty()) return false;

        // Placeholder replacement for command
        String setterCommand = command.replace("%player%", setter.getName());
        String targetCommand = command.replace("%player%", target.getName());

        // Execute commands synchronously and parse output (placeholder implementation)
        String setterGroup = executeCommandAndParse(setterCommand, regex);
        String targetGroup = executeCommandAndParse(targetCommand, regex);

        if (setterGroup != null && setterGroup.equals(targetGroup)) {
            sendErrorMessage(setter, check.getString("error-message", "You cannot target someone in your group!"), target);
            return true;
        }
        return false;
    }

    private String executeCommandAndParse(String command, String regex) {
        // This is a placeholder; actual implementation requires capturing command output
        // For now, return null to indicate unimplemented
        // You would need to use Bukkit.dispatchCommand and a custom CommandSender to capture output
        return null;
    }

    private void sendErrorMessage(Player sender, String message, Player target) {
        PlaceholderContext context = PlaceholderContext.create()
                .player(sender)
                .target(target.getUniqueId());
        String formattedMessage = Placeholders.apply(message, context);
        MessageUtils.sendFormattedMessage(sender, formattedMessage);
    }
}