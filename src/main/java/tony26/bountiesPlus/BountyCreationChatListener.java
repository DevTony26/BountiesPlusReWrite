package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tony26.bountiesPlus.utils.DebugManager;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;


/**
 * Listens for player chat input during bounty creation
 */
public class BountyCreationChatListener implements Listener {
    private final BountiesPlus plugin;

    public BountyCreationChatListener(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player chat input for bounty creation prompts // note: Processes chat input for various bounty creation steps
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        if (session == null || !session.isAwaitingInput()) {
            DebugManager debugManager = plugin.getDebugManager();
            debugManager.logDebug("No session or not awaiting input for " + player.getName() + ", ignoring chat event");
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage().trim();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Received chat input from " + player.getName() + ": " + input + ", awaiting: " + session.getAwaitingInput().name());

        final PlaceholderContext baseContext = PlaceholderContext.create()
                .player(player)
                .moneyValue(session.getMoney())
                .expValue(session.getExperience())
                .timeValue(session.getFormattedTime())
                .itemCount(session.getItemRewards().size())
                .itemValue(session.getItemRewards().stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum())
                .setter(player.getUniqueId())
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(session.getMoney() * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0));
        final PlaceholderContext context = session.getTargetUUID() != null ? baseContext.target(session.getTargetUUID()) : baseContext;

        final FileConfiguration messagesConfig = plugin.getMessagesConfig();

        // Run all player interactions on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (session.getAwaitingInput()) {
                case MONEY:
                    if (input.equalsIgnoreCase("cancel")) {
                        final String cancelMessage = messagesConfig.getString("money-input-cancelled", "&aMoney input cancelled.");
                        player.sendMessage(Placeholders.apply(cancelMessage, context));
                        session.cancelInputAndReturn();
                        debugManager.logDebug("Money input cancelled by " + player.getName());
                        return;
                    }
                    try {
                        double amount = Double.parseDouble(input);
                        if (amount <= 0) {
                            final String errorMessage = messagesConfig.getString("invalid-money-amount", "&cAmount must be positive!");
                            final String retryMessage = messagesConfig.getString("enter-valid-money", "&aPlease enter a valid amount:");
                            player.sendMessage(Placeholders.apply(errorMessage, context));
                            player.sendMessage(Placeholders.apply(retryMessage, context));
                            debugManager.logDebug("Invalid money amount entered by " + player.getName() + ": " + input);
                            return;
                        }
                        // Check minimum bounty amount
                        double minBountyAmount = plugin.getConfig().getDouble("min-bounty-amount", 100.0);
                        if (amount < minBountyAmount) {
                            final String errorMessage = messagesConfig.getString("invalid-money-amount", "&cAmount must be at least $%bountiesplus_min_amount%!");
                            final String retryMessage = messagesConfig.getString("enter-valid-money", "&aPlease enter a valid amount:");
                            PlaceholderContext minContext = context.withAmount(minBountyAmount);
                            player.sendMessage(Placeholders.apply(errorMessage, minContext));
                            player.sendMessage(Placeholders.apply(retryMessage, context));
                            debugManager.logDebug("Money amount below minimum by " + player.getName() + ": " + amount);
                            return;
                        }
                        // Check if player can afford the amount plus tax
                        TaxManager taxManager = plugin.getTaxManager();
                        double taxAmount = taxManager.calculateTax(amount, session.getItemRewards());
                        if (!taxManager.canAffordTax(player, amount, taxAmount)) {
                            final String retryMessage = messagesConfig.getString("enter-valid-money", "&aPlease enter a valid amount:");
                            player.sendMessage(Placeholders.apply(retryMessage, context));
                            debugManager.logDebug("Insufficient funds for " + player.getName() + ": amount=$" + amount + ", tax=$" + taxAmount);
                            return;
                        }
                        session.addMoney(amount);
                        final String successMessage = messagesConfig.getString("money-set", "&aAdded $%bountiesplus_money_value% to the bounty.");
                        final PlaceholderContext updatedContext = context.withAmount(amount);
                        player.sendMessage(Placeholders.apply(successMessage, updatedContext));
                        session.returnToCreateGUI();
                        debugManager.logDebug("Added $" + amount + " to bounty for " + player.getName());
                    } catch (NumberFormatException e) {
                        final String errorMessage = messagesConfig.getString("invalid-money-format", "&cInvalid amount! Please enter a number or 'cancel'.");
                        final String retryMessage = messagesConfig.getString("enter-valid-money", "&aPlease enter a valid amount:");
                        player.sendMessage(Placeholders.apply(errorMessage, context));
                        player.sendMessage(Placeholders.apply(retryMessage, context));
                        debugManager.logDebug("Invalid money format entered by " + player.getName() + ": " + input);
                    }
                    break;
                case EXPERIENCE:
                    if (input.equalsIgnoreCase("cancel")) {
                        final String cancelMessage = messagesConfig.getString("experience-input-cancelled", "&aExperience input cancelled.");
                        player.sendMessage(Placeholders.apply(cancelMessage, context));
                        session.cancelInputAndReturn();
                        debugManager.logDebug("Experience input cancelled by " + player.getName());
                        return;
                    }
                    try {
                        int levels = Integer.parseInt(input);
                        if (levels <= 0) {
                            final String errorMessage = messagesConfig.getString("invalid-experience-amount", "&cLevels must be positive!");
                            final String retryMessage = messagesConfig.getString("enter-valid-experience", "&aPlease enter a valid number of levels:");
                            player.sendMessage(Placeholders.apply(errorMessage, context));
                            player.sendMessage(Placeholders.apply(retryMessage, context));
                            debugManager.logDebug("Invalid experience amount entered by " + player.getName() + ": " + input);
                            return;
                        }
                        session.addExperience(levels);
                        final String successMessage = messagesConfig.getString("experience-set", "&aAdded %bountiesplus_levels% XP levels to the bounty.");
                        player.sendMessage(Placeholders.apply(successMessage, context));
                        session.returnToCreateGUI();
                        debugManager.logDebug("Added " + levels + " XP levels to bounty for " + player.getName());
                    } catch (NumberFormatException e) {
                        final String errorMessage = messagesConfig.getString("invalid-experience-format", "&cInvalid level! Please enter a number or 'cancel'.");
                        final String retryMessage = messagesConfig.getString("enter-valid-experience", "&aPlease enter a valid number of levels:");
                        player.sendMessage(Placeholders.apply(errorMessage, context));
                        player.sendMessage(Placeholders.apply(retryMessage, context));
                        debugManager.logDebug("Invalid experience format entered by " + player.getName() + ": " + input);
                    }
                    break;
                case TIME:
                    if (input.equalsIgnoreCase("cancel")) {
                        final String cancelMessage = messagesConfig.getString("time-input-cancelled", "&aTime input cancelled.");
                        player.sendMessage(Placeholders.apply(cancelMessage, context));
                        session.cancelInputAndReturn();
                        debugManager.logDebug("Time input cancelled by " + player.getName());
                        return;
                    }
                    try {
                        // Handle inputs like "30m", "2h", "1d", or "30 minutes"
                        String cleanedInput = input.replaceAll("\\s+", "").toLowerCase(); // Remove spaces and normalize
                        if (cleanedInput.matches("\\d+[mhdw]?")) { // Matches number followed by optional m, h, d, w
                            int amount = Integer.parseInt(cleanedInput.replaceAll("[^0-9]", ""));
                            if (amount < 0) {
                                final String errorMessage = messagesConfig.getString("invalid-time-format", "&cInvalid duration! Use format: <number> [m|h|d|w|minutes|hours|days|weeks] or 'cancel'.");
                                final String retryMessage = messagesConfig.getString("set-bounty-duration", "&aType the duration (e.g., '30m', '2h', '1d'):");
                                player.sendMessage(Placeholders.apply(errorMessage, context));
                                player.sendMessage(Placeholders.apply(retryMessage, context));
                                debugManager.logDebug("Negative time amount entered by " + player.getName() + ": " + input);
                                return;
                            }
                            String unit = cleanedInput.replaceAll("[0-9]", "");
                            if (unit.isEmpty()) {
                                unit = amount == 0 ? "" : "minutes"; // Default to minutes, empty for 0 (permanent)
                            }
                            session.setDuration(amount, unit);
                            final String successMessage = messagesConfig.getString("time-set", "&aSet bounty duration to %bountiesplus_duration%.");
                            player.sendMessage(Placeholders.apply(successMessage, context));
                            session.returnToCreateGUI();
                            debugManager.logDebug("Set bounty duration to " + session.getFormattedTime() + " for " + player.getName());
                        } else if (input.matches("\\d+\\s*(minutes|hours|days|weeks|min|hour|day|week)")) { // Handle full units
                            String[] parts = input.split("\\s+");
                            int amount = Integer.parseInt(parts[0]);
                            if (amount < 0) {
                                final String errorMessage = messagesConfig.getString("invalid-time-format", "&cInvalid duration! Use format: <number> [m|h|d|w|minutes|hours|days|weeks] or 'cancel'.");
                                final String retryMessage = messagesConfig.getString("set-bounty-duration", "&aType the duration (e.g., '30m', '2h', '1d'):");
                                player.sendMessage(Placeholders.apply(errorMessage, context));
                                player.sendMessage(Placeholders.apply(retryMessage, context));
                                debugManager.logDebug("Negative time amount entered by " + player.getName() + ": " + input);
                                return;
                            }
                            String unit = parts.length > 1 ? parts[1] : "minutes";
                            session.setDuration(amount, unit);
                            final String successMessage = messagesConfig.getString("time-set", "&aSet bounty duration to %bountiesplus_duration%.");
                            player.sendMessage(Placeholders.apply(successMessage, context));
                            session.returnToCreateGUI();
                            debugManager.logDebug("Set bounty duration to " + session.getFormattedTime() + " for " + player.getName());
                        } else {
                            throw new NumberFormatException("Invalid format");
                        }
                    } catch (NumberFormatException e) {
                        final String errorMessage = messagesConfig.getString("invalid-time-format", "&cInvalid duration! Use format: <number> [m|h|d|w|minutes|hours|days|weeks] or 'cancel'.");
                        final String retryMessage = messagesConfig.getString("set-bounty-duration", "&aType the duration (e.g., '30m', '2h', '1d'):");
                        player.sendMessage(Placeholders.apply(errorMessage, context));
                        player.sendMessage(Placeholders.apply(retryMessage, context));
                        debugManager.logDebug("Invalid time format entered by " + player.getName() + ": " + input);
                    }
                    break;
                case PLAYER_NAME:
                    if (input.equalsIgnoreCase("cancel")) {
                        final String cancelMessage = messagesConfig.getString("player-selection-cancelled", "&aPlayer selection cancelled.");
                        player.sendMessage(Placeholders.apply(cancelMessage, context));
                        session.cancelInputAndReturn();
                        debugManager.logDebug("Player selection cancelled by " + player.getName());
                        return;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(input);
                    if (target.hasPlayedBefore() || target.isOnline()) {
                        if (target.isOnline()) {
                            session.setTargetPlayer(target.getPlayer());
                        } else {
                            session.setTargetPlayerOffline(target);
                        }
                        final String successMessage = messagesConfig.getString("target-set", "&aTarget set to %bountiesplus_target%.");
                        player.sendMessage(Placeholders.apply(successMessage, context));
                        session.returnToCreateGUI();
                        debugManager.logDebug("Target set to " + target.getName() + " for " + player.getName());
                    } else {
                        final String errorMessage = messagesConfig.getString("player-not-found", "&cPlayer not found! Try again or type 'cancel'.");
                        final String retryMessage = messagesConfig.getString("player-selection-header", "&aType the name of the player to set a bounty on:");
                        player.sendMessage(Placeholders.apply(errorMessage, context));
                        player.sendMessage(Placeholders.apply(retryMessage, context));
                        debugManager.logDebug("Player not found for input by " + player.getName() + ": " + input);
                    }
                    break;
                case CANCEL_CONFIRMATION:
                    if (input.equalsIgnoreCase("yes")) {
                        BountyCreationSession.removeSession(player);
                        final String cancelMessage = messagesConfig.getString("bounty-cancelled", "&cBounty creation cancelled.");
                        player.sendMessage(Placeholders.apply(cancelMessage, context));
                        debugManager.logDebug("Bounty creation cancelled by " + player.getName());
                    } else if (input.equalsIgnoreCase("no")) {
                        final String resumeMessage = messagesConfig.getString("bounty-resumed", "&aBounty creation resumed.");
                        player.sendMessage(Placeholders.apply(resumeMessage, context));
                        session.returnToCreateGUI();
                        debugManager.logDebug("Bounty creation resumed by " + player.getName());
                    } else {
                        final String invalidMessage = messagesConfig.getString("invalid-cancel-response", "&cPlease type 'yes' or 'no'.");
                        player.sendMessage(Placeholders.apply(invalidMessage, context));
                        debugManager.logDebug("Invalid cancel response by " + player.getName() + ": " + input);
                    }
                    break;
                case ANONYMOUS_CONFIRMATION:
                    AnonymousBounty anonymousBounty = plugin.getAnonymousBounty();
                    AnonymousBounty.AnonymousSession anonymousSession = anonymousBounty.getPendingSession(player.getUniqueId());
                    if (anonymousSession == null) {
                        final String errorMessage = messagesConfig.getString("anonymous-session-expired", "&cAnonymous bounty session expired. Please start again.");
                        player.sendMessage(Placeholders.apply(errorMessage, context));
                        session.clearAwaitingInput();
                        BountyCreationSession.removeSession(player);
                        debugManager.logDebug("No anonymous session found for " + player.getName());
                        return;
                    }
                    anonymousBounty.processAnonymousInput(player, anonymousSession, input);
                    if (!input.equalsIgnoreCase("cancel")) {
                        session.clearAwaitingInput();
                    }
                    debugManager.logDebug("Processing anonymous input for " + player.getName() + ": " + input);
                    break;
            }
        });
    }

    /**
     * Handles player quit events, cleaning up sessions
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BountyCreationSession.removeSession(event.getPlayer());
        plugin.getLogger().info("Removed session for " + event.getPlayer().getName() + " on quit");
    }
}