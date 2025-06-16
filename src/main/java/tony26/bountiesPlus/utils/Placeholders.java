package tony26.bountiesPlus.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.Bounty;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.scheduler.BukkitTask;
import tony26.bountiesPlus.GUIs.AddItemsGUI;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles placeholder registration and application for BountiesPlus
 */
public class Placeholders {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    private static final Map<UUID, PlaceholderContext> contextMap = new ConcurrentHashMap<>(); // Store contexts by player UUID
    private static final ConcurrentMap<String, AtomicLong> debugLogCounts = new ConcurrentHashMap<>();
    private static BukkitTask debugLoggingTask = null;

    /**
     * Apply all placeholders to a string using PlaceholderAPI // note: Replaces placeholders with context data
     */
    public static String apply(String text, PlaceholderContext context) {
        if (text == null) return "";
        BountiesPlus plugin = BountiesPlus.getInstance();
        if (context == null || context.getPlayer() == null) {
            String result = PlaceholderAPI.setPlaceholders(null, text);
            result = ChatColor.translateAlternateColorCodes('&', result);
            return result; // Global placeholders only
        }
        contextMap.put(context.getPlayer().getUniqueId(), context); // Store context
        String result = PlaceholderAPI.setPlaceholders(context.getPlayer(), text); // Apply all placeholders
        result = ChatColor.translateAlternateColorCodes('&', result); // Apply color codes
        return result;
    }

    /**
     * Apply placeholders to a list of strings (useful for lore) // note: Replaces placeholders in each line with context data
     */
    public static List<String> apply(List<String> lines, PlaceholderContext context) {
        if (lines == null) return new ArrayList<>();
        BountiesPlus plugin = BountiesPlus.getInstance();
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(apply(line, context));
        }
        return result;
    }

    /**
     * Register PlaceholderAPI expansion // note: Initializes BountiesPlus placeholders with PlaceholderAPI
     */
    public static void registerPlaceholders() {
        BountiesPlus plugin = BountiesPlus.getInstance();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new BountiesPlusPlaceholderExpansion().register();
            plugin.getLogger().info("Registered placeholders with PlaceholderAPI"); // Logs registration
        } else {
            plugin.getLogger().warning("PlaceholderAPI not found, placeholders will not be registered"); // Logs failure
        }
    }

    /**
     * Format money amount using Vault economy // note: Formats currency using economy plugin or default format
     */
    private static String formatMoney(double amount) {
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("$%.2f", amount);
    }

    /**
     * Get prefix from messages config // note: Retrieves the message prefix with fallback
     */
    private static String getPrefix() {
        try {
            BountiesPlus plugin = BountiesPlus.getInstance();
            return plugin.getMessagesConfig().getString("prefix", "&4&lBounties &7&l» &7");
        } catch (Exception e) {
            return "&4&lBounties &7&l» &7";
        }
    }

    /**
     * Starts a task to periodically log buffered debug messages // note: Initializes a task to summarize placeholder debug logs every 30 seconds
     */
    public static void startDebugLoggingTask(BountiesPlus plugin) {
        if (debugLoggingTask != null) {
            debugLoggingTask.cancel();
        }
        debugLoggingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (debugLogCounts.isEmpty()) {
                return;
            }
            StringBuilder summary = new StringBuilder("Placeholder debug summary (past 30 seconds):\n");
            debugLogCounts.forEach((message, count) -> {
                summary.append(String.format("- %s: %d times\n", message, count.get()));
            });
            plugin.getLogger().info(summary.toString());
            debugLogCounts.clear();
        }, 600L, 600L); // 30 seconds (600 ticks)
    }

    /**
     * Stops the periodic debug logging task // note: Cancels the task that summarizes placeholder debug logs
     */
    public static void stopDebugLoggingTask() {
        if (debugLoggingTask != null) {
            debugLoggingTask.cancel();
            debugLoggingTask = null;
            debugLogCounts.clear();
        }
    }

    /**
     * PlaceholderAPI expansion for BountiesPlus
     */
    private static class BountiesPlusPlaceholderExpansion extends PlaceholderExpansion {
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
            return BountiesPlus.getInstance().getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        /**
         * Format money amount with commas for readability
         * // note: Formats currency with commas using economy plugin or default format
         */
        private static String formatMoneyWithCommas(double amount) {
            Economy economy = BountiesPlus.getEconomy();
            if (economy != null) {
                String formatted = economy.format(amount);
                // Ensure commas are included (some economy plugins may not add them)
                try {
                    java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
                    double parsed = Double.parseDouble(formatted.replaceAll("[^0-9.]", ""));
                    String numberPart = numberFormat.format((long) parsed);
                    String decimalPart = formatted.contains(".") ? formatted.substring(formatted.indexOf(".")) : ".00";
                    return formatted.startsWith("$") ? "$" + numberPart + decimalPart : numberPart + decimalPart;
                } catch (NumberFormatException e) {
                    return formatted; // Fallback to economy's format if parsing fails
                }
            }
            java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
            return "$" + numberFormat.format(Math.floor(amount)) + String.format("%.2f", amount).substring(String.format("%.2f", amount).indexOf("."));
        }

        /**
         * Handles placeholder requests for PlaceholderAPI
         * // note: Provides custom placeholders for BountiesPlus
         */
        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            BountiesPlus plugin = BountiesPlus.getInstance();
            String processingMsg = "Processing placeholder: identifier=" + identifier + ", player=" + (player != null ? player.getName() : "null");
            debugLogCounts.computeIfAbsent(processingMsg, k -> new AtomicLong()).incrementAndGet();

            switch (identifier.toLowerCase()) {
                case "prefix":
                    return ChatColor.translateAlternateColorCodes('&', getPrefix());
                case "gui_item_count":
                    if (player != null) {
                        AddItemsGUI gui = AddItemsGUI.getActiveInstance(player.getUniqueId());
                        if (gui != null) {
                            return String.valueOf(gui.getItemCount());
                        }
                    }
                    return "0";
                case "gui_item_value":
                    if (player != null) {
                        AddItemsGUI gui = AddItemsGUI.getActiveInstance(player.getUniqueId());
                        if (gui != null) {
                            ItemValueCalculator calculator = plugin.getItemValueCalculator();
                            return CurrencyUtil.formatMoney(gui.getItemValue());
                        }
                    }
                    return "0.00";
                case "item_name":
                    if (contextMap.containsKey(player.getUniqueId())) {
                        PlaceholderContext context = contextMap.get(player.getUniqueId());
                        return context.getItemName() != null ? context.getItemName() : "";
                    }
                    return "";
                case "item_uses":
                    if (contextMap.containsKey(player.getUniqueId())) {
                        PlaceholderContext context = contextMap.get(player.getUniqueId());
                        return context.getItemUses() != null ? String.valueOf(context.getItemUses()) : "";
                    }
                    return "";
            }

            if (player == null) {
                String warningMsg = "No player provided for placeholder: " + identifier;
                debugLogCounts.computeIfAbsent(warningMsg, k -> new AtomicLong()).incrementAndGet();
                return "";
            }

            PlaceholderContext context = contextMap.getOrDefault(player.getUniqueId(), null);
            if (context == null) {
                String warningMsg = "No context found for player " + player.getName() + " for placeholder: " + identifier;
                debugLogCounts.computeIfAbsent(warningMsg, k -> new AtomicLong()).incrementAndGet();
                return "";
            }

            Bounty bounty = context.getTargetUUID() != null ? plugin.getBountyManager().getBounty(context.getTargetUUID()) : null;

            // Retrieve player stats for leaderboard placeholders
            FileConfiguration statsConfig = plugin.getStatsConfig();
            String playerUUID = player.getUniqueId().toString();
            java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
            switch (identifier.toLowerCase()) {
                case "claimed":
                    return numberFormat.format(statsConfig.getInt("players." + playerUUID + ".claimed", 0));
                case "survived":
                    return numberFormat.format(statsConfig.getInt("players." + playerUUID + ".survived", 0));
                case "totalmoneyearned":
                    return formatMoneyWithCommas(statsConfig.getDouble("players." + playerUUID + ".money_earned", 0.0));
                case "totalxpearned":
                    return numberFormat.format(statsConfig.getInt("players." + playerUUID + ".xp_earned", 0));
                case "totalvalueearned":
                    return formatMoneyWithCommas(statsConfig.getDouble("players." + playerUUID + ".total_value_earned", 0.0));
                case "player":
                    return player.getName();
                case "player_display_name":
                    return player.getDisplayName();
                case "player_level":
                    return numberFormat.format(player.getLevel());
                case "player_x":
                    return numberFormat.format(player.getLocation().getBlockX());
                case "player_y":
                    return numberFormat.format(player.getLocation().getBlockY());
                case "player_z":
                    return numberFormat.format(player.getLocation().getBlockZ());
                case "target":
                    if (context.getTargetUUID() != null) {
                        Player targetPlayer = Bukkit.getPlayer(context.getTargetUUID());
                        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(context.getTargetUUID());
                        String name = targetPlayer != null ? targetPlayer.getName() : (offlineTarget.getName() != null ? offlineTarget.getName() : "Unknown");
                        String resolvedMsg = "Resolved target for " + name;
                        debugLogCounts.computeIfAbsent(resolvedMsg, k -> new AtomicLong()).incrementAndGet();
                        return name;
                    }
                    return "";
                case "online_status":
                    if (context.getTargetUUID() != null) {
                        Player targetPlayer = Bukkit.getPlayer(context.getTargetUUID());
                        String status = targetPlayer != null && targetPlayer.isOnline() ? "&aOnline" : "&cOffline";
                        String resolvedMsg = "Resolved online_status: " + status;
                        debugLogCounts.computeIfAbsent(resolvedMsg, k -> new AtomicLong()).incrementAndGet();
                        return status;
                    }
                    return "";
                case "amount":
                    if (context.getBountyAmount() != null) {
                        return formatMoneyWithCommas(context.getBountyAmount());
                    }
                    return "";
                case "cost":
                    if (context.getBountyAmount() != null) {
                        return formatMoneyWithCommas(context.getBountyAmount());
                    }
                    return "";
                case "tax":
                    if (context.getTaxAmount() != null) {
                        return formatMoneyWithCommas(context.getTaxAmount());
                    }
                    return "";
                case "total_amount":
                    if (context.getTotalBountyAmount() != null) {
                        return formatMoneyWithCommas(context.getTotalBountyAmount());
                    }
                    return "";
                case "total_bounty":
                    if (context.getTotalBountyAmount() != null) {
                        return numberFormat.format(context.getTotalBountyAmount().intValue());
                    }
                    return "";
                case "sponsor":
                    if (context.getSetterUUID() != null) {
                        OfflinePlayer setter = Bukkit.getOfflinePlayer(context.getSetterUUID());
                        String name = setter.getName() != null ? setter.getName() : "Unknown";
                        String resolvedMsg = "Resolved sponsor: " + name;
                        debugLogCounts.computeIfAbsent(resolvedMsg, k -> new AtomicLong()).incrementAndGet();
                        return name;
                    }
                    return "";
                case "set_time":
                    return context.getSetTime() != null ? context.getSetTime() : "";
                case "expire_time":
                    return context.getExpireTime() != null ? context.getExpireTime() : "";
                case "multiplier":
                    if (context.getMultiplier() != null) {
                        return String.format("%.1f", context.getMultiplier());
                    }
                    return "";
                case "killer":
                    return context.getKillerName() != null ? context.getKillerName() : "";
                case "killed":
                    if (context.getTargetUUID() != null) {
                        OfflinePlayer killed = Bukkit.getOfflinePlayer(context.getTargetUUID());
                        return killed.getName() != null ? killed.getName() : "Unknown";
                    }
                    return "";
                case "death_time":
                    return context.getDeathTime() != null ? context.getDeathTime() : "";
                case "sponsor_list":
                    return context.getSetterList() != null ? context.getSetterList() : "";
                case "bounty_count":
                    return context.getBountyCount() != null ? numberFormat.format(context.getBountyCount()) : "";
                case "money_value":
                    if (context.getMoneyValue() != null) {
                        return formatMoneyWithCommas(context.getMoneyValue());
                    }
                    return "";
                case "exp_value":
                    if (context.getExpValue() != null) {
                        return numberFormat.format(context.getExpValue()) + " XP";
                    }
                    return "";
                case "total_exp":
                    if (context.getExpValue() != null) {
                        return numberFormat.format(context.getExpValue()) + " XP";
                    }
                    return "";
                case "levels":
                    if (context.getExpValue() != null) {
                        return numberFormat.format(context.getExpValue());
                    }
                    return "";
                case "duration":
                    return context.getTimeValue() != null ? context.getTimeValue() : "";
                case "item_value":
                    if (context.getItemValue() != null) {
                        return formatMoneyWithCommas(context.getItemValue());
                    }
                    return "";
                case "item_count":
                    return context.getItemCount() != null ? numberFormat.format(context.getItemCount()) : "";
                case "tax_rate":
                    if (context.getTaxRate() != null) {
                        return String.format("%.1f", context.getTaxRate());
                    }
                    return "";
                case "refund":
                    if (context.getRefundAmount() != null) {
                        return formatMoneyWithCommas(context.getRefundAmount());
                    }
                    return "";
                case "filter_status":
                    return context.getFilterStatus() != null ? context.getFilterStatus() : "";
                case "filter_details":
                    return context.getFilterDetails() != null ? context.getFilterDetails() : "";
                case "current_page":
                    return context.getCurrentPage() != null ? numberFormat.format(context.getCurrentPage() + 1) : "";
                case "total_pages":
                    return context.getTotalPages() != null ? numberFormat.format(context.getTotalPages()) : "";
                case "time":
                    return context.getTime() != null ? context.getTime() : "";
                case "boost_time":
                    return context.getBoostTime() != null ? context.getBoostTime() : "";
                case "unit":
                    return context.getUnit() != null ? context.getUnit() : "";
                case "money_line":
                    return context.getMoneyLine() != null ? context.getMoneyLine() : "";
                case "experience_line":
                    return context.getExperienceLine() != null ? context.getExperienceLine() : "";
                case "sponsors":
                    if (bounty != null) {
                        List<String> sponsorNames = bounty.getSponsors().stream()
                                .map(sponsor -> sponsor.isAnonymous() ? "&k|||||||" : Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName())
                                .collect(Collectors.toList());
                        return sponsorNames.isEmpty() ? "None" : String.join(", ", sponsorNames);
                    }
                    return "None";
                case "pool":
                    if (bounty != null) {
                        return formatMoneyWithCommas(bounty.getCurrentPool());
                    }
                    return "0.00";
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
                    return plugin.getMessagesConfig().getString("bounty-status-no-expiration", "No expiration");
                case "top3_sponsors_commas":
                case "top5_sponsors_commas":
                case "top10_sponsors_commas":
                    if (bounty != null) {
                        int limit = identifier.equalsIgnoreCase("top3_sponsors_commas") ? 3 :
                                identifier.equalsIgnoreCase("top5_sponsors_commas") ? 5 : 10;
                        List<Bounty.Sponsor> topSponsors = bounty.getTopSponsors(limit);
                        List<String> enemies = topSponsors.stream()
                                .map(sponsor -> sponsor.isAnonymous() ? "&k|||||||" : Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName())
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
                    if (plugin.getBoostedBounty() != null && context.getTargetUUID() != null) {
                        UUID boostedTarget = plugin.getBoostedBounty().getCurrentBoostedTarget();
                        return boostedTarget != null && boostedTarget.equals(context.getTargetUUID()) ?
                                String.format("%.1f", plugin.getBoostedBounty().getCurrentBoostMultiplier(context.getTargetUUID())) : "1.0";
                    }
                    return "1.0";
                case "error":
                    return context.getError() != null ? context.getError() : "";
                case "item":
                    return context.getItem() != null ? context.getItem() : "";
                case "sender":
                    if (context.getSender() != null) {
                        OfflinePlayer sender = Bukkit.getOfflinePlayer(context.getSender());
                        return sender.getName() != null ? sender.getName() : "Unknown";
                    }
                    return "";
                case "material":
                    return context.getMaterial() != null ? context.getMaterial() : "";
                case "button":
                    return context.getButton() != null ? context.getButton() : "";
                case "anonymous_cost":
                    if (context.getAnonymousCost() != null) {
                        return formatMoneyWithCommas(context.getAnonymousCost());
                    }
                    return "";
                case "input":
                    return context.getInput() != null ? context.getInput() : "";
                case "min_amount":
                    if (context.getBountyAmount() != null) {
                        return formatMoneyWithCommas(context.getBountyAmount());
                    }
                    return "";
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
                            String name = sponsor.isAnonymous() ? "&k|||||||" : Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName();
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

            String warningMsg = "Unknown placeholder: " + identifier;
            debugLogCounts.computeIfAbsent(warningMsg, k -> new AtomicLong()).incrementAndGet();
            return null;
        }
    }
}