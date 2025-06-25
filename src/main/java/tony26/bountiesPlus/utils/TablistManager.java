// file: java/tony26/bountiesPlus/TablistManager.java
package tony26.bountiesPlus.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tony26.bountiesPlus.BountiesPlus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages custom prefixes/suffixes for players with bounties in the tablist
 */
public class TablistManager implements Listener {
    private final BountiesPlus plugin;
    private final Set<UUID> modifiedPlayers;

    public TablistManager(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.modifiedPlayers = new HashSet<>();
        eventManager.register(this); // Use EventManager
    }

    /**
     * Applies the configured prefix/suffix to a player's tablist name
     * // note: Updates player display name if they have a bounty and the feature is enabled, prioritizing boosted bounties at the top if configured
     */
    public void applyTablistName(Player player) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Attempting to apply tablist name for player: " + player.getName());
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("tablist-modification.enabled", false)) {
            debugManager.logDebug("Tablist modification disabled in config for " + player.getName());
            return;
        }
        if (!plugin.getBountyManager().hasBounty(player.getUniqueId())) {
            debugManager.logDebug("No bounty found for player: " + player.getName());
            return;
        }
        String format;
        boolean isBoosted = plugin.getBoostedBounty() != null && plugin.getBoostedBounty().getCurrentBoostedTarget() != null &&
                plugin.getBoostedBounty().getCurrentBoostedTarget().equals(player.getUniqueId());
        if (isBoosted && config.getBoolean("tablist-modification.move-boosted-to-top", false)) {
            format = config.getString("tablist-modification.boosted-format", "&6[Boosted %bountiesplus_boost%x] &a%bountiesplus_total_pool% &6] %player_name%");
            format = "\u00A7z" + format; // Prepend sorting character for boosted players
        } else {
            format = config.getString("tablist-modification.format", "&c[Bounty] %player_name%");
        }
        PlaceholderContext context = PlaceholderContext.create().player(player);
        String formattedName = Placeholders.apply(format, context);
        if (formattedName == null || formattedName.isEmpty()) {
            debugManager.logWarning("Formatted tablist name is null or empty for " + player.getName() + ", using default name");
            formattedName = player.getName();
        }
        if (formattedName.length() > 16 && plugin.getServer().getVersion().contains("1.8")) {
            formattedName = formattedName.substring(0, 16);
            debugManager.logDebug("Truncated tablist name for 1.8 compatibility: " + formattedName);
        }
        try {
            player.setPlayerListName(ChatColor.translateAlternateColorCodes('&', formattedName));
            modifiedPlayers.add(player.getUniqueId());
            debugManager.logDebug("Successfully applied tablist name to " + player.getName() + ": " + formattedName);
        } catch (Exception e) {
            debugManager.logWarning("Failed to apply tablist name for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Removes the custom prefix/suffix from a player's tablist name // note: Resets player display name to default if they no longer have a bounty
     */
    public void removeTablistName(Player player) {
        DebugManager debugManager = plugin.getDebugManager();
        if (!modifiedPlayers.contains(player.getUniqueId())) {
            return;
        }
        try {
            player.setPlayerListName(player.getName());
            modifiedPlayers.remove(player.getUniqueId());
            debugManager.logDebug("Removed tablist name modification for " + player.getName());
        } catch (Exception e) {
            debugManager.logWarning("Failed to remove tablist name for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Updates tablist names for all online players // note: Reapplies or removes tablist names based on current bounty status
     */
    public void updateAllTablistNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getBountyManager().hasBounty(player.getUniqueId())) {
                applyTablistName(player);
            } else {
                removeTablistName(player);
            }
        }
    }

    /**
     * Handles player join events to apply tablist name // note: Applies prefix/suffix if player has a bounty on join
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Player joined: " + player.getName() + ", checking for bounty");
        // Schedule tablist update with increased delay to ensure config and player state are stable
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getBountyManager().hasBounty(player.getUniqueId())) {
                applyTablistName(player);
            } else {
                debugManager.logDebug("No bounty found for joining player: " + player.getName());
            }
        }, 20L); // 1-second delay
    }

    /**
     * Handles player quit events to clean up // note: Removes player from modified list on quit
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        modifiedPlayers.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Cleans up on plugin disable // note: Resets all modified tablist names
     */
    public void cleanup() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeTablistName(player);
        }
        modifiedPlayers.clear();
    }
}