package tony26.bountiesPlus.GUIs;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryCloseEvent;
import tony26.bountiesPlus.*;
import tony26.bountiesPlus.utils.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.utils.Placeholders;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tony26.bountiesPlus.utils.SkullUtils;
import tony26.bountiesPlus.wrappers.VersionWrapperFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class BountyGUI implements Listener {

    private final BountiesPlus plugin;
    private static String GUI_TITLE;
    private static int currentPage = 0;
    private static boolean showOnlyOnline = false;
    private static boolean filterHighToLow = false;
    private static final int ITEMS_PER_PAGE = 21;
    private static final ConcurrentMap<UUID, Long> awaitingSearchInput = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Long> recentClicks = new ConcurrentHashMap<>();
    public static final long SEARCH_TIMEOUT = 30000L;
    private static final String FILTER_BUTTON_ID = "FILTER_BUTTON";
    private static final String CREATE_BOUNTY_ID = "CREATE_BOUNTY";
    private static final String HUNTERS_DEN_ID = "HUNTERS_DEN";
    private static final String BOUNTY_HUNTER_ID = "BOUNTY_HUNTER";
    private static final String BOOST_CLOCK_ID = "BOOST_CLOCK";
    private static final String PREVIOUS_PAGE_ID = "PREVIOUS_PAGE";
    private static final String NEXT_PAGE_ID = "NEXT_PAGE";
    private static final String TURN_IN_SKULLS_ID = "TURN_IN_SKULLS";
    private static final String BACK_TO_MAIN_ID = "BACK_TO_MAIN";
    private static final Map<String, String> buttonFailures = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> playerShowOnlyOnline = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> playerFilterHighToLow = new ConcurrentHashMap<>();
    private static int[] bountySkullSlots; // Stores configurable slots for bounty skulls
    private final Player player;

    public static class BountyData {
        private final UUID targetUUID;
        private final UUID setterUUID;
        private final int amount;
        private final String setTime;
        private final String expireTime;
        private final double multiplier;

        public BountyData(UUID targetUUID, UUID setterUUID, int amount, String setTime, String expireTime, double multiplier) {
            this.targetUUID = targetUUID;
            this.setterUUID = setterUUID;
            this.amount = amount;
            this.setTime = setTime;
            this.expireTime = expireTime;
            this.multiplier = multiplier;
        }

        public UUID getTargetUUID() {
            return targetUUID;
        }

        public UUID getSetterUUID() {
            return setterUUID;
        }

        public int getAmount() {
            return amount;
        }

        public String getSetTime() {
            return setTime;
        }

        public String getExpireTime() {
            return expireTime;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public double getTotalBountyAmount() {
            BountyManager manager = BountiesPlus.getInstance().getBountyManager();
            Map<UUID, Integer> bounties = manager.getBountiesOnTarget(targetUUID);
            return bounties.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Constructs the BountyGUI
     * // note: Initializes main bounty GUI and registers listeners
     */
    public BountyGUI(BountiesPlus plugin, EventManager eventManager, Player player) {
        this.plugin = plugin;
        this.player = player;
        eventManager.register(this);
        eventManager.register(new BountySearchListener(plugin));
        startBoostClockUpdateTask();
    }

    /**
     * Loads configurable bounty skull slots from config
     * // note: Initializes bountySkullSlots with validated slots from BountyGUI.yml
     */
    private static void loadBountySkullSlots(FileConfiguration config, DebugManager debugManager) {
        List<Integer> slots = config.getIntegerList("bounty-skull-slots.slots");
        if (slots.isEmpty()) {
            debugManager.logWarning("[DEBUG - BountyGUI] No bounty-skull-slots defined in BountyGUI.yml, using default slots");
            slots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        Set<Integer> uniqueSlots = new HashSet<>();
        List<Integer> validSlots = new ArrayList<>();
        Set<Integer> reservedSlots = new HashSet<>(Arrays.asList(
                config.getInt("Plugin-Items.filter-button.slot", 47),
                config.getInt("Plugin-Items.search-button.slot", 4),
                config.getInt("Plugin-Items.create-bounty-button.slot", 50),
                config.getInt("Plugin-Items.hunters-den-button.slot", 48),
                config.getInt("Plugin-Items.bounty-hunter-button.slot", 51),
                config.getInt("Plugin-Items.previous-page-button.slot", 45),
                config.getInt("Plugin-Items.next-page-button.slot", 53),
                config.getInt("Plugin-Items.boost-clock.slot", 49),
                config.getInt("search-results.single-slot", 22)
        ));
        reservedSlots.addAll(config.getIntegerList("border.slots"));
        if (config.contains("Custom-Items")) {
            for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
                String path = "Custom-Items." + key;
                if (config.contains(path + ".slot")) {
                    reservedSlots.add(config.getInt(path + ".slot"));
                }
                if (config.contains(path + ".slots")) {
                    reservedSlots.addAll(config.getIntegerList(path + ".slots"));
                }
            }
        }
        for (int slot : slots) {
            if (slot >= 0 && slot < 54 && !reservedSlots.contains(slot) && uniqueSlots.add(slot)) {
                validSlots.add(slot);
            } else {
                debugManager.logWarning("[DEBUG - BountyGUI] Invalid or reserved bounty-skull-slot " + slot + " in BountyGUI.yml (must be 0-53, unique, not in border or Plugin-Items)");
            }
        }
        if (validSlots.isEmpty()) {
            debugManager.logWarning("[DEBUG - BountyGUI] No valid bounty-skull-slots, using default slots");
            validSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }
        bountySkullSlots = validSlots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Opens the Bounty GUI for a player
     * // note: Displays bounty skulls with selected filter and sort order, including navigation and action buttons
     */
    public static void openBountyGUI(Player player, boolean filterHighToLow, boolean showOnlyOnline, int page) {
        BountiesPlus pluginInstance = BountiesPlus.getInstance();
        FileConfiguration config = pluginInstance.getBountyGUIConfig();
        DebugManager debugManager = pluginInstance.getDebugManager();

        // Verify configuration integrity
        File configFile = new File(pluginInstance.getDataFolder(), "GUIs/BountyGUI.yml");
        if (!configFile.exists() || config.getConfigurationSection("Plugin-Items") == null) {
            debugManager.logWarning("[DEBUG - BountyGUI] BountyGUI.yml is missing or invalid, reloading default");
            try {
                if (configFile.exists()) configFile.delete(); // Remove invalid file
                pluginInstance.saveResource("GUIs/BountyGUI.yml", false); // Copy default
                config = YamlConfiguration.loadConfiguration(configFile);
                pluginInstance.getDebugManager().logDebug("[DEBUG - BountyGUI] Reloaded default BountyGUI.yml");
            } catch (IllegalArgumentException e) {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to reload default BountyGUI.yml: " + e.getMessage());
            }
        }

        loadBountySkullSlots(config, debugManager);
        UUID playerUUID = player.getUniqueId();
        playerShowOnlyOnline.putIfAbsent(playerUUID, false);
        playerFilterHighToLow.putIfAbsent(playerUUID, true);
        playerShowOnlyOnline.put(playerUUID, showOnlyOnline);
        playerFilterHighToLow.put(playerUUID, filterHighToLow);
        boolean currentShowOnlyOnline = playerShowOnlyOnline.get(playerUUID);
        boolean currentFilterHighToLow = playerFilterHighToLow.get(playerUUID);
        GUI_TITLE = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
        int guiSize = config.getInt("gui-size", 54);
        Inventory bountyGui = Bukkit.createInventory(null, guiSize, GUI_TITLE);
        currentPage = page;
        List<Bounty> filteredBounties = getFilteredBounties(pluginInstance.getBountyManager(), currentShowOnlyOnline, currentFilterHighToLow);
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredBounties.size() / bountySkullSlots.length));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        ItemStack borderPane = createBorderItem(config);
        fillBorder(bountyGui, borderPane, config);
        boolean enableShop = pluginInstance.getConfig().getBoolean("enable-shop", true);
        placeCustomItems(bountyGui, config);
        placeConfiguredButtons(bountyGui, config, enableShop, currentFilterHighToLow, currentShowOnlyOnline, currentPage, totalPages, pluginInstance, player);
        placeBountyItems(bountyGui, pluginInstance, config);
        player.openInventory(bountyGui);
    }

    private static Map<String, String> getBoostPlaceholders(BountiesPlus plugin) {
        Map<String, String> placeholders = new HashMap<>();

        if (plugin.getBoostedBounty() != null) {
            BoostedBounty boostedBounty = plugin.getBoostedBounty();
            placeholders.put("%current_boost_info%", getCurrentBoostText(boostedBounty));
            placeholders.put("%last_boost_info%", getLastBoostText(boostedBounty));
            placeholders.put("%next_boost_info%", getNextBoostText(boostedBounty, plugin));
        } else {
            placeholders.put("%current_boost_info%", "→ No active boost");
            placeholders.put("%last_boost_info%", "→ No previous boost");
            placeholders.put("%next_boost_info%", "→ Soon...");
        }

        if (plugin.getFrenzy() != null) {
            Frenzy frenzy = plugin.getFrenzy();
            placeholders.put("%current_frenzy_info%", getCurrentFrenzyText(frenzy));
            placeholders.put("%last_frenzy_info%", getLastFrenzyText(frenzy));
            placeholders.put("%next_frenzy_info%", getNextFrenzyText(frenzy));
        } else {
            placeholders.put("%current_frenzy_info%", "→ Frenzy disabled");
            placeholders.put("%last_frenzy_info%", "→ Frenzy disabled");
            placeholders.put("%next_frenzy_info%", "→ Frenzy disabled");
        }

        return placeholders;
    }

    private static String getCurrentFrenzyText(Frenzy frenzy) {
        try {
            if (frenzy.isFrenzyActive()) {
                long timeRemaining = frenzy.getFrenzyTimeRemaining();
                double multiplier = frenzy.getFrenzyMultiplier();
                return "&c→ &f&lACTIVE &7(&c" + String.format("%.1f", multiplier) + "x&7)\n&7  Ends in: &c" + formatTime(timeRemaining);
            } else {
                return "&7→ &8No active frenzy";
            }
        } catch (Exception e) {
            return "&7→ &8Unable to calculate";
        }
    }

    private static String getLastFrenzyText(Frenzy frenzy) {
        try {
            String lastTime = frenzy.getLastFrenzyTime();
            if (lastTime != null) {
                double multiplier = frenzy.getLastFrenzyMultiplier();
                return "&c→ &f" + String.format("%.1f", multiplier) + "x &7multiplier\n&7  Ended: &6" + lastTime;
            } else {
                return "&7→ &8No previous frenzy";
            }
        } catch (Exception e) {
            return "&7→ &8Unable to calculate";
        }
    }

    private static String getNextFrenzyText(Frenzy frenzy) {
        try {
            long timeUntilNext = frenzy.getTimeUntilNextFrenzy();
            if (timeUntilNext > 0) {
                return "&c→ &fIn " + formatTime(timeUntilNext);
            } else if (timeUntilNext == 0) {
                return "&c→ &cFrenzy incoming!";
            }
        } catch (Exception e) {
            return "&7→ &8Unable to calculate";
        }
        return "&7→ &8Unable to calculate";
    }

    private static String getCurrentBoostText(BoostedBounty boostedBounty) {
        try {
            UUID currentBoostedTarget = boostedBounty.getCurrentBoostedTarget();
            if (currentBoostedTarget != null) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(currentBoostedTarget);
                double multiplier = boostedBounty.getCurrentBoostMultiplier(currentBoostedTarget);
                String targetName = target.getName() != null ? target.getName() : "Unknown";
                return "&7→ &f" + targetName + " &7(&a" + String.format("%.1f", multiplier) + "x&7)";
            }
        } catch (Exception e) {
            return "&7→ &8No active boost";
        }
        return "&7→ &8No active boost";
    }

    private static String getLastBoostText(BoostedBounty boostedBounty) {
        try {
            String lastBoostedPlayer = boostedBounty.getLastBoostedPlayer();
            String lastBoostTime = boostedBounty.getLastBoostTime();
            if (lastBoostedPlayer != null && lastBoostTime != null) {
                return "&7→ &f" + lastBoostedPlayer + " &7boosted " + lastBoostTime + " ago";
            }
        } catch (Exception e) {
            return "&7→ &8No previous boost";
        }
        return "&7→ &8No previous boost";
    }

    private static String getNextBoostText(BoostedBounty boostedBounty, BountiesPlus plugin) {
        try {
            long timeUntilNext = boostedBounty.getTimeUntilNextBoost();
            if (timeUntilNext > 0) {
                return "&7→ &fIn " + formatTime(timeUntilNext);
            } else if (timeUntilNext == 0) {
                return "&7→ &aBoost incoming!";
            }
        } catch (Exception e) {
            return "&7→ &8Unable to calculate";
        }
        return "&7→ &8Unable to calculate";
    }

    private static String formatTime(long seconds) {
        return TimeFormatter.formatTimeRemaining(seconds);
    }

    /**
     * Places configured buttons in the Bounty GUI
     * // note: Adds filter, search, navigation, and action buttons to the inventory
     */
    private static void placeConfiguredButtons(Inventory inventory, FileConfiguration config, boolean enableShop,
                                               boolean filterHighToLow, boolean showOnlyOnline, int currentPage,
                                               int totalPages, BountiesPlus plugin, Player player) {
        DebugManager debugManager = plugin.getDebugManager();
        buttonFailures.clear(); // Clear previous failures

        Map<String, Integer> buttons = new HashMap<>();
        buttons.put("filter-button", config.getInt("Plugin-Items.filter-button.slot", 47));
        buttons.put("search-button", config.getInt("Plugin-Items.search-button.slot", 4));
        buttons.put("create-bounty-button", config.getInt("Plugin-Items.create-bounty-button.slot", 50));
        if (enableShop) {
            buttons.put("hunters-den-button", config.getInt("Plugin-Items.hunters-den-button.slot", 48));
        }
        buttons.put("bounty-hunter-button", config.getInt("Plugin-Items.bounty-hunter-button.slot", 51));
        if (currentPage > 0) {
            buttons.put("previous-page-button", config.getInt("Plugin-Items.previous-page-button.slot", 45));
        }
        if (currentPage < totalPages - 1) {
            buttons.put("next-page-button", config.getInt("Plugin-Items.next-page-button.slot", 53));
        }
        boolean hasBoostClockConfig = config.contains("Plugin-Items.boost-clock");
        if (plugin.getBoostedBounty() != null && hasBoostClockConfig) {
            buttons.put("boost-clock", config.getInt("Plugin-Items.boost-clock.slot", 49));
        }

        int totalButtons = buttons.size();
        int successfulButtons = 0;

        // Place buttons
        for (Map.Entry<String, Integer> entry : buttons.entrySet()) {
            String buttonName = entry.getKey();
            int slot = entry.getValue();

            ItemStack button = null;
            switch (buttonName) {
                case "filter-button":
                    button = createFilterItem(config, showOnlyOnline, filterHighToLow, player);
                    break;
                case "search-button":
                    button = createSearchItem(config, player);
                    break;
                case "create-bounty-button":
                case "hunters-den-button":
                case "bounty-hunter-button":
                    button = createGuiItem(buttonName, config, PlaceholderContext.create().player(player));
                    break;
                case "previous-page-button":
                case "next-page-button":
                    button = createNavigationButton(buttonName, config, currentPage + 1, totalPages);
                    break;
                case "boost-clock":
                    button = createBoostClockItem(config, plugin);
                    break;
            }

            if (button != null && button.getType() != Material.AIR) {
                inventory.setItem(slot, button);
                successfulButtons++;
            } else {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to place button " + buttonName + " in slot " + slot);
            }
        }

        // Log consolidated debug message
        if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG - BountyGUI] All buttons created");
        } else {
            StringBuilder failureMessage = new StringBuilder("[DEBUG - BountyGUI] " + successfulButtons + "/" + totalButtons + " buttons created");
            if (!buttonFailures.isEmpty()) {
                List<String> failures = buttonFailures.entrySet().stream()
                        .map(e -> e.getKey() + " Reason: " + e.getValue())
                        .collect(Collectors.toList());
                failureMessage.append(", failed to create: ").append(String.join(", ", failures));
            }
            debugManager.bufferFailure("BountyGUI_buttons_" + System.currentTimeMillis(), failureMessage.toString());
        }
    }

    /**
     * Creates a navigation button for the Bounty GUI
     * // note: Generates a navigation button with material, name, lore, and glow for pagination
     */
    private static ItemStack createNavigationButton(String sectionName, FileConfiguration config, int currentPage, int totalPages) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String configPath = "Plugin-Items." + sectionName;
        String failureReason = null;

        String materialName = config.getString(configPath + ".material", "ARROW");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("ARROW")) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid material '" + materialName + "' for " + sectionName + ", using ARROW");
            failureReason = "Invalid material '" + materialName + "'";
            item = VersionUtils.getXMaterialItemStack("ARROW");
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - BountyGUI] Failed to get ItemMeta for " + sectionName);
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        String name = config.getString(configPath + ".name", "Navigation");
        name = ChatColor.translateAlternateColorCodes('&', name);
        meta.setDisplayName(name);

        List<String> lore = config.getStringList(configPath + ".lore");
        List<String> processedLore = new ArrayList<>();
        PlaceholderContext context = PlaceholderContext.create()
                .currentPage(currentPage)
                .totalPages(totalPages);
        for (String line : lore) {
            String processedLine = Placeholders.apply(line, context);
            processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
        }
        meta.setLore(processedLore);

        if (config.getBoolean(configPath + ".enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);

        if (failureReason != null) {
            buttonFailures.put(sectionName, failureReason);
        }

        return item;
    }

    /**
     * Creates the boost clock item for the Bounty GUI
     * // note: Generates a clock item with boost and frenzy information
     */
    private static ItemStack createBoostClockItem(FileConfiguration config, BountiesPlus plugin) {
        DebugManager debugManager = plugin.getDebugManager();
        String sectionName = "boost-clock";
        String configPath = "Plugin-Items." + sectionName;
        String failureReason = null;

        String materialName = config.getString(configPath + ".material", "CLOCK");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("CLOCK")) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid material '" + materialName + "' for boost-clock, using CLOCK");
            failureReason = "Invalid material '" + materialName + "'";
            item = VersionUtils.getXMaterialItemStack("CLOCK");
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - BountyGUI] Failed to get ItemMeta for boost-clock");
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        String name = config.getString(configPath + ".name", "&6⏰ Boost Clock");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> lore = config.getStringList(configPath + ".lore");
        List<String> processedLore = new ArrayList<>();
        Map<String, String> placeholders = getBoostPlaceholders(plugin);
        for (String line : lore) {
            String processedLine = line;
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                processedLine = processedLine.replace(placeholder.getKey(), placeholder.getValue());
            }
            processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
        }
        meta.setLore(processedLore);

        if (config.getBoolean(configPath + ".enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);

        if (failureReason != null) {
            buttonFailures.put(sectionName, failureReason);
        }

        return item;
    }

    /**
     * Creates a GUI item based on configuration
     * // note: Generates an item with specified material, name, lore, and glow for the Bounty GUI
     */
    private static ItemStack createGuiItem(String sectionName, FileConfiguration config, PlaceholderContext context) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String configPath = "Plugin-Items." + sectionName;
        String failureReason = null;

        if (!config.contains(configPath)) {
            debugManager.logWarning("[DEBUG - BountyGUI] Configuration path " + configPath + " not found in BountyGUI.yml");
            failureReason = "Missing configuration path";
            return new ItemStack(Material.STONE);
        }

        String materialName = config.getString(configPath + ".material", "STONE");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid material '" + materialName + "' for " + sectionName + ", falling back to STONE");
            failureReason = "Invalid material '" + materialName + "'";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - BountyGUI] Failed to get ItemMeta for " + sectionName + " with material " + item.getType().name());
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        String name = config.getString(configPath + ".name", "Item");
        name = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context));
        meta.setDisplayName(name);

        List<String> lore = config.getStringList(configPath + ".lore");
        List<String> processedLore = Placeholders.apply(lore, context);
        meta.setLore(processedLore);

        if (config.getBoolean(configPath + ".enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);

        if (failureReason != null) {
            buttonFailures.put(sectionName, failureReason);
        }

        return item;
    }

    /**
     * Creates the filter button item for the Bounty GUI
     * // note: Generates a firework star with filter status and details in lore
     */
    private static ItemStack createFilterItem(FileConfiguration config, boolean showOnlyOnline, boolean filterHighToLow, Player player) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String sectionName = "filter-button";
        String configPath = "Plugin-Items." + sectionName;
        String failureReason = null;

        if (!config.contains(configPath)) {
            debugManager.logWarning("[DEBUG - BountyGUI] Configuration path " + configPath + " not found in BountyGUI.yml");
            failureReason = "Missing configuration path";
            return new ItemStack(VersionUtils.getFireworkStarMaterial());
        }

        String materialName = config.getString(configPath + ".material", "FIREWORK_STAR");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("FIREWORK_STAR")) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid material '" + materialName + "' for filter-button, using FIREWORK_STAR");
            failureReason = "Invalid material '" + materialName + "'";
            item = VersionUtils.getXMaterialItemStack("FIREWORK_STAR");
        }

        boolean shouldGlow = showOnlyOnline && filterHighToLow;
        String filterStatus;
        String filterDetails;
        String colorConfigPath;
        if (showOnlyOnline && filterHighToLow) {
            filterStatus = "&cOnline &8| &eHigh→Low";
            filterDetails = "&aOnline Only &8+ &eHigh to Low Sorting";
            colorConfigPath = configPath + ".firework-effect.online-sorted-color";
        } else if (showOnlyOnline && !filterHighToLow) {
            filterStatus = "&cOnline &8| &eLow→High";
            filterDetails = "&aOnline Only &8+ &eLow to High Sorting";
            colorConfigPath = configPath + ".firework-effect.online-no-sort-color";
        } else if (!showOnlyOnline && filterHighToLow) {
            filterStatus = "&fAll &8| &eHigh→Low";
            filterDetails = "&fAll Bounties &8+ &eHigh to Low Sorting";
            colorConfigPath = configPath + ".firework-effect.all-sorted-color";
        } else {
            filterStatus = "&fAll &8| &eLow→High";
            filterDetails = "&fAll Bounties &8+ &eLow to High Sorting";
            colorConfigPath = configPath + ".firework-effect.all-no-sort-color";
        }

        applyFireworkStarColor(item, config, colorConfigPath);

        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .filterStatus(filterStatus)
                .filterDetails(filterDetails);

        String name = config.getString(configPath + ".name", "&eFilter: %bountiesplus_filter_status%");
        String processedName = Placeholders.apply(name, context);
        List<String> lore = config.getStringList(configPath + ".lore");
        if (lore.isEmpty()) {
            lore = Arrays.asList(
                    "&7Left Click: Toggle Online/All filter",
                    "&7Right Click: Toggle High-to-Low sorting",
                    "&7Current: %bountiesplus_filter_details%"
            );
        }
        List<String> processedLore = Placeholders.apply(lore, context);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(processedName);
            meta.setLore(processedLore);
            if (shouldGlow || config.getBoolean(configPath + ".enchantment-glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        } else {
            failureReason = "Failed to get ItemMeta";
        }

        if (failureReason != null) {
            buttonFailures.put(sectionName, failureReason);
        }

        return item;
    }

    /**
     * Creates a bounty item for the GUI
     * // note: Generates a player skull with customizable appearance based on bounty status
     */
    private static ItemStack createBountyItem(Bounty bounty, FileConfiguration config, BountiesPlus plugin) {
        DebugManager debugManager = plugin.getDebugManager();
        try {
            UUID targetUUID = bounty.getTargetUUID();
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetUUID);
            String targetName = offlineTarget.getName() != null ? offlineTarget.getName() : "Unknown";
            ItemStack skull = SkullUtils.createVersionAwarePlayerHead(offlineTarget);
            if (!VersionUtils.isPlayerHead(skull)) {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to create PLAYER_HEAD for bounty " + targetName);
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
                errorMessage = errorMessage.replace("%material%", "PLAYER_HEAD").replace("%button%", "bounty-item");
                debugManager.logDebug("[DEBUG - BountyGUI] " + ChatColor.stripColor(errorMessage));
                return new ItemStack(Material.STONE);
            }
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to get skull meta for bounty " + targetName);
                return skull;
            }
            BoostedBounty boostedBounty = plugin.getBoostedBounty();
            Frenzy frenzy = plugin.getFrenzy();
            boolean isBoosted = boostedBounty != null && boostedBounty.getCurrentBoostedTarget() != null &&
                    boostedBounty.getCurrentBoostedTarget().equals(targetUUID);
            boolean isFrenzyActive = frenzy != null && frenzy.isFrenzyActive();
            String configPath = isFrenzyActive ? "frenzy-skull" : (isBoosted ? "boosted-skull" : "bounty-item");
            if (!config.contains(configPath)) {
                debugManager.logWarning("[DEBUG - BountyGUI] Configuration path " + configPath + " not found in BountyGUI.yml, using defaults");
            }
            double totalBountyAmount = bounty.getCurrentPool();
            double moneyValue = bounty.getCurrentMoney();
            int expValue = bounty.getCurrentXP();
            int itemCount = bounty.getCurrentItems().size();
            double itemValue = bounty.getCurrentItemValue();
            double poolIncreasePercent = bounty.getPoolIncreasePercent();
            String expireTime = isFrenzyActive || isBoosted ? TimeFormatter.formatMinutesToReadable(bounty.getCurrentDurationMinutes(), bounty.isPermanent()) : "&4&k|||&4 &4&mDeath Contract&4 &4&k|||";
            List<String> top3Sponsors = bounty.getTopSponsors(3).stream()
                    .map(sponsor -> sponsor.isAnonymous() ? "&k|||||||" : (Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName() != null ? Bukkit.getOfflinePlayer(sponsor.getPlayerUUID()).getName() : "Unknown"))
                    .collect(Collectors.toList());
            PlaceholderContext context = PlaceholderContext.create()
                    .target(targetUUID)
                    .onlineStatus(Bukkit.getPlayer(targetUUID) != null ? "&aOnline" : "&cOffline")
                    .moneyValue(moneyValue)
                    .expValue(expValue)
                    .itemCount(itemCount)
                    .itemValue(itemValue)
                    .pool(totalBountyAmount)
                    .poolIncreasePercent(poolIncreasePercent)
                    .expireTime(expireTime)
                    .sponsors(String.join(", ", top3Sponsors));
            if (isFrenzyActive) {
                context = context.frenzy(frenzy.getFrenzyMultiplier());
            } else if (isBoosted) {
                context = context.boost(boostedBounty.getCurrentBoostMultiplier(targetUUID));
            }
            String name = config.getString(configPath + ".name", "&4&l%bountiesplus_target% &7&l&o(&4&l&o%bountiesplus_online_status%&7&l&o)");
            String displayName = Placeholders.apply(name, context);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            List<String> lore = config.getStringList(configPath + ".lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7&l✦ &eStatus: &bNo active bounty",
                        "&7&l✦ &eOnline: %bountiesplus_online_status%",
                        "&7&l✦ &eTotal Reward: &a%bountiesplus_pool%",
                        "&7&l✦ &eMoney: &a%bountiesplus_money_value%",
                        "&7&l✦ &eXP: &b%bountiesplus_exp_value%",
                        "&7&l✦ &eItems: &d%bountiesplus_item_count% &7(&d%bountiesplus_item_value%&7)",
                        "&7&l✦ &eValue Increase: &6%bountiesplus_pool_increase_percent%%%",
                        "&7&l✦ &eExpires: &f%bountiesplus_expire_time%",
                        "",
                        "&7&l✦ &eSponsors: &c%bountiesplus_sponsors%"
                );
            }
            List<String> processedLore = Placeholders.apply(lore, context);
            meta.setLore(processedLore);
            if (config.getBoolean(configPath + ".enchantment-glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            debugManager.logWarning("[DEBUG - BountyGUI] Error creating bounty item: " + e.getMessage());
            return new ItemStack(Material.STONE);
        }
    }

    /**
     * Opens a GUI showing a specific player's bounty
     * // note: Displays a single bounty for the searched player, if found
     */
    public static void openSearchBountyGUI(Player viewer, OfflinePlayer target, BountiesPlus plugin) {
        FileConfiguration config = plugin.getBountyGUIConfig();
        String guiTitle = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
        Inventory searchGui = Bukkit.createInventory(null, 54, guiTitle);
        ItemStack borderPane = createBorderItem(config);
        fillBorder(searchGui, borderPane, config);
        boolean enableShop = plugin.getConfig().getBoolean("enable-shop", true);
        placeConfiguredButtons(searchGui, config, enableShop, filterHighToLow, showOnlyOnline, 0, 1, plugin, viewer);
        BountyManager bountyManager = plugin.getBountyManager();
        Bounty bounty = bountyManager.getBounty(target.getUniqueId());
        if (bounty != null) {
            ItemStack bountyItem = createBountyItem(bounty, config, plugin);
            searchGui.setItem(config.getInt("search-results.single-slot", 22), bountyItem);
        } else {
            ItemStack searchItem = createSearchResultItem(target.getUniqueId(), config, plugin);
            searchGui.setItem(config.getInt("search-results.single-slot", 22), searchItem);
            MessageUtils.sendFormattedMessage(viewer, "bounty-player-not-found");
        }
        viewer.openInventory(searchGui);
    }

    private static void applyFireworkStarColor(ItemStack item, FileConfiguration config, String colorConfigPath) {
        if (item == null || item.getType() != VersionUtils.getFireworkStarMaterial()) {
            return;
        }
        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();
        if (meta == null) return;
        Color color = getColorFromConfig(config, colorConfigPath);
        String effectTypeString = config.getString("filter-button.firework-effect.effect-type", "STAR");
        FireworkEffect.Type effectType;
        try {
            effectType = FireworkEffect.Type.valueOf(effectTypeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            effectType = FireworkEffect.Type.STAR;
            BountiesPlus.getInstance().getLogger().warning("Invalid firework effect type: " + effectTypeString + ", using STAR");
        }
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(color)
                .with(effectType)
                .build();
        meta.setEffect(effect);
        item.setItemMeta(meta);
    }

    private static Color getColorFromConfig(FileConfiguration config, String colorConfigPath) {
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
                BountiesPlus.getInstance().getLogger().warning("Invalid hex color: " + hex + ", using white");
                return Color.WHITE;
            }
        }
        BountiesPlus.getInstance().getLogger().warning("No color configuration found for path: " + colorConfigPath + ", using white");
        return Color.WHITE;
    }

    /**
     * Creates the search button item for the Bounty GUI
     * // note: Generates a sign item to prompt a bounty search
     */
    private static ItemStack createSearchItem(FileConfiguration config, Player player) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String sectionName = "search-button";
        String configPath = "Plugin-Items." + sectionName;
        String failureReason = null;

        if (!config.contains(configPath)) {
            debugManager.logWarning("[DEBUG - BountyGUI] Configuration path " + configPath + " not found in BountyGUI.yml");
            failureReason = "Missing configuration path";
            return VersionUtils.getXMaterialItemStack("OAK_SIGN");
        }

        String materialName = config.getString(configPath + ".material", "OAK_SIGN");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("OAK_SIGN")) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid material '" + materialName + "' for search-button, using OAK_SIGN");
            failureReason = "Invalid material '" + materialName + "'";
            item = VersionUtils.getXMaterialItemStack("OAK_SIGN");
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - BountyGUI] Failed to get ItemMeta for search-button with material " + item.getType().name());
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        PlaceholderContext context = PlaceholderContext.create().player(player);
        String name = config.getString(configPath + ".name", "&eSearch Bounties");
        String processedName = Placeholders.apply(name, context);
        List<String> lore = config.getStringList(configPath + ".lore");
        List<String> processedLore = Placeholders.apply(lore, context);

        meta.setDisplayName(processedName);
        meta.setLore(processedLore);
        if (config.getBoolean(configPath + ".enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);

        if (failureReason != null) {
            buttonFailures.put(sectionName, failureReason);
        }

        return item;
    }

    /**
     * Creates a search result item for a player without a bounty
     * // note: Generates a player skull with customizable appearance for search results
     */
    private static ItemStack createSearchResultItem(UUID targetUUID, FileConfiguration config, BountiesPlus plugin) {
        DebugManager debugManager = plugin.getDebugManager();
        try {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetUUID);
            String targetName = offlineTarget.getName() != null ? offlineTarget.getName() : "Unknown";
            ItemStack skull = SkullUtils.createVersionAwarePlayerHead(offlineTarget);
            if (!VersionUtils.isPlayerHead(skull)) {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to create PLAYER_HEAD for search result " + targetName);
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
                errorMessage = errorMessage.replace("%material%", "PLAYER_HEAD").replace("%button%", "search-results-skull");
                debugManager.logDebug("[DEBUG - BountyGUI] " + ChatColor.stripColor(errorMessage));
                return new ItemStack(Material.STONE);
            }
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to get skull meta for search result " + targetName);
                return skull;
            }

            String configPath = "search-results-skull";
            if (!config.contains(configPath)) {
                debugManager.logWarning("[DEBUG - BountyGUI] Configuration path " + configPath + " not found in BountyGUI.yml, using defaults");
            }

            PlaceholderContext context = PlaceholderContext.create()
                    .target(targetUUID)
                    .onlineStatus(Bukkit.getPlayer(targetUUID) != null ? "&aOnline" : "&cOffline");

            String name = config.getString(configPath + ".name", "&7&l%bountiesplus_target% &7(&bNo Bounty&7)");
            String displayName = Placeholders.apply(name, context);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

            List<String> lore = config.getStringList(configPath + ".lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7&l✦ &eStatus: &bNo active bounty",
                        "&7&l✦ &eOnline: %bountiesplus_online_status%"
                );
            }
            List<String> processedLore = Placeholders.apply(lore, context);
            meta.setLore(processedLore);

            if (config.getBoolean(configPath + ".enchantment-glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            debugManager.logWarning("[DEBUG - BountyGUI] Error creating search result item: " + e.getMessage());
            return new ItemStack(Material.STONE);
        }
    }

    /**
     * Opens the Bounty GUI with search results for multiple players
     * // note: Populates GUI with skulls of all players matching the search query
     */
    public static void openSearchResultsGUI(Player player, List<UUID> targetUUIDs, BountiesPlus plugin) {
        FileConfiguration config = plugin.getBountyGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        loadBountySkullSlots(config, debugManager);
        String guiTitle = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
        int guiSize = config.getInt("gui-size", 54);
        Inventory searchGui = Bukkit.createInventory(null, guiSize, guiTitle);
        UUID playerUUID = player.getUniqueId();
        boolean currentShowOnlyOnline = playerShowOnlyOnline.getOrDefault(playerUUID, false);
        boolean currentFilterHighToLow = playerFilterHighToLow.getOrDefault(playerUUID, true);
        int totalPages = Math.max(1, (int) Math.ceil((double) targetUUIDs.size() / bountySkullSlots.length));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        ItemStack borderPane = createBorderItem(config);
        fillBorder(searchGui, borderPane, config);
        boolean enableShop = plugin.getConfig().getBoolean("enable-shop", true);
        placeCustomItems(searchGui, config);
        placeConfiguredButtons(searchGui, config, enableShop, currentFilterHighToLow, currentShowOnlyOnline, currentPage, totalPages, plugin, player);
        placeSearchResultItems(searchGui, plugin, config, targetUUIDs);
        player.openInventory(searchGui);
    }

    /**
     * Places search result items in the GUI
     * // note: Populates the inventory with skulls for players matching the search query
     */
    private static void placeSearchResultItems(Inventory inventory, BountiesPlus plugin, FileConfiguration config, List<UUID> targetUUIDs) {
        BountyManager bountyManager = plugin.getBountyManager();
        int startIndex = currentPage * bountySkullSlots.length;
        int endIndex = Math.min(startIndex + bountySkullSlots.length, targetUUIDs.size());
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            UUID targetUUID = targetUUIDs.get(i);
            Bounty bounty = bountyManager.getBounty(targetUUID);
            if (bounty != null) {
                ItemStack bountyItem = createBountyItem(bounty, config, plugin);
                inventory.setItem(bountySkullSlots[slotIndex], bountyItem);
            } else {
                ItemStack searchItem = createSearchResultItem(targetUUID, config, plugin);
                inventory.setItem(bountySkullSlots[slotIndex], searchItem);
            }
        }
    }

    /**
     * Creates a border item for the GUI based on configuration
     * // note: Generates border item with specified material and glow
     */
    private static ItemStack createBorderItem(FileConfiguration config) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String materialName = config.getString("border.material", "WHITE_STAINED_GLASS_PANE");
        ItemStack borderPane = VersionUtils.getXMaterialItemStack(materialName);
        if (borderPane.getType() == Material.STONE && !materialName.equalsIgnoreCase("WHITE_STAINED_GLASS_PANE")) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid border material '" + materialName + "' in BountyGUI.yml, using WHITE_STAINED_GLASS_PANE");
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", "border");
            debugManager.logDebug("[DEBUG - BountyGUI] " + ChatColor.stripColor(errorMessage));
            borderPane = VersionUtils.getXMaterialItemStack("WHITE_STAINED_GLASS_PANE");
        }
        ItemMeta borderMeta = borderPane.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', " "));
            if (config.getBoolean("border.enchantment-glow", false)) {
                borderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                borderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            borderPane.setItemMeta(borderMeta);
        } else {
            debugManager.logWarning("[DEBUG - BountyGUI] Failed to get ItemMeta for border item");
        }
        return borderPane;
    }

    /**
     * Fills the GUI border with configured items
     * // note: Places border items in specified slots from config, excluding plugin button slots and bounty skull slots
     */
    private static void fillBorder(Inventory inventory, ItemStack borderItem, FileConfiguration config) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        List<Integer> borderSlots = config.getIntegerList("border.slots");
        if (borderSlots.isEmpty()) {
            debugManager.logWarning("[DEBUG - BountyGUI] No border slots defined in BountyGUI.yml, using default slots");
            borderSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 46, 52);
        }
        // Exclude plugin button slots, custom item slots, and bounty skull slots to prevent overwrites
        Set<Integer> excludedSlots = new HashSet<>();
        excludedSlots.addAll(Arrays.asList(
                config.getInt("Plugin-Items.filter-button.slot", 47),
                config.getInt("Plugin-Items.search-button.slot", 4),
                config.getInt("Plugin-Items.create-bounty-button.slot", 50),
                config.getInt("Plugin-Items.hunters-den-button.slot", 48),
                config.getInt("Plugin-Items.bounty-hunter-button.slot", 51),
                config.getInt("Plugin-Items.previous-page-button.slot", 45),
                config.getInt("Plugin-Items.next-page-button.slot", 53),
                config.getInt("Plugin-Items.boost-clock.slot", 49),
                config.getInt("search-results.single-slot", 22)
        ));
        // Add custom item slots to exclusions
        if (config.contains("Custom-Items")) {
            for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
                String path = "Custom-Items." + key;
                if (config.contains(path + ".slot")) {
                    excludedSlots.add(config.getInt(path + ".slot"));
                }
                if (config.contains(path + ".slots")) {
                    excludedSlots.addAll(config.getIntegerList(path + ".slots"));
                }
            }
        }
        // Add bounty skull slots to exclusions
        for (int slot : bountySkullSlots) {
            excludedSlots.add(slot);
        }
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < inventory.getSize() && !excludedSlots.contains(slot)) {
                inventory.setItem(slot, borderItem.clone());
            } else if (slot < 0 || slot >= inventory.getSize()) {
                debugManager.logWarning("[DEBUG - BountyGUI] Invalid border slot " + slot + " in BountyGUI.yml (must be 0-" + (inventory.getSize() - 1) + ")");
            }
        }
    }

    /**
     * Places bounty items in the GUI // note: Populates the inventory with bounty skulls in configured slots
     */
    private static void placeBountyItems(Inventory inventory, BountiesPlus plugin, FileConfiguration config) {
        BountyManager bountyManager = plugin.getBountyManager();
        List<Bounty> filteredBounties = getFilteredBounties(bountyManager, showOnlyOnline, filterHighToLow);
        int startIndex = currentPage * bountySkullSlots.length;
        int endIndex = Math.min(startIndex + bountySkullSlots.length, filteredBounties.size());
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            Bounty bounty = filteredBounties.get(i);
            ItemStack bountyItem = createBountyItem(bounty, config, plugin);
            inventory.setItem(bountySkullSlots[slotIndex], bountyItem);
        }
    }

    /**
     * Places custom items in the Bounty GUI
     * // note: Populates the inventory with custom items defined in BountyGUI.yml
     */
    private static void placeCustomItems(Inventory inventory, FileConfiguration config) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        if (!config.contains("Custom-Items")) {
            debugManager.logDebug("[DEBUG - BountyGUI] No Custom-Items section found in BountyGUI.yml");
            return;
        }

        int totalItems = config.getConfigurationSection("Custom-Items").getKeys(false).size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
            String configPath = "Custom-Items." + key;
            String materialName = config.getString(configPath + ".material", "STONE");
            ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
            String failureReason = null;

            if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                debugManager.logWarning("[DEBUG - BountyGUI] Invalid material '" + materialName + "' for custom item " + key + ", using STONE");
                failureReason = "Invalid material '" + materialName + "'";
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - BountyGUI] Failed to get ItemMeta for custom item " + key);
                failureReason = "Failed to get ItemMeta";
            } else {
                String name = config.getString(configPath + ".name", "Item");
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                List<String> lore = config.getStringList(configPath + ".lore");
                List<String> processedLore = new ArrayList<>();
                for (String line : lore) {
                    processedLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(processedLore);
                if (config.getBoolean(configPath + ".enchantment-glow", false)) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }

            if (config.contains(configPath + ".slot")) {
                int slot = config.getInt(configPath + ".slot");
                if (slot >= 0 && slot < inventory.getSize()) {
                    if (failureReason == null) {
                        inventory.setItem(slot, item);
                        successfulItems++;
                    } else {
                        failures.add(key + " Reason: " + failureReason);
                    }
                } else {
                    debugManager.logWarning("[DEBUG - BountyGUI] Invalid slot " + slot + " for custom item " + key + " in BountyGUI.yml");
                    failures.add(key + " Reason: Invalid slot " + slot);
                }
            }

            if (config.contains(configPath + ".slots")) {
                List<Integer> slots = config.getIntegerList(configPath + ".slots");
                for (int slot : slots) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        if (failureReason == null) {
                            inventory.setItem(slot, item.clone());
                            successfulItems++;
                        } else {
                            failures.add(key + " Reason: " + failureReason);
                        }
                    } else {
                        debugManager.logWarning("[DEBUG - BountyGUI] Invalid slot " + slot + " for custom item " + key + " in BountyGUI.yml");
                        failures.add(key + " Reason: Invalid slot " + slot);
                    }
                }
            }
        }

        // Log consolidated debug message
        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - BountyGUI] All custom items created");
        } else {
            String failureMessage = "[DEBUG - BountyGUI] " + successfulItems + "/" + totalItems + " custom items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("BountyGUI_custom_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Retrieves filtered bounty data for display
     * // note: Filters and sorts bounties based on online status and sorting options
     */
    private static List<Bounty> getFilteredBounties(BountyManager bountyManager, boolean showOnlyOnline, boolean filterHighToLow) {
        Set<UUID> targets = bountyManager.getTargetsWithBounties();
        List<Bounty> bountyList = new ArrayList<>();
        BoostedBounty boostedBounty = BountiesPlus.getInstance().getBoostedBounty();
        Frenzy frenzy = BountiesPlus.getInstance().getFrenzy();
        boolean isFrenzyActive = frenzy != null && frenzy.isFrenzyActive();

        if (isFrenzyActive) {
            for (UUID targetUUID : targets) {
                Bounty bounty = bountyManager.getBounty(targetUUID);
                if (bounty != null) {
                    bountyList.add(bounty);
                }
            }
        } else {
            List<Bounty> boostedBounties = new ArrayList<>();
            List<Bounty> normalBounties = new ArrayList<>();
            for (UUID targetUUID : targets) {
                Bounty bounty = bountyManager.getBounty(targetUUID);
                if (bounty == null) continue;
                boolean isBoosted = boostedBounty != null && boostedBounty.getCurrentBoostedTarget() != null &&
                        boostedBounty.getCurrentBoostedTarget().equals(targetUUID);
                if (isBoosted) {
                    boostedBounties.add(bounty);
                } else {
                    normalBounties.add(bounty);
                }
            }

            // Sort by target name
            Comparator<Bounty> nameComparator = (b1, b2) -> {
                OfflinePlayer p1 = Bukkit.getOfflinePlayer(b1.getTargetUUID());
                OfflinePlayer p2 = Bukkit.getOfflinePlayer(b2.getTargetUUID());
                String name1 = p1.getName() != null ? p1.getName() : b1.getTargetUUID().toString();
                String name2 = p2.getName() != null ? p2.getName() : b2.getTargetUUID().toString();
                return name1.compareToIgnoreCase(name2);
            };
            boostedBounties.sort(nameComparator);
            normalBounties.sort(nameComparator);

            bountyList.addAll(boostedBounties);
            bountyList.addAll(normalBounties);
        }

        // Apply filters
        List<Bounty> filteredBounties = bountyList;
        if (showOnlyOnline) {
            filteredBounties = filteredBounties.stream()
                    .filter(bounty -> Bukkit.getPlayer(bounty.getTargetUUID()) != null && Bukkit.getPlayer(bounty.getTargetUUID()).isOnline())
                    .collect(Collectors.toList());
        }

        if (!isFrenzyActive) {
            filteredBounties = filteredBounties.stream()
                    .sorted((b1, b2) -> filterHighToLow ?
                            Double.compare(b2.getCurrentPool(), b1.getCurrentPool()) :
                            Double.compare(b1.getCurrentPool(), b2.getCurrentPool()))
                    .collect(Collectors.toList());
        }

        return filteredBounties;
    }

    /**
     * Handles inventory click events for the BountyGUI
     * // note: Processes clicks on player heads, buttons, and navigation controls to manage bounty selection and creation
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();
        FileConfiguration config = plugin.getBountyGUIConfig();

        event.setCancelled(true); // Prevent item movement
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - BountyGUI] Clicked slot " + slot + " by " + clickingPlayer.getName());

        // Debounce clicks to prevent multiple GUI initializations
        long currentTime = System.currentTimeMillis();
        Long lastClick = recentClicks.get(clickingPlayer.getUniqueId());
        if (lastClick != null && (currentTime - lastClick) < 300) {
            debugManager.logDebug("[DEBUG - BountyGUI] Ignored rapid click by " + clickingPlayer.getName());
            return;
        }
        recentClicks.put(clickingPlayer.getUniqueId(), currentTime);

        // Handle navigation buttons
        String previousPagePath = "Plugin-Items.previous-page-button.slot";
        String nextPagePath = "Plugin-Items.next-page-button.slot";
        String createBountyPath = "Plugin-Items.create-bounty-button.slot";

        UUID playerUUID = clickingPlayer.getUniqueId();
        boolean currentShowOnlyOnline = playerShowOnlyOnline.getOrDefault(playerUUID, false);
        boolean currentFilterHighToLow = playerFilterHighToLow.getOrDefault(playerUUID, true);

        if (slot == config.getInt(previousPagePath, 45) && clickedItem.getType() == Material.ARROW) {
            if (currentPage > 0) {
                currentPage--;
                openBountyGUI(clickingPlayer, currentFilterHighToLow, currentShowOnlyOnline, currentPage);
            }
        } else if (slot == config.getInt(nextPagePath, 53) && clickedItem.getType() == Material.ARROW) {
            List<Bounty> filteredBounties = getFilteredBounties(plugin.getBountyManager(), currentShowOnlyOnline, currentFilterHighToLow);
            int totalPages = Math.max(1, (int) Math.ceil((double) filteredBounties.size() / ITEMS_PER_PAGE));
            if (currentPage < totalPages - 1) {
                currentPage++;
                openBountyGUI(clickingPlayer, currentFilterHighToLow, currentShowOnlyOnline, currentPage);
            }
        } else if (slot == config.getInt(createBountyPath, 50)) {
            if (!clickingPlayer.hasPermission("bountiesplus.create")) {
                MessageUtils.sendFormattedMessage(clickingPlayer, "no-permission");
                debugManager.logDebug("[DEBUG - BountyGUI] No permission for create-bounty-button by " + clickingPlayer.getName());
                return;
            }
            clickingPlayer.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BountyCreationSession.removeSession(clickingPlayer); // Ensure clean session
                CreateGUI createGUI = new CreateGUI(clickingPlayer, plugin.getEventManager());
                createGUI.openInventory(clickingPlayer);
                debugManager.logDebug("[DEBUG - BountyGUI] Create Bounty button clicked by " + clickingPlayer.getName() + ", opened CreateGUI");
            }, 3L);
        } else if (VersionUtils.isPlayerHead(clickedItem)) {
            // Handle player head click by opening PreviewGUI
            SkullMeta skullMeta = (SkullMeta) clickedItem.getItemMeta();
            if (skullMeta != null && skullMeta.getOwningPlayer() != null) {
                OfflinePlayer target = skullMeta.getOwningPlayer();
                if (target.getName() != null) {
                    clickingPlayer.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        PreviewGUI previewGUI = new PreviewGUI(clickingPlayer, target.getUniqueId(), plugin.getEventManager());
                        previewGUI.openInventory(clickingPlayer);
                    }, 1L);
                }
            }
        } else {
            // Handle other buttons (filter, search, etc.) via handleButtonClick
            String buttonId = determineButtonId(slot, config);
            if (buttonId != null) {
                handleButtonClick(clickingPlayer, buttonId, event.getClick());
            }
        }
        clickingPlayer.updateInventory(); // Ensure inventory sync
    }

    /**
     * Determines the button ID based on the clicked slot
     * // note: Maps slot numbers to button identifiers for handling clicks
     */
    private String determineButtonId(int slot, FileConfiguration config) {
        if (slot == config.getInt("Plugin-Items.filter-button.slot", 47)) return FILTER_BUTTON_ID;
        if (slot == config.getInt("Plugin-Items.search-button.slot", 4)) return "SEARCH_BUTTON";
        if (slot == config.getInt("Plugin-Items.hunters-den-button.slot", 48)) return HUNTERS_DEN_ID;
        if (slot == config.getInt("Plugin-Items.bounty-hunter-button.slot", 51)) return BOUNTY_HUNTER_ID;
        if (slot == config.getInt("Plugin-Items.boost-clock.slot", 49)) return BOOST_CLOCK_ID;
        return null;
    }

    /**
     * Starts a task to update the boost-clock item's countdown
     * // note: Periodically refreshes boost-clock lore in open Bounty GUIs
     */
    private void startBoostClockUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTitle().equals(GUI_TITLE)) {
                    Inventory gui = player.getOpenInventory().getTopInventory();
                    FileConfiguration config = plugin.getBountyGUIConfig();
                    int boostClockSlot = config.getInt("boost-clock.slot", 49);
                    ItemStack currentItem = gui.getItem(boostClockSlot);
                    if (currentItem != null && currentItem.getType() == VersionUtils.getClockMaterial()) {
                        ItemStack updatedItem = createBoostClockItem(config, plugin);
                        gui.setItem(boostClockSlot, updatedItem);
                        player.updateInventory();
                    }
                }
            }
        }, 20L, 20L); // Update every second (20 ticks)
    }

    private void handleSkullTurnIn(Player player) {
        Economy economy = plugin.getEconomy();
        BountyManager bountyManager = plugin.getBountyManager();
        double totalReward = 0.0;
        int skullsProcessed = 0;
        List<ItemStack> skullsToRemove = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !VersionUtils.isPlayerHead(item)) continue;
            if (!HunterDenGUI.isBountySkull(item)) continue;
            double bountyAmount = HunterDenGUI.extractBountyValueFromSkull(item);
            if (bountyAmount <= 0) continue;
            totalReward += bountyAmount;
            skullsProcessed++;
            skullsToRemove.add(item);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            if (skullMeta != null && skullMeta.getLore() != null) {
                for (String loreLine : skullMeta.getLore()) {
                    if (loreLine.contains("Target:")) {
                        String targetName = ChatColor.stripColor(loreLine).replace("Target: ", "").trim();
                        Player targetPlayer = Bukkit.getPlayer(targetName);
                        if (targetPlayer != null) {
                            bountyManager.clearBounties(targetPlayer.getUniqueId());
                        }
                        break;
                    }
                }
            }
        }

        if (skullsProcessed == 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cNo valid bounty skulls found to turn in!"));
            return;
        }

        for (ItemStack skull : skullsToRemove) {
            player.getInventory().remove(skull);
        }

        if (economy != null) {
            economy.depositPlayer(player, totalReward);
        }

        FileConfiguration statsConfig = plugin.getStatsConfig();
        String playerUUID = player.getUniqueId().toString();
        int currentClaimed = statsConfig.getInt("players." + playerUUID + ".claimed", 0);
        double currentEarned = statsConfig.getDouble("players." + playerUUID + ".money_earned", 0.0);
        statsConfig.set("players." + playerUUID + ".claimed", currentClaimed + skullsProcessed);
        statsConfig.set("players." + playerUUID + ".money_earned", currentEarned + totalReward);
        plugin.saveEverything();

        String successMessage = ChatColor.translateAlternateColorCodes('&',
                "&a&lBounty Skulls Turned In!\n" +
                        "&7Skulls processed: &e" + skullsProcessed + "\n" +
                        "&7Total reward: &e$" + String.format("%.2f", totalReward));
        player.sendMessage(successMessage);

        try {
            String successSound = VersionWrapperFactory.getWrapper().getSuccessSound();
            player.playSound(player.getLocation(), Sound.valueOf(successSound), 1.0f, 1.0f);
        } catch (Exception e) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf("ENTITY_PLAYER_LEVELUP"), 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

// file: java/tony26/bountiesPlus/GUIs/BountyGUI.java (partial)
    /**
     * Handles button click events for the Bounty GUI
     * // note: Processes specific button interactions based on button ID
     */
    private void handleButtonClick(Player player, String buttonId, ClickType clickType) {
        DebugManager debugManager = plugin.getDebugManager();
        UUID playerUUID = player.getUniqueId();

        switch (buttonId) {
            case FILTER_BUTTON_ID:
                // Ensure filter states are initialized
                playerShowOnlyOnline.putIfAbsent(playerUUID, false);
                playerFilterHighToLow.putIfAbsent(playerUUID, true);
                boolean currentShowOnlyOnline = playerShowOnlyOnline.get(playerUUID);
                boolean currentFilterHighToLow = playerFilterHighToLow.get(playerUUID);

                if (clickType == ClickType.LEFT) {
                    // Toggle showOnlyOnline (All Bounties <-> Online Only), preserve filterHighToLow
                    playerShowOnlyOnline.put(playerUUID, !currentShowOnlyOnline);
                    debugManager.logDebug("[DEBUG - BountyGUI] Left clicked by " + player.getName() + " - Status updated to: \"" +
                            (playerShowOnlyOnline.get(playerUUID) ? "Online Only" : "All Bounties") + " - " +
                            (playerFilterHighToLow.get(playerUUID) ? "High to Low" : "Low to High") + "\"");
                } else if (clickType == ClickType.RIGHT) {
                    // Toggle filterHighToLow (High to Low <-> Low to High), preserve showOnlyOnline
                    playerFilterHighToLow.put(playerUUID, !currentFilterHighToLow);
                    debugManager.logDebug("[DEBUG - BountyGUI] Right clicked by " + player.getName() + " - Status updated to: \"" +
                            (playerShowOnlyOnline.get(playerUUID) ? "Online Only" : "All Bounties") + " - " +
                            (playerFilterHighToLow.get(playerUUID) ? "High to Low" : "Low to High") + "\"");
                }

                // Reset to page 0 and open the GUI with updated filter states
                currentPage = 0;
                openBountyGUI(player, playerFilterHighToLow.get(playerUUID), playerShowOnlyOnline.get(playerUUID), currentPage);
                break;

            case "SEARCH_BUTTON":
                if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                    if (!player.hasPermission("bountiesplus.bounty.search")) {
                        MessageUtils.sendFormattedMessage(player, "no-permission");
                        return;
                    }
                    // Close the GUI, send search prompt, and add player to awaiting search input
                    player.closeInventory();
                    MessageUtils.sendFormattedMessage(player, "search-prompt");
                    addAwaitingSearchInput(playerUUID);
                }
                break;

            case CREATE_BOUNTY_ID:
                CreateGUI createGUI = new CreateGUI(player, plugin.getEventManager());
                createGUI.openInventory(player);
                break;

            case HUNTERS_DEN_ID:
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    HunterDenGUI hunterDenGUI = new HunterDenGUI(player, plugin.getEventManager());
                    hunterDenGUI.openInventory(player);
                }, 1L);
                break;

            case BOUNTY_HUNTER_ID:
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    handleSkullTurnIn(player);
                }, 1L);
                break;

            case PREVIOUS_PAGE_ID:
                if (currentPage > 0) {
                    currentPage--;
                    openBountyGUI(player, playerFilterHighToLow.getOrDefault(playerUUID, true),
                            playerShowOnlyOnline.getOrDefault(playerUUID, false), currentPage);
                }
                break;

            case NEXT_PAGE_ID:
                List<Bounty> filteredBounties = getFilteredBounties(plugin.getBountyManager(),
                        playerShowOnlyOnline.getOrDefault(playerUUID, false),
                        playerFilterHighToLow.getOrDefault(playerUUID, true));
                int totalPages = Math.max(1, (int) Math.ceil((double) filteredBounties.size() / ITEMS_PER_PAGE));
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    openBountyGUI(player, playerFilterHighToLow.getOrDefault(playerUUID, true),
                            playerShowOnlyOnline.getOrDefault(playerUUID, false), currentPage);
                }
                break;

            case TURN_IN_SKULLS_ID:
                handleSkullTurnIn(player);
                break;

            case BACK_TO_MAIN_ID:
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openBountyGUI(player, playerFilterHighToLow.getOrDefault(playerUUID, true),
                            playerShowOnlyOnline.getOrDefault(playerUUID, false), currentPage);
                }, 1L);
                break;

            default:
                debugManager.logWarning("[DEBUG - BountyGUI] Unknown button clicked: " + buttonId);
                break;
        }
    }

    /**
     * Gets whether to show only online players in the Bounty GUI
     * // note: Returns the current online-only filter state
     */
    public static boolean getShowOnlyOnline() {
        return showOnlyOnline;
    }

    /**
     * Gets the current sorting direction for bounties
     * // note: Returns whether bounties are sorted from high to low
     */
    public static boolean getFilterHighToLow() {
        return filterHighToLow;
    }

    /**
     * Gets the current page of the Bounty GUI
     * // note: Returns the current page number for pagination
     */
    public static int getCurrentPage() {
        return currentPage;
    }

    /**
     * Gets the time a player was added to the search input awaiting list
     * // note: Returns the timestamp for a player’s search prompt, or null if not awaiting
     */
    public static Long getAwaitingSearchTime(UUID playerUUID) {
        return awaitingSearchInput.get(playerUUID);
    }

    /**
     * Removes a player from the search input awaiting list
     * // note: Clears a player from search prompt tracking
     */
    public static void removeAwaitingSearchInput(UUID playerUUID) {
        awaitingSearchInput.remove(playerUUID);
    }

    /**
     * Adds a player to the search input awaiting list
     * // note: Tracks a player waiting for chat input for bounty search
     */
    public static void addAwaitingSearchInput(UUID playerUUID) {
        awaitingSearchInput.put(playerUUID, System.currentTimeMillis());
    }

    /**
     * Clears the playerShowOnlyOnline map
     * // note: Resets the online-only filter states for all players
     */
    public static void clearPlayerShowOnlyOnline() {
        playerShowOnlyOnline.clear();
    }

    /**
     * Clears the playerFilterHighToLow map
     * // note: Resets the sorting filter states for all players
     */
    public static void clearPlayerFilterHighToLow() {
        playerFilterHighToLow.clear();
    }

    /**
     * Removes a player's online-only filter state
     * // note: Clears the specified player's entry from the online-only filter map
     */
    public static void removePlayerShowOnlyOnline(UUID playerUUID) {
        playerShowOnlyOnline.remove(playerUUID);
    }

    /**
     * Removes a player's sorting filter state
     * // note: Clears the specified player's entry from the high-to-low filter map
     */
    public static void removePlayerFilterHighToLow(UUID playerUUID) {
        playerFilterHighToLow.remove(playerUUID);
    }

    /**
     * Cleans up when the inventory is closed
     * // note: Removes player from tracking maps
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        UUID playerUUID = player.getUniqueId();
        playerShowOnlyOnline.remove(playerUUID);
        playerFilterHighToLow.remove(playerUUID);
    }
}