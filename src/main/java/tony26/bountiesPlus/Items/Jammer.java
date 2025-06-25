package tony26.bountiesPlus.Items;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.EventManager;
import tony26.bountiesPlus.utils.VersionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Jammer implements Listener {

    private final BountiesPlus plugin;
    private final Map<UUID, Long> activeJammers = new HashMap<>(); // Player UUID -> Expiry time
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> jammerTasks = new HashMap<>();

    // Configuration values
    private int jammingDuration; // in seconds
    private String jammerName;
    private List<String> jammerLore;
    private String itemIdentifier;
    private String jammerActivatedMessage;
    private String jammerDeactivatedMessage;
    private String jammerExpiredMessage;
    private String actionBarMessage;
    private String jammerBlockedMessage;

    /**
     * Initializes the Jammer system
     * // note: Sets up configuration and registers listener
     */
    public Jammer(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        loadConfiguration();
        eventManager.register(this);
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        this.jammingDuration = itemsConfig.getInt("jammer.jamming-duration", 300); // 5 minutes default
        this.jammerName = itemsConfig.getString("jammer.item-name", "&c&lSignal Jammer");
        this.jammerLore = itemsConfig.getStringList("jammer.item-lore");
        this.itemIdentifier = itemsConfig.getString("jammer.item-identifier", "SIGNAL_JAMMER_ITEM");
        this.jammerActivatedMessage = itemsConfig.getString("jammer.messages.jammer-activated", "&aJammer activated! You are now protected from tracking for &e%duration% &aseconds.");
        this.jammerDeactivatedMessage = itemsConfig.getString("jammer.messages.jammer-deactivated", "&cJammer deactivated.");
        this.jammerExpiredMessage = itemsConfig.getString("jammer.messages.jammer-expired", "&cYour jammer has expired and was removed!");
        this.jammerBlockedMessage = itemsConfig.getString("jammer.messages.jammer-blocked", "&cYour action was blocked by an active jammer!");
        this.actionBarMessage = itemsConfig.getString("jammer.action-bar-message", "&cJammer Active: &e%time%s remaining");

        // Set default lore if none configured
        if (jammerLore.isEmpty()) {
            jammerLore = new ArrayList<>();
            jammerLore.add("&7Right-click to toggle jammer on/off");
            jammerLore.add("&7Blocks tracker and UAV functionality");
            jammerLore.add("&7when active and in inventory");
            jammerLore.add("");
            jammerLore.add("&eDuration: &a" + (jammingDuration / 60) + " &eminutes");
            jammerLore.add("&cBreaks after full duration of use");
            jammerLore.add("");
            jammerLore.add("&7Perfect for staying off the radar!");
        }
    }

    /**
     * Creates a new jammer item
     * // note: Creates a redstone torch item with NBT tag for type
     */
    public ItemStack createJammerItem() {
        ItemStack jammer = new ItemStack(XMaterial.REDSTONE_TORCH.parseMaterial());
        ItemMeta meta = jammer.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', jammerName));
            List<String> lore = new ArrayList<>();
            for (String line : jammerLore) {
                String processedLine = line.replace("%duration%", String.valueOf(jammingDuration));
                lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);
            jammer.setItemMeta(meta);
            jammer = VersionUtils.setNBTString(jammer, "item_type", "jammer");
        }

        return jammer;
    }

    /**
     * Checks if an item is a jammer item // note: Verifies item type using NBT tag
     */
    public boolean isJammerItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) {
            return false;
        }
        return "jammer".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isJammerItem(item)) {
            return;
        }

        event.setCancelled(true);

        UUID playerUUID = player.getUniqueId();

        // Check if jammer is already active
        if (isJammerActive(player)) {
            // Deactivate jammer
            deactivateJammer(player);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerDeactivatedMessage));
        } else {
            // Activate jammer
            activateJammer(player);
            String message = jammerActivatedMessage.replace("%duration%", String.valueOf(jammingDuration));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    /**
     * Activates the jammer for a player
     */
    private void activateJammer(Player player) {
        UUID playerUUID = player.getUniqueId();
        long expiryTime = System.currentTimeMillis() + (jammingDuration * 1000L);

        activeJammers.put(playerUUID, expiryTime);

        // Start action bar countdown
        startActionBar(player);

        // Schedule jammer expiry
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                expireJammer(playerUUID);
            }
        }.runTaskLater(plugin, jammingDuration * 20L);

        jammerTasks.put(playerUUID, task);
    }

    /**
     * Deactivates the jammer for a player
     */
    private void deactivateJammer(Player player) {
        UUID playerUUID = player.getUniqueId();

        activeJammers.remove(playerUUID);
        stopActionBar(playerUUID);

        // Cancel the expiry task
        BukkitTask task = jammerTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Expires the jammer and removes the item
     */
    private void expireJammer(UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            // Remove jammer item from inventory
            for (ItemStack item : player.getInventory().getContents()) {
                if (isJammerItem(item)) {
                    player.getInventory().remove(item);
                    break;
                }
            }
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerExpiredMessage));
        }

        activeJammers.remove(playerUUID);
        stopActionBar(playerUUID);
        jammerTasks.remove(playerUUID);
    }

    /**
     * Starts the action bar countdown
     * // note: Displays a countdown for the jammer's remaining time
     */
    private void startActionBar(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Stop any existing action bar task
        stopActionBar(playerUUID);

        BukkitTask actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(playerUUID);
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    cancel();
                    return;
                }

                if (!isJammerActive(currentPlayer)) {
                    cancel();
                    return;
                }

                long expiryTime = activeJammers.get(playerUUID);
                long timeRemaining = (expiryTime - System.currentTimeMillis()) / 1000;

                if (timeRemaining <= 0) {
                    cancel();
                    return;
                }

                // Send action bar message
                String message = actionBarMessage.replace("%time%", String.valueOf(timeRemaining));
                sendActionBar(currentPlayer, ChatColor.translateAlternateColorCodes('&', message));
            }

            @Override
            public void cancel() {
                super.cancel();
                actionBarTasks.remove(playerUUID);
            }
        }.runTaskTimer(plugin, 0L, 20L);

        actionBarTasks.put(playerUUID, actionBarTask);
    }

    /**
     * Stops the action bar for a player
     */
    private void stopActionBar(UUID playerUUID) {
        BukkitTask task = actionBarTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Handles inventory changes to deactivate jammer if item is removed
     * // note: Toggles off jammer when the player no longer has a Jammer item in their inventory
     */
    @EventHandler
    public void onInventoryChange(org.bukkit.event.inventory.InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!isJammerActive(player)) {
            return;
        }

        // Schedule check after inventory change to ensure inventory is updated
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!hasJammerInInventory(player)) {
                deactivateJammer(player);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerDeactivatedMessage));
            }
        });
    }

    /**
     * Handles player dropping items to deactivate jammer
     * // note: Toggles off jammer when a Jammer item is dropped
     */
    @EventHandler
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if (!isJammerItem(item) || !isJammerActive(player)) {
            return;
        }

        deactivateJammer(player);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerDeactivatedMessage));
    }

    /**
     * Handles player death to deactivate jammer
     * // note: Toggles off jammer when a player dies and drops their inventory
     */
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!isJammerActive(player)) {
            return;
        }

        deactivateJammer(player);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerDeactivatedMessage));
    }

    /**
     * Gets the message sent when a jammer blocks an action
     * // note: Returns the configured jammer blocked message
     */
    public String getJammerBlockedMessage() {
        return jammerBlockedMessage;
    }

    /**
     * Sends an action bar message to a player // note: Sends action bar message using ProtocolLib for cross-version compatibility
     */
    private void sendActionBar(Player player, String message) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.CHAT);
        packet.getChatComponents().write(0, WrappedChatComponent.fromText(message));
        packet.getBytes().write(0, (byte) 2);

        try {
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }
    /**
     * Checks if a player has an active jammer
     * // note: Verifies if a jammer is active based on expiry time
     */
    public boolean isJammerActive(Player player) {
        UUID playerUUID = player.getUniqueId();
        Long expiryTime = activeJammers.get(playerUUID);

        if (expiryTime == null) {
            return false;
        }

        // Check if jammer has expired
        if (System.currentTimeMillis() > expiryTime) {
            expireJammer(playerUUID);
            return false;
        }

        return true;
    }

    /**
     * Checks if player has jammer item in inventory
     */
    private boolean hasJammerInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isJammerItem(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blocks tracker/UAV functionality if jammer is active
     * This method should be called by Tracker and UAV classes
     */
    public boolean isBlocked(Player player) {
        if (isJammerActive(player)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerBlockedMessage));
            return true;
        }
        return false;
    }

    /**
     * Clean up when plugin disables
     */
    public void cleanup() {
        for (BukkitTask task : actionBarTasks.values()) {
            task.cancel();
        }
        for (BukkitTask task : jammerTasks.values()) {
            task.cancel();
        }
        actionBarTasks.clear();
        jammerTasks.clear();
        activeJammers.clear();
    }

    // Getters for configuration values
    public int getJammingDuration() { return jammingDuration; }
}