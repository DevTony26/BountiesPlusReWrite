package tony26.bountiesPlus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.utils.TimeFormatter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a bounty with money, items, XP, duration, and sponsors
 */
public class Bounty {
    private final BountiesPlus plugin;
    private final UUID targetUUID;
    private double originalMoney;
    private double currentMoney;
    private int originalXP;
    private int currentXP;
    private int originalDurationMinutes;
    private int currentDurationMinutes;
    private boolean isPermanent;
    private final List<BountyItem> originalItems;
    private final List<BountyItem> currentItems;
    private final Map<UUID, Sponsor> sponsors;

    public static class BountyItem {
        private final ItemStack item;
        private final UUID contributor;
        private final boolean isAnonymous;

        public BountyItem(ItemStack item, UUID contributor, boolean isAnonymous) {
            this.item = item;
            this.contributor = contributor;
            this.isAnonymous = isAnonymous;
        }

        public ItemStack getItem() { return item; }
        public UUID getContributor() { return contributor; }
        public boolean isAnonymous() { return isAnonymous; }
    }

    public static class Sponsor {
        private final UUID playerUUID;
        private double money;
        private int xp;
        private int durationMinutes;
        private final List<BountyItem> items;
        private boolean isAnonymous;
        private long setTime; // Added
        private long expireTime; // Added
        private double multiplier; // Added

        public Sponsor(UUID playerUUID) {
            this.playerUUID = playerUUID;
            this.money = 0.0;
            this.xp = 0;
            this.durationMinutes = 0;
            this.items = new ArrayList<>();
            this.isAnonymous = false;
            this.setTime = System.currentTimeMillis();
            this.expireTime = -1;
            this.multiplier = 1.0;
        }

        public void addMoney(double amount) { this.money += amount; }
        public void addXP(int levels) { this.xp += levels; }
        public void addDuration(int minutes) { this.durationMinutes += minutes; }
        public void addItem(BountyItem item) { this.items.add(item); }
        public void setAnonymous(boolean anonymous) { this.isAnonymous = anonymous; }
        public void setSetTime(long time) { this.setTime = time; } // Added
        public void setExpireTime(long time) { this.expireTime = time; } // Added
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; } // Added
        public UUID getPlayerUUID() { return playerUUID; }
        public double getMoney() { return money; }
        public int getXP() { return xp; }
        public int getDurationMinutes() { return durationMinutes; }
        public List<BountyItem> getItems() { return new ArrayList<>(items); }
        public boolean isAnonymous() { return isAnonymous; }
        public long getSetTime() { return setTime; } // Added
        public long getExpireTime() { return expireTime; } // Added
        public double getMultiplier() { return multiplier; } // Added
        public double getTotalValue(BountiesPlus plugin) {
            double itemValue = items.stream()
                    .mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item.getItem()))
                    .sum();
            return money + itemValue;
        }
    }

    /**
     * Constructs a new Bounty for a target player
     */
    public Bounty(BountiesPlus plugin, UUID targetUUID) {
        this.plugin = plugin;
        this.targetUUID = targetUUID;
        this.originalMoney = 0.0;
        this.currentMoney = 0.0;
        this.originalXP = 0;
        this.currentXP = 0;
        this.originalDurationMinutes = 0;
        this.currentDurationMinutes = 0;
        this.isPermanent = true;
        this.originalItems = new ArrayList<>();
        this.currentItems = new ArrayList<>();
        this.sponsors = new HashMap<>();
    }

    /**
     * Adds a contribution to the bounty
     */
    public void addContribution(UUID sponsorUUID, double money, int xp, int durationMinutes, List<ItemStack> items, boolean isAnonymous, boolean isInitial) {
        Sponsor sponsor = sponsors.computeIfAbsent(sponsorUUID, Sponsor::new);
        sponsor.addMoney(money);
        sponsor.addXP(xp);
        sponsor.addDuration(durationMinutes);
        sponsor.setAnonymous(isAnonymous);
        sponsor.setSetTime(System.currentTimeMillis());
        sponsor.setExpireTime(money > 0 ? System.currentTimeMillis() + (plugin.getConfig().getInt("default-bounty-duration", 1440) * 60 * 1000L) : -1);
        sponsor.setMultiplier(1.0);
        for (ItemStack item : items) {
            if (item != null && !item.getType().equals(Material.AIR)) {
                BountyItem bountyItem = new BountyItem(item.clone(), sponsorUUID, isAnonymous);
                if (isInitial) {
                    originalItems.add(bountyItem);
                }
                currentItems.add(bountyItem);
                sponsor.addItem(bountyItem);
            }
        }
        if (isInitial) {
            originalMoney += money;
            originalXP += xp;
            originalDurationMinutes += durationMinutes;
        }
        currentMoney += money;
        currentXP += xp;
        currentDurationMinutes += durationMinutes;
        isPermanent = currentDurationMinutes == 0;
    }

    public List<Sponsor> getTopSponsors(int limit) {
        return sponsors.values().stream()
                .sorted((s1, s2) -> Double.compare(s2.getTotalValue(BountiesPlus.getInstance()), s1.getTotalValue(BountiesPlus.getInstance())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Removes a sponsor and their contributions from the bounty
     * Returns true if the sponsor was removed, false otherwise
     */
    public boolean removeSponsor(UUID sponsorUUID) {
        Sponsor sponsor = sponsors.remove(sponsorUUID);
        if (sponsor != null) {
            currentMoney -= sponsor.getMoney();
            currentXP -= sponsor.getXP();
            currentDurationMinutes -= sponsor.getDurationMinutes();
            currentItems.removeIf(item -> item.getContributor().equals(sponsorUUID));
            isPermanent = currentDurationMinutes == 0;
            return true;
        }
        return false;
    }

    public List<BountyItem> getSortedItems() {
        Map<UUID, List<BountyItem>> itemsByContributor = new LinkedHashMap<>();
        sponsors.keySet().stream()
                .sorted((uuid1, uuid2) -> {
                    String name1 = Bukkit.getOfflinePlayer(uuid1).getName();
                    String name2 = Bukkit.getOfflinePlayer(uuid2).getName();
                    return name1 != null && name2 != null ? name1.compareToIgnoreCase(name2) : 0;
                })
                .forEach(uuid -> itemsByContributor.put(uuid, new ArrayList<>()));
        for (BountyItem item : currentItems) {
            itemsByContributor.computeIfAbsent(item.getContributor(), k -> new ArrayList<>()).add(item);
        }
        List<BountyItem> sortedItems = new ArrayList<>();
        for (List<BountyItem> contributorItems : itemsByContributor.values()) {
            sortedItems.addAll(contributorItems);
        }
        return sortedItems;
    }

    public UUID getTargetUUID() { return targetUUID; }
    public double getOriginalMoney() { return originalMoney; }
    public double getCurrentMoney() { return currentMoney; }
    public double getPriceIncreasePercent() {
        return originalMoney == 0 ? 0 : ((currentMoney - originalMoney) / originalMoney) * 100;
    }
    public int getOriginalXP() { return originalXP; }
    public int getCurrentXP() { return currentXP; }
    public double getXPLevelIncreasePercent() {
        return originalXP == 0 ? 0 : ((double) (currentXP - originalXP) / originalXP) * 100;
    }
    public int getOriginalDurationMinutes() { return originalDurationMinutes; }
    public int getCurrentDurationMinutes() { return currentDurationMinutes; }
    public boolean isPermanent() { return isPermanent; }
    public double getDurationIncreasePercent() {
        return originalDurationMinutes == 0 ? 0 : ((double) (currentDurationMinutes - originalDurationMinutes) / originalDurationMinutes) * 100;
    }
    public List<BountyItem> getOriginalItems() { return new ArrayList<>(originalItems); }
    public List<BountyItem> getCurrentItems() { return new ArrayList<>(currentItems); }
    public double getOriginalItemValue() {
        return originalItems.stream()
                .mapToDouble(item -> BountiesPlus.getInstance().getItemValueCalculator().calculateItemValue(item.getItem()))
                .sum();
    }
    public double getCurrentItemValue() {
        return currentItems.stream()
                .mapToDouble(item -> BountiesPlus.getInstance().getItemValueCalculator().calculateItemValue(item.getItem()))
                .sum();
    }
    public double getItemIncreasePercent() {
        double originalValue = getOriginalItemValue();
        return originalValue == 0 ? 0 : ((getCurrentItemValue() - originalValue) / originalValue) * 100;
    }
    public double getOriginalPool() { return originalMoney + getOriginalItemValue(); }
    public double getCurrentPool() { return currentMoney + getCurrentItemValue(); }
    public double getPoolIncreasePercent() {
        double originalPool = getOriginalPool();
        return originalPool == 0 ? 0 : ((getCurrentPool() - originalPool) / originalPool) * 100;
    }
    public String getFormattedOriginalDuration() {
        return TimeFormatter.formatMinutesToReadable(originalDurationMinutes, originalDurationMinutes == 0);
    }
    public String getFormattedCurrentDuration() {
        return TimeFormatter.formatMinutesToReadable(currentDurationMinutes, isPermanent);
    }
    public List<Sponsor> getSponsors() { return new ArrayList<>(sponsors.values()); }
}