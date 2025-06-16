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

public class Frenzy {

    private final BountiesPlus plugin;
    private Object bossBar; // Using Object for version compatibility
    private int frenzyInterval;
    private int frenzyDuration;
    private Random random = new Random();
    private Map<Double, Double> multiplierChances;
    private String frenzyTitle;
    private String frenzySubtitle;
    private boolean enableTitleSubtitle;
    private boolean bossbarEnabled;
    private int displayBeforeFrenzy;
    private String bossbarMessage;
    private final Set<Player> onlinePlayers = new HashSet<>();
    private int taskId = -1;
    private boolean frenzyActive = false;
    private double currentFrenzyMultiplier = 1.0;
    private long frenzyStartTime = 0;
    private long frenzyEndTime = 0;
    private boolean isManualFrenzy = false; // Track if current frenzy is manual

    // Tracking for last frenzy info
    private double lastFrenzyMultiplier = 1.0;
    private long lastFrenzyTime = 0;
    private long lastFrenzyStartTime = 0;

    public Frenzy(BountiesPlus plugin) {
        this.plugin = plugin;
        loadConfig();
        startFrenzyCycle();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        frenzyInterval = Math.max(1, config.getInt("frenzy-mode.frenzy-interval", 300)); // 5 minutes default
        frenzyDuration = Math.max(1, config.getInt("frenzy-mode.frenzy-duration", 60)); // 1 minute default
        multiplierChances = new LinkedHashMap<>();

        ConfigurationSection multiplierSection = config.getConfigurationSection("frenzy-mode.multiplier-chances");
        if (multiplierSection == null) {
            plugin.getLogger().warning("Frenzy multiplier chances not found, using default: 2x (50%), 3x (30%), 5x (20%)");
            multiplierChances.put(2.0, 50.0);
            multiplierChances.put(3.0, 30.0);
            multiplierChances.put(5.0, 20.0);
        } else {
            for (String key : multiplierSection.getKeys(false)) {
                try {
                    double multiplier = Double.parseDouble(key);
                    double chance = multiplierSection.getDouble(key);
                    if (chance < 0 || multiplier <= 1) {
                        plugin.getLogger().warning("Invalid frenzy multiplier or chance: " + key);
                        continue;
                    }
                    multiplierChances.put(multiplier, chance);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid frenzy multiplier format: " + key);
                }
            }
        }

        frenzyTitle = ChatColor.translateAlternateColorCodes('&', config.getString("frenzy-mode.frenzy-title", "&c&lFRENZY MODE ACTIVATED!"));
        frenzySubtitle = ChatColor.translateAlternateColorCodes('&', config.getString("frenzy-mode.frenzy-subtitle", "&e&lAll bounties boosted by &c&l%multiplier%x &e&lfor %duration%s!"));
        enableTitleSubtitle = config.getBoolean("frenzy-mode.enable-title-subtitle", true);
        bossbarEnabled = config.getBoolean("frenzy-mode.bossbar.enabled", true);
        displayBeforeFrenzy = Math.max(1, config.getInt("frenzy-mode.bossbar.display-before-frenzy", 5));
        bossbarMessage = ChatColor.translateAlternateColorCodes('&', config.getString("frenzy-mode.bossbar.message", "&c&lFrenzy Mode in %time%"));

        // Check if boss bars are available in this version
        if (bossbarEnabled && !VersionUtils.isPost19()) {
            plugin.getLogger().info("Boss bars are not supported in this Minecraft version (" + VersionUtils.getVersionString() + "), disabling frenzy boss bar feature.");
            bossbarEnabled = false;
        }
    }

    private void startFrenzyCycle() {
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
                bossBar = VersionUtils.createBossBar(bossbarMessage.replace("%time%", String.valueOf(displayBeforeFrenzy)));
            }

            // Display countdown and then activate frenzy
            Bukkit.getScheduler().runTaskLater(plugin, this::activateFrenzy, (frenzyInterval - displayBeforeFrenzy) * 20L);
        }, 0L, frenzyInterval * 20L);
    }

    /**
     * Activates manual frenzy mode with custom parameters
     * @param multiplier The multiplier to apply to all bounties
     * @param duration The duration in seconds
     * @return true if frenzy was successfully activated, false otherwise
     */
    public boolean activateManualFrenzy(double multiplier, int duration) {
        // Check if frenzy is already active
        if (frenzyActive) {
            return false;
        }

        // Check if there are bounties to boost
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
        if (allBounties.isEmpty()) {
            return false;
        }

        // Store last frenzy info before setting new frenzy
        if (frenzyActive) {
            lastFrenzyMultiplier = currentFrenzyMultiplier;
            lastFrenzyTime = System.currentTimeMillis();
        }

        // Set up manual frenzy
        currentFrenzyMultiplier = multiplier;
        frenzyActive = true;
        isManualFrenzy = true;
        frenzyStartTime = System.currentTimeMillis();
        frenzyEndTime = frenzyStartTime + (duration * 1000L);
        lastFrenzyStartTime = frenzyStartTime;

        // Notify players with custom duration
        notifyPlayers(currentFrenzyMultiplier, duration);

        plugin.getLogger().info("Manual Frenzy Mode activated: Multiplier=" + currentFrenzyMultiplier + ", Duration=" + duration + "s");

        // End frenzy after duration
        Bukkit.getScheduler().runTaskLater(plugin, this::endFrenzy, duration * 20L);

        return true;
    }

    private void activateFrenzy() {
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
        if (allBounties.isEmpty()) {
            broadcastNoBounties();
            return;
        }

        // Store last frenzy info before setting new frenzy
        if (frenzyActive) {
            lastFrenzyMultiplier = currentFrenzyMultiplier;
            lastFrenzyTime = System.currentTimeMillis();
        }

        // Set up new frenzy (automatic)
        currentFrenzyMultiplier = getRandomMultiplier();
        frenzyActive = true;
        isManualFrenzy = false;
        frenzyStartTime = System.currentTimeMillis();
        frenzyEndTime = frenzyStartTime + (frenzyDuration * 1000L);
        lastFrenzyStartTime = frenzyStartTime;

        // Notify players
        notifyPlayers(currentFrenzyMultiplier, frenzyDuration);

        plugin.getLogger().info("Automatic Frenzy Mode activated: Multiplier=" + currentFrenzyMultiplier + ", Duration=" + frenzyDuration + "s");

        // End frenzy after duration
        Bukkit.getScheduler().runTaskLater(plugin, this::endFrenzy, frenzyDuration * 20L);
    }

    private void endFrenzy() {
        frenzyActive = false;
        lastFrenzyTime = System.currentTimeMillis();

        String frenzyType = isManualFrenzy ? "Manual" : "Automatic";
        isManualFrenzy = false; // Reset flag

        // Broadcast frenzy end message
        List<String> messageList = plugin.getMessagesConfig().getStringList("frenzy-mode-ended-message");
        if (messageList.isEmpty()) {
            String defaultMessage = "&c&lFrenzy Mode has ended! All bounty multipliers have returned to normal.";
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', defaultMessage));
            }
        } else {
            String frenzyEndMessage = String.join("\n", messageList);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', frenzyEndMessage));
            }
        }

        plugin.getLogger().info(frenzyType + " Frenzy Mode ended");
    }

    private void notifyPlayers(double multiplier, int duration) {
        if (enableTitleSubtitle) {
            String title = frenzyTitle;
            String subtitle = frenzySubtitle
                    .replace("%multiplier%", String.format("%.1f", multiplier))
                    .replace("%duration%", String.valueOf(duration));

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                VersionUtils.sendTitle(onlinePlayer, title, subtitle, 10, 70, 20);
            }
        }

        // Broadcast message
        List<String> messageList = plugin.getMessagesConfig().getStringList("frenzy-mode-activated-message");
        if (messageList.isEmpty()) {
            plugin.getLogger().warning("frenzy-mode-activated-message is missing in messages.yml");
            String defaultMessage = "&c&l[FRENZY MODE] &e&lAll bounties are now boosted by &c&l%multiplier%x &e&lfor %duration% seconds!";
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String message = defaultMessage
                        .replace("%multiplier%", String.format("%.1f", multiplier))
                        .replace("%duration%", String.valueOf(duration));
                onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        } else {
            String frenzyMessage = String.join("\n", messageList)
                    .replace("%multiplier%", String.format("%.1f", multiplier))
                    .replace("%duration%", String.valueOf(duration));
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', frenzyMessage));
            }
        }
    }

    private void broadcastNoBounties() {
        String message = plugin.getMessagesConfig().getString("no-bounties-for-frenzy", "&cNo bounties available for Frenzy Mode!");
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

    // Public methods for checking frenzy status
    public boolean isFrenzyActive() {
        // Double check time-based expiry
        if (frenzyActive && System.currentTimeMillis() > frenzyEndTime) {
            endFrenzy();
        }
        return frenzyActive;
    }

    public double getFrenzyMultiplier() {
        return isFrenzyActive() ? currentFrenzyMultiplier : 1.0;
    }

    public long getFrenzyTimeRemaining() {
        if (!isFrenzyActive()) return 0;
        return Math.max(0, (frenzyEndTime - System.currentTimeMillis()) / 1000);
    }

    public String getFrenzyEndTime() {
        if (!isFrenzyActive()) return null;
        long secondsRemaining = getFrenzyTimeRemaining();
        return TimeFormatter.formatTimeRemaining(secondsRemaining);
    }

    // Methods for tracking last frenzy info
    public double getLastFrenzyMultiplier() {
        return lastFrenzyMultiplier;
    }

    public String getLastFrenzyTime() {
        if (lastFrenzyTime == 0) {
            return "Never";
        }
        return TimeFormatter.formatTimestampToAgo(lastFrenzyTime);
    }

    public long getTimeUntilNextFrenzy() {
        // Calculate based on frenzy interval and last frenzy time
        long intervalMs = frenzyInterval * 1000L;
        long timeSinceLastStart = System.currentTimeMillis() - lastFrenzyStartTime;
        long timeUntilNext = intervalMs - (timeSinceLastStart % intervalMs);
        return timeUntilNext / 1000; // Return in seconds
    }

    // Player management methods
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
}