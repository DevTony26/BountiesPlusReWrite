package tony26.bountiesPlus.GUIs;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
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
import tony26.bountiesPlus.SkullUtils;
import tony26.bountiesPlus.wrappers.VersionWrapperFactory;

import java.util.*;
import java.util.stream.Collectors;

public class BountyGUI implements Listener {

    private final BountiesPlus plugin;
    private static String GUI_TITLE;
    private static int currentPage = 0;
    private static boolean showOnlyOnline = false;
    private static boolean filterHighToLow = false;
    private static final int ITEMS_PER_PAGE = 21;

    private static final String FILTER_BUTTON_ID = "FILTER_BUTTON";
    private static final String CREATE_BOUNTY_ID = "CREATE_BOUNTY";
    private static final String HUNTERS_DEN_ID = "HUNTERS_DEN";
    private static final String BOUNTY_HUNTER_ID = "BOUNTY_HUNTER";
    private static final String BOOST_CLOCK_ID = "BOOST_CLOCK";
    private static final String PREVIOUS_PAGE_ID = "PREVIOUS_PAGE";
    private static final String NEXT_PAGE_ID = "NEXT_PAGE";
    private static final String TURN_IN_SKULLS_ID = "TURN_IN_SKULLS";
    private static final String BACK_TO_MAIN_ID = "BACK_TO_MAIN";

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

        public UUID getTargetUUID() { return targetUUID; }
        public UUID getSetterUUID() { return setterUUID; }
        public int getAmount() { return amount; }
        public String getSetTime() { return setTime; }
        public String getExpireTime() { return expireTime; }
        public double getMultiplier() { return multiplier; }

        public double getTotalBountyAmount() {
            BountyManager manager = BountiesPlus.getInstance().getBountyManager();
            Map<UUID, Integer> bounties = manager.getBountiesOnTarget(targetUUID);
            return bounties.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public BountyGUI(BountiesPlus plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("BountyGUI listener registered.");
    }

    public static void openBountyGUI(Player player, boolean filterHighToLow, int page) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        FileConfiguration config = plugin.getBountyGUIConfig();
        plugin.reloadAllConfigs();

        GUI_TITLE = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
        plugin.getLogger().info("Opening BountyGUI for player: " + player.getName() + " with title: " + GUI_TITLE);
        Inventory bountyGui = Bukkit.createInventory(null, 54, GUI_TITLE);
        currentPage = page;
        BountyGUI.filterHighToLow = filterHighToLow;

        // Calculate total pages for navigation
        BountyManager bountyManager = plugin.getBountyManager();
        List<BountyData> filteredBounties = getFilteredBounties(bountyManager, showOnlyOnline, filterHighToLow);
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredBounties.size() / ITEMS_PER_PAGE));

        // Ensure current page is within bounds
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        // Create border glass pane
        ItemStack borderPane = createBorderItem(config);

        // Apply border if enabled
        boolean enableBorder = config.getBoolean("border.enabled", true);
        if (enableBorder) {
            fillBorder(bountyGui, borderPane, config);
        }

        // Check if shop is enabled in config.yml
        boolean enableShop = plugin.getConfig().getBoolean("enable-shop", true);
        plugin.getLogger().info("Enable-shop setting: " + enableShop);

        // Create and place buttons using slot configuration
        placeConfiguredButtons(bountyGui, config, enableShop, filterHighToLow, showOnlyOnline, currentPage, totalPages, plugin);

        // BOUNTY ITEMS (center area excluding border and bottom buttons)
        placeBountyItems(bountyGui, plugin, config);

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
     * Places configured buttons in the GUI // note: Adds filter, create, hunters den, bounty hunter, and navigation buttons
     */
    private static void placeConfiguredButtons(Inventory inventory, FileConfiguration config, boolean enableShop,
                                               boolean filterHighToLow, boolean showOnlyOnline, int currentPage, int totalPages, BountiesPlus plugin) {
        PlaceholderContext context = null; // No player-specific context for static buttons
        int filterSlot = config.getInt("filter-button.slot", 47);
        ItemStack filterButton = createFilterItem(config, showOnlyOnline, filterHighToLow);
        inventory.setItem(filterSlot, filterButton);
        int createSlot = config.getInt("create-bounty-button.slot", 50);
        ItemStack createButton = createGuiItem("create-bounty-button", config, context);
        inventory.setItem(createSlot, createButton);
        if (enableShop) {
            int huntersSlot = config.getInt("hunters-den-button.slot", 48);
            ItemStack huntersButton = createGuiItem("hunters-den-button", config, context);
            inventory.setItem(huntersSlot, huntersButton);
        }
        int bountyHunterSlot = enableShop ?
                config.getInt("bounty-hunter-button.slot", 51) :
                config.getInt("bounty-hunter-button.slot-no-shop", 49);
        ItemStack bountyHunterButton = createGuiItem("bounty-hunter-button", config, context);
        inventory.setItem(bountyHunterSlot, bountyHunterButton);
        if (currentPage > 0) {
            int prevSlot = config.getInt("previous-page-button.slot", 45);
            ItemStack prevButton = createNavigationButton("previous-page-button", config, currentPage + 1, totalPages);
            inventory.setItem(prevSlot, prevButton);
        }
        if (currentPage < totalPages - 1) {
            int nextSlot = config.getInt("next-page-button.slot", 53);
            ItemStack nextButton = createNavigationButton("next-page-button", config, currentPage + 1, totalPages);
            inventory.setItem(nextSlot, nextButton);
        }
        if (plugin.getBoostedBounty() != null && config.contains("boost-clock")) {
            int boostClockSlot = config.getInt("boost-clock.slot", 49);
            ItemStack boostClockItem = createBoostClockItem(config, plugin);
            inventory.setItem(boostClockSlot, boostClockItem);
        }
    }

    private static ItemStack createNavigationButton(String sectionName, FileConfiguration config, int currentPage, int totalPages) {
        String materialName = config.getString(sectionName + ".material", "ARROW");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("ARROW")) {
            BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' for " + sectionName + ", using ARROW");
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", sectionName);
            BountiesPlus.getInstance().getLogger().info(ChatColor.stripColor(errorMessage));
            item = VersionUtils.getXMaterialItemStack("ARROW");
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = ChatColor.translateAlternateColorCodes('&', config.getString(sectionName + ".name", "Navigation"));
        meta.setDisplayName(name);
        List<String> lore = config.getStringList(sectionName + ".lore");
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            line = ChatColor.translateAlternateColorCodes('&', line);
            line = line.replace("%current_page%", String.valueOf(currentPage));
            line = line.replace("%total_pages%", String.valueOf(totalPages));
            coloredLore.add(line);
        }
        meta.setLore(coloredLore);
        if (config.getBoolean(sectionName + ".enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createBoostClockItem(FileConfiguration config, BountiesPlus plugin) {
        String materialName = config.getString("boost-clock.material", "CLOCK");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("CLOCK")) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' for boost-clock, using CLOCK");
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", "boost-clock");
            plugin.getLogger().info(ChatColor.stripColor(errorMessage));
            item = VersionUtils.getXMaterialItemStack("CLOCK");
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = config.getString("boost-clock.name", "&6⏰ Boost Clock");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        Map<String, String> placeholders = getBoostPlaceholders(plugin);
        List<String> lore = config.getStringList("boost-clock.lore");
        List<String> processedLore = new ArrayList<>();
        for (String line : lore) {
            String processedLine = line;
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                processedLine = processedLine.replace(placeholder.getKey(), placeholder.getValue());
            }
            processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
        }
        meta.setLore(processedLore);
        if (config.getBoolean("boost-clock.enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createGuiItem(String sectionName, FileConfiguration config, PlaceholderContext context) {
        String materialName = config.getString(sectionName + ".material", "STONE");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' for " + sectionName + ", using STONE");
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", sectionName);
            BountiesPlus.getInstance().getLogger().info(ChatColor.stripColor(errorMessage));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = config.getString(sectionName + ".name", "Item");
        name = Placeholders.apply(name, context);
        meta.setDisplayName(name);
        List<String> lore = config.getStringList(sectionName + ".lore");
        List<String> processedLore = Placeholders.apply(lore, context);
        meta.setLore(processedLore);
        if (config.getBoolean(sectionName + ".enchantment-glow", false)) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createFilterItem(FileConfiguration config, boolean showOnlyOnline, boolean filterHighToLow) {
        String materialName = config.getString("filter-button.material", "FIREWORK_STAR");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("FIREWORK_STAR")) {
            BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' for filter-button, using FIREWORK_STAR");
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", "filter-button");
            BountiesPlus.getInstance().getLogger().info(ChatColor.stripColor(errorMessage));
            item = VersionUtils.getXMaterialItemStack("FIREWORK_STAR");
        }
        boolean shouldGlow = false;
        String filterStatus;
        String filterDetails;
        String colorConfigPath;
        if (showOnlyOnline && filterHighToLow) {
            shouldGlow = true;
            filterStatus = "&cOnline &8| &eHigh→Low";
            filterDetails = "&aOnline Only &8+ &eHigh to Low Sorting";
            colorConfigPath = "filter-button.firework-effect.online-sorted-color";
        } else if (showOnlyOnline && !filterHighToLow) {
            filterStatus = "&cOnline Only";
            filterDetails = "&aOnline Only";
            colorConfigPath = "filter-button.firework-effect.online-no-sort-color";
        } else if (!showOnlyOnline && filterHighToLow) {
            filterStatus = "&fAll &8| &eHigh→Low";
            filterDetails = "&fAll Bounties &8+ &eHigh to Low Sorting";
            colorConfigPath = "filter-button.firework-effect.all-sorted-color";
        } else {
            filterStatus = "&fAll Bounties";
            filterDetails = "&fAll Bounties";
            colorConfigPath = "filter-button.firework-effect.all-no-sort-color";
        }
        applyFireworkStarColor(item, config, colorConfigPath);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = ChatColor.translateAlternateColorCodes('&', config.getString("filter-button.name", "&eFilter: %filter_status%"));
            name = name.replace("%filter_status%", ChatColor.translateAlternateColorCodes('&', filterStatus));
            meta.setDisplayName(name);
            List<String> lore = config.getStringList("filter-button.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                line = ChatColor.translateAlternateColorCodes('&', line);
                line = line.replace("%filter_status%", ChatColor.translateAlternateColorCodes('&', filterStatus));
                line = line.replace("%filter_details%", ChatColor.translateAlternateColorCodes('&', filterDetails));
                coloredLore.add(line);
            }
            meta.setLore(coloredLore);
            if (shouldGlow || config.getBoolean("filter-button.enchantment-glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
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

    private static ItemStack createBorderItem(FileConfiguration config) {
        String materialName = config.getString("border.material", "BLACK_STAINED_GLASS_PANE");
        ItemStack borderPane = VersionUtils.getXMaterialItemStack(materialName);
        if (borderPane.getType() == Material.STONE && !materialName.equalsIgnoreCase("BLACK_STAINED_GLASS_PANE")) {
            BountiesPlus.getInstance().getLogger().warning("Invalid border material '" + materialName + "' in BountyGUI.yml, using BLACK_STAINED_GLASS_PANE");
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", "border");
            BountiesPlus.getInstance().getLogger().info(ChatColor.stripColor(errorMessage));
            borderPane = VersionUtils.getXMaterialItemStack("BLACK_STAINED_GLASS_PANE");
        }
        ItemMeta borderMeta = borderPane.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("border.name", " ")));
            List<String> borderLore = config.getStringList("border.lore");
            if (!borderLore.isEmpty()) {
                List<String> coloredBorderLore = borderLore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                borderMeta.setLore(coloredBorderLore);
            }
            if (config.getBoolean("border.enchantment-glow", false)) {
                borderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                borderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            borderPane.setItemMeta(borderMeta);
        }
        return borderPane;
    }

    private static void fillBorder(Inventory inventory, ItemStack borderItem, FileConfiguration config) {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderItem.clone());
        }
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, borderItem.clone());
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, borderItem.clone());
            inventory.setItem(row * 9 + 8, borderItem.clone());
        }
        int borderFillerSlot = config.getInt("border-filler.slot", -1);
        if (borderFillerSlot >= 0 && borderFillerSlot < 54) {
            inventory.setItem(borderFillerSlot, borderItem.clone());
        }
    }

    private static void placeBountyItems(Inventory inventory, BountiesPlus plugin, FileConfiguration config) {
        BountyManager bountyManager = plugin.getBountyManager();
        List<BountyData> filteredBounties = getFilteredBounties(bountyManager, showOnlyOnline, filterHighToLow);
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredBounties.size());
        int[] bountySlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            if (slotIndex < bountySlots.length) {
                BountyData bounty = filteredBounties.get(i);
                ItemStack bountyItem = createBountyItem(bounty, config, plugin);
                inventory.setItem(bountySlots[slotIndex], bountyItem);
            }
        }
    }

    /**
     * Retrieves filtered bounty data for display // note: Filters and sorts bounties based on online status and sorting options
     */
    private static List<BountyData> getFilteredBounties(BountyManager bountyManager, boolean showOnlyOnline, boolean filterHighToLow) {
        Map<UUID, Map<UUID, Integer>> allBounties = bountyManager.listAllBounties();
        List<BountyData> bountyDataList = new ArrayList<>();
        BoostedBounty boostedBounty = BountiesPlus.getInstance().getBoostedBounty();
        boolean isFrenzyActive = BountiesPlus.getInstance().getFrenzy() != null && BountiesPlus.getInstance().getFrenzy().isFrenzyActive();

        // Skip sorting during Frenzy Mode
        if (isFrenzyActive) {
            for (Map.Entry<UUID, Map<UUID, Integer>> targetEntry : allBounties.entrySet()) {
                UUID targetUUID = targetEntry.getKey();
                Map<UUID, Integer> setterBounties = targetEntry.getValue();
                for (Map.Entry<UUID, Integer> setterEntry : setterBounties.entrySet()) {
                    UUID setterUUID = setterEntry.getKey();
                    int amount = setterEntry.getValue();
                    String setTime = bountyManager.getBountySetTime(setterUUID, targetUUID);
                    String expireTime = bountyManager.getBountyExpireTime(setterUUID, targetUUID);
                    double multiplier = bountyManager.getBountyMultiplier(setterUUID, targetUUID);
                    bountyDataList.add(new BountyData(targetUUID, setterUUID, amount, setTime, expireTime, multiplier));
                }
            }
        } else {
            // Prioritize boosted bounties
            List<BountyData> boostedBounties = new ArrayList<>();
            List<BountyData> normalBounties = new ArrayList<>();

            for (Map.Entry<UUID, Map<UUID, Integer>> targetEntry : allBounties.entrySet()) {
                UUID targetUUID = targetEntry.getKey();
                Map<UUID, Integer> setterBounties = targetEntry.getValue();
                boolean isBoosted = boostedBounty != null && boostedBounty.getCurrentBoostedTarget() != null &&
                        boostedBounty.getCurrentBoostedTarget().equals(targetUUID);

                for (Map.Entry<UUID, Integer> setterEntry : setterBounties.entrySet()) {
                    UUID setterUUID = setterEntry.getKey();
                    int amount = setterEntry.getValue();
                    String setTime = bountyManager.getBountySetTime(setterUUID, targetUUID);
                    String expireTime = bountyManager.getBountyExpireTime(setterUUID, targetUUID);
                    double multiplier = bountyManager.getBountyMultiplier(setterUUID, targetUUID);
                    BountyData bountyData = new BountyData(targetUUID, setterUUID, amount, setTime, expireTime, multiplier);

                    if (isBoosted) {
                        boostedBounties.add(bountyData);
                    } else {
                        normalBounties.add(bountyData);
                    }
                }
            }

            // Sort boosted and normal bounties alphabetically by target name
            Comparator<BountyData> nameComparator = (b1, b2) -> {
                OfflinePlayer p1 = Bukkit.getOfflinePlayer(b1.getTargetUUID());
                OfflinePlayer p2 = Bukkit.getOfflinePlayer(b2.getTargetUUID());
                String name1 = p1.getName() != null ? p1.getName() : b1.getTargetUUID().toString();
                String name2 = p2.getName() != null ? p2.getName() : b2.getTargetUUID().toString();
                return name1.compareToIgnoreCase(name2);
            };

            boostedBounties.sort(nameComparator);
            normalBounties.sort(nameComparator);

            // Combine lists: boosted first, then normal
            bountyDataList.addAll(boostedBounties);
            bountyDataList.addAll(normalBounties);
        }

        // Apply filters
        List<BountyData> filteredBounties = bountyDataList;
        if (showOnlyOnline) {
            filteredBounties = filteredBounties.stream()
                    .filter(bounty -> {
                        Player target = Bukkit.getPlayer(bounty.getTargetUUID());
                        return target != null && target.isOnline();
                    })
                    .collect(Collectors.toList());
        }

        if (filterHighToLow && !isFrenzyActive) {
            filteredBounties = filteredBounties.stream()
                    .sorted((b1, b2) -> Double.compare(b2.getTotalBountyAmount(), b1.getTotalBountyAmount()))
                    .collect(Collectors.toList());
        }

        return filteredBounties;
    }

    /**
     * Creates a bounty item for the GUI // note: Generates a player skull with bounty details
     */
    private static ItemStack createBountyItem(BountyData bounty, FileConfiguration config, BountiesPlus plugin) {
        try {
            Player targetPlayer = Bukkit.getPlayer(bounty.getTargetUUID());
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(bounty.getTargetUUID());
            String targetName = targetPlayer != null ? targetPlayer.getName() :
                    (offlineTarget.getName() != null ? offlineTarget.getName() : "Unknown");
            ItemStack skull = SkullUtils.createVersionAwarePlayerHead(offlineTarget);
            if (!VersionUtils.isPlayerHead(skull)) {
                plugin.getLogger().warning("Failed to create PLAYER_HEAD for " + targetName);
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
                errorMessage = errorMessage.replace("%material%", "PLAYER_HEAD").replace("%button%", "bounty-item");
                plugin.getLogger().info(ChatColor.stripColor(errorMessage));
                return null;
            }
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta == null) {
                plugin.getLogger().warning("Failed to get skull meta for " + targetName);
                return skull;
            }
            List<String> setterNames = new ArrayList<>();
            Map<UUID, Integer> targetBounties = plugin.getBountyManager().getBountiesOnTarget(bounty.getTargetUUID());
            for (UUID setterUUID : targetBounties.keySet()) {
                OfflinePlayer setter = Bukkit.getOfflinePlayer(setterUUID);
                String setterName = setter.getName() != null ? setter.getName() : "Unknown";
                setterNames.add(setterName);
            }
            String setterList = String.join(", ", setterNames);
            boolean isFrenzyActive = plugin.getFrenzy() != null && plugin.getFrenzy().isFrenzyActive();
            boolean isBoosted = plugin.getBoostedBounty() != null &&
                    plugin.getBoostedBounty().getCurrentBoostedTarget() != null &&
                    plugin.getBoostedBounty().getCurrentBoostedTarget().equals(bounty.getTargetUUID());
            String nameKey = isFrenzyActive ? "frenzy-skull.name" :
                    (isBoosted ? "boosted-skull.name" : "bounty-item.name");
            String loreKey = isFrenzyActive ? "frenzy-skull.lore" :
                    (isBoosted ? "boosted-skull.lore" : "bounty-item.lore");
            String glowKey = isFrenzyActive ? "frenzy-skull.enchantment-glow" :
                    (isBoosted ? "boosted-skull.enchantment-glow" : null);
            PlaceholderContext context = PlaceholderContext.create()
                    .target(bounty.getTargetUUID())
                    .setter(bounty.getSetterUUID())
                    .bountyAmount((double) bounty.getAmount())
                    .totalBountyAmount(bounty.getTotalBountyAmount())
                    .setTime(bounty.getSetTime())
                    .expireTime(bounty.getExpireTime())
                    .multiplier(bounty.getMultiplier())
                    .bountyCount(targetBounties.size())
                    .setterList(setterList)
                    .onlineStatus(targetPlayer != null ? "&aOnline" : "&cOffline");
            String itemName = config.getString(nameKey, "%target%");
            String displayName = Placeholders.apply(itemName, context);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            List<String> loreLines = config.getStringList(loreKey);
            List<String> processedLore = Placeholders.apply(loreLines, context);
            meta.setLore(processedLore);
            if (glowKey != null && config.getBoolean(glowKey, isFrenzyActive || isBoosted)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            skull.setItemMeta(meta);
            return skull;
        } catch (Exception e) {
            plugin.getLogger().warning("Error creating bounty item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handles inventory click events for the Bounty GUI // note: Processes clicks on buttons and bounty skulls
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        if (title.equals(GUI_TITLE)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            FileConfiguration config = plugin.getBountyGUIConfig();
            if (slot == config.getInt("filter-button.slot", 47)) {
                if (!showOnlyOnline && !filterHighToLow) {
                    showOnlyOnline = true;
                    filterHighToLow = false;
                } else if (showOnlyOnline && !filterHighToLow) {
                    showOnlyOnline = false;
                    filterHighToLow = true;
                } else if (!showOnlyOnline && filterHighToLow) {
                    showOnlyOnline = true;
                    filterHighToLow = true;
                } else {
                    showOnlyOnline = false;
                    filterHighToLow = false;
                }
                currentPage = 0;
                openBountyGUI(player, filterHighToLow, currentPage);
            } else if (slot == config.getInt("create-bounty-button.slot", 50)) {
                new CreateGUI(player).openInventory(player);
            } else if (slot == config.getInt("hunters-den-button.slot", 48)) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    HunterDenGUI hunterDenGUI = new HunterDenGUI(player);
                    hunterDenGUI.openInventory(player);
                }, 1L);
            } else if (slot == config.getInt("bounty-hunter-button.slot", 51) ||
                    slot == config.getInt("bounty-hunter-button.slot-no-shop", 49)) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    handleSkullTurnIn(player);
                }, 1L);
            } else if (slot == config.getInt("boost-clock.slot", 49)) {
                openBountyGUI(player, filterHighToLow, currentPage);
                player.sendMessage(ChatColor.GREEN + "Boost information refreshed!");
            } else if (slot == config.getInt("previous-page-button.slot", 45) &&
                    clickedItem.getType() == Material.ARROW) {
                if (currentPage > 0) {
                    currentPage--;
                    openBountyGUI(player, filterHighToLow, currentPage);
                }
            } else if (slot == config.getInt("next-page-button.slot", 53) &&
                    clickedItem.getType() == Material.ARROW) {
                List<BountyData> filteredBounties = getFilteredBounties(plugin.getBountyManager(), showOnlyOnline, filterHighToLow);
                int totalPages = Math.max(1, (int) Math.ceil((double) filteredBounties.size() / ITEMS_PER_PAGE));
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    openBountyGUI(player, filterHighToLow, currentPage);
                }
            } else if (VersionUtils.isPlayerHead(clickedItem)) {
                int[] bountySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
                int slotIndex = -1;
                for (int i = 0; i < bountySlots.length; i++) {
                    if (slot == bountySlots[i]) {
                        slotIndex = i;
                        break;
                    }
                }
                if (slotIndex != -1) {
                    int dataIndex = currentPage * ITEMS_PER_PAGE + slotIndex;
                    List<BountyData> filteredBounties = getFilteredBounties(plugin.getBountyManager(), showOnlyOnline, filterHighToLow);
                    if (dataIndex < filteredBounties.size()) {
                        BountyData bounty = filteredBounties.get(dataIndex);
                        UUID targetUUID = bounty.getTargetUUID();
                        plugin.getLogger().info("Player " + player.getName() + " clicked bounty skull for target UUID: " + targetUUID);
                        new PreviewGUI(player, targetUUID).openInventory(player);
                    } else {
                        plugin.getLogger().warning("No BountyData found for slot " + slot + " (index " + dataIndex + ")");
                        String errorMessage = config.getString("messages.invalid-bounty-click", "&cInvalid bounty selection!");
                        PlaceholderContext context = PlaceholderContext.create().player(player);
                        player.sendMessage(Placeholders.apply(errorMessage, context));
                    }
                }
            }
        } else if (title.equals(ChatColor.translateAlternateColorCodes('&', "&6&lBounty Hunter - Turn In Skulls"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();
            if (clickedItem != null && clickedItem.hasItemMeta()) {
                FileConfiguration huntersDenConfig = plugin.getHuntersDenConfig();
                if (slot == huntersDenConfig.getInt("turn-in-skulls-button.slot", -1)) {
                    handleSkullTurnIn(player);
                } else if (slot == huntersDenConfig.getInt("back-to-main-button.slot", -1)) {
                    player.closeInventory();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        openBountyGUI(player, filterHighToLow, currentPage);
                    }, 1L);
                }
            }
        }
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

    private void handleButtonClick(Player player, String buttonId, ClickType clickType) {
        switch (buttonId) {
            case FILTER_BUTTON_ID:
                if (!showOnlyOnline && !filterHighToLow) {
                    showOnlyOnline = true;
                    filterHighToLow = false;
                } else if (showOnlyOnline && !filterHighToLow) {
                    showOnlyOnline = false;
                    filterHighToLow = true;
                } else if (!showOnlyOnline && filterHighToLow) {
                    showOnlyOnline = true;
                    filterHighToLow = true;
                } else {
                    showOnlyOnline = false;
                    filterHighToLow = false;
                }
                currentPage = 0;
                openBountyGUI(player, filterHighToLow, currentPage);
                break;
            case CREATE_BOUNTY_ID:
                new CreateGUI(player).openInventory(player);
                break;
            case HUNTERS_DEN_ID:
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    HunterDenGUI hunterDenGUI = new HunterDenGUI(player);
                    hunterDenGUI.openInventory(player);
                }, 1L);
                break;
            case BOUNTY_HUNTER_ID:
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    handleSkullTurnIn(player);
                }, 1L);
                break;
            case BOOST_CLOCK_ID:
                openBountyGUI(player, filterHighToLow, currentPage);
                player.sendMessage(ChatColor.GREEN + "Boost information refreshed!");
                break;
            case PREVIOUS_PAGE_ID:
                if (currentPage > 0) {
                    currentPage--;
                    openBountyGUI(player, filterHighToLow, currentPage);
                }
                break;
            case NEXT_PAGE_ID:
                List<BountyData> filteredBounties = getFilteredBounties(plugin.getBountyManager(), showOnlyOnline, filterHighToLow);
                int totalPages = Math.max(1, (int) Math.ceil((double) filteredBounties.size() / ITEMS_PER_PAGE));
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    openBountyGUI(player, filterHighToLow, currentPage);
                }
                break;
            case TURN_IN_SKULLS_ID:
                handleSkullTurnIn(player);
                break;
            case BACK_TO_MAIN_ID:
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openBountyGUI(player, filterHighToLow, currentPage);
                }, 1L);
                break;
            default:
                plugin.getLogger().info("Unknown button clicked: " + buttonId);
                break;
        }
    }
}