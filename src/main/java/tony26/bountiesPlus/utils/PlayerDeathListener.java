package tony26.bountiesPlus.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.BountyManager;
import tony26.bountiesPlus.BountyTeamCheck;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerDeathListener implements Listener {

    private final BountiesPlus plugin;

    /**
     * Initializes the player death listener
     * // note: Sets up the listener for processing bounty claims on player death
     */
    public PlayerDeathListener(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        eventManager.register(this);
    }

    /**
     * Handles player death events
     * // note: Processes bounty claims, drops skulls if required, and updates stats
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        BountyManager bountyManager = plugin.getBountyManager();

        if (killer == null || killer.equals(victim)) return;

        // Check if players are in the same group
        BountyTeamCheck teamCheck = new BountyTeamCheck(plugin);
        if (teamCheck.arePlayersInSameGroup(killer, victim)) {
            return; // Error message sent by BountyTeamCheck
        }

        UUID victimUUID = victim.getUniqueId();
        UUID killerUUID = killer.getUniqueId();

        if (!bountyManager.hasBounty(victimUUID)) return;

        FileConfiguration config = plugin.getConfig();
        boolean requireSkullTurnIn = config.getBoolean("bounties.require-skull-turn-in", true);
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        Map<UUID, Integer> bounties = bountyManager.getBountiesOnTarget(victimUUID);
        double totalMoney = bounties.values().stream().mapToDouble(Integer::intValue).sum();
        double totalItemValue = bounties.keySet().stream()
                .mapToDouble(setterUUID -> bountyManager.getBountyItems(victimUUID, setterUUID).stream()
                        .mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum())
                .sum();
        double totalValueEarned = totalMoney + totalItemValue;

        double manualMoneyBoost = bountyManager.getManualMoneyBoostMultiplier(victimUUID);
        double manualXpBoost = bountyManager.getManualXpBoostMultiplier(victimUUID);
        double boostedAmount = totalMoney * manualMoneyBoost;
        int xpReward = manualXpBoost > 1.0 ? (int) (totalMoney * manualXpBoost * 0.1) : 0;

        if (requireSkullTurnIn) {
            // Drop a bounty skull
            ItemStack skull = SkullUtils.createCustomBountySkull(victim, bounties, killer);
            if (skull != null) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), skull);
                sendSkullDropMessages(killer, victim, messagesConfig);
            } else {
                plugin.getLogger().warning("[DEBUG - PlayerDeathListener] Failed to create bounty skull for " + victim.getName());
            }
        } else {
            // Grant rewards immediately
            if (BountiesPlus.getEconomy() != null && boostedAmount > 0) {
                BountiesPlus.getEconomy().depositPlayer(killer, boostedAmount);
            }
            if (xpReward > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> killer.giveExp(xpReward));
            }
            processBountyClaims(killer, victim, bounties, messagesConfig);
            bountyManager.clearBounties(victimUUID);

            // Update stats asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    int currentClaimed = plugin.getMySQL().getClaimed(killerUUID).get();
                    int currentSurvived = plugin.getMySQL().getSurvived(victimUUID).get();
                    double currentMoneyEarned = plugin.getMySQL().getMoneyEarned(killerUUID).get();
                    int currentXPEarned = plugin.getMySQL().getXPEarned(killerUUID).get();
                    double currentTotalValueEarned = plugin.getMySQL().getTotalValueEarned(killerUUID).get();

                    plugin.getMySQL().setClaimed(killerUUID, currentClaimed + 1).get();
                    plugin.getMySQL().setSurvived(victimUUID, currentSurvived + 1).get();
                    plugin.getMySQL().setMoneyEarned(killerUUID, currentMoneyEarned + boostedAmount).get();
                    plugin.getMySQL().setXPEarned(killerUUID, currentXPEarned + xpReward).get();
                    plugin.getMySQL().setTotalValueEarned(killerUUID, currentTotalValueEarned + totalValueEarned).get();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to update stats for " + killerUUID + " or " + victimUUID + ": " + e.getMessage());
                }
            });

            // Send claim message without skull
            String claimMessage = messagesConfig.getString("bounty-claimed-no-skull", "&a&lBounty Claimed!\n&7You claimed the bounty on &e%killed% &7for &e$%amount%!");
            claimMessage = claimMessage.replace("%killed%", victim.getName()).replace("%amount%", String.format("%.2f", boostedAmount));
            MessageUtils.sendFormattedMessage(killer, claimMessage);
        }

        playBountySound(killer);
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

    /**
     * Processes bounty claims for a player death
     * // note: Sends claim messages to killer and broadcasts to server
     */
    private void processBountyClaims(Player killer, Player killed, Map<UUID, Integer> bounties, FileConfiguration messagesConfig) {
        BountyManager bountyManager = plugin.getBountyManager();
        double totalReward = bounties.values().stream().mapToDouble(Integer::intValue).sum();
        double manualMoneyBoost = bountyManager.getManualMoneyBoostMultiplier(killed.getUniqueId());
        double manualXpBoost = bountyManager.getManualXpBoostMultiplier(killed.getUniqueId());
        double boostedAmount = totalReward * manualMoneyBoost;

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

        if (totalReward > 0) {
            if (messagesConfig.getBoolean("bounty-claimed-message.killer.enabled", true)) {
                String killerMessage = messagesConfig.getString("bounty-claimed-message.killer.message", "%prefix%&aYou claimed the bounty on &e%killed%&a and received &e$%amount%&a!");
                killerMessage = killerMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killed%", killed.getName()).replace("%amount%", String.format("%.2f", boostedAmount));
                killer.sendMessage(ChatColor.translateAlternateColorCodes('&', killerMessage));
            }

            if (messagesConfig.getBoolean("bounty-claimed-message.broadcast.enabled", true)) {
                String broadcastMessage = messagesConfig.getString("bounty-claimed-message.broadcast.message", "%prefix%&e%killer%&a claimed the bounty on &e%killed%&a worth &e$%amount%&a!");
                broadcastMessage = broadcastMessage.replace("%prefix%", messagesConfig.getString("prefix", "")).replace("%killer%", killer.getName()).replace("%killed%", killed.getName()).replace("%amount%", String.format("%.2f", boostedAmount));
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