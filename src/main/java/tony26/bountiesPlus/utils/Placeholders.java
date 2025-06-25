package tony26.bountiesPlus.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.Bounty;
import tony26.bountiesPlus.GUIs.AddItemsGUI;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;


/**
 * Handles placeholder registration and application for BountiesPlus
 * // note: Registers and processes placeholders for dynamic data in GUIs and messages
 */
public class Placeholders extends PlaceholderExpansion {
    private final BountiesPlus plugin;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    private static final Map<UUID, PlaceholderContext> contextMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, AtomicLong> debugLogCounts = new ConcurrentHashMap<>();

    public Placeholders(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Apply all placeholders to a string using PlaceholderAPI
     * // note: Replaces placeholders with context data
     */
    public static String apply(String text, PlaceholderContext context) {
        if (text == null) return "";
        if (context == null || context.getPlayer() == null) {
            String result = PlaceholderAPI.setPlaceholders(null, text);
            return ChatColor.translateAlternateColorCodes('&', result);
        }
        contextMap.put(context.getPlayer().getUniqueId(), context);
        String result = PlaceholderAPI.setPlaceholders(context.getPlayer(), text);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /**
     * Apply placeholders to a list of strings
     * // note: Replaces placeholders in each line with context data
     */
    public static List<String> apply(List<String> lines, PlaceholderContext context) {
        if (lines == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(apply(line, context));
        }
        return result;
    }

    @Override
    public boolean register() {
        return super.register();
    }

    /**
     * Format money amount with commas for readability
     * // note: Formats currency with commas using economy plugin or default format
     */
    private static String formatMoneyWithCommas(double amount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null) {
            String formatted = economy.format(amount);
            try {
                java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
                double parsed = Double.parseDouble(formatted.replaceAll("[^0-9.]", ""));
                String numberPart = numberFormat.format((long) parsed);
                String decimalPart = formatted.contains(".") ? formatted.substring(formatted.indexOf(".")) : ".00";
                return formatted.startsWith("$") ? "$" + numberPart + decimalPart : numberPart + decimalPart;
            } catch (NumberFormatException e) {
                return formatted;
            }
        }
        java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
        return "$" + numberFormat.format(Math.floor(amount)) + String.format("%.2f", amount).substring(String.format("%.2f", amount).indexOf("."));
    }

    @Override
    public String getIdentifier() {
        return "bountiesplus";
    }

    @Override
    public String getAuthor() {
        return "Tony26";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Handles placeholder requests for PlaceholderAPI
     * // note: Provides custom placeholders for BountiesPlus
     */
    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier.equalsIgnoreCase("prefix")) {
            return ChatColor.translateAlternateColorCodes('&', plugin.getMessagesConfig().getString("prefix", "&4&lBounties &7&l» &7"));
        }

        PlaceholderContext context = player != null ? contextMap.getOrDefault(player.getUniqueId(), null) : null;
        Bounty bounty = context != null && context.getTargetUUID() != null ? plugin.getBountyManager().getBounty(context.getTargetUUID()) : null;
        java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
        boolean useXpLevels = plugin.getConfig().getBoolean("use-xp-levels", false);
        String anonymousSponsor = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("custom-placeholders.anonymous-sponsor", "&k|||||||"));

        switch (identifier.toLowerCase()) {
            case "target":
                if (context != null && context.getTargetUUID() != null) {
                    OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(context.getTargetUUID());
                    String name = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                    return name;
                }
                String fallback = plugin.getConfig().getString("custom-placeholders.bounty-target-fallback", "None");
                return ChatColor.translateAlternateColorCodes('&', Placeholders.apply(fallback, context));
            case "bounty_count":
                if (context != null && context.getTargetUUID() != null) {
                    return numberFormat.format(plugin.getBountyManager().getBountiesOnTarget(context.getTargetUUID()).size());
                }
                return "0";
            case "online_status":
                if (context != null && context.getTargetUUID() != null) {
                    Player targetPlayer = Bukkit.getPlayer(context.getTargetUUID());
                    String status = context.getOnlineStatus() != null ? context.getOnlineStatus() : (targetPlayer != null && targetPlayer.isOnline() ? "&aOnline" : "&cOffline");
                    return status;
                }
                return "";
            case "claimed":
            case "survived":
            case "totalmoneyearned":
            case "totalxpearned":
            case "totalvalueearned":
                CompletableFuture<?> statFuture;
                switch (identifier.toLowerCase()) {
                    case "claimed":
                        statFuture = plugin.getMySQL().getClaimed(player.getUniqueId());
                        break;
                    case "survived":
                        statFuture = plugin.getMySQL().getSurvived(player.getUniqueId());
                        break;
                    case "totalmoneyearned":
                        statFuture = plugin.getMySQL().getMoneyEarned(player.getUniqueId());
                        break;
                    case "totalxpearned":
                        statFuture = plugin.getMySQL().getXPEarned(player.getUniqueId());
                        break;
                    case "totalvalueearned":
                        statFuture = plugin.getMySQL().getTotalValueEarned(player.getUniqueId());
                        break;
                    default:
                        return "";
                }
                try {
                    Object statValue = statFuture.get();
                    if (statValue instanceof Double) {
                        return formatMoneyWithCommas((Double) statValue);
                    } else if (statValue instanceof Integer) {
                        return numberFormat.format(statValue);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to fetch stat " + identifier + " for " + player.getName() + ": " + e.getMessage());
                    return "0";
                }
                return "";
            case "gui_item_count":
                AddItemsGUI gui = AddItemsGUI.getActiveInstance(player.getUniqueId());
                return gui != null ? String.valueOf(gui.getItemCount()) : "0";
            case "gui_item_value":
                gui = AddItemsGUI.getActiveInstance(player.getUniqueId());
                return gui != null ? CurrencyUtil.formatMoney(gui.getItemValue()) : "0.00";
            case "item_name":
                return context != null && context.getItemName() != null ? context.getItemName() : "";
            case "item_uses":
                return context != null && context.getItemUses() != null ? String.valueOf(context.getItemUses()) : "";
            case "player":
                return player.getName();
            case "player_display_name":
                return player.getDisplayName();
            case "player_level":
                return numberFormat.format(player.getLevel());
            case "player_exp":
                return useXpLevels ? numberFormat.format(player.getLevel()) : numberFormat.format(player.getTotalExperience());
            case "player_x":
                return numberFormat.format(player.getLocation().getBlockX());
            case "player_y":
                return numberFormat.format(player.getLocation().getBlockY());
            case "player_z":
                return numberFormat.format(player.getLocation().getBlockZ());
            case "player_name":
                return player.getName();
            case "amount":
            case "cost":
                if (context != null && context.getBountyAmount() != null) {
                    return formatMoneyWithCommas(context.getBountyAmount());
                }
                return "";
            case "tax":
                if (context != null && context.getTaxAmount() != null) {
                    return formatMoneyWithCommas(context.getTaxAmount());
                }
                return "";
            case "total_amount":
            case "total_bounty":
                if (context != null && context.getTotalBountyAmount() != null) {
                    return identifier.equalsIgnoreCase("total_bounty") ?
                            numberFormat.format(context.getTotalBountyAmount().intValue()) :
                            formatMoneyWithCommas(context.getTotalBountyAmount());
                }
                return "";
            case "sponsor":
                if (context != null && context.getSetterUUID() != null) {
                    Bounty targetBounty = plugin.getBountyManager().getBounty(context.getTargetUUID());
                    if (targetBounty != null) {
                        Optional<Bounty.Sponsor> sponsor = targetBounty.getSponsors().stream()
                                .filter(s -> s.getPlayerUUID().equals(context.getSetterUUID()))
                                .findFirst();
                        if (sponsor.isPresent() && sponsor.get().isAnonymous()) {
                            return anonymousSponsor;
                        }
                    }
                    OfflinePlayer setter = Bukkit.getOfflinePlayer(context.getSetterUUID());
                    String name = setter.getName() != null ? setter.getName() : "Unknown";
                    return name;
                }
                return "";
            case "set_time":
                return context != null && context.getSetTime() != null ? context.getSetTime() : "";
            case "expire_time":
                return context != null && context.getExpireTime() != null ? context.getExpireTime() : "";
            case "total_expire_time":
                if (context != null && context.getTargetUUID() != null) {
                    Bounty targetBounty = plugin.getBountyManager().getBounty(context.getTargetUUID());
                    if (targetBounty != null && !targetBounty.isPermanent()) {
                        long totalMinutes = targetBounty.getSponsors().stream()
                                .mapToLong(sponsor -> sponsor.getExpireTime() > 0 ?
                                        (sponsor.getExpireTime() - System.currentTimeMillis()) / (60 * 1000) : 0)
                                .sum();
                        return totalMinutes > 0 ?
                                TimeFormatter.formatMinutesToReadable((int) totalMinutes, false) :
                                ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("custom-placeholders.bounty-status-no-expiration", "No expiration"));
                    }
                }
                return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("custom-placeholders.bounty-status-no-expiration", "No expiration"));
            case "multiplier":
                if (context != null && context.getMultiplier() != null) {
                    return String.format("%.1f", context.getMultiplier());
                }
                return "";
            case "killer":
                return context != null && context.getKillerName() != null ? context.getKillerName() : "";
            case "killed":
                if (context != null && context.getTargetUUID() != null) {
                    OfflinePlayer killed = Bukkit.getOfflinePlayer(context.getTargetUUID());
                    return killed.getName() != null ? killed.getName() : "Unknown";
                }
                return "";
            case "death_time":
                return context != null && context.getDeathTime() != null ? context.getDeathTime() : "";
            case "sponsor_list":
                return context != null && context.getSetterList() != null ? context.getSetterList() : "";
            case "money_value":
                if (context != null && context.getMoneyValue() != null) {
                    return formatMoneyWithCommas(context.getMoneyValue());
                }
                return "";
            case "exp_value":
            case "total_exp":
                if (context != null && context.getExpValue() != null) {
                    return useXpLevels ? numberFormat.format(context.getExpValue()) + " levels" : numberFormat.format(context.getExpValue()) + " XP";
                }
                return "0";
            case "levels":
                if (context != null && context.getExpValue() != null) {
                    return numberFormat.format(context.getExpValue());
                }
                return "0";
            case "duration":
                return context != null && context.getTimeValue() != null ? context.getTimeValue() : "";
            case "item_value":
                if (context != null && context.getItemValue() != null) {
                    return formatMoneyWithCommas(context.getItemValue());
                }
                return "";
            case "item_count":
                return context != null && context.getItemCount() != null ? numberFormat.format(context.getItemCount()) : "";
            case "tax_rate":
                if (context != null && context.getTaxRate() != null) {
                    return String.format("%.1f", context.getTaxRate());
                }
                return "";
            case "refund":
                if (context != null && context.getRefundAmount() != null) {
                    return formatMoneyWithCommas(context.getRefundAmount());
                }
                return "";
            case "filter_status":
                return context != null && context.getFilterStatus() != null ? context.getFilterStatus() : "";
            case "filter_details":
                return context != null && context.getFilterDetails() != null ? context.getFilterDetails() : "";
            case "current_page":
                return context != null && context.getCurrentPage() != null ? numberFormat.format(context.getCurrentPage() + 1) : "";
            case "total_pages":
                return context != null && context.getTotalPages() != null ? numberFormat.format(context.getTotalPages()) : "";
            case "time":
                return context != null && context.getTime() != null ? context.getTime() : "";
            case "boost_time":
                return context != null && context.getBoostTime() != null ? context.getBoostTime() : "";
            case "unit":
                return context != null && context.getUnit() != null ? context.getUnit() : "";
            case "money_line":
                return context != null && context.getMoneyLine() != null ? context.getMoneyLine() : "";
            case "experience_line":
                return context != null && context.getExperienceLine() != null ? context.getExperienceLine() : "";
            case "sponsors":
                if (bounty != null) {
                    List<String> sponsorNames = bounty.getSponsors().stream()
                            .map(sponsor -> sponsor.isAnonymous() ? anonymousSponsor : Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName())
                            .collect(Collectors.toList());
                    return sponsorNames.isEmpty() ? "None" : String.join(", ", sponsorNames);
                }
                return "None";
            case "pool":
            case "total_pool":
                if (bounty != null) {
                    return formatMoneyWithCommas(bounty.getCurrentPool());
                }
                return "0.00";
            case "expiry":
                if (bounty != null && !bounty.isPermanent()) {
                    long remainingMinutes = bounty.getCurrentDurationMinutes();
                    return TimeFormatter.formatMinutesToReadable((int) remainingMinutes, false);
                }
                return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("custom-placeholders.bounty-status-no-expiration", "No expiration"));
            case "top3_sponsors_commas":
            case "top5_sponsors_commas":
            case "top10_sponsors_commas":
                if (bounty != null) {
                    int limit = identifier.equalsIgnoreCase("top3_sponsors_commas") ? 3 :
                            identifier.equalsIgnoreCase("top5_sponsors_commas") ? 5 : 10;
                    List<Bounty.Sponsor> topSponsors = bounty.getTopSponsors(limit);
                    List<String> enemies = topSponsors.stream()
                            .map(sponsor -> sponsor.isAnonymous() ? anonymousSponsor : Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName())
                            .collect(Collectors.toList());
                    return enemies.isEmpty() ? "None" : String.join(", ", enemies);
                }
                return "None";
            case "hunters":
                if (bounty != null) {
                    return numberFormat.format(bounty.getSponsors().size());
                }
                return "0";
            case "frenzy":
                return plugin.getFrenzy() != null && plugin.getFrenzy().isFrenzyActive() ?
                        String.format("%.1f", plugin.getFrenzy().getFrenzyMultiplier()) : "1.0";
            case "boost":
                if (plugin.getBoostedBounty() != null && context != null && context.getTargetUUID() != null) {
                    UUID boostedTarget = plugin.getBoostedBounty().getCurrentBoostedTarget();
                    return boostedTarget != null && boostedTarget.equals(context.getTargetUUID()) ?
                            String.format("%.1f", plugin.getBoostedBounty().getCurrentBoostMultiplier(context.getTargetUUID())) : "1.0";
                }
                return "1.0";
            case "boost_prefix":
                if (plugin.getConfig().getBoolean("tablist-modification.enabled", false) && plugin.getBountyManager().hasBounty(player.getUniqueId())) {
                    String format = plugin.getConfig().getString("tablist-modification.format", "&c[Bounty] %player%");
                    return Placeholders.apply(format, context);
                }
                return "";
            case "next_frenzy_info":
                if (plugin.getFrenzy() != null) {
                    long timeUntilNext = plugin.getFrenzy().getTimeUntilNextFrenzy();
                    if (timeUntilNext > 0) {
                        return "&c→ &fIn " + TimeFormatter.formatTimeRemaining(timeUntilNext);
                    } else {
                        return "&c→ &cFrenzy incoming!";
                    }
                }
                return "&7→ &8Frenzy disabled";
            case "next_boost_info":
                if (plugin.getBoostedBounty() != null) {
                    long timeUntilNext = plugin.getBoostedBounty().getTimeUntilNextBoost();
                    if (timeUntilNext > 0) {
                        return "&b→ &fIn " + TimeFormatter.formatTimeRemaining(timeUntilNext);
                    } else {
                        return "&b→ &aBoost incoming!";
                    }
                }
                return "&7→ &8Boost disabled";
            case "error":
                return context != null && context.getError() != null ? context.getError() : "";
            case "item":
                return context != null && context.getItem() != null ? context.getItem() : "";
            case "sender":
                if (context != null && context.getSender() != null) {
                    OfflinePlayer sender = Bukkit.getOfflinePlayer(context.getSender());
                    return sender.getName() != null ? sender.getName() : "Unknown";
                }
                return "";
            case "material":
                return context != null && context.getMaterial() != null ? context.getMaterial() : "";
            case "button":
                return context != null && context.getButton() != null ? context.getButton() : "";
            case "anonymous_cost":
                if (context != null && context.getAnonymousCost() != null) {
                    return formatMoneyWithCommas(context.getAnonymousCost());
                }
                return "";
            case "input":
                return context != null && context.getInput() != null ? context.getInput() : "";
            case "top3_sponsors_numbered":
            case "top5_sponsors_numbered":
            case "top10_sponsors_numbered":
                if (bounty != null) {
                    int limit = identifier.equalsIgnoreCase("top3_sponsors_numbered") ? 3 :
                            identifier.equalsIgnoreCase("top5_sponsors_numbered") ? 5 : 10;
                    List<Bounty.Sponsor> topSponsors = bounty.getTopSponsors(limit);
                    StringBuilder sponsors = new StringBuilder();
                    for (int i = 0; i < topSponsors.size(); i++) {
                        Bounty.Sponsor sponsor = topSponsors.get(i);
                        String name = sponsor.isAnonymous() ? anonymousSponsor : Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName();
                        sponsors.append(i + 1).append(". ").append(name);
                        if (i < topSponsors.size() - 1) {
                            sponsors.append(", ");
                        }
                    }
                    return sponsors.length() > 0 ? sponsors.toString() : "None";
                }
                return "None";
            case "original_money":
                return bounty != null ? formatMoneyWithCommas(bounty.getOriginalMoney()) : "";
            case "price_increase_percent":
                return bounty != null ? String.format("%.1f", bounty.getPriceIncreasePercent()) : "";
            case "original_item_count":
                return bounty != null ? numberFormat.format(bounty.getOriginalItems().size()) : "";
            case "original_item_value":
                return bounty != null ? formatMoneyWithCommas(bounty.getOriginalItemValue()) : "";
            case "item_increase_percent":
                return bounty != null ? String.format("%.1f", bounty.getItemIncreasePercent()) : "";
            case "original_xp":
                return bounty != null ? numberFormat.format(bounty.getOriginalXP()) : "";
            case "xplevel_increase_percent":
                return bounty != null ? String.format("%.1f", bounty.getXPLevelIncreasePercent()) : "";
            case "original_duration":
                return bounty != null ? bounty.getFormattedOriginalDuration() : "";
            case "bountyduration_increase_percent":
                return bounty != null ? String.format("%.1f", bounty.getDurationIncreasePercent()) : "";
            case "original_pool":
                return bounty != null ? formatMoneyWithCommas(bounty.getOriginalPool()) : "";
            case "pool_increase_percent":
                return bounty != null ? String.format("%.1f", bounty.getPoolIncreasePercent()) : "";
            case "bounty_tabname":
                if (plugin.getConfig().getBoolean("tablist-modification.enabled", false) && plugin.getBountyManager().hasBounty(player.getUniqueId())) {
                    String format = plugin.getConfig().getString("tablist-modification.format", "&c[Bounty] %player%");
                    return Placeholders.apply(format, context);
                }
                return player.getName();
        }

        plugin.getDebugManager().logDebug("[DEBUG] Unknown placeholder: " + identifier);
        return null;
    }
}