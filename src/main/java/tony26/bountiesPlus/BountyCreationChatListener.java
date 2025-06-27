package tony26.bountiesPlus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.CreateGUI;
import tony26.bountiesPlus.utils.*;

import java.util.List;
import java.util.UUID;


/**
 * Listens for player chat input during bounty creation
 */
public class BountyCreationChatListener implements Listener {
    private final BountiesPlus plugin;
    private final DebugManager debugManager;

    /**
     * Listens for player chat input during bounty creation
     */
    public BountyCreationChatListener(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.debugManager = plugin.getDebugManager();
        eventManager.register(this);
    }

    /**
     * Handles player chat input for bounty creation
     * // note: Processes input for money, experience, time, player name, and cancellation confirmation
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        if (session == null || !session.isAwaitingInput()) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim().toLowerCase();
        if (input.equals("cancel")) {
            MessageUtils.sendFormattedMessage(player, "money-input-cancelled", PlaceholderContext.create().player(player));
            session.clearAwaitingInput();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                newGui.openInventory(player);
                debugManager.logDebug("[DEBUG - BountyCreationChatListener] Cancelled input and reopened CreateGUI for " + player.getName());
            }, 3L);
            return;
        }

        BountyCreationSession.InputType inputType = session.getAwaitingInput();
        session.clearAwaitingInput(); // Clear input before processing to prevent duplicate handling

        Bukkit.getScheduler().runTask(plugin, () -> {
            PlaceholderContext context = PlaceholderContext.create().player(player).input(input);

            switch (inputType) {
                case MONEY:
                    try {
                        double amount = Double.parseDouble(input);
                        if (amount <= 0) {
                            MessageUtils.sendFormattedMessage(player, "invalid-amount");
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid money amount '" + input + "' by " + player.getName());
                            MessageUtils.sendFormattedMessage(player, "add-money-prompt", context);
                            session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
                            return;
                        }
                        double minBountyAmount = plugin.getConfig().getDouble("min-bounty-amount", 100.0);
                        double maxBountyAmount = plugin.getConfig().getDouble("max-bounty-amount", 1000000.0);
                        TaxManager taxManager = plugin.getTaxManager();
                        double taxAmount = taxManager.calculateTax(amount, session.getItemRewards());
                        double totalCost = amount + taxAmount;
                        Economy economy = BountiesPlus.getEconomy();
                        if (economy == null) {
                            MessageUtils.sendFormattedMessage(player, "bounty-no-economy");
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] No economy plugin for money input by " + player.getName());
                            return;
                        }
                        if (!plugin.getConfig().getBoolean("allow-zero-dollar-bounties", false) && totalCost < minBountyAmount) {
                            context = context.withAmount(minBountyAmount);
                            MessageUtils.sendFormattedMessage(player, "bounty-below-minimum", context);
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Money amount $" + amount + " below minimum $" + minBountyAmount + " for " + player.getName());
                            MessageUtils.sendFormattedMessage(player, "add-money-prompt", context);
                            session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
                            return;
                        }
                        if (totalCost > maxBountyAmount) {
                            MessageUtils.sendFormattedMessage(player, "bounty-above-maximum");
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Money amount $" + amount + " exceeds maximum $" + maxBountyAmount + " for " + player.getName());
                            MessageUtils.sendFormattedMessage(player, "add-money-prompt", context);
                            session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
                            return;
                        }
                        if (!economy.has(player, totalCost)) {
                            context = context.withAmount(totalCost).taxAmount(taxAmount);
                            MessageUtils.sendFormattedMessage(player, "bounty-insufficient-funds", context);
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Insufficient funds for " + player.getName() + ": needs $" + totalCost);
                            MessageUtils.sendFormattedMessage(player, "add-money-prompt", context);
                            session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
                            return;
                        }
                        session.setMoney(amount);
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Set money $" + amount + " for " + player.getName());
                    } catch (NumberFormatException e) {
                        MessageUtils.sendFormattedMessage(player, "invalid-amount");
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid money input '" + input + "' by " + player.getName());
                        MessageUtils.sendFormattedMessage(player, "add-money-prompt", context);
                        session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
                        return;
                    }
                    break;

                case EXPERIENCE:
                    try {
                        int amount = Integer.parseInt(input);
                        if (amount <= 0) {
                            MessageUtils.sendFormattedMessage(player, "invalid-xp-amount");
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid XP amount '" + input + "' by " + player.getName());
                            reopenGui(player);
                            return;
                        }
                        boolean useXpLevels = plugin.getConfig().getBoolean("use-xp-levels", false);
                        int playerXp = useXpLevels ? player.getLevel() : player.getTotalExperience();
                        if (playerXp < amount) {
                            context = context.expValue(amount);
                            MessageUtils.sendFormattedMessage(player, "no-experience-levels", context);
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Insufficient XP for " + player.getName() + ": needs " + amount);
                            reopenGui(player);
                            return;
                        }
                        session.setExperience(amount);
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Set XP " + amount + " for " + player.getName());
                    } catch (NumberFormatException e) {
                        MessageUtils.sendFormattedMessage(player, "invalid-xp-amount");
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid XP input '" + input + "' by " + player.getName());
                        reopenGui(player);
                        return;
                    }
                    break;

                case TIME:
                    try {
                        int minutes = TimeFormatter.parseTimeString(input);
                        if (minutes <= 0) {
                            MessageUtils.sendFormattedMessage(player, "invalid-time");
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid time input '" + input + "' by " + player.getName());
                            reopenGui(player);
                            return;
                        }
                        int minTime = plugin.getConfig().getInt("time.min-time-minutes", 1);
                        if (minutes < minTime) {
                            context = context.time(String.valueOf(minTime));
                            MessageUtils.sendFormattedMessage(player, "time-below-minimum", context);
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Time " + minutes + " minutes below minimum " + minTime + " for " + player.getName());
                            reopenGui(player);
                            return;
                        }
                        session.setTimeMinutes(minutes);
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Set time " + minutes + " minutes for " + player.getName());
                    } catch (IllegalArgumentException e) {
                        MessageUtils.sendFormattedMessage(player, "invalid-time");
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid time input '" + input + "' by " + player.getName());
                        reopenGui(player);
                        return;
                    }
                    break;

                case PLAYER_NAME:
                    String playerName = event.getMessage().trim(); // Use original input for player name
                    OfflinePlayer targetPlayer = null;
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.getName().equalsIgnoreCase(playerName)) {
                            targetPlayer = onlinePlayer;
                            break;
                        }
                    }
                    if (targetPlayer == null && plugin.getConfig().getBoolean("allow-offline-players", true)) {
                        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                                targetPlayer = offlinePlayer;
                                break;
                            }
                        }
                    }
                    if (targetPlayer == null || targetPlayer.getUniqueId().equals(player.getUniqueId()) || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                        MessageUtils.sendFormattedMessage(player, "player-not-found");
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Player '" + playerName + "' not found for " + player.getName());
                        reopenGui(player);
                        return;
                    }
                    if (targetPlayer.isOnline()) {
                        session.setTargetPlayer(targetPlayer.getPlayer());
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Selected online target " + targetPlayer.getName() + " for " + player.getName());
                    } else {
                        session.setTargetPlayerOffline(targetPlayer);
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Selected offline target " + targetPlayer.getName() + " for " + player.getName());
                        MessageUtils.sendFormattedMessage(player, "player-offline");
                    }
                    break;

                case CANCEL_CONFIRMATION:
                    if (input.equals("yes") || input.equals("y")) {
                        // Collect session data for refund
                        double money = session.getMoney();
                        int experience = session.getExperience();
                        List<ItemStack> items = session.getItemRewards();
                        int itemCount = items.size();
                        double itemValue = plugin.getItemValueCalculator().calculateItemsValue(items);

                        // Refund money
                        if (money > 0 && BountiesPlus.getEconomy() != null) {
                            CurrencyUtil.addMoney(player, money);
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Refunded $" + money + " to " + player.getName());
                        }

                        // Refund XP
                        if (experience > 0) {
                            CurrencyUtil.addExperience(player, experience);
                            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Refunded " + experience + " XP to " + player.getName());
                        }

                        // Refund items
                        if (!items.isEmpty()) {
                            for (ItemStack item : items) {
                                if (item != null && item.getType() != Material.AIR) {
                                    plugin.returnItemToPlayer(player, item);
                                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Refunded item " + item.getType().name() + " x" + item.getAmount() + " to " + player.getName());
                                }
                            }
                        }

                        // Send cancellation message with refund details
                        PlaceholderContext contextWithRefunds = PlaceholderContext.create()
                                .player(player)
                                .moneyValue(money)
                                .expValue(experience)
                                .itemCount(itemCount)
                                .itemValue(itemValue);
                        MessageUtils.sendFormattedMessage(player, "bounty-creation-cancelled", contextWithRefunds);
                        BountyCreationSession.removeSession(player);
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Cancelled bounty creation for " + player.getName() + " with refunds: money=$" + money + ", XP=" + experience + ", items=" + itemCount);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            BountyGUI.openBountyGUI(player, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage());
                        }, 3L);
                        return;
                    } else if (input.equals("no") || input.equals("n")) {
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Resumed CreateGUI for " + player.getName() + " after cancel rejection");
                        reopenGui(player);
                        return;
                    } else {
                        MessageUtils.sendFormattedMessage(player, "invalid-cancel-input");
                        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Invalid cancel input '" + input + "' by " + player.getName());
                        reopenGui(player);
                        return;
                    }

                case ANONYMOUS_CONFIRMATION:
                    AnonymousBounty anonymousBounty = plugin.getAnonymousBounty();
                    if (anonymousBounty.isAwaitingAnonymousInput(player)) {
                        AnonymousBounty.AnonymousSession anonymousSession = anonymousBounty.getPendingSession(playerUUID);
                        if (anonymousSession != null) {
                            anonymousBounty.processAnonymousInput(player, anonymousSession, input);
                        } else {
                            debugManager.logWarning("[DEBUG - BountyCreationChatListener] No anonymous session found for " + player.getName());
                        }
                        return;
                    }
                    break;

                default:
                    debugManager.logWarning("[DEBUG - BountyCreationChatListener] Unknown input type " + inputType + " for " + player.getName());
                    reopenGui(player);
                    return;
            }

            // Reopen CreateGUI for valid inputs
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                newGui.openInventory(player);
                debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after processing input");
            }, 3L);
        });
    }

    /**
     * Helper method to reopen CreateGUI after invalid input
     * // note: Schedules GUI reopening with a 3-tick delay
     */
    private void reopenGui(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
            newGui.openInventory(player);
            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after invalid input");
        }, 3L);
    }

    /**
     * Handles player quit events, cleaning up sessions
     * // note: Removes the player's session when they quit
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BountyCreationSession.removeSession(event.getPlayer());
        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Removed session for " + event.getPlayer().getName() + " on quit");
    }
}