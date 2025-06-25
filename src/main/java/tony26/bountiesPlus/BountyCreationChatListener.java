package tony26.bountiesPlus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.CreateGUI;
import tony26.bountiesPlus.utils.*;


/**
 * Listens for player chat input during bounty creation
 */
public class BountyCreationChatListener implements Listener {
    private final BountiesPlus plugin;

    /**
     * Listens for player chat input during bounty creation
     */
    public BountyCreationChatListener(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        eventManager.register(this);
    }

    /**
     * Handles chat input for bounty creation prompts
     * // note: Processes player input for money, experience, time, player name, or cancel confirmation
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        if (session == null || !session.isAwaitingInput()) return;

        event.setCancelled(true); // Prevent chat message from broadcasting
        String input = event.getMessage().trim();
        DebugManager debugManager = plugin.getDebugManager();
        if (!session.validateChatInput(input)) {
            debugManager.logDebug("[DEBUG - BountyCreationChatListener] Ignoring duplicate chat input '" + input + "' from " + player.getName());
            return;
        }
        debugManager.logDebug("[DEBUG - BountyCreationChatListener] Chat input '" + input + "' from " + player.getName() + " for input type " + session.getAwaitingInput().name());

        // Handle cancellation for all input types
        if (input.equalsIgnoreCase("cancel")) {
            String messageKey;
            switch (session.getAwaitingInput()) {
                case MONEY:
                    messageKey = "money-input-cancelled";
                    break;
                case EXPERIENCE:
                    messageKey = "experience-input-cancelled";
                    break;
                case TIME:
                    messageKey = "time-input-cancelled";
                    break;
                case PLAYER_NAME:
                    messageKey = "player-selection-cancelled";
                    break;
                default:
                    messageKey = null;
                    break;
            }
            if (messageKey != null) {
                MessageUtils.sendFormattedMessage(player, messageKey);
                session.clearAwaitingInput();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                    newGui.openInventory(player);
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after cancelling " + messageKey);
                });
            }
            return;
        }

        // Handle MONEY input
        if (session.getAwaitingInput() == BountyCreationSession.InputType.MONEY) {
            try {
                double amount = Double.parseDouble(input);
                double minBountyAmount = plugin.getConfig().getDouble("min-bounty-amount", 100.0);
                boolean allowZeroDollarBounties = plugin.getConfig().getBoolean("allow-zero-dollar-bounties", false);
                if (!allowZeroDollarBounties && amount < minBountyAmount) {
                    MessageUtils.sendFormattedMessage(player, "invalid-money-amount");
                    return;
                }
                Economy economy = BountiesPlus.getEconomy();
                if (economy != null && economy.getBalance(player) < amount) {
                    MessageUtils.sendFormattedMessage(player, "bounty-insufficient-funds");
                    return;
                }
                session.setMoney(amount);
                PlaceholderContext context = PlaceholderContext.create().player(player).moneyValue(amount);
                MessageUtils.sendFormattedMessage(player, "money-set", context);
                session.clearAwaitingInput();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                    newGui.openInventory(player);
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after setting money to " + amount);
                });
            } catch (NumberFormatException e) {
                MessageUtils.sendFormattedMessage(player, "invalid-money-format");
            }
        }
        // Handle EXPERIENCE input
        else if (session.getAwaitingInput() == BountyCreationSession.InputType.EXPERIENCE) {
            try {
                int amount = Integer.parseInt(input);
                if (amount <= 0) {
                    MessageUtils.sendFormattedMessage(player, "invalid-experience-amount");
                    return;
                }
                if (player.getLevel() < amount) {
                    MessageUtils.sendFormattedMessage(player, "no-experience-levels");
                    return;
                }
                session.setExperience(amount);
                PlaceholderContext context = PlaceholderContext.create().player(player).expValue(amount);
                MessageUtils.sendFormattedMessage(player, "experience-set", context);
                session.clearAwaitingInput();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                    newGui.openInventory(player);
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after setting experience to " + amount);
                });
            } catch (NumberFormatException e) {
                MessageUtils.sendFormattedMessage(player, "invalid-experience-format");
            }
        }
        // Handle TIME input
        else if (session.getAwaitingInput() == BountyCreationSession.InputType.TIME) {
            try {
                String timeString = input.toLowerCase().trim();
                int durationMinutes = TimeFormatter.parseTimeString(timeString);
                if (durationMinutes < 0) {
                    MessageUtils.sendFormattedMessage(player, "invalid-time-format");
                    return;
                }
                session.setTimeMinutes(durationMinutes);
                PlaceholderContext context = PlaceholderContext.create().player(player).timeValue(TimeFormatter.formatMinutesToReadable(durationMinutes, durationMinutes == 0));
                MessageUtils.sendFormattedMessage(player, "time-set", context);
                session.clearAwaitingInput();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                    newGui.openInventory(player);
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after setting time to " + durationMinutes + " minutes");
                });
            } catch (IllegalArgumentException e) {
                MessageUtils.sendFormattedMessage(player, "invalid-time-format");
            }
        }
        // Handle PLAYER_NAME input
        else if (session.getAwaitingInput() == BountyCreationSession.InputType.PLAYER_NAME) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            if (!target.hasPlayedBefore()) {
                MessageUtils.sendFormattedMessage(player, "bounty-player-not-found");
                return;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                MessageUtils.sendFormattedMessage(player, "bounty-set-self");
                return;
            }
            session.setTargetPlayerOffline(target);
            if (!target.isOnline()) {
                MessageUtils.sendFormattedMessage(player, "player-offline");
            }
            session.clearAwaitingInput();
            Bukkit.getScheduler().runTask(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                newGui.openInventory(player);
                debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " after selecting target " + target.getName());
            });
        }
        // Handle CANCEL_CONFIRMATION input
        else if (session.getAwaitingInput() == BountyCreationSession.InputType.CANCEL_CONFIRMATION) {
            if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y")) {
                BountyCreationSession.removeSession(player);
                MessageUtils.sendFormattedMessage(player, "bounty-creation-cancelled");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BountyGUI.openBountyGUI(player, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage());
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Cancelled bounty creation for " + player.getName() + ", opened BountyGUI");
                });
            } else if (input.equalsIgnoreCase("no") || input.equalsIgnoreCase("n")) {
                MessageUtils.sendFormattedMessage(player, "bounty-resumed");
                session.clearAwaitingInput();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                    newGui.openInventory(player);
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Resumed CreateGUI for " + player.getName());
                });
            } else {
                MessageUtils.sendFormattedMessage(player, "invalid-cancel-response");
            }
        }
        // Handle ANONYMOUS_CONFIRMATION input
        else if (session.getAwaitingInput() == BountyCreationSession.InputType.ANONYMOUS_CONFIRMATION) {
            AnonymousBounty anonymousBounty = plugin.getAnonymousBounty();
            AnonymousBounty.AnonymousSession anonymousSession = anonymousBounty.getPendingSession(player.getUniqueId());
            if (anonymousSession == null) {
                MessageUtils.sendFormattedMessage(player, "anonymous-prompt-cancelled");
                session.clearAwaitingInput();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                    newGui.openInventory(player);
                    debugManager.logDebug("[DEBUG - BountyCreationChatListener] Reopened CreateGUI for " + player.getName() + " due to missing anonymous session");
                });
            } else {
                anonymousBounty.processAnonymousInput(player, anonymousSession, input);
                session.clearAwaitingInput();
            }
        }
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