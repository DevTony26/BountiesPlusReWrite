
package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import tony26.bountiesPlus.utils.DebugManager;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BountyManager {

    private final BountiesPlus plugin;
    private final Map<UUID, Bounty> bounties = new HashMap<>();
    private final Map<UUID, Double> manualMoneyBoosts = new HashMap<>();
    private final Map<UUID, Double> manualXpBoosts = new HashMap<>();
    private final Map<UUID, Long> manualBoostExpireTimes = new HashMap<>();

    /**
     * Constructs the BountyManager
     * // note: Initializes bounty management and loads bounties from storage
     */
    public BountyManager(BountiesPlus plugin, List<String> warnings) {
        this.plugin = plugin;
        loadBounties(warnings);
        startExpiryCheck();
    }

    /**
     * Gets the manual XP boost multiplier for a player
     */
    public double getManualXpBoostMultiplier(UUID targetUUID) {
        Long expireTime = manualBoostExpireTimes.get(targetUUID);
        if (expireTime != null && System.currentTimeMillis() > expireTime) {
            removeManualBoost(targetUUID, "XP");
            return 1.0;
        }
        return manualXpBoosts.getOrDefault(targetUUID, 1.0);
    }

    /**
     * Gets the manual money boost multiplier for a player
     */
    public double getManualMoneyBoostMultiplier(UUID targetUUID) {
        Long expireTime = manualBoostExpireTimes.get(targetUUID);
        if (expireTime != null && System.currentTimeMillis() > expireTime) {
            removeManualBoost(targetUUID, "MONEY");
            return 1.0;
        }
        return manualMoneyBoosts.getOrDefault(targetUUID, 1.0);
    }

    /**
     * Applies a manual boost to a player's bounty
     * // note: Updates multiplier and expire time in storage
     */
    public void applyManualBoost(UUID targetUUID, double multiplier, String boostType, int timeMinutes) {
        long expireTime = System.currentTimeMillis() + (timeMinutes * 60 * 1000L);
        Bounty bounty = bounties.computeIfAbsent(targetUUID, k -> new Bounty(plugin, targetUUID));
        if (boostType.equalsIgnoreCase("MONEY") || boostType.equalsIgnoreCase("both")) {
            manualMoneyBoosts.put(targetUUID, multiplier);
        }
        if (boostType.equalsIgnoreCase("XP") || boostType.equalsIgnoreCase("both")) {
            manualXpBoosts.put(targetUUID, multiplier);
        }
        manualBoostExpireTimes.put(targetUUID, expireTime);
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            if (boostType.equalsIgnoreCase("MONEY") || boostType.equalsIgnoreCase("both")) {
                sponsor.setMultiplier(multiplier);
            }
            if (boostType.equalsIgnoreCase("XP") || boostType.equalsIgnoreCase("both")) {
                sponsor.setMultiplier(multiplier);
            }
            sponsor.setExpireTime(expireTime);
        }
        if (plugin.getMySQL().isEnabled()) {
            saveManualBoostToYAML(targetUUID, multiplier, expireTime);
        } else {
            saveManualBoostToYAML(targetUUID, multiplier, expireTime);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeManualBoost(targetUUID, boostType);
        }, timeMinutes * 60 * 20L);
    }

    /**
     * Removes a manual boost from a player's bounty
     * // note: Resets multiplier and expire time in storage
     */
    public void removeManualBoost(UUID targetUUID, String boostType) {
        Bounty bounty = bounties.get(targetUUID);
        if (boostType.equalsIgnoreCase("MONEY") || boostType.equalsIgnoreCase("both")) {
            manualMoneyBoosts.remove(targetUUID);
        }
        if (boostType.equalsIgnoreCase("XP") || boostType.equalsIgnoreCase("both")) {
            manualXpBoosts.remove(targetUUID);
        }
        manualBoostExpireTimes.remove(targetUUID);
        if (bounty != null) {
            for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
                if (boostType.equalsIgnoreCase("MONEY") || boostType.equalsIgnoreCase("both")) {
                    sponsor.setMultiplier(1.0);
                }
                if (boostType.equalsIgnoreCase("XP") || boostType.equalsIgnoreCase("both")) {
                    sponsor.setMultiplier(1.0);
                }
                sponsor.setExpireTime(-1L);
            }
            if (plugin.getMySQL().isEnabled()) {
                removeManualBoostFromYAML(targetUUID);
            } else {
                removeManualBoostFromYAML(targetUUID);
            }
        }
    }

    /**
     * Checks if a player has an active manual boost
     * // note: Verifies if a manual boost is active for a player
     */
    public boolean hasActiveManualBoost(UUID targetUUID) {
        Bounty bounty = bounties.get(targetUUID);
        if (bounty == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            return false;
        }
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            long expireTime = config.getLong("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".expire_time", -1);
            if (expireTime > 0 && currentTime < expireTime) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a specific bounty contribution
     * // note: Removes a sponsor's bounty and updates tablist
     */
    public void removeBounty(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty != null) {
            if (bounty.removeSponsor(setter)) {
                if (plugin.getMySQL().isEnabled()) {
                    plugin.getMySQL().removeBountyAsync(setter, target).exceptionally(e -> {
                        plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to remove bounty asynchronously: " + e.getMessage());
                        removeFromYAML(setter, target);
                        return null;
                    });
                } else {
                    removeFromYAML(setter, target);
                }
                if (bounty.getSponsors().isEmpty()) {
                    bounties.remove(target);
                }
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer != null && !hasBounty(target)) {
                    plugin.getTablistManager().removeTablistName(targetPlayer);
                }
            }
        }
    }

    /**
     * Loads bounties from configuration or MySQL
     * // note: Populates the bounties map from storage
     */
    private void loadBounties(List<String> warnings) {
        bounties.clear();
        if (plugin.getMySQL().isEnabled()) {
            plugin.getMySQL().loadBountiesAsync().thenAccept(loadedBounties -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Map.Entry<UUID, Map<UUID, Integer>> entry : loadedBounties.entrySet()) {
                        UUID targetUUID = entry.getKey();
                        Bounty bounty = new Bounty(plugin, targetUUID);
                        for (Map.Entry<UUID, Integer> sponsorEntry : entry.getValue().entrySet()) {
                            UUID setterUUID = sponsorEntry.getKey();
                            int amount = sponsorEntry.getValue();
                            bounty.addContribution(setterUUID, amount, 0, 0, new ArrayList<>(), false, true);
                        }
                        if (!bounty.getSponsors().isEmpty()) {
                            bounties.put(targetUUID, bounty);
                        }
                    }
                    plugin.getLogger().info("Loaded " + bounties.size() + " bounties from MySQL.");
                });
            }).exceptionally(e -> {
                warnings.add("Failed to load bounties from MySQL: " + e.getMessage());
                loadBountiesFromYAML(warnings);
                return null;
            });
        } else {
            loadBountiesFromYAML(warnings);
        }
    }

    /**
     * Loads bounties from YAML configuration
     * // note: Fallback method to load bounties from BountyStorage.yml
     */
    private void loadBountiesFromYAML(List<String> warnings) {
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            warnings.add("BountyStorage.yml not loaded, cannot load bounties!");
            return;
        }
        if (!config.isConfigurationSection("bounties")) {
            return;
        }
        for (String targetUUIDStr : config.getConfigurationSection("bounties").getKeys(false)) {
            UUID targetUUID;
            try {
                targetUUID = UUID.fromString(targetUUIDStr);
            } catch (IllegalArgumentException e) {
                warnings.add("Invalid UUID in BountyStorage.yml: " + targetUUIDStr);
                continue;
            }
            Bounty bounty = new Bounty(plugin, targetUUID);
            for (String setterUUIDStr : config.getConfigurationSection("bounties." + targetUUIDStr).getKeys(false)) {
                UUID setterUUID;
                try {
                    setterUUID = UUID.fromString(setterUUIDStr);
                } catch (IllegalArgumentException e) {
                    warnings.add("Invalid setter UUID in BountyStorage.yml: " + setterUUIDStr);
                    continue;
                }
                double amount = config.getDouble("bounties." + targetUUIDStr + "." + setterUUIDStr + ".amount", 0.0);
                int xp = config.getInt("bounties." + targetUUIDStr + "." + setterUUIDStr + ".xp", 0);
                int durationMinutes = config.getInt("bounties." + targetUUIDStr + "." + setterUUIDStr + ".duration", 0);
                boolean isAnonymous = config.getBoolean("anonymous-bounties." + targetUUIDStr + "." + setterUUIDStr, false);
                long setTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".set_time", System.currentTimeMillis());
                long expireTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".expire_time", -1);
                double multiplier = config.getDouble("bounties." + targetUUIDStr + "." + setterUUIDStr + ".multiplier", 1.0);
                List<ItemStack> items = new ArrayList<>();
                List<String> itemStrings = config.getStringList("bounties." + targetUUIDStr + "." + setterUUIDStr + ".items");
                for (String itemStr : itemStrings) {
                    String[] parts = itemStr.split(":");
                    if (parts.length == 2) {
                        try {
                            Material material = Material.valueOf(parts[0]);
                            int itemAmount = Integer.parseInt(parts[1]);
                            items.add(new ItemStack(material, itemAmount));
                        } catch (IllegalArgumentException e) {
                            warnings.add("Invalid item in BountyStorage.yml: " + itemStr);
                        }
                    }
                }
                if (amount > 0 || xp > 0 || durationMinutes > 0 || !items.isEmpty()) {
                    bounty.addContribution(setterUUID, amount, xp, durationMinutes, items, isAnonymous, true);
                    Bounty.Sponsor sponsor = bounty.getSponsors().stream()
                            .filter(s -> s.getPlayerUUID().equals(setterUUID))
                            .findFirst()
                            .orElse(null);
                    if (sponsor != null) {
                        sponsor.setSetTime(setTime);
                        sponsor.setExpireTime(expireTime);
                        sponsor.setMultiplier(multiplier);
                    }
                }
            }
            if (!bounty.getSponsors().isEmpty()) {
                bounties.put(targetUUID, bounty);
            }
        }
        plugin.getLogger().info("Loaded " + bounties.size() + " bounties from YAML.");
    }

    /**
     * Sets a bounty with specified parameters
     * // note: Creates or updates a bounty in storage and updates tablist
     */
    /**
     * Sets a bounty with specified parameters
     * // note: Creates or updates a bounty in storage and updates tablist
     */
    public void setBounty(UUID setter, UUID target, int amount, long expireTime) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[BountyManager] Setting bounty: setter=" + setter + ", target=" + target + ", amount=" + amount + ", expireTime=" + expireTime);
        Bounty bounty = bounties.computeIfAbsent(target, k -> new Bounty(plugin, target));
        bounty.addContribution(setter, amount, 0, 0, new ArrayList<>(), false, !bounties.containsKey(target));
        if (plugin.getMySQL().isEnabled()) {
            plugin.getMySQL().setBountyAsync(setter, target, amount, expireTime).exceptionally(e -> {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to set bounty asynchronously: " + e.getMessage());
                saveToYAML(setter, target, amount, 0, 0, false, expireTime, new ArrayList<>());
                return null;
            });
        } else {
            saveToYAML(setter, target, amount, 0, 0, false, expireTime, new ArrayList<>());
        }
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            debugManager.logDebug("[BountyManager] Target player " + targetPlayer.getName() + " is online, scheduling tablist update");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getTablistManager().applyTablistName(targetPlayer);
            }, 10L);
        } else {
            debugManager.logDebug("[BountyManager] Target player " + target + " is offline, tablist update skipped");
        }
    }

    /**
     * Adds a regular bounty
     */
    public void addBounty(UUID target, UUID setter, int amount) {
        setBounty(setter, target, amount, -1);
    }

    /**
     * Adds an anonymous bounty
     * // note: Creates a bounty with money, items, XP, and duration, marking it as anonymous and updates tablist
     */
    public void addAnonymousBounty(UUID target, UUID setter, double amount, int xp, int durationMinutes, List<ItemStack> items) {
        DebugManager debugManager = plugin.getDebugManager();
        boolean useXpLevels = plugin.getConfig().getBoolean("use-xp-levels", false);
        debugManager.logDebug("[BountyManager] Adding anonymous bounty for target: " + target +
                ", setter: " + setter + ", amount: $" + amount + ", " + (useXpLevels ? "levels: " : "xp: ") + xp +
                ", duration: " + durationMinutes + " minutes, items: " + items.size());
        Bounty bounty = bounties.computeIfAbsent(target, k -> new Bounty(plugin, target));
        bounty.addContribution(setter, amount, xp, durationMinutes, items, true, !bounties.containsKey(target));
        if (plugin.getMySQL().isEnabled()) {
            saveToYAML(setter, target, amount, xp, durationMinutes, true, -1, items);
        } else {
            saveToYAML(setter, target, amount, xp, durationMinutes, true, -1, items);
        }
        debugManager.logDebug("[BountyManager] Saved anonymous bounty for target: " + target);
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            debugManager.logDebug("[BountyManager] Target player " + targetPlayer.getName() + " is online, scheduling tablist update");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getTablistManager().applyTablistName(targetPlayer);
            }, 10L);
        } else {
            debugManager.logDebug("[BountyManager] Target player " + target + " is offline, tablist update skipped");
        }
    }

    /**
     * Gets the bounty for a target
     */
    public Bounty getBounty(UUID target) {
        return bounties.get(target);
    }

    /**
     * Clears all bounties on a target
     * // note: Removes all bounties and updates tablist
     */
    public void clearBounties(UUID target) {
        bounties.remove(target);
        if (plugin.getMySQL().isEnabled()) {
            plugin.getMySQL().clearBountiesAsync(target).exceptionally(e -> {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to clear bounties asynchronously: " + e.getMessage());
                clearFromYAML(target);
                return null;
            });
        } else {
            clearFromYAML(target);
        }
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            plugin.getTablistManager().removeTablistName(targetPlayer);
        }
    }

    public Map<UUID, Integer> getBountiesOnTarget(UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return new HashMap<>();
        }
        Map<UUID, Integer> targetBounties = new HashMap<>();
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            targetBounties.put(sponsor.getPlayerUUID(), (int) sponsor.getMoney());
        }
        return targetBounties;
    }

    public Map<UUID, Map<UUID, Integer>> listAllBounties() {
        Map<UUID, Map<UUID, Integer>> allBounties = new HashMap<>();
        for (Map.Entry<UUID, Bounty> entry : bounties.entrySet()) {
            UUID target = entry.getKey();
            Bounty bounty = entry.getValue();
            Map<UUID, Integer> targetBounties = new HashMap<>();
            for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
                targetBounties.put(sponsor.getPlayerUUID(), (int) sponsor.getMoney());
            }
            allBounties.put(target, targetBounties);
        }
        return allBounties;
    }

    public String getBountySetTime(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return null;
        }
        FileConfiguration config = plugin.getBountiesConfig();
        long setTime = config.getLong("bounties." + target + "." + setter + ".set_time", -1);
        if (setTime == -1) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(setTime));
    }

    public String getBountyExpireTime(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return "&4&k|||&4 &4&mDeath Contract&4 &4&k|||";
        }
        FileConfiguration config = plugin.getBountiesConfig();
        long expireTime = config.getLong("bounties." + target + "." + setter + ".expire_time", -1);
        if (expireTime <= 0) {
            return "&4&k|||&4 &4&mDeath Contract&4 &4&k|||";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(expireTime));
    }

    public double getBountyMultiplier(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return 1.0;
        }
        FileConfiguration config = plugin.getBountiesConfig();
        return config.getDouble("bounties." + target + "." + setter + ".multiplier", 1.0);
    }

    public boolean hasBounty(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return false;
        }
        return bounty.getSponsors().stream().anyMatch(sponsor -> sponsor.getPlayerUUID().equals(setter));
    }

    public boolean hasBounty(UUID target) {
        Bounty bounty = bounties.get(target);
        return bounty != null && !bounty.getSponsors().isEmpty();
    }

    public int getBountyAmount(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return 0;
        }
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            if (sponsor.getPlayerUUID().equals(setter)) {
                return (int) sponsor.getMoney();
            }
        }
        return 0;
    }

    public int getTotalBountyAmount(UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return 0;
        }
        return (int) bounty.getCurrentMoney();
    }

    public Set<UUID> getTargetsWithBounties() {
        return new HashSet<>(bounties.keySet());
    }

    public Set<UUID> getBountySetters(UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty == null) {
            return new HashSet<>();
        }
        Set<UUID> setters = new HashSet<>();
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            setters.add(sponsor.getPlayerUUID());
        }
        return setters;
    }

    private void startExpiryCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkExpiredBounties, 6000L, 6000L);
    }

    private void checkExpiredBounties() {
        long currentTime = System.currentTimeMillis();
        List<UUID> targetsToRemove = new ArrayList<>();
        FileConfiguration config = plugin.getBountiesConfig();

        for (Map.Entry<UUID, Bounty> entry : bounties.entrySet()) {
            UUID target = entry.getKey();
            Bounty bounty = entry.getValue();
            List<UUID> settersToRemove = new ArrayList<>();

            for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
                UUID setter = sponsor.getPlayerUUID();
                long expireTime = config.getLong("bounties." + target + "." + setter + ".expire_time", -1);
                if (expireTime > 0 && currentTime > expireTime) {
                    settersToRemove.add(setter);
                    plugin.getLogger().info("Bounty expired: " + setter + " -> " + target);
                }
            }

            for (UUID setter : settersToRemove) {
                config.set("bounties." + target + "." + setter, null);
                config.set("anonymous-bounties." + target + "." + setter, null);
            }

            if (settersToRemove.size() == bounty.getSponsors().size()) {
                targetsToRemove.add(target);
                config.set("bounties." + target, null);
                config.set("anonymous-bounties." + target, null);
            }
        }

        for (UUID target : targetsToRemove) {
            bounties.remove(target);
        }

        if (!targetsToRemove.isEmpty()) {
            plugin.saveEverything();
            plugin.getLogger().info("Removed " + targetsToRemove.size() + " expired bounty targets");
        }
    }

    /**
     * Retrieves items contributed to a bounty by a setter
     * // note: Returns the list of items from a specific sponsor’s contribution
     */
    public List<ItemStack> getBountyItems(UUID targetUUID, UUID setterUUID) {
        Bounty bounty = bounties.get(targetUUID);
        if (bounty == null) {
            return new ArrayList<>();
        }
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            if (sponsor.getPlayerUUID().equals(setterUUID)) {
                return sponsor.getItems().stream()
                        .map(Bounty.BountyItem::getItem)
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    /**
     * Saves a bounty to YAML
     * // note: Updates BountyStorage.yml with bounty data
     */
    private void saveToYAML(UUID setter, UUID target, double amount, int xp, int durationMinutes, boolean isAnonymous, long expireTime, List<ItemStack> items) {
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            plugin.getLogger().warning("BountyStorage.yml not loaded, cannot save bounty for " + target);
            return;
        }
        long setTime = System.currentTimeMillis();
        String path = "bounties." + target + "." + setter;
        config.set(path + ".amount", amount);
        config.set(path + ".xp", xp);
        config.set(path + ".duration", durationMinutes);
        config.set(path + ".set_time", setTime);
        config.set(path + ".expire_time", expireTime);
        config.set(path + ".multiplier", 1.0);
        config.set("anonymous-bounties." + target + "." + setter, isAnonymous);
        List<String> itemStrings = items.stream()
                .filter(item -> item != null && !item.getType().equals(Material.AIR))
                .map(item -> item.getType().name() + ":" + item.getAmount())
                .collect(Collectors.toList());
        config.set(path + ".items", itemStrings);
        plugin.saveEverything();
    }

    /**
     * Removes a bounty from YAML
     * // note: Deletes a bounty record from BountyStorage.yml
     */
    private void removeFromYAML(UUID setter, UUID target) {
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            plugin.getLogger().warning("BountyStorage.yml not loaded, cannot remove bounty for " + target);
            return;
        }
        config.set("bounties." + target + "." + setter, null);
        config.set("anonymous-bounties." + target + "." + setter, null);
        if (config.getConfigurationSection("bounties." + target) == null || config.getConfigurationSection("bounties." + target).getKeys(false).isEmpty()) {
            config.set("bounties." + target, null);
            config.set("anonymous-bounties." + target, null);
        }
        plugin.saveEverything();
    }

    /**
     * Clears all bounties for a target from YAML
     * // note: Deletes all bounty records for a target from BountyStorage.yml
     */
    private void clearFromYAML(UUID target) {
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            plugin.getLogger().warning("BountyStorage.yml not loaded, cannot clear bounties for " + target);
            return;
        }
        config.set("bounties." + target, null);
        config.set("anonymous-bounties." + target, null);
        plugin.saveEverything();
    }

    /**
     * Saves a manual boost to YAML
     * // note: Updates BountyStorage.yml with manual boost data
     */
    private void saveManualBoostToYAML(UUID targetUUID, double multiplier, long expireTime) {
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            plugin.getLogger().warning("BountyStorage.yml not loaded, cannot save manual boost for " + targetUUID);
            return;
        }
        for (Bounty.Sponsor sponsor : bounties.getOrDefault(targetUUID, new Bounty(plugin, targetUUID)).getSponsors()) {
            config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".multiplier", multiplier);
            config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".expire_time", expireTime);
        }
        plugin.saveEverything();
    }

    /**
     * Removes a manual boost from YAML
     * // note: Resets manual boost data in BountyStorage.yml
     */
    private void removeManualBoostFromYAML(UUID targetUUID) {
        FileConfiguration config = plugin.getBountiesConfig();
        if (config == null) {
            plugin.getLogger().warning("BountyStorage.yml not loaded, cannot remove manual boost for " + targetUUID);
            return;
        }
        for (Bounty.Sponsor sponsor : bounties.getOrDefault(targetUUID, new Bounty(plugin, targetUUID)).getSponsors()) {
            config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".multiplier", 1.0);
            config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".expire_time", -1);
        }
        plugin.saveEverything();
    }

    /**
     * Reloads bounties from storage
     * // note: Refreshes the bounties map
     */
    public void reload(List<String> warnings) {
        loadBounties(warnings);
    }
    /**
     * Cleans up the bounty manager
     * // note: Saves bounties and clears the bounties map
     */
    public void cleanup() {
        saveBounties();
        bounties.clear();
    }

    /**
     * Saves all bounty data to storage // note: Persists active bounties and items to YAML
     */
    public void saveBounties() {
        FileConfiguration storage = plugin.getBountiesConfig();
        storage.set("bounties", null); // Clear existing data
        storage.set("anonymous-bounties", null); // Clear anonymous data
        for (Map.Entry<UUID, Bounty> bountyEntry : bounties.entrySet()) {
            UUID targetUUID = bountyEntry.getKey();
            Bounty bounty = bountyEntry.getValue();
            for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
                UUID setterUUID = sponsor.getPlayerUUID();
                String path = "bounties." + targetUUID + "." + setterUUID;
                storage.set(path + ".amount", sponsor.getMoney());
                storage.set(path + ".xp", sponsor.getXP());
                storage.set(path + ".duration", sponsor.getDurationMinutes());
                storage.set(path + ".set_time", sponsor.getSetTime());
                storage.set(path + ".expire_time", sponsor.getExpireTime());
                storage.set(path + ".multiplier", sponsor.getMultiplier());
                storage.set("anonymous-bounties." + targetUUID + "." + setterUUID, sponsor.isAnonymous());
                List<String> itemStrings = sponsor.getItems().stream()
                        .filter(item -> item.getItem() != null && !item.getItem().getType().equals(Material.AIR))
                        .map(item -> item.getItem().getType().name() + ":" + item.getItem().getAmount())
                        .collect(Collectors.toList());
                storage.set(path + ".items", itemStrings);
            }
        }
        plugin.saveEverything();
    }
}