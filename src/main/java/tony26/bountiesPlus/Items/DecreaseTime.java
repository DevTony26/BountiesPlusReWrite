
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

public class DecreaseTime implements Listener {

    private final BountiesPlus plugin;

    // Configuration values
    private String chronosShardName;
    private List<String> chronosShardLore;
    private String itemIdentifier;
    private String noBountyMessage;
    private String successMessage;
    private String failureMessage;
    private String failureWithTimeMessage;
    private String serverBroadcastMessage;
    private double minDecreasePercent;
    private double maxDecreasePercent;
    private double minFailureChance;
    private double maxFailureChance;
    private boolean failureAddsTime;
    private double failureIncreasePercent;

    /**
     * Initializes the DecreaseTime system
     * // note: Sets up configuration and registers listener
     */
    public DecreaseTime(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        loadConfiguration();
        eventManager.register(this);
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        this.chronosShardName = itemsConfig.getString("chronos-shard.item-name", "&b&lChronos Shard");
        this.chronosShardLore = itemsConfig.getStringList("chronos-shard.item-lore");
        this.itemIdentifier = itemsConfig.getString("chronos-shard.item-identifier", "CHRONOS_SHARD_ITEM");
        this.noBountyMessage = itemsConfig.getString("chronos-shard.messages.no-bounty", "&cYou have no bounty to decrease!");
        this.successMessage = itemsConfig.getString("chronos-shard.messages.success", "&aChronos Shard activated! Your bounty time was decreased by &e%percentage%&a%!");
        this.failureMessage = itemsConfig.getString("chronos-shard.messages.failure", "&cChronos Shard failed! The time manipulation was unsuccessful.");
        this.failureWithTimeMessage = itemsConfig.getString("chronos-shard.messages.failure-with-time", "&cChronos Shard backfired! Your bounty time was increased by &e%percentage%&c%!");
        this.serverBroadcastMessage = itemsConfig.getString("chronos-shard.server-broadcast-message", "&6&l[BOUNTY] &e%player% &6used a Chronos Shard and decreased their bounty time!");
        this.minDecreasePercent = itemsConfig.getDouble("chronos-shard.decrease-range.min-percent", 5.0);
        this.maxDecreasePercent = itemsConfig.getDouble("chronos-shard.decrease-range.max-percent", 15.0);

        // Load failure range settings
        this.minFailureChance = itemsConfig.getDouble("chronos-shard.failure-range.min-chance", 15.0);
        this.maxFailureChance = itemsConfig.getDouble("chronos-shard.failure-range.max-chance", 25.0);

        this.failureAddsTime = itemsConfig.getBoolean("chronos-shard.failure.adds-time", true);
        this.failureIncreasePercent = itemsConfig.getDouble("chronos-shard.failure.increase-percent", 10.0);

        // Set default lore if none configured
        if (chronosShardLore.isEmpty()) {
            chronosShardLore = new ArrayList<>();
            chronosShardLore.add("&7Right-click to manipulate time and");
            chronosShardLore.add("&7decrease your bounty duration");
            chronosShardLore.add("");
            chronosShardLore.add("&eDecrease Range: &a%min_decrease%%-&a%max_decrease%%");
            chronosShardLore.add("&cFailure Chance: &e%min_failure%%-&e%max_failure%%");
            if (failureAddsTime) {
                chronosShardLore.add("&cFailure Effect: &e+%failure_increase%% time");
            } else {
                chronosShardLore.add("&cFailure Effect: &eNo penalty");
            }
            chronosShardLore.add("");
            chronosShardLore.add("&7Harness the power of time itself!");
        }
    }

    /**
     * Creates a new Chronos Shard item // note: Creates a prismarine shard item with NBT tag for type
     */
    public ItemStack createChronosShardItem() {
        ItemStack chronosShard = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = chronosShard.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', chronosShardName));
            List<String> lore = new ArrayList<>();
            for (String line : chronosShardLore) {
                String processedLine = line
                        .replace("%min_decrease%", String.format("%.1f", minDecreasePercent))
                        .replace("%max_decrease%", String.format("%.1f", maxDecreasePercent))
                        .replace("%min_failure%", String.format("%.1f", minFailureChance))
                        .replace("%max_failure%", String.format("%.1f", maxFailureChance))
                        .replace("%failure_increase%", String.format("%.1f", failureIncreasePercent));
                lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);
            chronosShard.setItemMeta(meta);
            chronosShard = VersionUtils.setNBTString(chronosShard, "item_type", "chronos_shard");
        }

        return chronosShard;
    }

    /**
     * Checks if an item is a Chronos Shard item // note: Verifies item type using NBT tag
     */
    public boolean isChronosShardItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        return "chronos_shard".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isChronosShardItem(item)) {
            return;
        }

        event.setCancelled(true);

        // Check if player has a bounty
        if (!plugin.getBountyManager().hasBounty(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noBountyMessage));
            return;
        }

        // Remove the item from inventory
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        // Determine if the shard should fail
        boolean shouldFail = shouldFail();

        if (shouldFail) {
            if (failureAddsTime) {
                // Increase bounty time by failure percentage
                increaseAllBountyTimes(player, failureIncreasePercent);
                String message = failureWithTimeMessage.replace("%percentage%", String.format("%.1f", failureIncreasePercent));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            } else {
                // Just fail without penalty
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', failureMessage));
            }
        } else {
            // Success - decrease bounty time
            double decreasePercent = getRandomDecreasePercent();
            decreaseAllBountyTimes(player, decreasePercent);

            String message = successMessage.replace("%percentage%", String.format("%.1f", decreasePercent));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

            // Broadcast to server if configured
            if (serverBroadcastMessage != null && !serverBroadcastMessage.isEmpty()) {
                String broadcast = serverBroadcastMessage.replace("%player%", player.getName());
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcast));
            }
        }
    }

    /**
     * Decreases all bounty times for a player by the specified percentage
     */
    private void decreaseAllBountyTimes(Player player, double percentage) {
        UUID playerUUID = player.getUniqueId();
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();

        if (!allBounties.containsKey(playerUUID)) {
            return;
        }

        Map<UUID, Integer> playerBounties = allBounties.get(playerUUID);
        boolean modified = false;

        // Get expire times for this player's bounties
        for (UUID setterUUID : playerBounties.keySet()) {
            String expireTime = plugin.getBountyManager().getBountyExpireTime(setterUUID, playerUUID);
            if (expireTime != null && !expireTime.equals("Never")) {
                try {
                    long expireTimeMs = Long.parseLong(expireTime);
                    long currentTime = System.currentTimeMillis();
                    long remainingTime = expireTimeMs - currentTime;

                    if (remainingTime > 0) {
                        // Calculate the new remaining time (decreased by percentage)
                        long decreaseAmount = (long) (remainingTime * (percentage / 100.0));
                        long newRemainingTime = remainingTime - decreaseAmount;

                        // Make sure we don't go below 1 minute minimum
                        newRemainingTime = Math.max(newRemainingTime, 60000); // 1 minute minimum

                        // Update the expire time
                        long newExpireTime = currentTime + newRemainingTime;
                        plugin.getBountyManager().setBounty(setterUUID, playerUUID, playerBounties.get(setterUUID), newExpireTime);
                        modified = true;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid expire times
                    plugin.getLogger().warning("Invalid expire time format for bounty: " + expireTime);
                }
            }
        }

        if (modified) {
            plugin.getBountyManager().saveBounties();
        }
    }

    /**
     * Increases all bounty times for a player by the specified percentage (for failures)
     */
    private void increaseAllBountyTimes(Player player, double percentage) {
        UUID playerUUID = player.getUniqueId();
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();

        if (!allBounties.containsKey(playerUUID)) {
            return;
        }

        Map<UUID, Integer> playerBounties = allBounties.get(playerUUID);
        boolean modified = false;

        // Get expire times for this player's bounties
        for (UUID setterUUID : playerBounties.keySet()) {
            String expireTime = plugin.getBountyManager().getBountyExpireTime(setterUUID, playerUUID);
            if (expireTime != null && !expireTime.equals("Never")) {
                try {
                    long expireTimeMs = Long.parseLong(expireTime);
                    long currentTime = System.currentTimeMillis();
                    long remainingTime = expireTimeMs - currentTime;

                    if (remainingTime > 0) {
                        // Calculate the new remaining time (increased by percentage)
                        long increaseAmount = (long) (remainingTime * (percentage / 100.0));
                        long newRemainingTime = remainingTime + increaseAmount;

                        // Update the expire time
                        long newExpireTime = currentTime + newRemainingTime;
                        plugin.getBountyManager().setBounty(setterUUID, playerUUID, playerBounties.get(setterUUID), newExpireTime);
                        modified = true;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid expire times
                    plugin.getLogger().warning("Invalid expire time format for bounty: " + expireTime);
                }
            }
        }

        if (modified) {
            plugin.getBountyManager().saveBounties();
        }
    }

    /**
     * Gets a random decrease percentage within the configured range
     */
    private double getRandomDecreasePercent() {
        Random random = new Random();
        return minDecreasePercent + (random.nextDouble() * (maxDecreasePercent - minDecreasePercent));
    }

    /**
     * Determines if the Chronos Shard should fail based on random chance within range
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
    public double getMinDecreasePercent() { return minDecreasePercent; }
    public double getMaxDecreasePercent() { return maxDecreasePercent; }
    public double getMinFailureChance() { return minFailureChance; }
    public double getMaxFailureChance() { return maxFailureChance; }
    public boolean isFailureAddsTime() { return failureAddsTime; }
    public double getFailureIncreasePercent() { return failureIncreasePercent; }
}