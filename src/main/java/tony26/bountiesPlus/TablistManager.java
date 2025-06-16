// file: java/tony26/bountiesPlus/TablistManager.java
package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tony26.bountiesPlus.utils.DebugManager;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages custom prefixes/suffixes for players with bounties in the tablist
 */
public class TablistManager implements Listener {
    private final BountiesPlus plugin;
    private final Set<UUID> modifiedPlayers;

    public TablistManager(BountiesPlus plugin) {
        this.plugin = plugin;
        this.modifiedPlayers = new HashSet<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Applies the configured prefix/suffix to a player's tablist name // note: Updates player display name if they have a bounty and the feature is enabled
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
        String format = config.getString("tablist-modification.format", "&c[Bounty] %bountiesplus_player_name%");
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