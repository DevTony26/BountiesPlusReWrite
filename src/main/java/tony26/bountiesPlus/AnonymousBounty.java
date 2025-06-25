package tony26.bountiesPlus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.GUIs.CreateGUI;
import tony26.bountiesPlus.utils.MessageUtils;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;
import java.util.*;
import java.util.stream.Collectors;
/**
 * Manages anonymous bounty functionality
 */
public class AnonymousBounty {
    private final BountiesPlus plugin;
    private final Map<UUID, AnonymousSession> pendingSessions = new HashMap<>();

    // Anonymous session data
    public static class AnonymousSession {
        private final BountyCreationSession bountySession;
        private final double anonymousCost;

        public AnonymousSession(BountyCreationSession bountySession, double anonymousCost) {
            this.bountySession = bountySession;
            this.anonymousCost = anonymousCost;
        }

        public BountyCreationSession getBountySession() { return bountySession; }
        public double getAnonymousCost() { return anonymousCost; }
    }

    public AnonymousBounty(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates an anonymous bounty on a target player
     * // note: Sets a hidden bounty with specified rewards and tax
     */
    public void createAnonymousBounty(Player player, UUID targetUUID) {
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        FileConfiguration config = plugin.getConfig();
        double taxRate = config.getDouble("bounty-place-tax-rate", 0.0);
        double taxAmount = session.getMoney() * taxRate;

        BountyManager manager = plugin.getBountyManager();
        Bounty bounty = new Bounty();
        bounty.setTargetUUID(targetUUID);
        bounty.setSponsorUUID(player.getUniqueId());
        bounty.setMoney(session.getMoney());
        bounty.setExpPoints(session.getExpPoints());
        bounty.setRewardItems(session.getRewardItems());
        bounty.setAnonymous(true);
        bounty.setTaxRate(taxRate);
        bounty.setTaxAmount(taxAmount);
        manager.addBounty(bounty);

        FileConfiguration guiConfig = plugin.getCreateGUIConfig();
        FileConfiguration messagesConfig = plugin.getMessagesConfig();

        MessageUtils.sendFormattedMessage(player, "bounty-created");
        session.clearSession();
        player.closeInventory();

        if (guiConfig.getBoolean("confirm-button.confirm-button-filler", false)) {
            new CreateGUI(player, plugin.getEventManager());
        }
    }

    /**
     * Prompts the player to confirm anonymity for a bounty
     * // note: Sends anonymous-bounty-prompt message and sets up chat listener for response
     */
    public void promptForAnonymity(Player player, BountyCreationSession session) {
        plugin.getLogger().info("Prompting " + player.getName() + " for anonymous bounty confirmation");
        // Prevent re-sending prompt if already awaiting anonymous confirmation
        if (session.getAwaitingInput() == BountyCreationSession.InputType.ANONYMOUS_CONFIRMATION) {
            plugin.getDebugManager().logDebug("[DEBUG - AnonymousBounty] Skipping duplicate prompt for " + player.getName() + ": already awaiting ANONYMOUS_CONFIRMATION");
            return;
        }
        double anonymousCost = calculateAnonymousCost(session.getMoney());
        pendingSessions.put(player.getUniqueId(), new AnonymousSession(session, anonymousCost));
        session.setAwaitingInput(BountyCreationSession.InputType.ANONYMOUS_CONFIRMATION);
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(session.getTargetUUID())
                .moneyValue(session.getMoney())
                .expValue(session.getExperience())
                .timeValue(session.getFormattedTime())
                .itemCount(session.getItemRewards().size())
                .itemValue(session.getItemRewards().stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum());
        context = context.setter(player.getUniqueId())
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(session.getMoney() * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .withAmount(anonymousCost); // Add anonymous cost to context
        MessageUtils.sendFormattedMessage(player, "anonymous-bounty-prompt", context);
    }
    /**
     * Calculates the cost for an anonymous bounty based on bounty value
     */
    private double calculateAnonymousCost(double bountyAmount) {
        FileConfiguration config = plugin.getConfig();
        double basePercentage = config.getDouble("anonymous-bounties.base-percentage", 15.0);
        double minCost = config.getDouble("anonymous-bounties.minimum-cost", 50.0);
        double maxCost = config.getDouble("anonymous-bounties.maximum-cost", 5000.0);
        double percentageCost = bountyAmount * (basePercentage / 100.0);
        double finalCost = Math.max(minCost, Math.min(maxCost, percentageCost));
        return Math.round(finalCost * 100.0) / 100.0;
    }

    /**
     * Processes the player's anonymous decision
     */
    public void processAnonymousInput(Player player, AnonymousSession session, String input) {
        UUID playerUUID = player.getUniqueId();
        BountyCreationSession bountySession = session.getBountySession();
        plugin.getLogger().info("Processing anonymous input for " + player.getName() + ": " + input);
        FileConfiguration guiConfig = plugin.getCreateGUIConfig();
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(bountySession.getTargetUUID())
                .moneyValue(bountySession.getMoney())
                .expValue(bountySession.getExperience())
                .timeValue(bountySession.getFormattedTime())
                .itemCount(bountySession.getItemRewards().size())
                .itemValue(bountySession.getItemRewards().stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum());
        context = context.setter(player.getUniqueId())
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(bountySession.getMoney() * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0));
        switch (input) {
            case "yes":
            case "y":
                placeAnonymousBounty(player, session);
                break;
            case "no":
            case "n":
                placeNormalBounty(player, bountySession);
                break;
            case "cancel":
                String cancelMessage = guiConfig.getString("messages.anonymous-prompt-cancelled", "&aReturned to bounty creation GUI.");
                player.sendMessage(Placeholders.apply(cancelMessage, context));
                bountySession.returnToCreateGUI();
                plugin.getLogger().info("Resumed CreateGUI for " + player.getName() + " after cancelling anonymous prompt");
                break;
            default:
                String invalidMessage = guiConfig.getString("messages.invalid-anonymous-input", "&cInvalid input: '&f%input%&c'\n&7Please type &f'yes'&7, &f'y'&7, &f'no'&7, &f'n'&7, or &f'cancel'");
                invalidMessage = invalidMessage.replace("%input%", input);
                player.sendMessage(Placeholders.apply(invalidMessage, context));
                plugin.getLogger().info("Invalid anonymous input by " + player.getName() + ": " + input);
                return;
        }
        if (!input.equals("cancel")) {
            pendingSessions.remove(playerUUID);
            BountyCreationSession.removeSession(player);
        }
    }

    /**
     * Places an anonymous bounty // note: Creates a bounty with money, items, XP, and duration, hiding the setter’s identity
     */
    private void placeAnonymousBounty(Player player, AnonymousSession session) {
        BountyCreationSession bountySession = session.getBountySession();
        double anonymousCost = session.getAnonymousCost();
        Economy economy = BountiesPlus.getEconomy();
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(bountySession.getTargetUUID())
                .moneyValue(bountySession.getMoney())
                .expValue(bountySession.getExperience())
                .timeValue(bountySession.getFormattedTime())
                .itemCount(bountySession.getItemRewards().size())
                .itemValue(bountySession.getItemRewards().stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum());
        context = context.setter(player.getUniqueId())
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(bountySession.getMoney() * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0));
        plugin.getLogger().info("[AnonymousBounty] Attempting to place anonymous bounty by " + player.getName() +
                " on " + bountySession.getTargetName() + ": Money=$" + bountySession.getMoney() +
                ", Items=" + bountySession.getItemRewards().size() + ", XP=" + bountySession.getExperience() +
                ", Duration=" + bountySession.getFormattedTime());
        if (economy != null && !economy.has(player, anonymousCost)) {
            String insufficientMessage = messagesConfig.getString("insufficient-funds-anonymous",
                    "&cInsufficient funds for anonymous bounty!\n" +
                            "&7Required: &a$%cost%\n" +
                            "&7Your balance: &a$%balance%\n" +
                            "&7Placing bounty normally instead...");
            insufficientMessage = insufficientMessage.replace("%cost%", String.format("%.2f", anonymousCost))
                    .replace("%balance%", String.format("%.2f", economy.getBalance(player)));
            player.sendMessage(Placeholders.apply(insufficientMessage, context));
            plugin.getLogger().info("[AnonymousBounty] Insufficient funds for anonymous bounty by " + player.getName() +
                    ": required $" + anonymousCost);
            placeNormalBounty(player, bountySession);
            return;
        }
        if (economy != null) {
            economy.withdrawPlayer(player, anonymousCost);
            plugin.getLogger().info("[AnonymousBounty] Withdrew anonymous fee $" + anonymousCost +
                    " from " + player.getName());
        }
        UUID targetUUID = bountySession.getTargetUUID();
        double bountyAmount = bountySession.getMoney();
        int experience = bountySession.getExperience();
        int durationMinutes = bountySession.getTimeMinutes();
        List<ItemStack> items = new ArrayList<>(bountySession.getItemRewards());
        plugin.getLogger().info("[AnonymousBounty] Items to add: " + items.size() +
                " [" + items.stream().map(item -> item.getType().name() + " x" + item.getAmount())
                .collect(Collectors.joining(", ")) + "]");
        plugin.getBountyManager().addAnonymousBounty(targetUUID, player.getUniqueId(), bountyAmount,
                experience, durationMinutes, items);
        String successMessage = messagesConfig.getString("anonymous-bounty-placed",
                "&a&lAnonymous bounty successfully placed!\n" +
                        "&7Target: &e%bountiesplus_target_name%\n" +
                        "&7Bounty amount: &a$%bountiesplus_money_value%\n" +
                        "&7Items: &b%bountiesplus_item_count% &7(&b$%bountiesplus_item_value%&7)\n" +
                        "&7Experience: &e%bountiesplus_exp_value%\n" +
                        "&7Duration: &d%bountiesplus_time_value%\n" +
                        "&7Anonymous fee: &c$%anonymous_cost%\n" +
                        "&7Your identity will remain hidden from other players.");
        successMessage = successMessage.replace("%anonymous_cost%", String.format("%.2f", anonymousCost));
        player.sendMessage(Placeholders.apply(successMessage, context));
        plugin.getLogger().info("[AnonymousBounty] Anonymous bounty placed by " + player.getName() +
                " on " + bountySession.getTargetName() + " for $" + bountyAmount +
                ", Items=" + items.size() + ", XP=" + experience + ", Duration=" + durationMinutes +
                " minutes (anonymous fee: $" + anonymousCost + ")");
        pendingSessions.remove(player.getUniqueId());
        BountyCreationSession.removeSession(player);
        plugin.getLogger().info("[AnonymousBounty] Cleaned up session for " + player.getName());
    }

    /**
     * Places a normal (non-anonymous) bounty
     * // note: Creates a bounty with money, items, XP, and duration, showing the setter’s identity
     */
    private void placeNormalBounty(Player player, BountyCreationSession session) {
        CreateGUI createGUI = new CreateGUI(player, plugin.getEventManager());
        createGUI.handleConfirmButtonDirect(player, session);
    }

    /**
     * Checks if a player is currently in an anonymous bounty prompt
     */
    public boolean isAwaitingAnonymousInput(Player player) {
        return pendingSessions.containsKey(player.getUniqueId());
    }

    /**
     * Cancels any pending anonymous session for a player
     */
    public void cancelAnonymousSession(Player player) {
        pendingSessions.remove(player.getUniqueId());
    }

    /**
     * Cleans up when plugin disables
     */
    public void cleanup() {
        pendingSessions.clear();
        plugin.getLogger().info("Cleaned up AnonymousBounty sessions");
    }

    /**
     * Retrieves the pending anonymous session for a player
     */
    public AnonymousSession getPendingSession(UUID playerUUID) {
        return pendingSessions.get(playerUUID);
    }
}