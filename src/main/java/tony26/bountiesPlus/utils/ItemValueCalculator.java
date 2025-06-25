package tony26.bountiesPlus.utils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;

import java.util.List;
import java.util.Map;

public class ItemValueCalculator {

    private final BountiesPlus plugin;
    private FileConfiguration itemValueConfig;
    private void loadConfig() {
        this.itemValueConfig = plugin.getItemValueConfig();
    }
    public void reloadConfig() {
        loadConfig();
    }

    public ItemValueCalculator(BountiesPlus plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Calculates the total value of a list of items // note: Computes value for a List<ItemStack> by converting to array
     */
    public double calculateItemsValue(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return 0.0;
        }
        ItemStack[] itemArray = items.stream()
                .filter(item -> item != null && item.getType() != Material.AIR)
                .toArray(ItemStack[]::new);
        return calculateTotalValue(itemArray);
    }

    /**
     * Calculate the total value of an ItemStack including enchantments and custom items // note: Computes value based on material, enchantments, or NBT tags
     */
    public double calculateItemValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0.0;
        }

        double customValue = getCustomItemValue(item);
        if (customValue >= 0) {
            return customValue * item.getAmount();
        }

        double baseValue = getBaseItemValue(item.getType());
        double enchantmentValue = calculateEnchantmentValue(item);
        boolean applyMultiplierToBase = itemValueConfig.getBoolean("calculation.apply_enchantment_multiplier_to_base", true);
        double enchantmentMultiplier = itemValueConfig.getDouble("enchantment-multiplier", 1.5);

        double totalValue;
        if (applyMultiplierToBase && hasEnchantments(item)) {
            totalValue = (baseValue * enchantmentMultiplier) + enchantmentValue;
        } else {
            totalValue = baseValue + enchantmentValue;
        }

        totalValue *= item.getAmount();
        totalValue = applyCalculationConstraints(totalValue, baseValue);

        return totalValue;
    }

    /**
     * Gets the value of a custom item based on NBT tags // note: Retrieves value from items.yml based on item type and uses
     */
    private double getCustomItemValue(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return -1.0;
        }
        String itemType = VersionUtils.getNBTString(item, "item_type");
        if (itemType == null) {
            return -1.0;
        }
        FileConfiguration itemsConfig = plugin.getItemsConfig();
        String path = "custom-items." + itemType + ".value";
        if (!itemsConfig.contains(path)) {
            return -1.0;
        }
        double baseValue = itemsConfig.getDouble(path + ".base", 0.0);
        double perUseValue = itemsConfig.getDouble(path + ".per-use", 0.0);
        if (perUseValue > 0) {
            Integer uses = VersionUtils.getNBTInteger(item, "uses");
            if (uses != null) {
                return baseValue + (perUseValue * uses);
            }
        }
        return baseValue;
    }

    /**
     * Calculate total value of multiple items
     */
    public double calculateTotalValue(ItemStack[] items) {
        double total = 0.0;
        for (ItemStack item : items) {
            if (item != null) {
                total += calculateItemValue(item);
            }
        }
        return total;
    }

    /**
     * Get base value for a material type
     */
    private double getBaseItemValue(Material material) {
        String materialName = material.name();

        // Check specific item values first
        ConfigurationSection itemsSection = itemValueConfig.getConfigurationSection("items");
        if (itemsSection != null && itemsSection.contains(materialName)) {
            return itemsSection.getDouble(materialName);
        }

        // Check legacy names
        ConfigurationSection legacySection = itemValueConfig.getConfigurationSection("legacy_items");
        if (legacySection != null && legacySection.contains(materialName)) {
            return legacySection.getDouble(materialName);
        }

        // Return default value
        return itemValueConfig.getDouble("default-item-value", 10.0);
    }

    /**
     * Calculate total enchantment value for an item
     */
    private double calculateEnchantmentValue(ItemStack item) {
        if (!item.hasItemMeta()) {
            return 0.0;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasEnchants()) {
            return 0.0;
        }

        double totalEnchantmentValue = 0.0;
        Map<Enchantment, Integer> enchantments = meta.getEnchants();

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            double enchantValue = getEnchantmentValue(enchant, level);
            totalEnchantmentValue += enchantValue;
        }

        return totalEnchantmentValue;
    }

    /**
     * Get value for a specific enchantment and level
     */
    private double getEnchantmentValue(Enchantment enchantment, int level) {
        ConfigurationSection enchantSection = itemValueConfig.getConfigurationSection("enchantments");
        if (enchantSection == null) {
            return 0.0;
        }

        String enchantName = getEnchantmentConfigName(enchantment);
        ConfigurationSection specificEnchant = enchantSection.getConfigurationSection(enchantName);

        if (specificEnchant != null) {
            double baseValue = specificEnchant.getDouble("base_value", 0.0);
            double perLevel = specificEnchant.getDouble("per_level", 0.0);
            int maxLevel = specificEnchant.getInt("max_level", level);

            // Cap level at max_level
            int effectiveLevel = Math.min(level, maxLevel);

            return baseValue + (perLevel * effectiveLevel);
        }

        return 0.0;
    }

    /**
     * Convert Bukkit enchantment to config name - Compatible with Java 8/1.8
     */
    private String getEnchantmentConfigName(Enchantment enchantment) {
        // Check legacy mappings first
        ConfigurationSection legacySection = itemValueConfig.getConfigurationSection("legacy_enchantments");
        if (legacySection != null) {
            String bukkitName = enchantment.getName();
            if (legacySection.contains(bukkitName)) {
                return legacySection.getString(bukkitName);
            }
        }

        // For older versions, use the enchantment name directly
        // This is compatible with Minecraft 1.8 and Java 8
        return enchantment.getName().toUpperCase();
    }

    /**
     * Check if item has any enchantments
     */
    private boolean hasEnchantments(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasEnchants();
    }

    /**
     * Apply calculation constraints from config
     */
    private double applyCalculationConstraints(double value, double baseValue) {
        // Apply minimum value
        double minValue = itemValueConfig.getDouble("calculation.minimum_item_value", 1.0);
        if (value < minValue) {
            value = minValue;
        }

        // Apply maximum multiplier
        double maxMultiplier = itemValueConfig.getDouble("calculation.maximum_value_multiplier", 100.0);
        double maxValue = baseValue * maxMultiplier;
        if (value > maxValue) {
            value = maxValue;
        }

        // Apply rounding if enabled
        boolean roundFinalValue = itemValueConfig.getBoolean("calculation.round_final_value", true);
        if (roundFinalValue) {
            value = Math.round(value);
        }

        return value;
    }
}