
package tony26.bountiesPlus;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.BountyCancel;
import tony26.bountiesPlus.GUIs.PreviewGUI;
import tony26.bountiesPlus.GUIs.TopGUI;
import tony26.bountiesPlus.utils.MessageUtils;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;

import java.util.*;

public class BountyCommand implements CommandExecutor {

    private final BountiesPlus plugin;

    public BountyCommand(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles the /bounty command execution
     * // note: Dispatches to appropriate subcommand based on arguments
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String consoleNotAllowed = messagesConfig.getString("console-not-allowed", "&cThis command can only be used by players, except for 'give' command.");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', consoleNotAllowed));
            return true;
        }

        Player player = (Player) sender;
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        FileConfiguration config = plugin.getConfig();
        boolean allowTime = config.getBoolean("allow-time", false);

        if (args.length == 0) {
            if (config.getBoolean("require-skull-turn-in", true)) {
                BountyGUI.openBountyGUI(player, false, 0);
            } else {
                MessageUtils.sendFormattedMessage(player, "bounty-gui-disabled");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "set":
                return handleSetCommand(player, args, messagesConfig, config, allowTime);
            case "check":
                return handleCheckCommand(player, args, messagesConfig);
            case "cancel":
                return handleCancelCommand(player, messagesConfig);
            case "stats":
                return handleStatsCommand(player, messagesConfig);
            case "status":
                return handleStatusCommand(player, args, messagesConfig);
            case "give":
                return handleGiveCommand(sender, args);
            case "reload":
                return handleReloadCommand(player, args, messagesConfig, allowTime);
            case "frenzy":
                return handleFrenzyCommand(sender, args, messagesConfig);
            case "preview":
                if (args.length != 2) {
                    MessageUtils.sendFormattedMessage(player, "bounty-preview-usage");
                    return true;
                }
                String previewTargetName = args[1];
                OfflinePlayer previewTarget = Bukkit.getOfflinePlayer(previewTargetName);
                if (previewTarget == null || !previewTarget.hasPlayedBefore()) {
                    MessageUtils.sendFormattedMessage(player, "bounty-player-not-found");
                    return true;
                }
                new PreviewGUI(player, previewTarget.getUniqueId()).openInventory(player);
                return true;
            case "top":
                if (!player.hasPermission("bountiesplus.bounty.top")) {
                    MessageUtils.sendFormattedMessage(player, "bounty-top-no-permission");
                    return true;
                }
                if (args.length != 1) {
                    MessageUtils.sendFormattedMessage(player, "bounty-top-usage");
                    return true;
                }
                new TopGUI(plugin).openTopGUI(player, 0, TopGUI.FilterType.CLAIMED, true);
                return true;
            default:
                MessageUtils.sendFormattedMessage(player, "bounty-usage");
                return true;
        }
    }

    /**
     * Handles the /bounty status command
     * // note: Displays the bounty status for the player or a specified target
     */
    private boolean handleStatusCommand(Player player, String[] args, FileConfiguration messagesConfig) {
        if (!player.hasPermission("bountiesplus.bounty.status")) {
            MessageUtils.sendFormattedMessage(player, "no-permission");
            return true;
        }

        String targetName;
        OfflinePlayer target;

        // If no player specified, check own status
        if (args.length == 1) {
            targetName = player.getName();
            target = player;
        } else if (args.length == 2) {
            targetName = args[1];
            target = Bukkit.getOfflinePlayer(targetName);

            if (target == null || !target.hasPlayedBefore()) {
                String playerNotFound = messagesConfig.getString("bounty-player-not-found", "%prefix%&cPlayer &e%target%&c not found.");
                playerNotFound = playerNotFound.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
                playerNotFound = playerNotFound.replace("%target%", targetName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerNotFound));
                return true;
            }
        } else {
            MessageUtils.sendFormattedMessage(player, "bounty-status-usage");
            return true;
        }

        UUID targetUUID = target.getUniqueId();
        Map<UUID, Integer> bounties = plugin.getBountyManager().getBountiesOnTarget(targetUUID);

        if (bounties.isEmpty()) {
            String noBounty = messagesConfig.getString("bounty-status-none", "%prefix%&7%target% &ahas no bounties.");
            noBounty = noBounty.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            noBounty = noBounty.replace("%target%", targetName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noBounty));
            return true;
        }

        // Calculate total bounty pool with multipliers
        double totalPool = 0;
        for (Map.Entry<UUID, Integer> entry : bounties.entrySet()) {
            UUID setterUUID = entry.getKey();
            int amount = entry.getValue();
            double multiplier = plugin.getBountyManager().getBountyMultiplier(setterUUID, targetUUID);
            totalPool += amount * multiplier;
        }

        // Apply frenzy and boost multipliers if active
        double frenzyMultiplier = 1.0;
        double boostMultiplier = 1.0;

        if (plugin.getFrenzy() != null && plugin.getFrenzy().isFrenzyActive()) {
            frenzyMultiplier = plugin.getFrenzy().getFrenzyMultiplier();
        }

        if (plugin.getBoostedBounty() != null) {
            UUID currentBoosted = plugin.getBoostedBounty().getCurrentBoostedTarget();
            if (currentBoosted != null && currentBoosted.equals(targetUUID)) {
                boostMultiplier = plugin.getBoostedBounty().getCurrentBoostMultiplier(targetUUID);
            }
        }

        totalPool *= frenzyMultiplier * boostMultiplier;

        // Get earliest expiration time
        String timeUntilExpiry = messagesConfig.getString("bounty-status-no-expiration", "No expiration");
        long currentTime = System.currentTimeMillis();
        long earliestExpiry = Long.MAX_VALUE;

        for (UUID sponsorUUID : bounties.keySet()) {
            long timestamp = plugin.getBountiesConfig().getLong("bounties." + targetUUID + "." + sponsorUUID + ".expire_time", -1);
            if (timestamp > 0 && timestamp < earliestExpiry) {
                earliestExpiry = timestamp;
            }
        }

        if (earliestExpiry != Long.MAX_VALUE) {
            long remainingMillis = earliestExpiry - currentTime;
            if (remainingMillis > 0) {
                long days = remainingMillis / (1000 * 60 * 60 * 24);
                remainingMillis %= (1000 * 60 * 60 * 24);
                long hours = remainingMillis / (1000 * 60 * 60);
                remainingMillis %= (1000 * 60 * 60);
                long minutes = remainingMillis / (1000 * 60);

                List<String> parts = new ArrayList<>();
                if (days > 0) parts.add(days + (days == 1 ? " day" : " days"));
                if (hours > 0) parts.add(hours + (hours == 1 ? " hour" : " hours"));
                if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
                timeUntilExpiry = parts.isEmpty() ? messagesConfig.getString("bounty-status-less-minute", "< 1 minute") : String.join(", ", parts);
            } else {
                timeUntilExpiry = messagesConfig.getString("bounty-status-expired", "Expired");
            }
        }

        // Get number of bounty hunters (unique setters)
        int hunterCount = bounties.size();

        // Build PlaceholderContext
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(targetUUID)
                .pool(totalPool)
                .hunters(hunterCount)
                .frenzy(frenzyMultiplier)
                .boost(boostMultiplier)
                .expiry(timeUntilExpiry);

        // Send consolidated status message
        List<String> statusMessages = messagesConfig.getStringList("bounty-status");
        if (statusMessages.isEmpty()) {
            statusMessages = Arrays.asList(
                    "-----------------------------------",
                    "                    &4&l&nBounty Status",
                    "                 &4| &7Sponsors: &4%sponsors%",
                    "                 &4| &7Top 3 Sponsors: &4%top3_sponsors_numbered%",
                    "                 &4| &7Top 5 Sponsors: &4%top5_sponsors_numbered%",
                    "                 &4| &7Top 10 Sponsors: &4%top10_sponsors_numbered%",
                    "                   &4| &7Pool: &4$%pool%",
                    "                    &4| &7Time til Expiry: &4%expiry%",
                    "                    &4| &7Biggest Enemies (Top 3): &4%top3_sponsors_commas%",
                    "                    &4| &7Biggest Enemies (Top 5): &4%top5_sponsors_commas%",
                    "                    &4| &7Biggest Enemies (Top 10): &4%top10_sponsors_commas%",
                    "-----------------------------------"
            );
        }

        List<String> formattedMessages = Placeholders.apply(statusMessages, context);
        for (String message : formattedMessages) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        // Add a blank line for separation
        player.sendMessage("");

        // Send clickable preview line
        String clickableText = messagesConfig.getString("bounty-status-clickable", "&7[Click to view preview]");
        clickableText = ChatColor.translateAlternateColorCodes('&', clickableText);
        TextComponent clickable = new TextComponent(clickableText);
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bounty preview " + targetName));
        player.spigot().sendMessage(clickable);

        return true;
    }

    /**
     * Handles the /bounty give command // note: Gives a custom item to a player with validation
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        FileConfiguration messagesConfig = plugin.getMessagesConfig();

        // Check permission
        if (!sender.hasPermission("bountiesplus.bounty.give")) {
            String noPermission = messagesConfig.getString("give-no-permission", "&cYou don't have permission to use this command.");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }

        // Usage: /bounty give <player> <item_name> <amount> [<multiplier> <time> <failure>]
        if (args.length < 4) {
            String giveUsage = messagesConfig.getString("give-usage", "&cUsage: /bounty give <player> <item_name> <amount> [<multiplier> <time> <failure>]");
            String availableItems = messagesConfig.getString("give-available-items", "&eAvailable items: tracker, jammer, uav, manual-boost, manual-frenzy, chronos-shard, reverse-bounty");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', giveUsage));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', availableItems));
            return true;
        }

        String playerName = args[1];
        String itemName = args[2].toLowerCase();
        int amount;

        try {
            amount = Integer.parseInt(args[3]);
            if (amount <= 0) {
                String invalidAmount = messagesConfig.getString("give-invalid-amount", "&cAmount must be a positive number.");
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidAmount));
                return true;
            }
        } catch (NumberFormatException e) {
            String invalidAmount = messagesConfig.getString("give-invalid-amount-format", "&cInvalid amount. Please enter a valid number.");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidAmount));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            String playerNotFound = messagesConfig.getString("give-player-not-found", "&cPlayer '%player%' not found or not online.");
            playerNotFound = playerNotFound.replace("%player%", playerName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', playerNotFound));
            return true;
        }

        ItemStack itemToGive = null;

        // Create the appropriate item based on item name
        switch (itemName) {
            case "tracker":
                if (plugin.getTracker() != null) {
                    int maxUses = plugin.getItemsConfig().getInt("tracker.max-uses", 5);
                    itemToGive = plugin.getTracker().createTrackerItem(maxUses);
                }
                break;
            case "jammer":
                if (plugin.getJammer() != null) {
                    itemToGive = plugin.getJammer().createJammerItem();
                }
                break;
            case "uav":
                if (plugin.getUAV() != null) {
                    int maxUses = plugin.getItemsConfig().getInt("uav.max-uses", 3);
                    itemToGive = plugin.getUAV().createUAVItem(maxUses);
                }
                break;
            case "manual-boost":
                if (plugin.getManualBoost() != null) {
                    itemToGive = plugin.getManualBoost().createManualBoostItem();
                }
                break;
            case "manual-frenzy":
                if (plugin.getManualFrenzy() != null) {
                    // Check if additional parameters are provided for manual frenzy
                    if (args.length >= 7) {
                        try {
                            double multiplier = Double.parseDouble(args[4]);
                            int time = Integer.parseInt(args[5]);
                            double failure = Double.parseDouble(args[6]);
                            itemToGive = plugin.getManualFrenzy().createManualFrenzyItem(multiplier, time, failure);
                        } catch (NumberFormatException e) {
                            String invalidParams = messagesConfig.getString("give-invalid-frenzy-params", "&cInvalid parameters for manual-frenzy. Usage: <multiplier> <time> <failure>");
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidParams));
                            return true;
                        }
                    } else {
                        itemToGive = plugin.getManualFrenzy().createManualFrenzyItem();
                    }
                }
                break;
            case "chronos-shard":
                if (plugin.getDecreaseTime() != null) {
                    itemToGive = plugin.getDecreaseTime().createChronosShardItem();
                }
                break;
            case "reverse-bounty":
                if (plugin.getReverseBounty() != null) {
                    itemToGive = plugin.getReverseBounty().createReverseBountyItem();
                }
                break;
            default:
                String unknownItem = messagesConfig.getString("give-unknown-item", "&cUnknown item: %item%");
                String availableItems = messagesConfig.getString("give-available-items", "&eAvailable items: tracker, jammer, uav, manual-boost, manual-frenzy, chronos-shard, reverse-bounty");
                unknownItem = unknownItem.replace("%item%", itemName);
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', unknownItem));
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', availableItems));
                return true;
        }

        if (itemToGive == null) {
            String failedCreate = messagesConfig.getString("give-failed-create", "&cFailed to create item: %item%. The item system may not be initialized.");
            failedCreate = failedCreate.replace("%item%", itemName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', failedCreate));
            return true;
        }

        // Set the amount
        itemToGive.setAmount(amount);

        // Give the item to the player
        HashMap<Integer, ItemStack> overflow = targetPlayer.getInventory().addItem(itemToGive);

        if (!overflow.isEmpty()) {
            String inventoryFull = messagesConfig.getString("give-inventory-full", "&ePlayer's inventory is full. Some items were dropped on the ground.");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', inventoryFull));
            for (ItemStack overflowItem : overflow.values()) {
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), overflowItem);
            }
        }

        // Send success messages
        String giveSuccess = messagesConfig.getString("give-success", "&aSuccessfully gave %amount%x %item% to %player%");
        giveSuccess = giveSuccess.replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemName)
                .replace("%player%", targetPlayer.getName());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', giveSuccess));

        String giveReceived = messagesConfig.getString("give-received", "&aYou received %amount%x %item% from %sender%");
        giveReceived = giveReceived.replace("%amount%", String.valueOf(amount))
                .replace("%item%", itemName)
                .replace("%sender%", sender.getName());
        targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', giveReceived));

        return true;
    }

    private boolean handleFrenzyCommand(CommandSender sender, String[] args, FileConfiguration messagesConfig) {
        // Check permission
        if (!sender.hasPermission("bountiesplus.admin.frenzy")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    messagesConfig.getString("no-permission", "&cYou don't have permission to use this command!")));
            return true;
        }

        // Check if frenzy system is available
        if (plugin.getFrenzy() == null) {
            sender.sendMessage(ChatColor.RED + "Frenzy system is not available!");
            return true;
        }

        // Validate arguments
        if (args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /bounty frenzy <time_in_seconds> <multiplier>");
            sender.sendMessage(ChatColor.GRAY + "Example: /bounty frenzy 60 2.5");
            return true;
        }

        int duration;
        double multiplier;

        try {
            duration = Integer.parseInt(args[1]);
            if (duration <= 0 || duration > 3600) { // Max 1 hour
                sender.sendMessage(ChatColor.RED + "Duration must be between 1 and 3600 seconds!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid duration! Please enter a valid number.");
            return true;
        }

        try {
            multiplier = Double.parseDouble(args[2]);
            if (multiplier <= 1.0 || multiplier > 10.0) { // Reasonable limits
                sender.sendMessage(ChatColor.RED + "Multiplier must be between 1.0 and 10.0!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid multiplier! Please enter a valid number.");
            return true;
        }

        // Check if frenzy is already active
        if (plugin.getFrenzy().isFrenzyActive()) {
            sender.sendMessage(ChatColor.RED + "Frenzy mode is already active! Please wait for it to end.");
            return true;
        }

        // Check if there are bounties to boost
        if (plugin.getBountyManager().listAllBounties().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No bounties available to boost!");
            return true;
        }

        // Check if there are enough online players
        if (Bukkit.getOnlinePlayers().size() < 2) {
            sender.sendMessage(ChatColor.RED + "At least 2 players must be online to start frenzy mode!");
            return true;
        }

        // Activate manual frenzy
        boolean success = plugin.getFrenzy().activateManualFrenzy(multiplier, duration);

        if (success) {
            // Send confirmation to admin
            sender.sendMessage(ChatColor.GREEN + "Frenzy mode activated manually!");
            sender.sendMessage(ChatColor.GRAY + "Duration: " + duration + " seconds");
            sender.sendMessage(ChatColor.GRAY + "Multiplier: " + String.format("%.1fx", multiplier));

            // Log the admin action
            plugin.getLogger().info(sender.getName() + " manually activated frenzy mode (Duration: " + duration + "s, Multiplier: " + multiplier + "x)");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to activate frenzy mode. Please check the console for errors.");
        }

        return true;
    }


    /**
     * Handles the /bounty set command // note: Processes bounty placement with validation and tax handling
     */
    private boolean handleSetCommand(Player player, String[] args, FileConfiguration messagesConfig, FileConfiguration config, boolean allowTime) {
        if (!player.hasPermission("bountiesplus.bounty")) {
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }

        if (args.length < 3 || (allowTime && args.length == 4)) {
            String usageMessage = messagesConfig.getString("bounty-usage", "%prefix%&eUsage: /bounty set <player> <amount>" + (allowTime ? " [<time> <Minutes|Hours|Days>]" : "") + " or /bounty check <player> or /bounty cancel or /bounty stats or /bounty reload");
            usageMessage = usageMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', usageMessage));
            return true;
        }

        if (allowTime && args.length > 5) {
            String usageMessage = messagesConfig.getString("bounty-usage", "%prefix%&eUsage: /bounty set <player> <amount> [<time> <Minutes|Hours|Days>] or /bounty check <player> or /bounty cancel or /bounty stats or /bounty reload");
            usageMessage = usageMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', usageMessage));
            return true;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target == null || !target.hasPlayedBefore()) {
            String playerNotFound = messagesConfig.getString("bounty-player-not-found", "%prefix%&cPlayer &e%target%&c not found.");
            playerNotFound = playerNotFound.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            playerNotFound = playerNotFound.replace("%target%", targetName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerNotFound));
            return true;
        }

        UUID setterUUID = player.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        if (setterUUID.equals(targetUUID)) {
            String setSelf = messagesConfig.getString("bounty-set-self", "%prefix%&cYou cannot place a bounty on yourself.");
            setSelf = setSelf.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', setSelf));
            return true;
        }

        // Check if the setter and target have the same IP address, if restriction is enabled
        boolean restrictSameIP = plugin.getConfig().getBoolean("restrict-same-ip-bounties", true);
        if (restrictSameIP && target.isOnline()) {
            Player targetPlayer = target.getPlayer();
            String setterIP = player.getAddress().getAddress().getHostAddress();
            String targetIP = targetPlayer.getAddress().getAddress().getHostAddress();
            if (setterIP.equals(targetIP)) {
                String sameIP = messagesConfig.getString("bounty-same-ip", "%prefix%&cYou cannot place a bounty on a player with the same IP address.");
                sameIP = sameIP.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', sameIP));
                return true;
            }
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            String invalidAmount = messagesConfig.getString("bounty-invalid-amount", "%prefix%&cInvalid amount: &e%amount%&c! Please enter a positive number.");
            invalidAmount = invalidAmount.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            invalidAmount = invalidAmount.replace("%amount%", args[2]);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidAmount));
            return true;
        }

        if (amount <= 0) {
            String invalidAmount = messagesConfig.getString("bounty-invalid-amount", "%prefix%&cInvalid amount: &e%amount%&c! Please enter a positive number.");
            invalidAmount = invalidAmount.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            invalidAmount = invalidAmount.replace("%amount%", String.valueOf(amount));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidAmount));
            return true;
        }

        // Handle tax via TaxManager
        TaxManager taxManager = plugin.getTaxManager();
        double taxAmount = taxManager.calculateTax(amount, null);
        if (!taxManager.canAffordTax(player, amount, taxAmount)) {
            return true;
        }

        // Deduct money and tax
        if (!taxManager.deductTax(player, amount, taxAmount)) {
            player.sendMessage(ChatColor.RED + "Failed to deduct funds!");
            return true;
        }

        long expireTime = -1;

        if (allowTime && args.length == 5) {
            long timeValue;
            try {
                timeValue = Long.parseLong(args[3]);
                if (timeValue <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                String invalidTime = messagesConfig.getString("bounty-invalid-time", "%prefix%&cInvalid time: &e%time%&c! Please enter a positive number.");
                invalidTime = invalidTime.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
                invalidTime = invalidTime.replace("%time%", args[3]);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidTime));
                return true;
            }

            String unit = args[4].toLowerCase();
            switch (unit) {
                case "m":
                case "minutes":
                    expireTime = System.currentTimeMillis() + timeValue * 60 * 1000;
                    break;
                case "h":
                case "hours":
                    expireTime = System.currentTimeMillis() + timeValue * 60 * 60 * 1000;
                    break;
                case "d":
                case "days":
                    expireTime = System.currentTimeMillis() + timeValue * 24 * 60 * 60 * 1000;
                    break;
                default:
                    String invalidUnit = messagesConfig.getString("bounty-invalid-unit", "%prefix%&cInvalid unit: &e%unit%&c! Use Minutes, Hours, or Days.");
                    invalidUnit = invalidUnit.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
                    invalidUnit = invalidUnit.replace("%unit%", args[4]);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidUnit));
                    return true;
            }
            plugin.getLogger().info("Setting bounty with expireTime: " + expireTime + " (" + args[3] + " " + unit + ")");
            plugin.getLogger().info("Setter UUID: " + setterUUID + ", Target UUID: " + targetUUID);
        }

        plugin.getBountyManager().setBounty(setterUUID, targetUUID, amount, expireTime);
        plugin.reloadAllConfigs();

        // Send tax messages
        taxManager.sendTaxMessages(player, targetUUID, amount, taxAmount);

        if (target.isOnline()) {
            Player targetPlayer = target.getPlayer();
            String receivedMessage = messagesConfig.getString("bounty-received", "%prefix%&cA bounty of &e%amount%&c has been placed on you by &e%from%&c!");
            receivedMessage = receivedMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            receivedMessage = receivedMessage.replace("%amount%", String.valueOf(amount));
            receivedMessage = receivedMessage.replace("%from%", player.getName());
            targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', receivedMessage));

            if (plugin.isBountySoundEnabled()) {
                String soundName = plugin.getBountySoundName();
                float volume = plugin.getBountySoundVolume();
                float pitch = plugin.getBountySoundPitch();

                try {
                    Sound sound = Sound.valueOf(soundName);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (targetPlayer.isOnline()) {
                            targetPlayer.playSound(targetPlayer.getLocation(), sound, volume, pitch);
                        }
                    }, 2L);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (targetPlayer.isOnline()) {
                            targetPlayer.playSound(targetPlayer.getLocation(), sound, volume, pitch);
                        }
                    }, 12L);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound name: " + soundName + " in config.yml");
                } catch (Exception e) {
                    plugin.getLogger().warning("Error playing sound for player " + targetPlayer.getName() + ": " + e.getMessage());
                }
            }
        }
        return true;
    }

    private boolean handleCheckCommand(Player player, String[] args, FileConfiguration messagesConfig) {
        if (!player.hasPermission("bountiesplus.bounty.check")) {
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }

        if (args.length != 2) {
            String usageMessage = messagesConfig.getString("bounty-check-usage", "%prefix%&eUsage: /bounty check <player>");
            usageMessage = usageMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', usageMessage));
            return true;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target == null || !target.hasPlayedBefore()) {
            String playerNotFound = messagesConfig.getString("bounty-player-not-found", "%prefix%&cPlayer &e%target%&c not found.");
            playerNotFound = playerNotFound.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            playerNotFound = playerNotFound.replace("%target%", targetName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerNotFound));
            return true;
        }

        UUID targetUUID = target.getUniqueId();
        Map<UUID, Integer> bounties = plugin.getBountyManager().getBountiesOnTarget(targetUUID);

        if (bounties.isEmpty()) {
            String noBounty = messagesConfig.getString("bounty-none", "%prefix%&7%target% &ahas no active bounties.");
            noBounty = noBounty.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            noBounty = noBounty.replace("%target%", targetName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noBounty));
            return true;
        }

        // List of Sponsors
        List<String> sponsorNames = new ArrayList<>();
        for (UUID sponsorUUID : bounties.keySet()) {
            OfflinePlayer sponsor = Bukkit.getOfflinePlayer(sponsorUUID);
            sponsorNames.add(sponsor.getName());
        }
        String sponsorsList = String.join(", ", sponsorNames);

        // Total bounty pool
        double totalPool = bounties.values().stream().mapToDouble(Integer::doubleValue).sum();
        // Apply individual multipliers
        for (Map.Entry<UUID, Integer> entry : bounties.entrySet()) {
            UUID setterUUID = entry.getKey();
            double multiplier = plugin.getBountyManager().getBountyMultiplier(setterUUID, targetUUID);
            totalPool += entry.getValue() * (multiplier - 1.0);
        }

        // Earliest expiration time
        String timeUntilExpiry = messagesConfig.getString("bounty-status-no-expiration", "No expiration");
        long currentTime = System.currentTimeMillis();
        long earliestExpiry = Long.MAX_VALUE;
        for (UUID sponsorUUID : bounties.keySet()) {
            String expireTime = plugin.getBountyManager().getBountyExpireTime(sponsorUUID, targetUUID);
            if (!expireTime.equals("&4&k|||&4 &4&mDeath Contract&4 &4&k|||")) {
                long timestamp = plugin.getBountiesConfig().getLong("bounties." + targetUUID + "." + sponsorUUID + ".expire_time", -1);
                if (timestamp > 0 && timestamp < earliestExpiry) {
                    earliestExpiry = timestamp;
                }
            }
        }
        if (earliestExpiry != Long.MAX_VALUE) {
            long remainingMillis = earliestExpiry - currentTime;
            if (remainingMillis > 0) {
                long days = remainingMillis / (1000 * 60 * 60 * 24);
                remainingMillis %= (1000 * 60 * 60 * 24);
                long hours = remainingMillis / (1000 * 60 * 60);
                remainingMillis %= (1000 * 60 * 60);
                long minutes = remainingMillis / (1000 * 60);

                List<String> parts = new ArrayList<>();
                if (days > 0) parts.add(days + (days == 1 ? " day" : " days"));
                if (hours > 0) parts.add(hours + (hours == 1 ? " hour" : " hours"));
                if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
                timeUntilExpiry = parts.isEmpty() ? messagesConfig.getString("bounty-status-less-minute", "< 1 minute") : String.join(", ", parts);
            } else {
                timeUntilExpiry = messagesConfig.getString("bounty-status-expired", "Expired");
            }
        }

        // Top 3 Sponsors by bounty amount
        List<Map.Entry<UUID, Integer>> sortedBounties = new ArrayList<>(bounties.entrySet());
        sortedBounties.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> topSponsors = new ArrayList<>();
        for (int i = 0; i < Math.min(3, sortedBounties.size()); i++) {
            OfflinePlayer sponsor = Bukkit.getOfflinePlayer(sortedBounties.get(i).getKey());
            topSponsors.add(sponsor.getName());
        }
        String biggestEnemies = topSponsors.isEmpty() ? messagesConfig.getString("bounty-status-none", "None") : String.join(", ", topSponsors);

        // Send formatted message with customizable format
        String statusHeader = messagesConfig.getString("bounty-status-header", "-----------------------------------");
        String statusTitle = messagesConfig.getString("bounty-status-title", "                    &4&l&nBounty Status");
        String statusSponsors = messagesConfig.getString("bounty-status-sponsors", "                 &4| &7Sponsors: &4%sponsors%");
        String statusPool = messagesConfig.getString("bounty-status-pool", "                   &4| &7Pool: &4%pool%$");
        String statusExpiry = messagesConfig.getString("bounty-status-expiry", "                    &4| &7Time til Expiry: &4%expiry%");
        String statusEnemies = messagesConfig.getString("bounty-status-enemies", "                    &4| &7Biggest Enemies: &4%enemies%");
        String statusFooter = messagesConfig.getString("bounty-status-footer", "-----------------------------------");

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusHeader));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusTitle));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusSponsors.replace("%sponsors%", sponsorsList)));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusPool.replace("%pool%", String.format("%.2f", totalPool))));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusExpiry.replace("%expiry%", timeUntilExpiry)));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusEnemies.replace("%enemies%", biggestEnemies)));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', statusFooter));

        return true;
    }

    private boolean handleCancelCommand(Player player, FileConfiguration messagesConfig) {
        if (!player.hasPermission("bountiesplus.bounty.cancel")) {
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }
        BountyCancel.handleCancelCommand(player, plugin);
        return true;
    }

    private boolean handleStatsCommand(Player player, FileConfiguration messagesConfig) {
        if (!player.hasPermission("bountiesplus.bounty.stats")) {
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }
        BountyStats statsHandler = new BountyStats(plugin);
        return statsHandler.handleStatsCommand(player);
    }

    private boolean handleReloadCommand(Player player, String[] args, FileConfiguration messagesConfig, boolean allowTime) {
        if (!player.hasPermission("bountiesplus.bounty.reload")) {
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }

        if (args.length != 1) {
            String usageMessage = messagesConfig.getString("bounty-usage", "%prefix%&eUsage: /bounty set <player> <amount>" + (allowTime ? " [<time> <Minutes|Hours|Days>]" : "") + " or /bounty check <player> or /bounty cancel or /bounty stats or /bounty reload");
            usageMessage = usageMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', usageMessage));
            return true;
        }

        try {
            // Save bounties before reloading
            plugin.saveEverything();
            // Reload all configurations
            plugin.reloadConfig();
            plugin.reloadAllConfigs();
            plugin.reloadAllConfigs();
            plugin.reloadAllConfigs();
            plugin.reloadAllConfigs();
            // Reload particle and sound configs
            plugin.loadBountyParticleConfig();
            plugin.loadBountySoundConfig();
            plugin.loadBountyGUITitle();

            String successMessage = messagesConfig.getString("bounty-reload-success", "%prefix%&aConfiguration files reloaded successfully.");
            successMessage = successMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMessage));
        } catch (Exception e) {
            String errorMessage = messagesConfig.getString("bounty-reload-error", "%prefix%&cError reloading configuration files: %error%");
            errorMessage = errorMessage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"))
                    .replace("%error%", e.getMessage());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMessage));
            plugin.getLogger().warning("Error reloading configs: " + e.getMessage());
        }
        return true;
    }
}