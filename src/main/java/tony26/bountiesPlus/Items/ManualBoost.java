
package tony26.bountiesPlus.Items;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.VersionUtils;

import java.util.*;

public class ManualBoost implements Listener {

    private final BountiesPlus plugin;
    private final Set<UUID> awaitingPlayerName = new HashSet<>();

    // Configuration values
    private String manualBoostName;
    private List<String> manualBoostLore;
    private String itemIdentifier;
    private double minMultiplier;
    private double maxMultiplier;
    private int minTimeMinutes;
    private int maxTimeMinutes;
    private double minFailureChance;
    private double maxFailureChance;
    private String promptMessage;
    private String cancelMessage;
    private String invalidPlayerMessage;
    private String playerOfflineMessage;
    private String playerNotFoundMessage;
    private String alreadyBoostedMessage;
    private String successMessage;
    private String failureMessage;
    private String serverBroadcastMessage;
    private String cancelCommand;

    public ManualBoost(BountiesPlus plugin) {
        this.plugin = plugin;
        loadConfiguration();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        this.manualBoostName = itemsConfig.getString("manual-boost.item-name", "&d&lManual Boost");
        this.manualBoostLore = itemsConfig.getStringList("manual-boost.item-lore");
        this.itemIdentifier = itemsConfig.getString("manual-boost.item-identifier", "MANUAL_BOOST_ITEM");
        this.minMultiplier = itemsConfig.getDouble("manual-boost.multiplier-range.min-multiplier", 1.5);
        this.maxMultiplier = itemsConfig.getDouble("manual-boost.multiplier-range.max-multiplier", 3.0);
        this.minTimeMinutes = itemsConfig.getInt("manual-boost.time-range.min-minutes", 10);
        this.maxTimeMinutes = itemsConfig.getInt("manual-boost.time-range.max-minutes", 30);
        this.minFailureChance = itemsConfig.getDouble("manual-boost.failure-range.min-chance", 10.0);
        this.maxFailureChance = itemsConfig.getDouble("manual-boost.failure-range.max-chance", 20.0);
        this.promptMessage = itemsConfig.getString("manual-boost.messages.prompt", "&eType the name of the player you want to boost, or type 'cancel' to cancel:");
        this.cancelMessage = itemsConfig.getString("manual-boost.messages.cancel", "&cManual boost cancelled. Item returned to your inventory.");
        this.invalidPlayerMessage = itemsConfig.getString("manual-boost.messages.invalid-player", "&cInvalid player name. Please try again or type 'cancel'.");
        this.playerOfflineMessage = itemsConfig.getString("manual-boost.messages.player-offline", "&cThat player is currently offline. Please choose an online player.");
        this.playerNotFoundMessage = itemsConfig.getString("manual-boost.messages.player-not-found", "&cPlayer not found. Please try again or type 'cancel'.");
        this.alreadyBoostedMessage = itemsConfig.getString("manual-boost.messages.already-boosted", "&cThat player already has an active manual boost!");
        this.successMessage = itemsConfig.getString("manual-boost.messages.success", "&aManual boost applied! &e%target%&a now has a &e%multiplier%x&a boost for &e%time%&a minutes!");
        this.failureMessage = itemsConfig.getString("manual-boost.messages.failure", "&cManual boost failed! The boost application was unsuccessful.");
        this.serverBroadcastMessage = itemsConfig.getString("manual-boost.server-broadcast-message", "&6&l[BOUNTY] &e%player% &6has applied a manual boost to &e%target%&6!");
        this.cancelCommand = itemsConfig.getString("manual-boost.cancel-command", "cancel");

        // Set default lore if none configured
        if (manualBoostLore.isEmpty()) {
            manualBoostLore = new ArrayList<>();
            manualBoostLore.add("&7Right-click to apply a manual boost");
            manualBoostLore.add("&7to a player's bounty rewards");
            manualBoostLore.add("");
            manualBoostLore.add("&eMultiplier Range: &a%min_multiplier%x-&a%max_multiplier%x");
            manualBoostLore.add("&eDuration Range: &a%min_time%-&a%max_time% minutes");
            manualBoostLore.add("&cFailure Chance: &e%min_failure%%-&e%max_failure%%");
            manualBoostLore.add("");
            manualBoostLore.add("&7Perfect for rewarding hunters!");
        }
    }

    /**
     * Creates a new manual boost item // note: Creates an enchanted book item with NBT tag for type
     */
    public ItemStack createManualBoostItem() {
        ItemStack manualBoost = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = manualBoost.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', manualBoostName));
            List<String> lore = new ArrayList<>();
            for (String line : manualBoostLore) {
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
            manualBoost.setItemMeta(meta);
            manualBoost = VersionUtils.setNBTString(manualBoost, "item_type", "manual_boost");
        }

        return manualBoost;
    }

    /**
     * Checks if an item is a manual boost item // note: Verifies item type using NBT tag
     */
    public boolean isManualBoostItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        return "manual_boost".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isManualBoostItem(item)) {
            return;
        }

        event.setCancelled(true);

        // Remove the item from inventory first (consumed regardless)
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        // Add player to waiting list and send prompt
        awaitingPlayerName.add(player.getUniqueId());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', promptMessage));
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!awaitingPlayerName.contains(playerUUID)) {
            return;
        }

        event.setCancelled(true);
        awaitingPlayerName.remove(playerUUID);

        String message = event.getMessage().trim();

        // Check for cancel command
        if (message.equalsIgnoreCase(cancelCommand)) {
            // Return the item to player
            player.getInventory().addItem(createManualBoostItem());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', cancelMessage));
            return;
        }

        // Find the target player
        Player targetPlayer = Bukkit.getPlayer(message);
        if (targetPlayer == null) {
            // Try exact name match with offline players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().equalsIgnoreCase(message)) {
                    targetPlayer = onlinePlayer;
                    break;
                }
            }
        }

        if (targetPlayer == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerNotFoundMessage));
            // Return the item
            player.getInventory().addItem(createManualBoostItem());
            return;
        }

        if (!targetPlayer.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerOfflineMessage));
            // Return the item
            player.getInventory().addItem(createManualBoostItem());
            return;
        }

        // Check if target already has an active manual boost
        if (plugin.getBountyManager().hasActiveManualBoost(targetPlayer.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyBoostedMessage));
            // Return the item
            player.getInventory().addItem(createManualBoostItem());
            return;
        }

        // Determine if the boost should fail
        boolean shouldFail = shouldFail();

        if (shouldFail) {
            // Boost failed - just send failure message
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', failureMessage));
        } else {
            // Success - apply the boost
            // Generate random multiplier and time within ranges
            double multiplier = getRandomMultiplier();
            int timeMinutes = getRandomTimeMinutes();

            // Apply the manual boost (assuming both money and XP boost)
            plugin.getBountyManager().applyManualBoost(targetPlayer.getUniqueId(), multiplier, "both", timeMinutes);

            // Send success message to user
            String successMsg = successMessage
                    .replace("%target%", targetPlayer.getName())
                    .replace("%multiplier%", String.format("%.1f", multiplier))
                    .replace("%time%", String.valueOf(timeMinutes));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));

            // Broadcast server message if configured
            if (serverBroadcastMessage != null && !serverBroadcastMessage.isEmpty()) {
                String broadcast = serverBroadcastMessage
                        .replace("%player%", player.getName())
                        .replace("%target%", targetPlayer.getName());
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcast));
            }

            // Optional: Send notification to target player
            String targetNotification = "&aYou have received a manual boost! Your bounty rewards are now boosted by &e"
                    + String.format("%.1f", multiplier) + "x&a for &e" + timeMinutes + "&a minutes!";
            targetPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', targetNotification));
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
     * Determines if the manual boost should fail based on random chance within range
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
        awaitingPlayerName.clear();
    }

    // Getters for configuration values
    public double getMinMultiplier() { return minMultiplier; }
    public double getMaxMultiplier() { return maxMultiplier; }
    public int getMinTimeMinutes() { return minTimeMinutes; }
    public int getMaxTimeMinutes() { return maxTimeMinutes; }
    public double getMinFailureChance() { return minFailureChance; }
    public double getMaxFailureChance() { return maxFailureChance; }
}