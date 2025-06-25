
package tony26.bountiesPlus.Items;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

public class Tracker implements Listener {

    private final BountiesPlus plugin;
    private final Map<UUID, BukkitTask> activeTrackers = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();

    // Configuration values - will be loaded from items.yml
    private int maxUses;
    private double searchRadius;
    private int trackingDuration; // in seconds
    private String actionBarMessage;
    private String trackerName;
    private List<String> trackerLore;
    private String noTargetMessage;
    private String trackingStartMessage;
    private String trackerExpiredMessage;
    private String itemIdentifier;
    private String alreadyActiveMessage;
    private String targetLostMessage;
    private String trackingExpiredMessage;
    private String jammerBlockedMessage;

    /**
     * Initializes the Tracker system
     * // note: Sets up configuration and registers listener
     */
    public Tracker(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        loadConfiguration();
        eventManager.register(this);
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        // Load configuration values with defaults
        this.maxUses = itemsConfig.getInt("tracker.max-uses", 5);
        this.searchRadius = itemsConfig.getDouble("tracker.search-radius", 100.0);
        this.trackingDuration = itemsConfig.getInt("tracker.tracking-duration", 30);
        this.actionBarMessage = itemsConfig.getString("tracker.action-bar-message", "&eTracking time remaining: &c%time%s");
        this.trackerName = itemsConfig.getString("tracker.item-name", "&6&lBounty Tracker");
        this.trackerLore = itemsConfig.getStringList("tracker.item-lore");
        this.noTargetMessage = itemsConfig.getString("tracker.messages.no-target", "&cNo players found within tracking range!");
        this.trackingStartMessage = itemsConfig.getString("tracker.messages.tracking-start", "&aTracker activated! Pointing to nearest player for %duration% seconds.");
        this.trackerExpiredMessage = itemsConfig.getString("tracker.messages.tracker-expired", "&cYour tracker has run out of uses and was removed!");
        this.alreadyActiveMessage = itemsConfig.getString("tracker.messages.already-active", "&cTracker is already active!");
        this.targetLostMessage = itemsConfig.getString("tracker.messages.target-lost", "&cTarget lost! No other players in range.");
        this.trackingExpiredMessage = itemsConfig.getString("tracker.messages.tracking-expired", "&eTracker has expired.");
        this.jammerBlockedMessage = itemsConfig.getString("tracker.messages.jammer-blocked", "&c&lJAMMED! &7Enemy countermeasures are blocking your tracker!");
        this.itemIdentifier = itemsConfig.getString("tracker.item-identifier", "BOUNTY_TRACKER_ITEM");

        // Set default lore if none configured
        if (trackerLore.isEmpty()) {
            trackerLore = new ArrayList<>();
            trackerLore.add("&7Right-click to track the nearest player");
            trackerLore.add("&7within a " + (int) searchRadius + " block radius");
            trackerLore.add("&c&lUses: &f%uses%&c/&f%max_uses%");
            trackerLore.add("");
            trackerLore.add("&eThis compass will point to your");
            trackerLore.add("&etarget for " + trackingDuration + " seconds");
            trackerLore.add("");
            trackerLore.add("&c&lWARNING: &7Can be blocked by jammers!");
        }
    }

    /**
     * Creates a new tracker item with the specified number of uses // note: Creates a compass item with NBT tags for type and uses
     */
    public ItemStack createTrackerItem(int uses) {
        ItemStack tracker = new ItemStack(Material.COMPASS);
        ItemMeta meta = tracker.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', trackerName));
            List<String> lore = new ArrayList<>();
            for (String line : trackerLore) {
                String processedLine = line.replace("%uses%", String.valueOf(uses))
                        .replace("%max_uses%", String.valueOf(maxUses))
                        .replace("%radius%", String.valueOf((int) searchRadius))
                        .replace("%duration%", String.valueOf(trackingDuration));
                lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);
            tracker.setItemMeta(meta);
            tracker = VersionUtils.setNBTString(tracker, "item_type", "tracker");
            tracker = VersionUtils.setNBTInteger(tracker, "uses", uses);
        }

        return tracker;
    }

    /**
     * Checks if an item is a tracker item // note: Verifies item type using NBT tag
     */
    public boolean isTrackerItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || item.getItemMeta() == null) {
            return false;
        }
        return "tracker".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    /**
     * Gets the remaining uses from a tracker item // note: Retrieves uses from NBT tag
     */
    private int getUsesFromItem(ItemStack item) {
        if (!isTrackerItem(item)) return 0;
        Integer uses = VersionUtils.getNBTInteger(item, "uses");
        return uses != null ? uses : 0;
    }

    /**
     * Updates the tracker item with new uses count // note: Creates a new item with updated uses in NBT and lore
     */
    private ItemStack updateTrackerUses(ItemStack item, int newUses) {
        if (!isTrackerItem(item)) return item;
        return createTrackerItem(newUses);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isTrackerItem(item)) {
            return;
        }

        event.setCancelled(true);

        // Check if player is already tracking
        if (activeTrackers.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyActiveMessage));
            return;
        }

        // Get current uses
        int currentUses = getUsesFromItem(item);
        if (currentUses <= 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', trackerExpiredMessage));
            player.getInventory().remove(item);
            return;
        }

        // Find nearest player
        Player nearestPlayer = findNearestPlayer(player);
        if (nearestPlayer == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noTargetMessage));
            return;
        }

        // JAMMER CHECK: Check if the target has an active jammer
        if (plugin.getJammer() != null && plugin.getJammer().isJammerActive(nearestPlayer)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerBlockedMessage));
            // Still consume the tracker use when blocked by jammer
            int newUses = currentUses - 1;
            if (newUses <= 0) {
                player.getInventory().remove(item);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', trackerExpiredMessage));
            } else {
                ItemStack updatedItem = updateTrackerUses(item, newUses);
                int slot = player.getInventory().getHeldItemSlot();
                player.getInventory().setItem(slot, updatedItem);
            }
            return;
        }

        // Start tracking
        startTracking(player, nearestPlayer);

        // Update item uses
        int newUses = currentUses - 1;
        if (newUses <= 0) {
            // Remove item when uses are exhausted
            player.getInventory().remove(item);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', trackerExpiredMessage));
        } else {
            // Update item with new uses count
            ItemStack updatedItem = updateTrackerUses(item, newUses);
            int slot = player.getInventory().getHeldItemSlot();
            player.getInventory().setItem(slot, updatedItem);
        }

        // Send tracking start message
        String message = trackingStartMessage.replace("%duration%", String.valueOf(trackingDuration));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    /**
     * Finds the nearest player within the search radius
     */
    private Player findNearestPlayer(Player tracker) {
        Location trackerLocation = tracker.getLocation();
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Skip self
            if (onlinePlayer.getUniqueId().equals(tracker.getUniqueId())) {
                continue;
            }

            // Skip if in different world
            if (!onlinePlayer.getWorld().equals(tracker.getWorld())) {
                continue;
            }

            // Check distance
            double distance = trackerLocation.distance(onlinePlayer.getLocation());
            if (distance <= searchRadius && distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = onlinePlayer;
            }
        }

        return nearestPlayer;
    }

    /**
     * Starts tracking a target player
     */
    private void startTracking(Player tracker, Player target) {
        UUID trackerUUID = tracker.getUniqueId();

        // Cancel any existing tracking for this player
        stopTracking(trackerUUID);

        // Start compass tracking task
        BukkitTask trackingTask = new BukkitRunnable() {
            private int timeRemaining = trackingDuration;

            @Override
            public void run() {
                // Check if tracker is still online
                Player currentTracker = Bukkit.getPlayer(trackerUUID);
                if (currentTracker == null || !currentTracker.isOnline()) {
                    cancel();
                    return;
                }

                // Check if target is still online
                Player currentTarget = Bukkit.getPlayer(target.getUniqueId());
                if (currentTarget == null || !currentTarget.isOnline()) {
                    // Target went offline, find new target
                    Player newTarget = findNearestPlayer(currentTracker);
                    if (newTarget == null) {
                        currentTracker.sendMessage(ChatColor.translateAlternateColorCodes('&', targetLostMessage));
                        cancel();
                        return;
                    }

                    // Check if new target has jammer active
                    if (plugin.getJammer() != null && plugin.getJammer().isJammerActive(newTarget)) {
                        currentTracker.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerBlockedMessage));
                        cancel();
                        return;
                    }

                    // Update to track new target (restart tracking with new target)
                    cancel();
                    startTracking(currentTracker, newTarget);
                    return;
                }

                // CONTINUOUS JAMMER CHECK: Check if target activated jammer during tracking
                if (plugin.getJammer() != null && plugin.getJammer().isJammerActive(currentTarget)) {
                    currentTracker.sendMessage(ChatColor.translateAlternateColorCodes('&', jammerBlockedMessage));
                    cancel();
                    return;
                }

                // Update compass direction
                currentTracker.setCompassTarget(currentTarget.getLocation());

                timeRemaining--;
                if (timeRemaining <= 0) {
                    // Reset compass to world spawn
                    currentTracker.setCompassTarget(currentTracker.getWorld().getSpawnLocation());
                    currentTracker.sendMessage(ChatColor.translateAlternateColorCodes('&', trackingExpiredMessage));
                    cancel();
                }
            }

            @Override
            public void cancel() {
                super.cancel();
                activeTrackers.remove(trackerUUID);
                stopActionBar(trackerUUID);
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second

        activeTrackers.put(trackerUUID, trackingTask);

        // Start action bar countdown
        startActionBar(tracker);
    }

    /**
     * Starts the action bar countdown
     */
    private void startActionBar(Player player) {
        UUID playerUUID = player.getUniqueId();

        BukkitTask actionBarTask = new BukkitRunnable() {
            private int timeRemaining = trackingDuration;

            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(playerUUID);
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    cancel();
                    return;
                }

                if (timeRemaining <= 0 || !activeTrackers.containsKey(playerUUID)) {
                    cancel();
                    return;
                }

                // Send action bar message
                String message = actionBarMessage.replace("%time%", String.valueOf(timeRemaining));
                sendActionBar(currentPlayer, ChatColor.translateAlternateColorCodes('&', message));

                timeRemaining--;
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
     * Stops tracking for a player
     */
    private void stopTracking(UUID playerUUID) {
        BukkitTask task = activeTrackers.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
        stopActionBar(playerUUID);

        // Reset compass to world spawn
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
    }

    /**
     * Stops action bar for a player
     */
    private void stopActionBar(UUID playerUUID) {
        BukkitTask task = actionBarTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Clean up when plugin disables
     */
    public void cleanup() {
        for (BukkitTask task : activeTrackers.values()) {
            task.cancel();
        }
        for (BukkitTask task : actionBarTasks.values()) {
            task.cancel();
        }
        activeTrackers.clear();
        actionBarTasks.clear();
    }

    /**
     * Check if a player is currently tracking
     */
    public boolean isTracking(Player player) {
        return activeTrackers.containsKey(player.getUniqueId());
    }

    /**
     * Manually stop tracking for a player (useful for other plugins or commands)
     */
    public void stopTracking(Player player) {
        stopTracking(player.getUniqueId());
    }

    // Getters for configuration values (useful for other classes)
    public int getMaxUses() { return maxUses; }
    public double getSearchRadius() { return searchRadius; }
    public int getTrackingDuration() { return trackingDuration; }
    public String getJammerBlockedMessage() { return jammerBlockedMessage; }
}