
package tony26.bountiesPlus.Items;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.EventManager;
import tony26.bountiesPlus.utils.VersionUtils;

import java.util.*;

public class ManualFrenzy implements Listener {

    private final BountiesPlus plugin;

    // Configuration values
    private String manualFrenzyName;
    private List<String> manualFrenzyLore;
    private String itemIdentifier;
    private double minMultiplier;
    private double maxMultiplier;
    private int minTimeMinutes;
    private int maxTimeMinutes;
    private double minFailureChance;
    private double maxFailureChance;
    private String successMessage;
    private String failureMessage;
    private String alreadyActiveMessage;
    private String serverBroadcastMessage;

    /**
     * Initializes the ManualFrenzy system
     * // note: Sets up configuration and registers listener
     */
    public ManualFrenzy(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        loadConfiguration();
        eventManager.register(this);
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        this.manualFrenzyName = itemsConfig.getString("manual-frenzy.item-name", "&c&lManual Frenzy");
        this.manualFrenzyLore = itemsConfig.getStringList("manual-frenzy.item-lore");
        this.itemIdentifier = itemsConfig.getString("manual-frenzy.item-identifier", "MANUAL_FRENZY_ITEM");
        this.minMultiplier = itemsConfig.getDouble("manual-frenzy.multiplier-range.min-multiplier", 2.0);
        this.maxMultiplier = itemsConfig.getDouble("manual-frenzy.multiplier-range.max-multiplier", 4.0);
        this.minTimeMinutes = itemsConfig.getInt("manual-frenzy.time-range.min-minutes", 5);
        this.maxTimeMinutes = itemsConfig.getInt("manual-frenzy.time-range.max-minutes", 15);
        this.minFailureChance = itemsConfig.getDouble("manual-frenzy.failure-range.min-chance", 15.0);
        this.maxFailureChance = itemsConfig.getDouble("manual-frenzy.failure-range.max-chance", 25.0);
        this.successMessage = itemsConfig.getString("manual-frenzy.messages.success", "&aManual frenzy activated! All bounty rewards are now boosted by &e%multiplier%x&a for &e%time%&a minutes!");
        this.failureMessage = itemsConfig.getString("manual-frenzy.messages.failure", "&cManual frenzy failed! The frenzy activation was unsuccessful.");
        this.alreadyActiveMessage = itemsConfig.getString("manual-frenzy.messages.already-active", "&cFrenzy mode is already active!");
        this.serverBroadcastMessage = itemsConfig.getString("manual-frenzy.server-broadcast-message", "&6&l[BOUNTY] &e%player% &6has activated a manual frenzy! All bounty rewards are boosted by &e%multiplier%x&6!");

        // Set default lore if none configured
        if (manualFrenzyLore.isEmpty()) {
            manualFrenzyLore = new ArrayList<>();
            manualFrenzyLore.add("&7Right-click to activate a manual");
            manualFrenzyLore.add("&7frenzy mode for all bounty rewards");
            manualFrenzyLore.add("");
            manualFrenzyLore.add("&eMultiplier Range: &c%min_multiplier%x-&c%max_multiplier%x");
            manualFrenzyLore.add("&eDuration Range: &c%min_time%-&c%max_time% minutes");
            manualFrenzyLore.add("&cFailure Chance: &e%min_failure%%-&e%max_failure%%");
            manualFrenzyLore.add("");
            manualFrenzyLore.add("&cAffects ALL bounty kills during duration!");
            manualFrenzyLore.add("&7Unleash chaos upon the server!");
        }
    }
    /**
     * Creates a new manual frenzy item with specified parameters // note: Creates a blaze powder item with custom multiplier, time, and failure chance
     */
    public ItemStack createManualFrenzyItem(double multiplier, int timeMinutes, double failureChance) {
        ItemStack manualFrenzy = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = manualFrenzy.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', manualFrenzyName));
            List<String> lore = new ArrayList<>();
            for (String line : manualFrenzyLore) {
                String processedLine = line
                        .replace("%multiplier%", String.format("%.1f", multiplier))
                        .replace("%time%", String.valueOf(timeMinutes))
                        .replace("%failure%", String.format("%.1f", failureChance));
                lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);
            manualFrenzy.setItemMeta(meta);
            manualFrenzy = VersionUtils.setNBTString(manualFrenzy, "item_type", "manual_frenzy");
            manualFrenzy = VersionUtils.setNBTDouble(manualFrenzy, "multiplier", multiplier);
            manualFrenzy = VersionUtils.setNBTInteger(manualFrenzy, "time_minutes", timeMinutes);
            manualFrenzy = VersionUtils.setNBTDouble(manualFrenzy, "failure_chance", failureChance);
        }

        return manualFrenzy;
    }

    /**
     * Creates a new manual frenzy item with default configuration values // note: Creates a blaze powder item using defaults from items.yml
     */
    public ItemStack createManualFrenzyItem() {
        ItemStack manualFrenzy = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = manualFrenzy.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', manualFrenzyName));
            List<String> lore = new ArrayList<>();
            for (String line : manualFrenzyLore) {
                String processedLine = line
                        .replace("%min_multiplier%", String.format("%.1f", minMultiplier))
                        .replace("%max_multiplier%", String.format("%.1f", maxMultiplier))
                        .replace("%min_time%", String.valueOf(minTimeMinutes))
                        .replace("%max_time%", String.valueOf(maxTimeMinutes))
                        .replace("%min_failure%", String.format("%.1f", minFailureChance))
                        .replace("%max_failure%", String.format("%.1f", maxFailureChance));
                lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);
            manualFrenzy.setItemMeta(meta);
            manualFrenzy = VersionUtils.setNBTString(manualFrenzy, "item_type", "manual_frenzy");
            manualFrenzy = VersionUtils.setNBTDouble(manualFrenzy, "multiplier", minMultiplier);
            manualFrenzy = VersionUtils.setNBTInteger(manualFrenzy, "time_minutes", minTimeMinutes);
            manualFrenzy = VersionUtils.setNBTDouble(manualFrenzy, "failure_chance", minFailureChance);
        }

        return manualFrenzy;
    }

    /**
     * Checks if an item is a manual frenzy item // note: Verifies item type using NBT tag
     */
    public boolean isManualFrenzyItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        return "manual_frenzy".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isManualFrenzyItem(item)) {
            return;
        }

        event.setCancelled(true);

        // Check if frenzy is already active
        if (plugin.getFrenzy().isFrenzyActive()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyActiveMessage));
            return;
        }

        // Remove the item from inventory first (consumed regardless)
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        // Determine if the frenzy should fail
        boolean shouldFail = shouldFail();

        if (shouldFail) {
            // Frenzy failed - just send failure message
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', failureMessage));
        } else {
            // Success - activate manual frenzy
            // Generate random multiplier and time within ranges
            double multiplier = getRandomMultiplier();
            int timeMinutes = getRandomTimeMinutes();

            // Activate manual frenzy mode
            activateManualFrenzy(multiplier, timeMinutes);

            // Send success message to user
            String successMsg = successMessage
                    .replace("%multiplier%", String.format("%.1f", multiplier))
                    .replace("%time%", String.valueOf(timeMinutes));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));

            // Broadcast server message if configured
            if (serverBroadcastMessage != null && !serverBroadcastMessage.isEmpty()) {
                String broadcast = serverBroadcastMessage
                        .replace("%player%", player.getName())
                        .replace("%multiplier%", String.format("%.1f", multiplier));
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcast));
            }
        }
    }

    /**
     * Activates manual frenzy mode using the existing Frenzy system
     */
    private void activateManualFrenzy(double multiplier, int timeMinutes) {
        // Convert minutes to seconds for the Frenzy system
        int timeSeconds = timeMinutes * 60;

        // Use the existing activateManualFrenzy method from Frenzy class
        boolean success = plugin.getFrenzy().activateManualFrenzy(multiplier, timeSeconds);

        if (!success) {
            plugin.getLogger().warning("Failed to activate manual frenzy - frenzy system unavailable");
        }

    }

    /**
     * Gets a random multiplier within the configured range
     */
    private double getRandomMultiplier() {
        Random random = new Random();
        return minMultiplier + (random.nextDouble() * (maxMultiplier - minMultiplier));
    }

    /**
     * Gets a random time in minutes within the configured range
     */
    private int getRandomTimeMinutes() {
        Random random = new Random();
        return minTimeMinutes + random.nextInt(maxTimeMinutes - minTimeMinutes + 1);
    }

    /**
     * Determines if the manual frenzy should fail based on random chance within range
     */
    private boolean shouldFail() {
        Random random = new Random();
        double failureChance = minFailureChance + (maxFailureChance - minFailureChance) * random.nextDouble();
        double roll = random.nextDouble() * 100.0;

        return roll < failureChance;
    }

    /**
     * Clean up when plugin disables
     */
    public void cleanup() {
        // No cleanup needed for this item
    }

    // Getters for configuration values
    public double getMinMultiplier() { return minMultiplier; }
    public double getMaxMultiplier() { return maxMultiplier; }
    public int getMinTimeMinutes() { return minTimeMinutes; }
    public int getMaxTimeMinutes() { return maxTimeMinutes; }
    public double getMinFailureChance() { return minFailureChance; }
    public double getMaxFailureChance() { return maxFailureChance; }
}