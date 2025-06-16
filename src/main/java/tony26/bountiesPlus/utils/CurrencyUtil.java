
package tony26.bountiesPlus.utils;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.BountiesPlus;
import org.bukkit.ChatColor;

import java.util.List;

public class CurrencyUtil {

    // ==================== MONEY OPERATIONS ====================

    /**
     * Check if player has enough money using Vault economy
     */
    public static boolean hasEnoughMoney(Player player, double amount) {
        Economy economy = BountiesPlus.getEconomy();
        return economy != null && economy.has(player, amount);
    }

    /**
     * Remove money from player using Vault economy
     */
    public static boolean removeMoney(Player player, double amount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null && economy.has(player, amount)) {
            return economy.withdrawPlayer(player, amount).transactionSuccess();
        }
        return false;
    }

    /**
     * Add money to player using Vault economy
     */
    public static boolean addMoney(Player player, double amount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null) {
            return economy.depositPlayer(player, amount).transactionSuccess();
        }
        return false;
    }

    /**
     * Get player's current balance
     */
    public static double getBalance(Player player) {
        Economy economy = BountiesPlus.getEconomy();
        return economy != null ? economy.getBalance(player) : 0.0;
    }

    /**
     * Format money amount using Vault economy
     */
    public static String formatMoney(double amount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("$%.2f", amount);
    }

    // ==================== EXPERIENCE OPERATIONS ====================

    /**
     * Check if player has enough XP
     */
    public static boolean hasEnoughXP(Player player, int amount) {
        return getTotalExperience(player) >= amount;
    }

    /**
     * Remove XP from player
     */
    public static void removeExperience(Player player, int amount) {
        int totalExp = getTotalExperience(player);
        totalExp -= amount;

        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);

        if (totalExp > 0) {
            player.giveExp(totalExp);
        }
    }

    /**
     * Add XP to player
     */
    public static void addExperience(Player player, int amount) {
        player.giveExp(amount);
    }

    /**
     * Get total experience including levels
     */
    public static int getTotalExperience(Player player) {
        int exp = Math.round(player.getExp() * player.getExpToLevel());
        int currentLevel = player.getLevel();

        for (int level = 0; level < currentLevel; level++) {
            exp += getExpAtLevel(level);
        }

        return exp;
    }

    /**
     * Get experience required for a specific level
     */
    public static int getExpAtLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    // ==================== BOUNTY SKULL OPERATIONS ====================

    /**
     * Check if item is a bounty skull
     */
    public static boolean isBountySkull(ItemStack item) {
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getLore() == null) {
            return false;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line).toLowerCase();
            if (cleanLine.contains("bounty value:") || cleanLine.contains("skull value:")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract bounty value from skull lore
     */
    public static double extractBountyValueFromSkull(ItemStack skull) {
        if (!isBountySkull(skull)) {
            return 0.0;
        }

        List<String> lore = skull.getItemMeta().getLore();
        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line).toLowerCase();

            // Look for patterns like "bounty value: $123.45" or "skull value: $123.45"
            if (cleanLine.contains("bounty value:") || cleanLine.contains("skull value:")) {
                try {
                    // Extract the number after the colon
                    String[] parts = cleanLine.split(":");
                    if (parts.length >= 2) {
                        String valuePart = parts[1].trim();
                        // Remove currency symbols and parse
                        valuePart = valuePart.replaceAll("[^0-9.]", "");
                        return Double.parseDouble(valuePart);
                    }
                } catch (NumberFormatException e) {
                    // Continue to next line if parsing fails
                }
            }
        }
        return 0.0;
    }

    /**
     * Check if player has enough bounty skulls with minimum value
     */
    public static boolean checkSkullRequirements(Player player, int requiredCount, double minValue) {
        int validSkullCount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isBountySkull(item)) {
                double skullValue = extractBountyValueFromSkull(item);
                if (skullValue >= minValue) {
                    validSkullCount += item.getAmount();
                }
            }
        }

        return validSkullCount >= requiredCount;
    }

    /**
     * Remove required skulls from player inventory
     */
    public static boolean removeSkullsFromInventory(Player player, int requiredCount, double minValue) {
        if (!checkSkullRequirements(player, requiredCount, minValue)) {
            return false;
        }

        int toRemove = requiredCount;

        for (int i = 0; i < player.getInventory().getSize() && toRemove > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (isBountySkull(item)) {
                double skullValue = extractBountyValueFromSkull(item);
                if (skullValue >= minValue) {
                    int stackAmount = item.getAmount();
                    int removeFromStack = Math.min(toRemove, stackAmount);

                    if (removeFromStack >= stackAmount) {
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(stackAmount - removeFromStack);
                    }

                    toRemove -= removeFromStack;
                }
            }
        }

        return toRemove == 0;
    }

    // ==================== VALIDATION HELPERS ====================

    /**
     * Get formatted balance string
     */
    public static String getFormattedBalance(Player player) {
        return formatMoney(getBalance(player));
    }

    /**
     * Get player's current XP level
     */
    public static int getCurrentLevel(Player player) {
        return player.getLevel();
    }

    /**
     * Format XP amount
     */
    public static String formatXP(int amount) {
        return amount + " XP Level" + (amount != 1 ? "s" : "");
    }
}