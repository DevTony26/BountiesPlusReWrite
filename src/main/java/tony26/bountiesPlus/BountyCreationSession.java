package tony26.bountiesPlus;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.GUIs.CreateGUI;
import tony26.bountiesPlus.utils.CurrencyUtil;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;
import tony26.bountiesPlus.utils.TimeFormatter;

import java.util.*;

/**
 * Manages the creation of a bounty for a player
 * // note: Tracks money, experience, items, time, target, and anonymity for a player's bounty creation process
 */
public class BountyCreationSession {
    private static final Map<UUID, BountyCreationSession> sessions = new HashMap<>();
    private final Player player;
    private double money = 0;
    private int experienceLevels = 0;
    private int timeMinutes = 0;
    private UUID targetUUID = null;
    private String targetName = null;
    private List<ItemStack> itemRewards = new ArrayList<>();
    private boolean confirmPressed = false;
    private InputType awaitingInput = null;
    private final Map<String, String> buttonFailures = new HashMap<>();
    private boolean isGuiActive = false;
    private final long creationTime = System.currentTimeMillis();
    private String lastChatInput = null;
    private long lastChatTimestamp = 0;
    private boolean isAnonymous = false; // Added for anonymous bounty support

    /**
     * Constructs a new bounty creation session for a player
     * // note: Initializes an empty session for bounty creation
     */
    private BountyCreationSession(Player player) {
        this.player = player;
        this.lastChatInput = null;
        this.lastChatTimestamp = 0;
        this.isAnonymous = false;
    }

    /**
     * Checks if the bounty is anonymous
     * // note: Returns true if the bounty is set to be anonymous
     */
    public boolean isAnonymous() {
        return isAnonymous;
    }

    /**
     * Sets the anonymity state of the bounty
     * // note: Updates whether the bounty should hide the setter’s identity
     */
    public void setAnonymous(boolean isAnonymous) {
        this.isAnonymous = isAnonymous;
    }

    /**
     * Enum for tracking input types during bounty creation
     * // note: Defines states for chat input prompts and title displays
     */
    public enum InputType {
        MONEY,
        EXPERIENCE,
        TIME,
        PLAYER_NAME,
        CANCEL_CONFIRMATION,
        ANONYMOUS_CONFIRMATION,
        NO_EXPERIENCE_TITLE,
        NO_MONEY_TITLE,
        CLOSE_WITH_SESSION_TITLE
    }

    /**
     * Gets or creates a bounty creation session for a player
     * // note: Returns existing session or creates a new one if none exists
     */
    public static BountyCreationSession getOrCreateSession(Player player) {
        BountyCreationSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new BountyCreationSession(player);
            sessions.put(player.getUniqueId(), session);
            if (BountiesPlus.getInstance().getConfig().getBoolean("debug-enabled", false)) {
                BountiesPlus.getInstance().getDebugManager().logDebug("[DEBUG] Created new bounty creation session for " + player.getName());
            }
        }
        return session;
    }

    /**
     * Gets an existing bounty creation session for a player
     * // note: Returns the session if it exists, otherwise null
     */
    public static BountyCreationSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * Removes a player's bounty creation session
     * // note: Clears the session from the sessions map
     */
    public static void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
        if (BountiesPlus.getInstance().getConfig().getBoolean("debug-enabled", false)) {
            BountiesPlus.getInstance().getDebugManager().logDebug("[DEBUG] Removed bounty creation session for " + player.getName());
        }
    }

    /**
     * Checks if the session is awaiting input
     * // note: Returns true if the session is waiting for player input (e.g., money, experience)
     */
    public boolean isAwaitingInput() {
        return awaitingInput != null;
    }

    /**
     * Gets the type of input the session is awaiting
     * // note: Returns the current input type or null if not awaiting input
     */
    public InputType getAwaitingInput() {
        return awaitingInput;
    }

    /**
     * Sets the type of input the session is awaiting
     * // note: Updates the session to wait for specific player input
     */
    public void setAwaitingInput(InputType inputType) {
        this.awaitingInput = inputType;
    }

    /**
     * Checks if the session has a target selected
     * // note: Returns true if a target player UUID is set
     */
    public boolean hasTarget() {
        return targetUUID != null;
    }

    /**
     * Gets the target player's UUID
     * // note: Returns the UUID of the selected target player
     */
    public UUID getTargetUUID() {
        return targetUUID;
    }

    /**
     * Gets the target player's name
     * // note: Returns the name of the selected target player
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Sets the target player for the bounty
     * // note: Updates the target UUID and name for the session
     */
    public void setTarget(UUID targetUUID, String targetName) {
        this.targetUUID = targetUUID;
        this.targetName = targetName;
    }

    /**
     * Gets the money amount for the bounty
     * // note: Returns the monetary value set for the bounty
     */
    public double getMoney() {
        return money;
    }

    /**
     * Sets the money amount for the bounty
     * // note: Updates the monetary value and clears related button failures
     */
    public void setMoney(double money) {
        this.money = money;
        clearButtonFailures("money");
    }

    /**
     * Gets the experience levels for the bounty
     * // note: Returns the experience levels set for the bounty
     */
    public int getExperience() {
        return experienceLevels;
    }

    /**
     * Sets the experience levels for the bounty
     * // note: Updates the experience levels and clears related button failures
     */
    public void setExperience(int experienceLevels) {
        this.experienceLevels = experienceLevels;
        clearButtonFailures("experience");
    }

    /**
     * Gets the time duration for the bounty in minutes
     * // note: Returns the duration set for the bounty
     */
    public int getTimeMinutes() {
        return timeMinutes;
    }

    /**
     * Sets the time duration for the bounty in minutes
     * // note: Updates the duration and clears related button failures
     */
    public void setTimeMinutes(int timeMinutes) {
        this.timeMinutes = timeMinutes;
        clearButtonFailures("time");
    }

    /**
     * Checks if the bounty is permanent
     * // note: Returns true if no time duration is set
     */
    public boolean isPermanent() {
        return timeMinutes == 0;
    }

    /**
     * Gets the item rewards for the bounty
     * // note: Returns the list of items set as bounty rewards
     */
    public List<ItemStack> getItemRewards() {
        return new ArrayList<>(itemRewards);
    }

    /**
     * Checks if the session has item rewards
     * // note: Returns true if any items are set as rewards
     */
    public boolean hasItemRewards() {
        return !itemRewards.isEmpty();
    }

    /**
     * Sets the item rewards for the bounty
     * // note: Updates the list of items and clears related button failures
     */
    public void setItemRewards(List<ItemStack> itemRewards) {
        this.itemRewards = itemRewards != null ? new ArrayList<>(itemRewards) : new ArrayList<>();
        clearButtonFailures("items");
    }

    /**
     * Checks if the confirm button was pressed
     * // note: Returns true if the player confirmed the bounty
     */
    public boolean isConfirmPressed() {
        return confirmPressed;
    }

    /**
     * Sets the confirm button pressed state
     * // note: Updates whether the player confirmed the bounty
     */
    public void setConfirmPressed(boolean confirmPressed) {
        this.confirmPressed = confirmPressed;
    }

    /**
     * Checks if the session has changes
     * // note: Returns true if money, experience, time, target, or items are set
     */
    public boolean hasChanges() {
        return money > 0 || experienceLevels > 0 || timeMinutes > 0 || hasTarget() || hasItemRewards();
    }

    /**
     * Validates the bounty for completeness
     * // note: Returns true if the bounty meets all requirements
     */
    public boolean isComplete() {
        return isValid() && (money > 0 || experienceLevels > 0 || hasItemRewards());
    }

    /**
     * Validates the bounty configuration
     * // note: Returns true if the bounty is valid based on config settings
     */
    public boolean isValid() {
        BountiesPlus plugin = BountiesPlus.getInstance();
        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        if (!hasTarget()) {
            return false;
        }

        if (!config.getBoolean("money.allow-zero-dollar-bounties", true) && money == 0 && !hasItemRewards() && experienceLevels == 0) {
            return false;
        }

        if (money < 0 || experienceLevels < 0 || timeMinutes < 0) {
            return false;
        }

        if (config.getBoolean("time.require-time", false) && timeMinutes == 0) {
            return false;
        }

        if (money > config.getDouble("max-bounty-amount", 1000000.0)) {
            return false;
        }

        double totalItemValue = itemRewards.stream()
                .filter(Objects::nonNull)
                .mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item))
                .sum();
        if (money + totalItemValue > config.getDouble("max-bounty-amount", 1000000.0)) {
            return false;
        }

        return true;
    }

    /**
     * Gets the validation error message
     * // note: Returns a message explaining why the bounty is invalid
     */
    public String getValidationError() {
        BountiesPlus plugin = BountiesPlus.getInstance();
        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        if (!hasTarget()) {
            return ChatColor.translateAlternateColorCodes('&',
                    messages.getString("no-target-selected", "&cYou must select a target player!"));
        }

        if (!config.getBoolean("money.allow-zero-dollar-bounties", true) && money == 0 && !hasItemRewards() && experienceLevels == 0) {
            return ChatColor.translateAlternateColorCodes('&',
                    messages.getString("zero-dollar-bounty", "&cBounties must include money, items, or experience!"));
        }

        if (money < 0) {
            return ChatColor.translateAlternateColorCodes('&',
                    messages.getString("negative-money", "&cBounty money cannot be negative!"));
        }

        if (experienceLevels < 0) {
            return ChatColor.translateAlternateColorCodes('&',
                    messages.getString("negative-experience", "&cBounty experience cannot be negative!"));
        }

        if (timeMinutes < 0) {
            return ChatColor.translateAlternateColorCodes('&',
                    messages.getString("negative-time", "&cBounty time cannot be negative!"));
        }

        if (config.getBoolean("time.require-time", false) && timeMinutes == 0) {
            return ChatColor.translateAlternateColorCodes('&',
                    messages.getString("time-required", "&cYou must set a time duration for the bounty!"));
        }

        double maxBountyAmount = config.getDouble("max-bounty-amount", 1000000.0);
        if (money > maxBountyAmount) {
            return ChatColor.translateAlternateColorCodes('&',
                    Placeholders.apply(
                            messages.getString("bounty-invalid-amount", "&cTotal bounty value cannot exceed $%bountiesplus_max_amount%!"),
                            PlaceholderContext.create().withAmount(maxBountyAmount)));
        }

        double totalItemValue = itemRewards.stream()
                .filter(Objects::nonNull)
                .mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item))
                .sum();
        if (money + totalItemValue > maxBountyAmount) {
            return ChatColor.translateAlternateColorCodes('&',
                    Placeholders.apply(
                            messages.getString("bounty-invalid-amount", "&cTotal bounty value cannot exceed $%bountiesplus_max_amount%!"),
                            PlaceholderContext.create().withAmount(maxBountyAmount)));
        }

        return ChatColor.translateAlternateColorCodes('&',
                messages.getString("invalid-bounty", "&cInvalid bounty configuration!"));
    }

    /**
     * Validates if a chat input is unique to prevent duplicate processing
     * // note: Checks if the input is within a 50ms window to debounce duplicates
     */
    public boolean validateChatInput(String input) {
        long currentTime = System.currentTimeMillis();
        if (lastChatInput != null && lastChatInput.equals(input) && (currentTime - lastChatTimestamp < 50)) {
            return false;
        }
        lastChatInput = input;
        lastChatTimestamp = currentTime;
        return true;
    }

    /**
     * Returns the player to the CreateGUI
     * // note: Reopens the CreateGUI for the player
     */
    public void returnToCreateGUI() {
        CreateGUI createGUI = new CreateGUI(player, BountiesPlus.getInstance().getEventManager());
        createGUI.openInventory(player);
    }

    /**
     * Adds a button failure message
     * // note: Stores a failure message for a specific button action
     */
    public void addButtonFailure(String button, String message) {
        buttonFailures.put(button, message);
    }

    /**
     * Clears all button failure messages
     * // note: Removes all failure messages for buttons in the session
     */
    public void clearButtonFailures() {
        buttonFailures.clear();
    }

    /**
     * Clears button failure messages for a specific button
     * // note: Removes failure messages for a specific button
     */
    public void clearButtonFailures(String button) {
        buttonFailures.remove(button);
    }

    /**
     * Gets the button failure messages
     * // note: Returns a list of failure messages for buttons
     */
    public List<String> getButtonFailures() {
        return new ArrayList<>(buttonFailures.values());
    }

    /**
     * Gets whether the GUI is actively open
     * // note: Indicates if the CreateGUI is currently displayed for the player
     */
    public boolean isGuiActive() {
        return isGuiActive;
    }

    /**
     * Sets whether the GUI is actively open
     * // note: Updates the state of the CreateGUI display
     */
    public void setGuiActive(boolean isGuiActive) {
        this.isGuiActive = isGuiActive;
    }

    /**
     * Formats the money amount for display
     * // note: Returns the formatted money value using CurrencyUtil
     */
    public String getFormattedMoney() {
        return CurrencyUtil.formatMoney(money);
    }

    /**
     * Formats the experience levels for display
     * // note: Returns the formatted experience value with appropriate units
     */
    public String getFormattedExperience() {
        return experienceLevels == 0 ? "0 XP Levels" : experienceLevels + " XP Level" + (experienceLevels > 1 ? "s" : "");
    }

    /**
     * Formats the time duration for display
     * // note: Returns the formatted duration using TimeFormatter or 'Permanent' if none set
     */
    public String getFormattedTime() {
        return TimeFormatter.formatMinutesToReadable(timeMinutes, isPermanent());
    }

    /**
     * Adds money to the existing bounty amount
     * // note: Accumulates the money value and clears related button failures
     */
    public void addMoney(double amount) {
        this.money += amount;
        clearButtonFailures("money");
    }

    /**
     * Adds experience levels to the existing bounty
     * // note: Accumulates the experience levels and clears related button failures
     */
    public void addExperience(int levels) {
        this.experienceLevels += levels;
        clearButtonFailures("experience");
    }

    /**
     * Sets the duration based on amount and unit
     * // note: Converts input amount and unit to minutes and updates the session
     */
    public void setDuration(int amount, String unit) {
        int minutes;
        switch (unit.toLowerCase()) {
            case "d":
            case "day":
            case "days":
                minutes = amount * 24 * 60;
                break;
            case "h":
            case "hour":
            case "hours":
            case "hr":
                minutes = amount * 60;
                break;
            case "m":
            case "min":
            case "minutes":
            case "":
                minutes = amount;
                break;
            default:
                minutes = amount; // Default to minutes if unit is unrecognized
                break;
        }
        setTimeMinutes(minutes);
    }

    /**
     * Sets the target player (online)
     * // note: Updates the target UUID and name for an online player
     */
    public void setTargetPlayer(Player target) {
        if (target != null) {
            this.targetUUID = target.getUniqueId();
            this.targetName = target.getName();
        } else {
            this.targetUUID = null;
            this.targetName = null;
        }
    }

    /**
     * Sets the target player (offline)
     * // note: Updates the target UUID and name for an offline player
     */
    public void setTargetPlayerOffline(OfflinePlayer target) {
        if (target != null) {
            this.targetUUID = target.getUniqueId();
            this.targetName = target.getName() != null ? target.getName() : "Unknown";
        } else {
            this.targetUUID = null;
            this.targetName = null;
        }
    }

    /**
     * Clears the awaiting input state
     * // note: Resets the input type to null
     */
    public void clearAwaitingInput() {
        this.awaitingInput = null;
    }

    /**
     * Cancels input and returns to CreateGUI
     * // note: Clears awaiting input and reopens the CreateGUI
     */
    public void cancelInputAndReturn() {
        clearAwaitingInput();
        returnToCreateGUI();
    }
}