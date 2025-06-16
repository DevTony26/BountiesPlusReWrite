package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.SkullUtils;
import tony26.bountiesPlus.utils.VersionUtils;

import java.util.*;

public class PlayerDeathListener implements Listener {

    private final BountiesPlus plugin;

    public PlayerDeathListener(BountiesPlus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Handles player death events to process bounties // note: Processes bounty claims, drops skulls, and updates tablist
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        Player killer = killed.getKiller();
        if (killer == null) return;
        UUID killedUUID = killed.getUniqueId();
        UUID killerUUID = killer.getUniqueId();
        Map<UUID, Integer> bounties = plugin.getBountyManager().getBountiesOnTarget(killedUUID);
        if (bounties.isEmpty()) return;
        boolean requireSkullTurnIn = plugin.getConfig().getBoolean("require-skull-turn-in", true);
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        if (requireSkullTurnIn) {
            ItemStack skull = SkullUtils.createCustomBountySkull(killed, bounties, killer);
            if (skull != null) {
                killed.getWorld().dropItemNaturally(killed.getLocation(), skull);
            }
            sendSkullDropMessages(killer, killed, messagesConfig);
        } else {
            processBountyClaims(killer, killed, bounties, messagesConfig);
        }
        for (UUID setterUUID : bounties.keySet()) {
            plugin.getBountyManager().removeBounty(setterUUID, killedUUID);
        }
        updateKillerStats(killerUUID, bounties);
        updateKilledStats(killedUUID);
        if (plugin.isBountySoundEnabled()) {
            playBountySound(killer);
        }
        plugin.getTablistManager().removeTablistName(killed); // Remove tablist name after bounties cleared
    }

    private void sendSkullDropMessages(Player killer, Player killed, FileConfiguration messagesConfig) {
        if (messagesConfig.getBoolean("skull-dropped-message.killer.enabled", true)) {
            String killerMessage = messagesConfig.getString("skull-dropped-message.killer.message", "%prefix%&aYou killed &e%killed% &awho had bounties! Their skull has been dropped for you to turn in.");
            killerMessage = killerMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killed%", killed.getName());
            killer.sendMessage(ChatColor.translateAlternateColorCodes('&', killerMessage));
        }

        if (messagesConfig.getBoolean("skull-dropped-message.killed.enabled", true)) {
            String killedMessage = messagesConfig.getString("skull-dropped-message.killed.message", "%prefix%&cYou were killed by &e%killer%&c! Your bounty skull was dropped.");
            killedMessage = killedMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killer%", killer.getName());
            killed.sendMessage(ChatColor.translateAlternateColorCodes('&', killedMessage));
        }

        String instructionMessage = messagesConfig.getString("skull-drop-instruction", "%prefix%&eTake the skull to the Bounty Hunter to claim your rewards!");
        instructionMessage = instructionMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%bounty_hunter_button%", "the Bounty Hunter");
        killer.sendMessage(ChatColor.translateAlternateColorCodes('&', instructionMessage));
    }

    private void processBountyClaims(Player killer, Player killed, Map<UUID, Integer> bounties, FileConfiguration messagesConfig) {
        BountyManager bountyManager = plugin.getBountyManager();

        // Get manual boost multipliers - ADD THESE LINES
        double manualMoneyBoost = bountyManager.getManualMoneyBoostMultiplier(killed.getUniqueId());
        double manualXpBoost = bountyManager.getManualXpBoostMultiplier(killed.getUniqueId());

        double totalReward = 0.0;

        for (Map.Entry<UUID, Integer> bountyEntry : bounties.entrySet()) {
            UUID setterUUID = bountyEntry.getKey();
            int bountyAmount = bountyEntry.getValue();

            // Apply manual money boost - MODIFY THIS LINE
            double boostedAmount = bountyAmount * manualMoneyBoost;
            totalReward += boostedAmount;

            // Handle XP rewards if you have them - ADD THESE LINES IF NEEDED
            if (manualXpBoost > 1.0) {
                int xpReward = (int) (bountyAmount * manualXpBoost * 0.1); // 10% of money as XP
                killer.giveExp(xpReward);
            }
        }

        // Give money rewards
        if (BountiesPlus.getEconomy() != null && totalReward > 0) {
            BountiesPlus.getEconomy().depositPlayer(killer, totalReward);
        }

        // Send boost notification if active - ADD THESE LINES
        if (manualMoneyBoost > 1.0 || manualXpBoost > 1.0) {
            String boostMessage = ChatColor.GREEN + "Boost Applied! ";
            if (manualMoneyBoost > 1.0) {
                boostMessage += "Money: " + manualMoneyBoost + "x ";
            }
            if (manualXpBoost > 1.0) {
                boostMessage += "XP: " + manualXpBoost + "x";
            }
            killer.sendMessage(boostMessage);
        }

        // Rest of your existing code for messages and clearing bounties...
        bountyManager.clearBounties(killed.getUniqueId());

        if (BountiesPlus.getEconomy() != null && totalReward > 0) {
            BountiesPlus.getEconomy().depositPlayer(killer, totalReward);

            if (messagesConfig.getBoolean("bounty-claimed-message.killer.enabled", true)) {
                String killerMessage = messagesConfig.getString("bounty-claimed-message.killer.message", "%prefix%&aYou claimed the bounty on &e%killed%&a and received &e$%amount%&a!");
                killerMessage = killerMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killed%", killed.getName()).replace("%amount%", String.valueOf(totalReward));
                killer.sendMessage(ChatColor.translateAlternateColorCodes('&', killerMessage));
            }

            if (messagesConfig.getBoolean("bounty-claimed-message.broadcast.enabled", true)) {
                String broadcastMessage = messagesConfig.getString("bounty-claimed-message.broadcast.message", "%prefix%&e%killer%&a claimed the bounty on &e%killed%&a worth &e$%amount%&a!");
                broadcastMessage = broadcastMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killer%", killer.getName()).replace("%killed%", killed.getName()).replace("%amount%", String.valueOf(totalReward));

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', broadcastMessage));
                }
            }
        } else {
            if (messagesConfig.getBoolean("bounty-killed-no-reward.enabled", true)) {
                String noRewardMessage = messagesConfig.getString("bounty-killed-no-reward.message", "%prefix%&aYou killed &e%killed%&a, who had a bounty on their head!");
                noRewardMessage = noRewardMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killed%", killed.getName());
                killer.sendMessage(ChatColor.translateAlternateColorCodes('&', noRewardMessage));
            }
        }
    }

    private void updateKillerStats(UUID killerUUID, Map<UUID, Integer> bounties) {
        FileConfiguration statsConfig = plugin.getStatsConfig();
        String path = "players." + killerUUID.toString();

        int currentClaimed = statsConfig.getInt(path + ".claimed", 0);
        double currentMoney = statsConfig.getDouble(path + ".money_earned", 0.0);
        int currentReputation = statsConfig.getInt(path + ".reputation", 0);

        int totalReward = bounties.values().stream().mapToInt(Integer::intValue).sum();

        statsConfig.set(path + ".claimed", currentClaimed + bounties.size());
        statsConfig.set(path + ".money_earned", currentMoney + totalReward);
        statsConfig.set(path + ".reputation", currentReputation + (bounties.size() * 10));

        plugin.saveEverything();
    }

    private void updateKilledStats(UUID killedUUID) {
        FileConfiguration statsConfig = plugin.getStatsConfig();
        String path = "players." + killedUUID.toString();

        int currentSurvived = statsConfig.getInt(path + ".survived", 0);
        int currentReputation = statsConfig.getInt(path + ".reputation", 0);

        statsConfig.set(path + ".survived", currentSurvived);
        statsConfig.set(path + ".reputation", Math.max(0, currentReputation - 5));

        plugin.saveEverything();
    }

    private void playBountySound(Player player) {
        try {
            if (VersionUtils.isLegacy()) {
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getBountySoundName()), plugin.getBountySoundVolume(), plugin.getBountySoundPitch());
            } else {
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getBountySoundName()), plugin.getBountySoundVolume(), plugin.getBountySoundPitch());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to play bounty sound: " + e.getMessage());
        }


    }
}