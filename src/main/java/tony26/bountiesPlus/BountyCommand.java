
package tony26.bountiesPlus;

import net.md_5.bungee.api.chat.BaseComponent;
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
import tony26.bountiesPlus.GUIs.*;
import tony26.bountiesPlus.utils.*;
import tony26.bountiesPlus.utils.ShopGuiPlusIntegration;

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

        if (args.length == 0) {
            if (config.getBoolean("require-skull-turn-in", true)) {
                BountyGUI.openBountyGUI(player, false, false, 0);
            } else {
                MessageUtils.sendFormattedMessage(player, "bounty-gui-disabled");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "set":
                return handleSetCommand(player, args, config, messagesConfig);
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
                return handleReloadCommand(player, args, messagesConfig, config.getBoolean("allow-time", false));
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
                PreviewGUI previewGUI = new PreviewGUI(player, previewTarget.getUniqueId(), plugin.getEventManager());
                previewGUI.openInventory(player);
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
                TopGUI topGUI = new TopGUI(plugin, plugin.getEventManager());
                topGUI.openTopGUI(player, 0, TopGUI.FilterType.CLAIMED, true);
                return true;
            case "shop":
                return handleShopCommand(player, args);
            default:
                MessageUtils.sendFormattedMessage(player, "bounty-usage");
                return true;
        }
    }

    /**
     * Handles the shop subcommand
     * // note: Opens the Hunters Den shop GUI or ShopGUIPlus shop
     */
    private boolean handleShopCommand(Player player, String[] args) {
        if (!player.hasPermission("bountiesplus.shop")) {
            MessageUtils.sendFormattedMessage(player, "no-permission");
            plugin.getDebugManager().logDebug("[BountyCommand] " + player.getName() + " attempted /bounty shop without permission");
            return true;
        }
        if (!plugin.getConfig().getBoolean("shop.enable-shop", true)) {
            MessageUtils.sendFormattedMessage(player, "shop-disabled");
            plugin.getDebugManager().logDebug("[BountyCommand] " + player.getName() + " attempted /bounty shop but shop is disabled");
            return true;
        }
        ShopGuiPlusIntegration shopIntegration = plugin.getShopGuiPlusIntegration();
        if (shopIntegration != null) {
            shopIntegration.openShop(player);
        } else {
            HunterDenGUI gui = new HunterDenGUI(player, plugin.getEventManager());
            gui.openInventory(player);
            plugin.getDebugManager().logDebug("[BountyCommand] Opened default HunterDenGUI for " + player.getName());
        }
        return true;
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
            PlaceholderContext context = PlaceholderContext.create().player(player).target(targetUUID);
            MessageUtils.sendFormattedMessage(player, "bounty-status-none", context);
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
            BaseComponent[] components = TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message));
            player.spigot().sendMessage(components);
        }

        // Add a blank line for separation
        player.sendMessage("");

        // Send clickable preview line
        String clickableText = messagesConfig.getString("bounty-status-clickable", "&7[Click to view preview]");
        clickableText = Placeholders.apply(clickableText, context);
        TextComponent clickable = new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', clickableText)));
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
     * Handles the /bounty set command
     * // note: Processes bounty placement with validation for amount, time, and permissions
     */
    private boolean handleSetCommand(Player player, String[] args, FileConfiguration config, FileConfiguration messagesConfig) {
        if (args.length < 3) {
            String usage = messagesConfig.getString("bounty-usage", "%prefix%&eUsage: /bounty set <player> <amount> [<time> <Minutes|Hours|Days>]");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', usage.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"))));
            return true;
        }

        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        OfflinePlayer target;
        if (targetPlayer != null) {
            target = targetPlayer;
        } else {
            target = Bukkit.getOfflinePlayer(targetName);
            if (!config.getBoolean("allow-offline-players", true) || !target.hasPlayedBefore()) {
                String notFound = messagesConfig.getString("bounty-player-not-found", "%prefix%&cPlayer &e%target%&c not found.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', notFound.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%target%", targetName)));
                return true;
            }
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            String selfBounty = messagesConfig.getString("bounty-set-self", "%prefix%&cYou cannot place a bounty on yourself.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', selfBounty.replace("%prefix%", messagesConfig.getString("prefix", ""))));
            return true;
        }

        // Check if players are in the same group
        BountyTeamCheck teamCheck = new BountyTeamCheck(plugin);
        if (teamCheck.arePlayersInSameGroup(player, target)) {
            return true; // Error message sent by BountyTeamCheck
        }

        boolean restrictSameIP = config.getBoolean("restrict-same-ip-bounties", true);
        if (restrictSameIP && target.isOnline()) {
            String setterIP = player.getAddress().getAddress().getHostAddress();
            String targetIP = targetPlayer.getAddress().getAddress().getHostAddress();
            if (setterIP.equals(targetIP)) {
                String sameIP = messagesConfig.getString("bounty-same-ip", "%prefix%&cYou cannot place a bounty on a player with the same IP address.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', sameIP.replace("%prefix%", messagesConfig.getString("prefix", ""))));
                return true;
            }
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) {
                String invalidAmount = messagesConfig.getString("bounty-invalid-amount", "%prefix%&cInvalid amount: &e%amount%&c! Please enter a positive number.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidAmount.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%amount%", args[2])));
                return true;
            }
            double minBountyAmount = config.getDouble("money.min-bounty-amount", 100.0);
            double maxBountyAmount = config.getDouble("money.max-bounty-amount", 1000000.0);
            boolean allowZeroDollarBounties = config.getBoolean("money.allow-zero-dollar-bounties", false);
            if (!allowZeroDollarBounties && amount < minBountyAmount) {
                String invalidAmount = messagesConfig.getString("bounty-invalid-amount", "%prefix%&cAmount must be at least $%bountiesplus_min_amount%!");
                PlaceholderContext context = PlaceholderContext.create().player(player).moneyValue(minBountyAmount);
                player.sendMessage(Placeholders.apply(invalidAmount.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
                return true;
            }
            if (amount > maxBountyAmount) {
                String invalidAmount = messagesConfig.getString("bounty-invalid-amount", "%prefix%&cAmount cannot exceed $%bountiesplus_max_amount%!");
                PlaceholderContext context = PlaceholderContext.create().player(player).moneyValue(maxBountyAmount);
                player.sendMessage(Placeholders.apply(invalidAmount.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
                return true;
            }
        } catch (NumberFormatException e) {
            String invalidFormat = messagesConfig.getString("bounty-invalid-amount-format", "%prefix%&cInvalid amount format! Please enter a number.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidFormat.replace("%prefix%", messagesConfig.getString("prefix", ""))));
            return true;
        }

        long expireTime = -1;
        boolean allowTime = config.getBoolean("time.allow-time", false);
        boolean requireTime = config.getBoolean("time.require-time", false);
        int minBountyTime = config.getInt("time.min-bounty-time", 3600);
        int maxBountyTime = config.getInt("time.max-bounty-time", 86400);
        if (allowTime && args.length == 5) {
            try {
                long timeValue = Long.parseLong(args[3]);
                if (timeValue <= 0) {
                    String invalidTime = messagesConfig.getString("bounty-invalid-time", "%prefix%&cInvalid time: &e%time%&c! Please enter a positive number.");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidTime.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%time%", args[3])));
                    return true;
                }
                String unit = args[4].toLowerCase();
                long minutes;
                switch (unit) {
                    case "m":
                    case "minutes":
                        minutes = timeValue;
                        break;
                    case "h":
                    case "hours":
                        minutes = timeValue * 60;
                        break;
                    case "d":
                    case "days":
                        minutes = timeValue * 60 * 24;
                        break;
                    default:
                        String invalidUnit = messagesConfig.getString("bounty-invalid-unit", "%prefix%&cInvalid unit: &e%unit%&c! Use Minutes, Hours, or Days.");
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidUnit.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%unit%", unit)));
                        return true;
                }
                if (minutes < minBountyTime) {
                    String invalidTime = messagesConfig.getString("bounty-invalid-time", "%prefix%&cTime must be at least %bountiesplus_min_time%!");
                    PlaceholderContext context = PlaceholderContext.create().player(player).timeValue(TimeFormatter.formatMinutesToReadable(minBountyTime, false));
                    player.sendMessage(Placeholders.apply(invalidTime.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
                    return true;
                }
                if (minutes > maxBountyTime) {
                    String invalidTime = messagesConfig.getString("bounty-invalid-time", "%prefix%&cTime cannot exceed %bountiesplus_max_time%!");
                    PlaceholderContext context = PlaceholderContext.create().player(player).timeValue(TimeFormatter.formatMinutesToReadable(maxBountyTime, false));
                    player.sendMessage(Placeholders.apply(invalidTime.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
                    return true;
                }
                expireTime = System.currentTimeMillis() + minutes * 60 * 1000;
            } catch (NumberFormatException e) {
                String invalidTime = messagesConfig.getString("bounty-invalid-time", "%prefix%&cInvalid time: &e%time%&c! Please enter a positive number.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', invalidTime.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%time%", args[3])));
                return true;
            }
        } else if (requireTime) {
            String timeRequired = messagesConfig.getString("bounty-time-required", "%prefix%&cA time duration is required for bounties!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', timeRequired.replace("%prefix%", messagesConfig.getString("prefix", ""))));
            return true;
        }

        TaxManager taxManager = plugin.getTaxManager();
        double taxAmount = taxManager.calculateTax(amount, null);
        double totalCost = amount + taxAmount;

        if (!taxManager.canAffordTax(player, amount, taxAmount)) {
            String insufficientFunds = messagesConfig.getString("bounty-insufficient-funds", "%prefix%&cInsufficient funds! You need &e$%cost%&7 (including &e$%tax%&7 tax). Your balance: &e$%vault_eco_balance%");
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .moneyValue(totalCost)
                    .taxAmount(taxAmount);
            player.sendMessage(Placeholders.apply(insufficientFunds.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
            return true;
        }

        if (!taxManager.deductTax(player, amount, taxAmount)) {
            String errorMessage = messagesConfig.getString("bounty-insufficient-funds", "%prefix%&cInsufficient funds! You need &e$%cost%&7 (including &e$%tax%&7 tax). Your balance: &e$%vault_eco_balance%");
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .moneyValue(totalCost)
                    .taxAmount(taxAmount);
            player.sendMessage(Placeholders.apply(errorMessage.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
            return true;
        }

        plugin.getBountyManager().setBounty(player.getUniqueId(), target.getUniqueId(), (int) amount, expireTime);

        taxManager.sendTaxMessages(player, target.getUniqueId(), amount, taxAmount);

        if (target.isOnline()) {
            String received = messagesConfig.getString("bounty-received", "%prefix%&cA bounty of &e%amount%&c has been placed on you by &e%sponsor%&c!");
            PlaceholderContext context = PlaceholderContext.create()
                    .player(targetPlayer)
                    .moneyValue(amount)
                    .setter(player.getUniqueId());
            targetPlayer.sendMessage(Placeholders.apply(received.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
        }

        String successMessage = allowTime && expireTime != -1 ?
                messagesConfig.getString("bounty-set-success-timed", "%prefix%&aYou placed a bounty of &e$%amount%&a on &e%target%&a for &e%time% %unit%&a! Tax of &e$%tax%&a was deducted.") :
                messagesConfig.getString("bounty-set-success", "%prefix%&aYou placed a bounty of &e$%amount%&a on &e%target%&a! Tax of &e$%tax%&a was deducted.");
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(target.getUniqueId())
                .moneyValue(amount)
                .taxAmount(taxAmount);
        if (allowTime && expireTime != -1) {
            long timeValue = (expireTime - System.currentTimeMillis()) / 1000;
            String unit;
            long displayTime;
            if (timeValue >= 24 * 60 * 60) {
                unit = "Days";
                displayTime = timeValue / (24 * 60 * 60);
            } else if (timeValue >= 60 * 60) {
                unit = "Hours";
                displayTime = timeValue / (60 * 60);
            } else {
                unit = "Minutes";
                displayTime = timeValue / 60;
            }
            context = context.time(String.valueOf(displayTime)).unit(unit);
        }
        player.sendMessage(Placeholders.apply(successMessage.replace("%prefix%", messagesConfig.getString("prefix", "")), context));

        if (plugin.isBountySoundEnabled()) {
            try {
                player.getWorld().playSound(player.getLocation(), Sound.valueOf(plugin.getBountySoundName()), plugin.getBountySoundVolume(), plugin.getBountySoundPitch());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name: " + plugin.getBountySoundName());
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

    /**
     * Handles the /bounty stats command
     * // note: Displays a player's bounty statistics
     */
    private boolean handleStatsCommand(Player player, FileConfiguration messagesConfig) {
        if (!player.hasPermission("bountiesplus.bounty.stats")) {
            String noPermission = messagesConfig.getString("no-permission", "%prefix%&cYou do not have permission to use this command.");
            noPermission = noPermission.replace("%prefix%", messagesConfig.getString("prefix", "&4&lBounties &7&l» &7"));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return true;
        }
        BountyStats statsHandler = new BountyStats(plugin);
        return statsHandler.execute(player, new String[]{});
    }

    /**
     * Handles the /bounty reload command
     * // note: Reloads all plugin configurations and systems
     */
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
            // Reload all configurations and systems
            plugin.reloadEverything();
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