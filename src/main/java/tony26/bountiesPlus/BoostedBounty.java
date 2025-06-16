
package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import tony26.bountiesPlus.utils.VersionUtils;
import tony26.bountiesPlus.utils.TimeFormatter;


import java.util.*;

public class BoostedBounty {

    private final BountiesPlus plugin;
    private Object bossBar; // Using Object for version compatibility
    private int boostInterval;
    private Random random = new Random();
    private Map<Double, Double> multiplierChances;
    private String boostedTitle;
    private String boostedSubtitle;
    private boolean enableTitleSubtitle;
    private boolean bossbarEnabled;
    private int displayBeforeBoost;
    private String bossbarMessage;
    private final Map<UUID, Set<UUID>> boostedBounties = new HashMap<>();
    private final Map<UUID, Double> boostedMultipliers = new HashMap<>();
    private final Set<Player> onlinePlayers = new HashSet<>();
    private BukkitRunnable boostTask;
    private int taskId = -1;

    public BoostedBounty(BountiesPlus plugin) {
        this.plugin = plugin;
        loadConfig();
        startBoostCycle();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        boostInterval = Math.max(1, config.getInt("boosted-bounties.boost-interval", 30));
        multiplierChances = new LinkedHashMap<>();

        ConfigurationSection multiplierSection = config.getConfigurationSection("boosted-bounties.multiplier-chances");
        if (multiplierSection == null) {
            plugin.getLogger().warning("Multiplier chances not found, using default: 2x (50%), 3x (30%), 5x (20%)");
            multiplierChances.put(2.0, 50.0);
            multiplierChances.put(3.0, 30.0);
            multiplierChances.put(5.0, 20.0);
        } else {
            for (String key : multiplierSection.getKeys(false)) {
                try {
                    double multiplier = Double.parseDouble(key);
                    double chance = multiplierSection.getDouble(key);
                    if (chance < 0 || multiplier <= 1) {
                        plugin.getLogger().warning("Invalid multiplier or chance: " + key);
                        continue;
                    }
                    multiplierChances.put(multiplier, chance);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid multiplier format: " + key);
                }
            }
        }

        boostedTitle = ChatColor.translateAlternateColorCodes('&', config.getString("boosted-bounties.boosted-title", "&aBounty Boosted!"));
        boostedSubtitle = ChatColor.translateAlternateColorCodes('&', config.getString("boosted-bounties.boosted-subtitle", "&e%target% &aby &e%multiplier%x"));
        enableTitleSubtitle = config.getBoolean("boosted-bounties.enable-title-subtitle", true);
        bossbarEnabled = config.getBoolean("boosted-bounties.bossbar.enabled", true);
        displayBeforeBoost = Math.max(1, config.getInt("boosted-bounties.bossbar.display-before-boost", 3));
        bossbarMessage = ChatColor.translateAlternateColorCodes('&', config.getString("boosted-bounties.bossbar.message", "&6Next Boosted Bounty in %time%"));

        // Check if boss bars are available in this version
        if (bossbarEnabled && !VersionUtils.isPost19()) {
            plugin.getLogger().info("Boss bars are not supported in this Minecraft version (" + VersionUtils.getVersionString() + "), disabling boss bar feature.");
            bossbarEnabled = false;
        }
    }

    private void startBoostCycle() {
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (onlinePlayers.size() < 2) {
                return;
            }

            Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
            if (allBounties.isEmpty()) {
                return;
            }

            // Create boss bar if supported and enabled
            if (bossbarEnabled && bossBar == null) {
                bossBar = VersionUtils.createBossBar(bossbarMessage.replace("%time%", String.valueOf(displayBeforeBoost)));
            }

            // Display countdown and then select bounty
            Bukkit.getScheduler().runTaskLater(plugin, this::selectRandomBounty, (boostInterval - displayBeforeBoost) * 20L);
        }, 0L, boostInterval * 20L);
    }

    private void selectRandomBounty() {
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
        if (allBounties.isEmpty()) {
            broadcastNoBounties();
            return;
        }

        List<UUID> eligibleTargets = new ArrayList<>();
        for (UUID target : allBounties.keySet()) {
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                eligibleTargets.add(target);
            }
        }

        if (eligibleTargets.isEmpty()) {
            broadcastNoBounties();
            return;
        }

        UUID selectedTarget = eligibleTargets.get(new Random().nextInt(eligibleTargets.size()));
        Map<UUID, Integer> targetBounties = allBounties.get(selectedTarget);

        if (targetBounties.isEmpty()) {
            broadcastNoBounties();
            return;
        }

        UUID selectedSetter = targetBounties.keySet().iterator().next();
        double multiplier = getRandomMultiplier();

        // Store last boost info before setting new boost
        if (!boostedMultipliers.isEmpty()) {
            UUID oldTarget = boostedMultipliers.keySet().iterator().next();
            Player oldPlayer = Bukkit.getPlayer(oldTarget);
            if (oldPlayer != null) {
                lastBoostedPlayer = oldPlayer.getName();
                lastMultiplier = boostedMultipliers.get(oldTarget);
                lastBoostTime = System.currentTimeMillis();
            }
        }

        // Store boosted bounty info
        boostedBounties.computeIfAbsent(selectedTarget, k -> new HashSet<>()).add(selectedSetter);
        boostedMultipliers.put(selectedTarget, multiplier);
        lastBoostStartTime = System.currentTimeMillis();

        // Notify players
        notifyPlayers(selectedTarget, selectedSetter, multiplier);

        plugin.getLogger().info("Boosted bounty selected: Target=" + selectedTarget + ", Setter=" + selectedSetter + ", Multiplier=" + multiplier);

        // Remove boost after 5 minutes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boostedBounties.remove(selectedTarget);
            boostedMultipliers.remove(selectedTarget);
        }, 300L);
    }

    private void notifyPlayers(UUID targetUUID, UUID setterUUID, double multiplier) {
        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        Player setterPlayer = Bukkit.getPlayer(setterUUID);

        String targetName = targetPlayer != null ? targetPlayer.getName() : "Unknown";
        String setterName = setterPlayer != null ? setterPlayer.getName() : "Unknown";

        if (enableTitleSubtitle) {
            String title = boostedTitle;
            String subtitle = boostedSubtitle
                    .replace("%target%", targetName)
                    .replace("%multiplier%", String.format("%.1f", multiplier));

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                VersionUtils.sendTitle(onlinePlayer, title, subtitle, 10, 70, 20);
            }
        }

        // Broadcast message
        List<String> messageList = plugin.getMessagesConfig().getStringList("boosted-bounty-message");
        if (messageList.isEmpty()) {
            plugin.getLogger().warning("boosted-bounty-message is missing in messages.yml");
            String defaultMessage = "&6[Bounty Boost] &e%target%&a's bounty has been boosted by &e%multiplier%x&a!";
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String message = defaultMessage
                        .replace("%target%", targetName)
                        .replace("%setter%", setterName)
                        .replace("%multiplier%", String.valueOf(multiplier));
                onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        } else {
            String boostedBountyMessage = String.join("\n", messageList)
                    .replace("%target%", targetName)
                    .replace("%setter%", setterName)
                    .replace("%multiplier%", String.valueOf(multiplier));
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', boostedBountyMessage));
            }
        }
    }

    private void broadcastNoBounties() {
        String message = plugin.getMessagesConfig().getString("no-bounties-to-boost", "&cNo bounties available to boost!");
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private double getRandomMultiplier() {
        if (multiplierChances.isEmpty()) {
            return 2.0; // Default multiplier
        }

        double totalChance = multiplierChances.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * totalChance;
        double cumulativeProbability = 0;

        for (Map.Entry<Double, Double> entry : multiplierChances.entrySet()) {
            cumulativeProbability += entry.getValue();
            if (randomValue <= cumulativeProbability) {
                return entry.getKey();
            }
        }

        return multiplierChances.keySet().stream().findFirst().orElse(2.0);
    }

    public boolean isBoosted(UUID target, UUID setter) {
        Set<UUID> boostedSetters = boostedBounties.get(target);
        return boostedSetters != null && boostedSetters.contains(setter);
    }

    public double getBoostedMultiplier() {
        return boostedMultipliers.values().stream().findFirst().orElse(1.0);
    }

    public void addPlayer(Player player) {
        onlinePlayers.add(player);

        // Add to boss bar if supported and available
        if (bossbarEnabled && bossBar != null) {
            try {
                // Use reflection to add player to boss bar
                Object[] players = (Object[]) bossBar.getClass().getMethod("getPlayers").invoke(bossBar);
                boolean hasPlayer = Arrays.stream(players).anyMatch(p -> p.equals(player));
                if (!hasPlayer) {
                    bossBar.getClass().getMethod("addPlayer", Player.class).invoke(bossBar, player);
                }
            } catch (Exception e) {
                // Silently handle reflection errors
            }
        }
    }

    public void removePlayer(Player player) {
        onlinePlayers.remove(player);

        // Remove from boss bar if supported and available
        if (bossbarEnabled && bossBar != null) {
            try {
                bossBar.getClass().getMethod("removePlayer", Player.class).invoke(bossBar, player);
            } catch (Exception e) {
                // Silently handle reflection errors
            }
        }
    }

    public void cleanup() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        if (bossbarEnabled && bossBar != null) {
            try {
                // Remove all players and hide boss bar
                bossBar.getClass().getMethod("setVisible", boolean.class).invoke(bossBar, false);
                Object players = bossBar.getClass().getMethod("getPlayers").invoke(bossBar);
                if (players instanceof Collection) {
                    for (Object player : new ArrayList<>((Collection<?>) players)) {
                        bossBar.getClass().getMethod("removePlayer", Player.class).invoke(bossBar, player);
                    }
                }
            } catch (Exception e) {
                // Silently handle reflection errors
            }
        }
    }
    // Add these new methods to support the boost clock
    public UUID getCurrentBoostedTarget() {
        return boostedMultipliers.keySet().stream().findFirst().orElse(null);
    }

    public double getCurrentBoostMultiplier(UUID target) {
        return boostedMultipliers.getOrDefault(target, 1.0);
    }

    // You'll need to add tracking for last boost and timing
    private String lastBoostedPlayer = null;
    private double lastMultiplier = 1.0;
    private long lastBoostTime = 0;
    private long lastBoostStartTime = 0;

    public String getLastBoostedPlayer() {
        return lastBoostedPlayer;
    }

    public double getLastBoostMultiplier() {
        return lastMultiplier;
    }

    public long getTimeUntilNextBoost() {
        // Calculate based on boost interval and last boost time
        long intervalMs = boostInterval * 1000L;
        long timeSinceLastStart = System.currentTimeMillis() - lastBoostStartTime;
        long timeUntilNext = intervalMs - (timeSinceLastStart % intervalMs);
        return timeUntilNext / 1000; // Return in seconds
    }

    public String getLastBoostTime() {
        if (lastBoostTime == 0) {
            return "Never";
        }
        return TimeFormatter.formatTimestampToAgo(lastBoostTime);
    }


}
