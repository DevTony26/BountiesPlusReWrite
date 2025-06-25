package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.MessageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles chat input for bounty search prompts
 * // note: Processes player chat input to search for bounties by partial name and display all matching players
 */
public class BountySearchListener implements Listener {
    private final BountiesPlus plugin;

    public BountySearchListener(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles chat input for bounty search
     * // note: Processes player input to find and display all players matching the partial name
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Long promptTime = BountyGUI.getAwaitingSearchTime(playerUUID);
        if (promptTime == null) return;

        event.setCancelled(true); // Prevent chat message from broadcasting
        String input = event.getMessage().trim();

        // Check for timeout
        if (System.currentTimeMillis() - promptTime > BountyGUI.SEARCH_TIMEOUT) {
            BountyGUI.removeAwaitingSearchInput(playerUUID);
            MessageUtils.sendFormattedMessage(player, "search-timeout");
            Bukkit.getScheduler().runTask(plugin, () -> BountyGUI.openBountyGUI(player, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage()));
            return;
        }

        // Handle cancellation
        if (input.equalsIgnoreCase("cancel")) {
            BountyGUI.removeAwaitingSearchInput(playerUUID);
            MessageUtils.sendFormattedMessage(player, "search-cancelled");
            Bukkit.getScheduler().runTask(plugin, () -> BountyGUI.openBountyGUI(player, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage()));
            return;
        }

        // Process partial player name input
        List<OfflinePlayer> matches = findMatchingPlayers(input);
        if (matches.isEmpty()) {
            MessageUtils.sendFormattedMessage(player, "bounty-player-not-found");
            Bukkit.getScheduler().runTask(plugin, () -> BountyGUI.openBountyGUI(player, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage()));
            return;
        }

        // Remove player from awaiting input and open GUI with search results
        BountyGUI.removeAwaitingSearchInput(playerUUID);
        List<UUID> matchUUIDs = matches.stream().map(OfflinePlayer::getUniqueId).collect(Collectors.toList());
        Bukkit.getScheduler().runTask(plugin, () -> BountyGUI.openSearchResultsGUI(player, matchUUIDs, plugin));
    }

    /**
     * Finds all players whose names partially match the input
     * // note: Returns a list of offline players who have played before and whose names contain the input string (case-insensitive)
     */
    private List<OfflinePlayer> findMatchingPlayers(String input) {
        String lowerInput = input.toLowerCase();
        List<OfflinePlayer> matches = new ArrayList<>();

        // Collect all matching players who have played before
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().toLowerCase().contains(lowerInput) && offlinePlayer.hasPlayedBefore()) {
                matches.add(offlinePlayer);
            }
        }

        return matches;
    }
}