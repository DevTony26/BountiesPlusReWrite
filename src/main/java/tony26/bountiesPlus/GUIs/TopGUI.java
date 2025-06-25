package tony26.bountiesPlus.GUIs;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.EventManager;
import tony26.bountiesPlus.utils.SkullUtils;
import tony26.bountiesPlus.utils.*;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TopGUI implements Listener {
    private static final int ROWS = 6;
    private static final int SLOTS_PER_PAGE = 28; // 4 rows (2-5) minus 8 border slots per row
    private final BountiesPlus plugin;
    private FileConfiguration config;
    private final Map<UUID, Integer> openPages;
    private final Map<UUID, Inventory> openInventories;
    private static FilterType currentFilterType = FilterType.CLAIMED;
    private static boolean sortHighToLow = true;
    // Static field to track item failures
    private static final Map<String, String> itemFailures = new ConcurrentHashMap<>();

    public enum FilterType {
        CLAIMED, SURVIVED, MONEY_EARNED, XP_EARNED, TOTAL_VALUE
    }

    private static class PlayerData {
        private final UUID uuid;
        private final int claimed;
        private final int survived;
        private final double moneyEarned;
        private final int xpEarned;
        private final double totalValueEarned;


        public PlayerData(UUID uuid, int claimed, int survived, double moneyEarned, int xpEarned, double totalValueEarned) {
            this.uuid = uuid;
            this.claimed = claimed;
            this.survived = survived;
            this.moneyEarned = moneyEarned;
            this.xpEarned = xpEarned;
            this.totalValueEarned = totalValueEarned;
        }

        public UUID getUUID() { return uuid; }
        public int getClaimed() { return claimed; }
        public int getSurvived() { return survived; }
        public double getMoneyEarned() { return moneyEarned; }
        public int getXPEarned() { return xpEarned; }
        public double getTotalValueEarned() { return totalValueEarned; }
    }

    /**
     * Constructs the TopGUI
     * // note: Initializes leaderboard GUI and registers listeners
     */
    public TopGUI(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.openPages = new HashMap<>();
        this.openInventories = new HashMap<>();
        loadConfig();
        eventManager.register(this); // Use EventManager
    }

    /**
     * Loads or creates TopGUI.yml configuration
     * // note: Initializes GUI settings for the leaderboard display
     */
    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "GUIs/TopGUI.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveResource("GUIs/TopGUI.yml", false);
                if (plugin.getDebugManager() != null) {
                    plugin.getDebugManager().logDebug("[DEBUG - TopGUI] Created default TopGUI.yml");
                } else {
                    plugin.getLogger().info("[DEBUG - TopGUI] Created default TopGUI.yml (DebugManager not initialized)");
                }
            } catch (IllegalArgumentException e) {
                if (plugin.getDebugManager() != null) {
                    plugin.getDebugManager().logWarning("[DEBUG - TopGUI] Failed to save default TopGUI.yml: " + e.getMessage());
                } else {
                    plugin.getLogger().warning("[DEBUG - TopGUI] Failed to save default TopGUI.yml: " + e.getMessage());
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        // Verify configuration integrity
        if (config.getConfigurationSection("Plugin-Items") == null) {
            if (plugin.getDebugManager() != null) {
                plugin.getDebugManager().logWarning("[DEBUG - TopGUI] TopGUI.yml is empty or invalid, reloading default");
            } else {
                plugin.getLogger().warning("[DEBUG - TopGUI] TopGUI.yml is empty or invalid, reloading default");
            }
            try {
                configFile.delete();
                plugin.saveResource("GUIs/TopGUI.yml", false);
                config = YamlConfiguration.loadConfiguration(configFile);
            } catch (IllegalArgumentException e) {
                if (plugin.getDebugManager() != null) {
                    plugin.getDebugManager().logWarning("[DEBUG - TopGUI] Failed to reload default TopGUI.yml: " + e.getMessage());
                } else {
                    plugin.getLogger().warning("[DEBUG - TopGUI] Failed to reload default TopGUI.yml: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Opens the leaderboard GUI for a player
     * // note: Displays player skulls with selected filter and sort order, including navigation and filter buttons
     */
    public void openTopGUI(Player player, int page, FilterType filterType, boolean sortHighToLow) {
        DebugManager debugManager = plugin.getDebugManager();
        itemFailures.clear(); // Clear previous failures

        String title = ChatColor.translateAlternateColorCodes('&', config.getString("title", "&4&lBounty Leaderboard"));
        Inventory inventory = Bukkit.createInventory(null, ROWS * 9, title);
        currentFilterType = filterType;
        this.sortHighToLow = sortHighToLow;

        // List of items to create
        Map<String, Integer> items = new HashMap<>();
        items.put("Plugin-Items.Border", -1); // Multiple slots
        items.put("Plugin-Items.Info", 3);
        items.put("Plugin-Items.Player-Skull", 4); // Player skull
        items.put("Plugin-Items.Filter", 5);
        items.put("Plugin-Items.Close", 49);
        if (page > 0) {
            items.put("Plugin-Items.Previous", 48);
        }
        if (page < (int) Math.ceil((double) getFilteredPlayers(filterType, sortHighToLow).size() / SLOTS_PER_PAGE) - 1) {
            items.put("Plugin-Items.Next", 50);
        }

        int totalItems = items.size() + 1; // +1 for border slots
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        // Set border with glass panes
        ItemStack borderItem = createItem("Plugin-Items.Border");
        if (borderItem != null && borderItem.getType() != Material.AIR) {
            for (int i = 0; i < 9; i++) {
                inventory.setItem(i, borderItem); // Top row
                inventory.setItem(45 + i, borderItem); // Bottom row
            }
            for (int i = 9; i <= 45; i += 9) {
                inventory.setItem(i, borderItem); // Left column
                inventory.setItem(i + 8, borderItem); // Right column
            }
            successfulItems++; // Count border as one item
        } else {
            failures.add("Plugin-Items.Border Reason: Failed to create item");
        }

        // Place other items
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String path = entry.getKey();
            int slot = entry.getValue();
            if (path.equals("Plugin-Items.Border")) continue; // Handled above

            ItemStack item = null;
            if (path.equals("Plugin-Items.Filter")) {
                item = createFilterItem(filterType, sortHighToLow);
            } else if (path.equals("Plugin-Items.Player-Skull")) {
                item = createPlayerSkull(player);
            } else {
                item = createItem(path);
            }

            if (item != null && item.getType() != Material.AIR && slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
                successfulItems++;
            } else if (item == null || item.getType() == Material.AIR) {
                String failure = itemFailures.get(path);
                failures.add(path + " Reason: " + (failure != null ? failure : "Failed to create item"));
            } else {
                failures.add(path + " Reason: Invalid slot " + slot);
            }
        }

        // Fill slots with player skulls
        List<PlayerData> playerDataList = getFilteredPlayers(filterType, sortHighToLow);
        int totalPages = (int) Math.ceil((double) playerDataList.size() / SLOTS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, playerDataList.size());
        int[] contentSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int totalPlayerSkulls = endIndex - startIndex;
        int successfulPlayerSkulls = 0;
        List<String> playerSkullFailures = new ArrayList<>();

        for (int i = startIndex, slotIndex = 0; i < endIndex && slotIndex < contentSlots.length; i++, slotIndex++) {
            PlayerData data = playerDataList.get(i);
            ItemStack skull = createPlayerSkull(data);
            if (skull != null && skull.getType() != Material.AIR) {
                inventory.setItem(contentSlots[slotIndex], skull);
                successfulPlayerSkulls++;
            } else {
                String failure = itemFailures.get("Plugin-Items.Player-Skull-" + data.getUUID());
                playerSkullFailures.add("Player-Skull-" + data.getUUID() + " Reason: " + (failure != null ? failure : "Failed to create skull"));
            }
        }

        // Combine items and player skulls for total count
        totalItems += totalPlayerSkulls;
        successfulItems += successfulPlayerSkulls;
        failures.addAll(playerSkullFailures);

        // Log consolidated debug message
        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - TopGUI] All items created");
        } else {
            String failureMessage = "[DEBUG - TopGUI] " + successfulItems + "/" + totalItems + " items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("TopGUI_items_" + System.currentTimeMillis(), failureMessage);
        }

        openPages.put(player.getUniqueId(), page);
        openInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    /**
     * Retrieves a color from the configuration
     * // note: Parses RGB or hex color values with fallback to white
     */
    private Color getColorFromConfig(FileConfiguration config, String colorConfigPath) {
        if (config.contains(colorConfigPath + ".red") &&
                config.contains(colorConfigPath + ".green") &&
                config.contains(colorConfigPath + ".blue")) {
            int red = config.getInt(colorConfigPath + ".red", 255);
            int green = config.getInt(colorConfigPath + ".green", 255);
            int blue = config.getInt(colorConfigPath + ".blue", 255);
            red = Math.max(0, Math.min(255, red));
            green = Math.max(0, Math.min(255, green));
            blue = Math.max(0, Math.min(255, blue));
            return Color.fromRGB(red, green, blue);
        }
        if (config.contains(colorConfigPath + ".hex")) {
            String hex = config.getString(colorConfigPath + ".hex", "#FFFFFF");
            try {
                if (hex.startsWith("#")) {
                    hex = hex.substring(1);
                }
                int rgb = Integer.parseInt(hex, 16);
                return Color.fromRGB(rgb);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[DEBUG - TopGUI] Invalid hex color: " + hex + ", using white");
                return Color.WHITE;
            }
        }
        plugin.getLogger().warning("[DEBUG - TopGUI] No color configuration found for path: " + colorConfigPath + ", using white");
        return Color.WHITE;
    }

    /**
     * Applies a firework effect color to a firework star item
     * // note: Sets the firework star’s color based on config settings
     */
    private void applyFireworkStarColor(ItemStack item, FileConfiguration config, String colorConfigPath) {
        if (item == null || item.getType() != VersionUtils.getFireworkStarMaterial()) {
            return;
        }
        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();
        if (meta == null) return;
        Color color = getColorFromConfig(config, colorConfigPath);
        String effectTypeString = config.getString("Plugin-Items.Filter.firework-effect.effect-type", "STAR");
        FireworkEffect.Type effectType;
        try {
            effectType = FireworkEffect.Type.valueOf(effectTypeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            effectType = FireworkEffect.Type.STAR;
            plugin.getLogger().warning("[DEBUG - TopGUI] Invalid firework effect type: " + effectTypeString + ", using STAR");
        }
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(color)
                .with(effectType)
                .build();
        meta.setEffect(effect);
        item.setItemMeta(meta);
    }

    /**
     * Creates a filter item with dynamic appearance based on filter and sort state
     * // note: Generates a firework star with color and glow reflecting current filter and sort order
     */
    private ItemStack createFilterItem(FilterType filterType, boolean sortHighToLow) {
        DebugManager debugManager = plugin.getDebugManager();
        String path = "Plugin-Items.Filter";
        String failureReason = null;

        String materialName = config.getString(path + ".Material", "FIREWORK_STAR");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("FIREWORK_STAR")) {
            debugManager.logWarning("[DEBUG - TopGUI] Invalid material '" + materialName + "' for filter-button in TopGUI.yml, using FIREWORK_STAR");
            failureReason = "Invalid material '" + materialName + "'";
            item = VersionUtils.getXMaterialItemStack("FIREWORK_STAR");
        }

        boolean shouldGlow = sortHighToLow;
        String filterStatus;
        String filterDetails;
        String colorConfigPath;
        switch (filterType) {
            case CLAIMED:
                filterStatus = "&eClaimed";
                filterDetails = "&eBounties Claimed";
                colorConfigPath = path + ".firework-effect.claimed-color";
                break;
            case SURVIVED:
                filterStatus = "&eSurvived";
                filterDetails = "&eBounties Survived";
                colorConfigPath = path + ".firework-effect.survived-color";
                break;
            case MONEY_EARNED:
                filterStatus = "&eMoney";
                filterDetails = "&eMoney Earned";
                colorConfigPath = path + ".firework-effect.money-earned-color";
                break;
            case XP_EARNED:
                filterStatus = "&eXP";
                filterDetails = "&eXP Earned";
                colorConfigPath = path + ".firework-effect.xp-earned-color";
                break;
            case TOTAL_VALUE:
                filterStatus = "&eValue";
                filterDetails = "&eTotal Value Earned";
                colorConfigPath = path + ".firework-effect.total-value-color";
                break;
            default:
                filterStatus = "&eAll";
                filterDetails = "&eAll Stats";
                colorConfigPath = path + ".firework-effect.all-color";
        }
        if (sortHighToLow) {
            filterStatus += " &8| &eHigh→Low";
            filterDetails += " &8+ &eHigh to Low Sorting";
            shouldGlow = true;
        } else {
            filterStatus += " &8| &eLow→High";
            filterDetails += " &8+ &eLow to High Sorting";
        }

        applyFireworkStarColor(item, config, colorConfigPath);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - TopGUI] Failed to get ItemMeta for filter-button");
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        String name = ChatColor.translateAlternateColorCodes('&', config.getString(path + ".Name", "&eFilter: %filter_status%"));
        name = name.replace("%filter_status%", ChatColor.translateAlternateColorCodes('&', filterStatus));
        meta.setDisplayName(name);

        List<String> lore = config.getStringList(path + ".Lore");
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            line = ChatColor.translateAlternateColorCodes('&', line);
            line = line.replace("%filter_status%", ChatColor.translateAlternateColorCodes('&', filterStatus));
            line = line.replace("%filter_details%", ChatColor.translateAlternateColorCodes('&', filterDetails));
            coloredLore.add(line);
        }
        meta.setLore(coloredLore);

        if (shouldGlow || config.getBoolean(path + ".Enchantment-Glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);

        if (failureReason != null) {
            itemFailures.put(path, failureReason);
        }

        return item;
    }

    /**
     * Retrieves filtered and sorted player data
     * // note: Filters by stat type and sorts by value in specified order
     */
    private List<PlayerData> getFilteredPlayers(FilterType filterType, boolean sortHighToLow) {
        List<PlayerData> playerDataList = new ArrayList<>();

        if (plugin.getMySQL().isEnabled()) {
            try (Connection conn = plugin.getMySQL().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT player_uuid, claimed, survived, money_earned, xp_earned, total_value_earned FROM player_stats")) {
                while (rs.next()) {
                    String uuidString = rs.getString("player_uuid");
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        if (offlinePlayer.getName() == null) continue;
                        int claimed = rs.getInt("claimed");
                        int survived = rs.getInt("survived");
                        double moneyEarned = rs.getDouble("money_earned");
                        int xpEarned = rs.getInt("xp_earned");
                        double totalValueEarned = rs.getDouble("total_value_earned");
                        playerDataList.add(new PlayerData(uuid, claimed, survived, moneyEarned, xpEarned, totalValueEarned));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[DEBUG - TopGUI] Invalid UUID in player_stats: " + uuidString);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG - TopGUI] Failed to fetch stats from MySQL: " + e.getMessage());
            }
        } else {
            FileConfiguration statsConfig = plugin.getStatsConfig();
            ConfigurationSection playersSection = statsConfig.getConfigurationSection("players");
            if (playersSection != null) {
                for (String uuidString : playersSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                        if (offlinePlayer.getName() == null) continue;
                        int claimed = statsConfig.getInt("players." + uuidString + ".claimed", 0);
                        int survived = statsConfig.getInt("players." + uuidString + ".survived", 0);
                        double moneyEarned = statsConfig.getDouble("players." + uuidString + ".money_earned", 0.0);
                        int xpEarned = statsConfig.getInt("players." + uuidString + ".xp_earned", 0);
                        double totalValueEarned = statsConfig.getDouble("players." + uuidString + ".total_value_earned", 0.0);
                        playerDataList.add(new PlayerData(uuid, claimed, survived, moneyEarned, xpEarned, totalValueEarned));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[DEBUG - TopGUI] Invalid UUID in stats.yml: " + uuidString);
                    }
                }
            }
        }

        Comparator<PlayerData> comparator;
        switch (filterType) {
            case CLAIMED:
                comparator = Comparator.comparingInt(PlayerData::getClaimed);
                break;
            case SURVIVED:
                comparator = Comparator.comparingInt(PlayerData::getSurvived);
                break;
            case MONEY_EARNED:
                comparator = Comparator.comparingDouble(PlayerData::getMoneyEarned);
                break;
            case XP_EARNED:
                comparator = Comparator.comparingInt(PlayerData::getXPEarned);
                break;
            case TOTAL_VALUE:
                comparator = Comparator.comparingDouble(PlayerData::getTotalValueEarned);
                break;
            default:
                comparator = (p1, p2) -> {
                    OfflinePlayer op1 = Bukkit.getOfflinePlayer(p1.getUUID());
                    OfflinePlayer op2 = Bukkit.getOfflinePlayer(p2.getUUID());
                    String name1 = op1.getName() != null ? op1.getName() : p1.getUUID().toString();
                    String name2 = op2.getName() != null ? op2.getName() : p2.getUUID().toString();
                    return name1.compareToIgnoreCase(name2);
                };
        }

        if (sortHighToLow) {
            comparator = comparator.reversed();
        }

        return playerDataList.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    /**
     * Creates a customizable item from config
     * // note: Builds an item with specified material, name, lore, and glow
     */
    private ItemStack createItem(String path) {
        DebugManager debugManager = plugin.getDebugManager();
        String failureReason = null;

        String materialName = config.getString(path + ".Material", "STONE");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            debugManager.logWarning("[DEBUG - TopGUI] Invalid material '" + materialName + "' at " + path + ", using STONE");
            failureReason = "Invalid material '" + materialName + "'";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - TopGUI] Failed to get ItemMeta for item at " + path);
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        String name = config.getString(path + ".Name", "");
        if (!name.isEmpty()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        List<String> lore = config.getStringList(path + ".Lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList()));
        }

        if (config.getBoolean(path + ".Enchantment-Glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);

        if (failureReason != null) {
            itemFailures.put(path, failureReason);
        }

        return item;
    }

    /**
     * Creates a player skull with customizable settings and stats
     * // note: Builds a skull with the player’s skin and stats
     */
    private ItemStack createPlayerSkull(PlayerData data) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(data.getUUID());
        ItemStack skull = SkullUtils.createVersionAwarePlayerHead(player);
        if (skull == null || !VersionUtils.isPlayerHead(skull)) {
            plugin.getLogger().warning("[DEBUG - TopGUI] Failed to create player head for " + (player.getName() != null ? player.getName() : data.getUUID()));
            skull = VersionUtils.getXMaterialItemStack("SKELETON_SKULL");
        }
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            String name = config.getString("Plugin-Items.Player-Skull.Name", "&e%player%")
                    .replace("%player%", player.getName() != null ? player.getName() : "Unknown");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = config.getStringList("Plugin-Items.Player-Skull.Lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7Claimed: &e%bountiesplus_claimed%",
                        "&7Survived: &e%bountiesplus_survived%",
                        "&7Money: &e%bountiesplus_totalmoneyearned%",
                        "&7XP: &e%bountiesplus_totalxpearned%",
                        "&7Value: &e%bountiesplus_totalvalueearned%"
                );
            }
            PlaceholderContext context = PlaceholderContext.create()
                    .target(data.getUUID())
                    .bountyCount(data.getClaimed())
                    .withAmount(data.getMoneyEarned())
                    .expValue(data.getXPEarned())
                    .totalBountyAmount(data.getTotalValueEarned());
            List<String> processedLore = Placeholders.apply(lore, context);
            meta.setLore(processedLore);
            if (config.getBoolean("Plugin-Items.Player-Skull.Enchantment-Glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Creates a player skull for a Player object
     * // note: Builds a skull with the player’s skin using config-defined properties
     */
    private ItemStack createPlayerSkull(Player player) {
        ItemStack skull = SkullUtils.createVersionAwarePlayerHead(player);
        if (skull == null || !VersionUtils.isPlayerHead(skull)) {
            plugin.getLogger().warning("[DEBUG - TopGUI] Failed to create player head for " + player.getName());
            skull = VersionUtils.getXMaterialItemStack("SKELETON_SKULL");
        }
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            String name = config.getString("Plugin-Items.Player-Skull.Name", "&e%player%")
                    .replace("%player%", player.getName());
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = config.getStringList("Plugin-Items.Player-Skull.Lore");
            if (!lore.isEmpty()) {
                PlaceholderContext context = PlaceholderContext.create().player(player);
                List<String> processedLore = Placeholders.apply(lore, context);
                meta.setLore(processedLore);
            }
            if (config.getBoolean("Plugin-Items.Player-Skull.Enchantment-Glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Handles clicks in the TopGUI
     * // note: Prevents item movement and processes button actions, including filter cycling and sort toggling
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        DebugManager debugManager = plugin.getDebugManager();

        // Validate inventory
        Inventory trackedInventory = openInventories.getOrDefault(player.getUniqueId(), null);
        if (trackedInventory == null || !trackedInventory.equals(inventory)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || event.getSlotType() == null) return;

        int slot = event.getRawSlot();
        UUID playerUUID = player.getUniqueId();
        int currentPage = openPages.getOrDefault(playerUUID, 0);

        debugManager.bufferDebug("TopGUI click by " + player.getName() + " in slot " + slot);

        if (slot == 48 && inventory.getItem(48) != null) {
            // Previous Page
            openTopGUI(player, currentPage - 1, currentFilterType, sortHighToLow);
        } else if (slot == 49) {
            // Close
            player.closeInventory();
        } else if (slot == 50 && inventory.getItem(50) != null) {
            // Next Page
            openTopGUI(player, currentPage + 1, currentFilterType, sortHighToLow);
        } else if (slot == 5) {
            // Filter Button
            if (event.getClick() == ClickType.LEFT) {
                currentFilterType = FilterType.values()[(currentFilterType.ordinal() + 1) % FilterType.values().length];
                debugManager.bufferDebug("[DEBUG - TopGUI] Filter cycled to " + currentFilterType + " by " + player.getName());
            } else if (event.getClick() == ClickType.RIGHT) {
                sortHighToLow = !sortHighToLow;
                debugManager.bufferDebug("[DEBUG - TopGUI] Sort toggled to " + (sortHighToLow ? "High→Low" : "Low→High") + " by " + player.getName());
            }
            openTopGUI(player, 0, currentFilterType, sortHighToLow);
        }

        player.updateInventory();
    }

    /**
     * Cleans up when the inventory is closed
     * // note: Removes player from tracking maps
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (openInventories.containsKey(playerUUID)) {
            openPages.remove(playerUUID);
            openInventories.remove(playerUUID);
        }
    }
}