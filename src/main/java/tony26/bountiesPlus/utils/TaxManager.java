package tony26.bountiesPlus.utils;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.BountiesPlus;

import java.util.List;
import java.util.UUID;

/**
 * Manages tax calculations, validations, and deductions for bounty placement // note: Centralizes all tax-related logic for BountiesPlus
 */
public class TaxManager {
    private final BountiesPlus plugin;

    public TaxManager(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculates the tax amount for a bounty // note: Computes tax based on money and optional item value per config settings
     */
    public double calculateTax(double money, List<ItemStack> items) {
        FileConfiguration config = plugin.getConfig();
        double taxRate = config.getDouble("bounty-place-tax-rate", 0.0) / 100.0;
        boolean taxTotalValue = config.getBoolean("tax-total-value", false);
        double itemValue = 0.0;

        if (taxTotalValue && items != null && !items.isEmpty()) {
            ItemValueCalculator calculator = plugin.getItemValueCalculator();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    itemValue += calculator.calculateItemValue(item);
                }
            }
        }

        double taxableAmount = taxTotalValue ? (money + itemValue) : money;
        return taxableAmount * taxRate;
    }

    /**
     * Validates if the player can afford the bounty with tax // note: Checks funds and sends error message if insufficient
     */
    public boolean canAffordTax(Player player, double money, double taxAmount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy == null) {
            player.sendMessage(ChatColor.RED + "Economy system is not available!");
            return false;
        }

        double totalCost = money + taxAmount;
        if (!economy.has(player, totalCost)) {
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String errorMessage = messagesConfig.getString("bounty-insufficient-funds", "&cInsufficient funds! You need $%cost% (includes $%tax% tax)");
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .withAmount(totalCost)
                    .taxAmount(taxAmount);
            player.sendMessage(Placeholders.apply(errorMessage, context));
            return false;
        }

        return true;
    }

    /**
     * Deducts the bounty amount and tax from the player // note: Withdraws total cost using Vault economy
     */
    public boolean deductTax(Player player, double money, double taxAmount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy == null) {
            return false;
        }

        double totalCost = money + taxAmount;
        return economy.withdrawPlayer(player, totalCost).transactionSuccess();
    }

    /**
     * Sends tax-related messages to the player // note: Sends success and tax notification messages with placeholders
     */
    public void sendTaxMessages(Player player, UUID targetUUID, double money, double taxAmount) {
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        double taxRate = plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0);

        // Send success message
        String successMessage = messagesConfig.getString("bounty-set-success", "&aYou placed a bounty of &e%amount%&a on &e%target%&a! Tax of &e%tax%&a was deducted.");
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(targetUUID)
                .withAmount(money)
                .taxAmount(taxAmount);
        player.sendMessage(Placeholders.apply(successMessage, context));

        // Send tax notification if applicable
        if (taxAmount > 0) {
            String taxMessage = messagesConfig.getString("bounty-cancel-tax", "&eA &c%tax_rate%% &etax has been applied. &eTax amount: &c$%tax_amount%");
            context = context.taxRate(taxRate).taxAmount(taxAmount);
            player.sendMessage(Placeholders.apply(taxMessage, context));
        }
    }

    /**
     * Refunds the deducted amount and tax to the player // note: Deposits total cost back to player if deduction fails
     */
    public void refundTax(Player player, double money, double taxAmount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null && (money + taxAmount) > 0) {
            economy.depositPlayer(player, money + taxAmount);
        }
    }
}