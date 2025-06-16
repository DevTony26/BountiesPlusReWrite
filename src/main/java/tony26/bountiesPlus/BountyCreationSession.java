package tony26.bountiesPlus;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import tony26.bountiesPlus.GUIs.CreateGUI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tony26.bountiesPlus.utils.CurrencyUtil;
import tony26.bountiesPlus.utils.ItemValueCalculator;
import tony26.bountiesPlus.utils.TimeFormatter;



/**
 * Manages a player's bounty creation session
 */
public class BountyCreationSession {
    public enum InputType {
        MONEY,
        EXPERIENCE,
        TIME,
        PLAYER_NAME,
        CANCEL_CONFIRMATION,
        ANONYMOUS_CONFIRMATION // Added for anonymous bounty prompt
    }

    private static final Map<UUID, BountyCreationSession> sessions = new HashMap<>();
    private final Player player;
    private double money = 0.0;
    private int experienceLevels = 0;
    private int timeMinutes = 0;
    private InputType awaitingInput = null;
    private Player targetPlayer = null;
    private OfflinePlayer targetOfflinePlayer = null;
    private double calculatedItemValue = 0.0;
    private List<ItemStack> itemRewards = new ArrayList<>();
    private boolean permanent = true;
    private boolean isConfirmPressed = false; // Tracks confirm button press

    public BountyCreationSession(Player player) {
        this.player = player;
    }

    public static void createSession(Player player) {
        sessions.put(player.getUniqueId(), new BountyCreationSession(player));
    }

    public static BountyCreationSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public static BountyCreationSession getOrCreateSession(Player player) {
        BountyCreationSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new BountyCreationSession(player);
            sessions.put(player.getUniqueId(), session);
            player.sendMessage(ChatColor.GREEN + "Bounty creation session started!");
        }
        return session;
    }

    public boolean hasChanges() {
        return money > 0 || experienceLevels > 0 || timeMinutes > 0 || hasTarget() || hasItemRewards();
    }

    public static void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public static boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public double getMoney() {
        return money;
    }

    public void setMoney(double money) {
        this.money = Math.max(0, money);
    }

    public int getExperience() {
        return experienceLevels;
    }

    public void setExperience(int experienceLevels) {
        this.experienceLevels = Math.max(0, experienceLevels);
    }

    public int getTimeMinutes() {
        return timeMinutes;
    }

    public void setTimeMinutes(int timeMinutes) {
        this.timeMinutes = Math.max(0, timeMinutes);
        this.permanent = (timeMinutes == 0);
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
        if (permanent) {
            this.timeMinutes = 0;
        }
    }

    public InputType getAwaitingInput() {
        return awaitingInput;
    }

    public void setAwaitingInput(InputType awaitingInput) {
        this.awaitingInput = awaitingInput;
    }

    public void returnToCreateGUI() {
        returnToCreateGUI(null);
    }

    public void returnToCreateGUI(String message) {
        clearAwaitingInput();
        Bukkit.getScheduler().runTask(BountiesPlus.getInstance(), () -> {
            if (player.getOpenInventory() != null) {
                player.closeInventory();
            }
            Bukkit.getScheduler().runTaskLater(BountiesPlus.getInstance(), () -> {
                new CreateGUI(player).openInventory(player);
                if (message != null && !message.trim().isEmpty()) {
                    player.sendMessage(message);
                }
            }, 2L);
        });
    }

    public void cancelInputAndReturn() {
        returnToCreateGUI(ChatColor.GREEN + "Returned to bounty creation menu.");
    }

    public void cancelInputAndReturn(String message) {
        returnToCreateGUI(message);
    }

    public void clearAwaitingInput() {
        this.awaitingInput = null;
    }

    public boolean isAwaitingInput() {
        return awaitingInput != null;
    }

    public Player getTargetPlayer() {
        return targetPlayer;
    }

    public void setTargetPlayer(Player targetPlayer) {
        this.targetPlayer = targetPlayer;
        this.targetOfflinePlayer = null;
    }

    public OfflinePlayer getTargetOfflinePlayer() {
        return targetOfflinePlayer;
    }

    public void setTargetPlayerOffline(OfflinePlayer targetOfflinePlayer) {
        this.targetOfflinePlayer = targetOfflinePlayer;
        this.targetPlayer = null;
    }

    public boolean hasTarget() {
        return targetPlayer != null || targetOfflinePlayer != null;
    }

    public String getTargetName() {
        if (targetPlayer != null) {
            return targetPlayer.getName();
        } else if (targetOfflinePlayer != null) {
            return targetOfflinePlayer.getName();
        }
        return "None";
    }

    public UUID getTargetUUID() {
        if (targetPlayer != null) {
            return targetPlayer.getUniqueId();
        } else if (targetOfflinePlayer != null) {
            return targetOfflinePlayer.getUniqueId();
        }
        return null;
    }

    public boolean isTargetOnline() {
        if (targetPlayer != null) {
            return targetPlayer.isOnline();
        } else if (targetOfflinePlayer != null) {
            return targetOfflinePlayer.isOnline();
        }
        return false;
    }

    public List<ItemStack> getItemRewards() {
        return new ArrayList<>(itemRewards);
    }

    public void setItemRewards(List<ItemStack> itemRewards) {
        this.itemRewards = new ArrayList<>(itemRewards);
        recalculateItemValue();
    }

    public void addItemReward(ItemStack item) {
        if (item != null && !item.getType().name().equals("AIR")) {
            this.itemRewards.add(item.clone());
            recalculateItemValue();
        }
    }

    public void clearItemRewards() {
        this.itemRewards.clear();
        this.calculatedItemValue = 0.0;
    }

    private void recalculateItemValue() {
        BountiesPlus plugin = BountiesPlus.getInstance();
        if (plugin != null && plugin.getItemValueCalculator() != null) {
            ItemValueCalculator calculator = plugin.getItemValueCalculator();
            this.calculatedItemValue = 0.0;
            for (ItemStack item : itemRewards) {
                if (item != null) {
                    this.calculatedItemValue += calculator.calculateItemValue(item);
                }
            }
        }
    }

    public double getCalculatedItemValue() {
        return calculatedItemValue;
    }

    public String getFormattedItemValue() {
        BountiesPlus plugin = BountiesPlus.getInstance();
        if (plugin != null && plugin.getItemValueCalculator() != null) {
            return CurrencyUtil.formatMoney(calculatedItemValue);
        }
        return String.format("%.2f", calculatedItemValue);
    }

    public int getItemRewardCount() {
        return itemRewards.size();
    }

    public int getTotalItemCount() {
        return itemRewards.stream().mapToInt(ItemStack::getAmount).sum();
    }

    public boolean hasItemRewards() {
        return !itemRewards.isEmpty();
    }

    public String getFormattedTime() {
        return TimeFormatter.formatMinutesToReadable(timeMinutes, permanent);
    }

    public String getFormattedMoney() {
        if (money <= 0) {
            return "$0";
        }
        return "$" + String.format("%.2f", money);
    }

    public String getFormattedExperience() {
        if (experienceLevels == 0) {
            return "0 XP Levels";
        }
        return experienceLevels + " XP Level" + (experienceLevels > 1 ? "s" : "");
    }

    public boolean isComplete() {
        return hasTarget() && hasRewards();
    }

    public boolean hasRewards() {
        return money > 0 || experienceLevels > 0 || hasItemRewards();
    }

    public double getTotalValue() {
        double total = money;
        if (hasItemRewards()) {
            total += calculatedItemValue;
        }
        return total;
    }

    public String getFormattedItems() {
        if (itemRewards.isEmpty()) {
            return "No items";
        }
        int totalCount = getTotalItemCount();
        String valueText = calculatedItemValue > 0 ?
                " (Value: $" + getFormattedItemValue() + ")" : "";
        return totalCount + " item" + (totalCount != 1 ? "s" : "") + valueText;
    }

    public void reset() {
        this.money = 0.0;
        this.experienceLevels = 0;
        this.timeMinutes = 0;
        this.permanent = true;
        this.targetPlayer = null;
        this.targetOfflinePlayer = null;
        this.awaitingInput = null;
        this.itemRewards.clear();
        this.isConfirmPressed = false;
    }

    public Player getPlayer() {
        return player;
    }

    public void addMoney(double amount) {
        this.money += amount;
        if (this.money < 0) {
            this.money = 0;
        }
    }

    public void addExperience(int levels) {
        this.experienceLevels += levels;
        if (this.experienceLevels < 0) {
            this.experienceLevels = 0;
        }
    }

    public void setDuration(int amount, String unit) {
        switch (unit.toLowerCase()) {
            case "minutes":
            case "minute":
            case "min":
            case "m":
                this.timeMinutes = amount;
                break;
            case "hours":
            case "hour":
            case "hr":
            case "h":
                this.timeMinutes = amount * 60;
                break;
            case "days":
            case "day":
            case "d":
                this.timeMinutes = amount * 60 * 24;
                break;
            case "weeks":
            case "week":
            case "w":
                this.timeMinutes = amount * 60 * 24 * 7;
                break;
            default:
                this.timeMinutes = amount;
                break;
        }
        this.permanent = (this.timeMinutes == 0);
    }

    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Bounty Creation Summary ===\n");
        summary.append("Target: ").append(getTargetName());
        if (hasTarget()) {
            summary.append(" (").append(isTargetOnline() ? "Online" : "Offline").append(")\n");
        } else {
            summary.append("\n");
        }
        summary.append("Money Reward: ").append(getFormattedMoney()).append("\n");
        summary.append("Experience Reward: ").append(getFormattedExperience()).append("\n");
        summary.append("Item Rewards: ").append(getFormattedItems()).append("\n");
        summary.append("Duration: ").append(getFormattedTime()).append("\n");
        summary.append("Total Value: ").append(String.format("$%.2f", getTotalValue()));
        return summary.toString();
    }

    public String getValidationError() {
        if (!hasTarget()) {
            return "No target player selected";
        }
        if (!hasRewards()) {
            return "No rewards specified (money, experience, or Items)";
        }
        if (money > 0) {
            net.milkbowl.vault.economy.Economy economy = BountiesPlus.getEconomy();
            if (economy != null && !economy.has(player, money)) {
                return "Insufficient funds (need: " + getFormattedMoney() + ", have: $" +
                        String.format("%.2f", economy.getBalance(player)) + ")";
            }
        }
        if (experienceLevels > 0 && player.getLevel() < experienceLevels) {
            return "Insufficient experience levels (need: " + experienceLevels +
                    ", have: " + player.getLevel() + ")";
        }
        return null;
    }

    public boolean isValid() {
        return getValidationError() == null;
    }

    public BountyCreationSession clone() {
        BountyCreationSession clone = new BountyCreationSession(this.player);
        clone.money = this.money;
        clone.experienceLevels = this.experienceLevels;
        clone.timeMinutes = this.timeMinutes;
        clone.permanent = this.permanent;
        clone.targetPlayer = this.targetPlayer;
        clone.targetOfflinePlayer = this.targetOfflinePlayer;
        clone.itemRewards = new ArrayList<>(this.itemRewards);
        clone.isConfirmPressed = this.isConfirmPressed;
        return clone;
    }

    /**
     * Gets whether the confirm button was pressed
     */
    public boolean isConfirmPressed() {
        return isConfirmPressed;
    }

    /**
     * Sets whether the confirm button was pressed
     */
    public void setConfirmPressed(boolean isConfirmPressed) {
        this.isConfirmPressed = isConfirmPressed;
    }
}