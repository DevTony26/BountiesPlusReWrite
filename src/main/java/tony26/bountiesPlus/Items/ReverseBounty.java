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
import tony26.bountiesPlus.utils.VersionUtils;

import java.util.*;

public class ReverseBounty implements Listener {

    private final BountiesPlus plugin;

    // Configuration values
    private String reverseBountyName;
    private List<String> reverseBountyLore;
    private String itemIdentifier;
    private String noBountyMessage;
    private String reverseBountySuccessMessage;
    private String reverseBountyFailureMessage;
    private String serverBroadcastMessage;
    private boolean chanceEnabled;
    private double minFailureChance;
    private double maxFailureChance;

    public ReverseBounty(BountiesPlus plugin) {
        this.plugin = plugin;
        loadConfiguration();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        this.reverseBountyName = itemsConfig.getString("reverse-bounty.item-name", "&d&lReverse Bounty");
        this.reverseBountyLore = itemsConfig.getStringList("reverse-bounty.item-lore");
        this.itemIdentifier = itemsConfig.getString("reverse-bounty.item-identifier", "REVERSE_BOUNTY_ITEM");
        this.noBountyMessage = itemsConfig.getString("reverse-bounty.messages.no-bounty", "&cYou have no bounty to reverse!");
        this.reverseBountySuccessMessage = itemsConfig.getString("reverse-bounty.messages.success", "&aReverse bounty successful! The bounty has been transferred to &e%target%&a.");
        this.reverseBountyFailureMessage = itemsConfig.getString("reverse-bounty.messages.failure", "&cReverse bounty failed! The item was consumed but the reversal did not work.");
        this.serverBroadcastMessage = itemsConfig.getString("reverse-bounty.server-broadcast-message", "&6&l[BOUNTY] &e%player% &6has used a reverse bounty! The bounty is now on &e%target%&6!");
        this.chanceEnabled = itemsConfig.getBoolean("reverse-bounty.chance.enabled", true);
        this.minFailureChance = itemsConfig.getDouble("reverse-bounty.chance.min-failure", 10.0);
        this.maxFailureChance = itemsConfig.getDouble("reverse-bounty.chance.max-failure", 30.0);

        // Set default lore if none configured
        if (reverseBountyLore.isEmpty()) {
            reverseBountyLore = new ArrayList<>();
            reverseBountyLore.add("&7Right-click to reverse your bounty");
            reverseBountyLore.add("&7onto the player who contributed");
            reverseBountyLore.add("&7the most money to your bounty");
            reverseBountyLore.add("");
            reverseBountyLore.add("&eTransfers all bounties from you");
            reverseBountyLore.add("&eto the highest contributor");
            reverseBountyLore.add("");
            if (chanceEnabled) {
                reverseBountyLore.add("&cChance of failure: &e" + (int)minFailureChance + "%-" + (int)maxFailureChance + "%");
                reverseBountyLore.add("");
            }
            reverseBountyLore.add("&7Perfect for turning the tables!");
        }
    }

    /**
     * Creates a new reverse bounty item // note: Creates a nether star item with NBT tag for type
     */
    public ItemStack createReverseBountyItem() {
        ItemStack reverseBounty = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reverseBounty.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', reverseBountyName));
            List<String> lore = new ArrayList<>();
            for (String line : reverseBountyLore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            reverseBounty.setItemMeta(meta);
            reverseBounty = VersionUtils.setNBTString(reverseBounty, "item_type", "reverse_bounty");
        }

        return reverseBounty;
    }

    /**
     * Checks if an item is a reverse bounty item // note: Verifies item type using NBT tag
     */
    public boolean isReverseBountyItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        return "reverse_bounty".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isReverseBountyItem(item)) {
            return;
        }

        event.setCancelled(true);

        UUID playerUUID = player.getUniqueId();

        // Check if player has any bounties
        Map<UUID, Integer> playerBounties = plugin.getBountyManager().getBountiesOnTarget(playerUUID);
        if (playerBounties.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noBountyMessage));
            return;
        }

        // Find the player who contributed the most money
        UUID highestContributor = findHighestContributor(playerBounties);
        if (highestContributor == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noBountyMessage));
            return;
        }

        // Remove the item first (consumed regardless of success/failure)
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        // Check for failure chance if enabled
        if (chanceEnabled && shouldFail()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', reverseBountyFailureMessage));
            return;
        }

        // Calculate total bounty amount
        int totalBountyAmount = playerBounties.values().stream().mapToInt(Integer::intValue).sum();

        // Remove all bounties from the player
        for (UUID setterUUID : new HashSet<>(playerBounties.keySet())) {
            plugin.getBountyManager().removeBounty(playerUUID, setterUUID);
        }

        // Add the total bounty amount to the highest contributor
        // Use the original player as the setter for the reversed bounty
        plugin.getBountyManager().addBounty(highestContributor, playerUUID, totalBountyAmount);

        // Get target player name for messages
        String targetName = getPlayerName(highestContributor);

        // Send success message to player
        String successMsg = reverseBountySuccessMessage.replace("%target%", targetName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', successMsg));

        // Send server broadcast message
        String broadcastMsg = serverBroadcastMessage
                .replace("%player%", player.getName())
                .replace("%target%", targetName);
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMsg));
    }

    /**
     * Finds the player who contributed the most money to the bounty pool
     */
    private UUID findHighestContributor(Map<UUID, Integer> bounties) {
        UUID highestContributor = null;
        int highestAmount = 0;

        for (Map.Entry<UUID, Integer> entry : bounties.entrySet()) {
            if (entry.getValue() > highestAmount) {
                highestAmount = entry.getValue();
                highestContributor = entry.getKey();
            }
        }

        return highestContributor;
    }

    /**
     * Determines if the reverse bounty should fail based on chance
     */
    private boolean shouldFail() {
        if (!chanceEnabled) return false;

        Random random = new Random();
        double failureChance = minFailureChance + (maxFailureChance - minFailureChance) * random.nextDouble();
        double roll = random.nextDouble() * 100.0;

        return roll < failureChance;
    }

    /**
     * Gets a player's name from UUID (handles offline players)
     */
    private String getPlayerName(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            return player.getName();
        }

        // Try to get offline player name
        try {
            return Bukkit.getOfflinePlayer(playerUUID).getName();
        } catch (Exception e) {
            return "Unknown Player";
        }
    }

    /**
     * Clean up when plugin disables
     */
    public void cleanup() {
        // No cleanup needed for this item
    }

    // Getters for configuration values
    public boolean isChanceEnabled() { return chanceEnabled; }
    public double getMinFailureChance() { return minFailureChance; }
    public double getMaxFailureChance() { return maxFailureChance; }
}