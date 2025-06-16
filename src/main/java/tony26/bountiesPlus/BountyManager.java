
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

    public BountyManager(BountiesPlus plugin) {
        this.plugin = plugin;
        loadBounties();
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
        FileConfiguration config = plugin.getBountiesConfig();
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".multiplier", multiplier);
            config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".expire_time", expireTime);
        }
        plugin.saveEverything();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeManualBoost(targetUUID, boostType);
        }, timeMinutes * 60 * 20L);
    }

    /**
     * Removes a manual boost from a player's bounty
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
            FileConfiguration config = plugin.getBountiesConfig();
            for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
                config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".multiplier", 1.0);
                config.set("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".expire_time", -1);
            }
            plugin.saveEverything();
        }
    }

    /**
     * Checks if a player has an active manual boost
     */
    public boolean hasActiveManualBoost(UUID targetUUID) {
        Bounty bounty = bounties.get(targetUUID);
        if (bounty == null) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        FileConfiguration config = plugin.getBountiesConfig();
        for (Bounty.Sponsor sponsor : bounty.getSponsors()) {
            long expireTime = config.getLong("bounties." + targetUUID + "." + sponsor.getPlayerUUID() + ".expire_time", -1);
            if (expireTime > 0 && currentTime < expireTime) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a specific bounty contribution // note: Removes a sponsor's bounty and updates tablist
     */
    public void removeBounty(UUID setter, UUID target) {
        Bounty bounty = bounties.get(target);
        if (bounty != null) {
            if (bounty.removeSponsor(setter)) {
                FileConfiguration config = plugin.getBountiesConfig();
                config.set("bounties." + target + "." + setter, null);
                config.set("anonymous-bounties." + target + "." + setter, null);
                if (bounty.getSponsors().isEmpty()) {
                    bounties.remove(target);
                    config.set("bounties." + target, null);
                    config.set("anonymous-bounties." + target, null);
                }
                plugin.saveEverything();
                FileConfiguration statsConfig = plugin.getStatsConfig();
                int bountiesSurvived = statsConfig.getInt("players." + target + ".survived", 0) + 1;
                int bountiesClaimed = statsConfig.getInt("players." + target + ".claimed", 0);
                double moneyEarned = statsConfig.getDouble("players." + target + ".money_earned", 0.0);
                int reputation = bountiesClaimed * 10 + bountiesSurvived * 5;
                statsConfig.set("players." + target + ".survived", bountiesSurvived);
                statsConfig.set("players." + target + ".reputation", reputation);
                plugin.saveEverything();
                Player targetPlayer = Bukkit.getPlayer(target);
                if (targetPlayer != null && !hasBounty(target)) {
                    plugin.getTablistManager().removeTablistName(targetPlayer); // Remove tablist name if no bounties remain
                }
            }
        }
    }

    /**
     * Loads bounties from configuration
     */
    private void loadBounties() {
        FileConfiguration config = plugin.getBountiesConfig();
        if (!config.isConfigurationSection("bounties")) {
            return;
        }
        for (String targetUUIDStr : config.getConfigurationSection("bounties").getKeys(false)) {
            UUID targetUUID;
            try {
                targetUUID = UUID.fromString(targetUUIDStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in BountyStorage.yml: " + targetUUIDStr);
                continue;
            }
            Bounty bounty = new Bounty(plugin, targetUUID);
            for (String setterUUIDStr : config.getConfigurationSection("bounties." + targetUUIDStr).getKeys(false)) {
                UUID setterUUID;
                try {
                    setterUUID = UUID.fromString(setterUUIDStr);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid setter UUID in BountyStorage.yml: " + setterUUIDStr);
                    continue;
                }
                int amount = config.getInt("bounties." + targetUUIDStr + "." + setterUUIDStr + ".amount", 0);
                boolean isAnonymous = config.getBoolean("anonymous-bounties." + targetUUIDStr + "." + setterUUIDStr, false);
                long setTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".set_time", System.currentTimeMillis());
                long expireTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".expire_time", -1);
                double multiplier = config.getDouble("bounties." + targetUUIDStr + "." + setterUUIDStr + ".multiplier", 1.0);
                if (amount > 0) {
                    bounty.addContribution(setterUUID, amount, 0, 0, new ArrayList<>(), isAnonymous, true);
                }
            }
            if (!bounty.getSponsors().isEmpty()) {
                bounties.put(targetUUID, bounty);
            }
        }
    }

    /**
     * Sets a bounty with specified parameters // note: Creates or updates a bounty and updates tablist
     */
    public void setBounty(UUID setter, UUID target, int amount, long expireTime) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[BountyManager] Setting bounty: setter=" + setter + ", target=" + target + ", amount=" + amount + ", expireTime=" + expireTime);
        Bounty bounty = bounties.computeIfAbsent(target, k -> new Bounty(plugin, target));
        bounty.addContribution(setter, amount, 0, 0, new ArrayList<>(), false, !bounties.containsKey(target));
        FileConfiguration config = plugin.getBountiesConfig();
        long setTime = System.currentTimeMillis();
        config.set("bounties." + target + "." + setter + ".amount", amount);
        config.set("bounties." + target + "." + setter + ".set_time", setTime);
        config.set("bounties." + target + "." + setter + ".expire_time", expireTime);
        config.set("bounties." + target + "." + setter + ".multiplier", 1.0);
        plugin.saveEverything();
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            debugManager.logDebug("[BountyManager] Target player " + targetPlayer.getName() + " is online, scheduling tablist update");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getTablistManager().applyTablistName(targetPlayer);
            }, 10L); // 0.5-second delay
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
     * Adds an anonymous bounty // note: Creates a bounty with money, items, XP, and duration, marking it as anonymous and updates tablist
     */
    public void addAnonymousBounty(UUID target, UUID setter, double amount, int xp, int durationMinutes, List<ItemStack> items) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[BountyManager] Adding anonymous bounty for target: " + target +
                ", setter: " + setter + ", amount: $" + amount + ", items: " + items.size() +
                ", xp: " + xp + ", duration: " + durationMinutes + " minutes");
        Bounty bounty = bounties.computeIfAbsent(target, k -> new Bounty(plugin, target));
        bounty.addContribution(setter, amount, xp, durationMinutes, items, true, !bounties.containsKey(target));
        long expireTime = durationMinutes > 0 ?
                System.currentTimeMillis() + (durationMinutes * 60 * 1000L) :
                System.currentTimeMillis() + (plugin.getConfig().getInt("default-bounty-duration", 1440) * 60 * 1000L);
        FileConfiguration config = plugin.getBountiesConfig();
        config.set("bounties." + target + "." + setter + ".amount", amount);
        config.set("bounties." + target + "." + setter + ".xp", xp);
        config.set("bounties." + target + "." + setter + ".duration", durationMinutes);
        config.set("bounties." + target + "." + setter + ".set_time", System.currentTimeMillis());
        config.set("bounties." + target + "." + setter + ".expire_time", expireTime);
        config.set("bounties." + target + "." + setter + ".multiplier", 1.0);
        config.set("anonymous-bounties." + target + "." + setter, true);
        List<String> itemStrings = items.stream()
                .filter(item -> item != null && !item.getType().equals(Material.AIR))
                .map(item -> item.getType().name() + ":" + item.getAmount())
                .collect(Collectors.toList());
        config.set("bounties." + target + "." + setter + ".items", itemStrings);
        debugManager.logDebug("[BountyManager] Saved " + itemStrings.size() + " items to config: " + itemStrings);
        plugin.saveEverything();
        debugManager.logDebug("[BountyManager] Saved anonymous bounty to config for target: " + target);
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            debugManager.logDebug("[BountyManager] Target player " + targetPlayer.getName() + " is online, scheduling tablist update");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getTablistManager().applyTablistName(targetPlayer);
            }, 10L); // 0.5-second delay
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
     * Clears all bounties on a target // note: Removes all bounties and updates tablist
     */
    public void clearBounties(UUID target) {
        bounties.remove(target);
        FileConfiguration config = plugin.getBountiesConfig();
        config.set("bounties." + target, null);
        config.set("anonymous-bounties." + target, null);
        plugin.saveEverything();
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            plugin.getTablistManager().removeTablistName(targetPlayer); // Remove tablist name
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

    public void cleanup() {
        saveBounties();
        bounties.clear();
    }

    public void saveBounties() {
        plugin.saveEverything();
    }
}