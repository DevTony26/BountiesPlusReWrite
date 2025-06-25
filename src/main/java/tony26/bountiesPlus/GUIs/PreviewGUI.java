package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.Bounty;
import tony26.bountiesPlus.utils.EventManager;
import tony26.bountiesPlus.utils.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Displays a preview of an active bounty when opened from BountyGUI
 */
public class PreviewGUI implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final Player player;
    private final BountiesPlus plugin;
    private final UUID targetUUID;
    private final String GUI_TITLE;
    private final Set<Integer> protectedSlots = new HashSet<>();
    private int currentPage = 0;

    /**
     * Constructs the PreviewGUI
     * // note: Initializes the bounty preview GUI and registers listeners
     */
    public PreviewGUI(Player player, UUID targetUUID, EventManager eventManager) {
        this.player = player;
        this.plugin = BountiesPlus.getInstance();
        this.targetUUID = targetUUID;
        FileConfiguration config = plugin.getPreviewGUIConfig();
        File configFile = new File(plugin.getDataFolder(), "GUIs/PreviewGUI.yml");

        // Verify configuration integrity
        if (!configFile.exists() || config.getConfigurationSection("bounty-info") == null) {
            plugin.getDebugManager().logWarning("[DEBUG - PreviewGUI] PreviewGUI.yml is missing or invalid, reloading default");
            try {
                if (configFile.exists()) configFile.delete(); // Remove invalid file
                plugin.saveResource("GUIs/PreviewGUI.yml", false); // Copy default
                config = YamlConfiguration.loadConfiguration(configFile);
                plugin.getDebugManager().logDebug("[DEBUG - PreviewGUI] Reloaded default PreviewGUI.yml");
            } catch (IllegalArgumentException e) {
                plugin.getDebugManager().logWarning("[DEBUG - PreviewGUI] Failed to reload default PreviewGUI.yml: " + e.getMessage());
            }
        }

        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&6Bounty Preview"));
        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE);
        eventManager.register(this);
        initializeGUI();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void openInventory(Player player) {
        updateProtectedSlots();
        if (!player.equals(this.player)) {
            return;
        }
        refreshGUI();
        player.openInventory(inventory);
    }

    private void initializeGUI() {
        refreshGUI();
    }

    /**
     * Refreshes the GUI with updated content // note: Clears and repopulates the inventory with borders, bounty info, bounty items, and custom items
     */
    private void refreshGUI() {
        inventory.clear();
        addBorders();
        addBountyInfoItem();
        addBountyItems();
        addCustomItems();
        updateProtectedSlots();
    }

    private void loadProtectedSlots(FileConfiguration config) {
        protectedSlots.clear();
        if (inventory == null) {
            return;
        }
        List<Integer> configSlots = config.getIntegerList("border.slots");
        if (!configSlots.isEmpty()) {
            protectedSlots.addAll(configSlots);
        } else {
            List<Integer> defaultSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
            protectedSlots.addAll(defaultSlots);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                protectedSlots.add(i);
            }
        }
    }

    private void updateProtectedSlots() {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        loadProtectedSlots(config);
    }

    /**
     * Adds border items to the GUI
     * // note: Populates border slots with configured material
     */
    private void addBorders() {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getPreviewGUIConfig();
        if (!config.getBoolean("border.enabled", true)) {
            debugManager.logDebug("[DEBUG - PreviewGUI] Borders disabled in PreviewGUI.yml");
            return;
        }

        String materialName = config.getString("border.material", "WHITE_STAINED_GLASS_PANE");
        ItemStack borderItem = VersionUtils.getXMaterialItemStack(materialName);
        String failureReason = null;
        if (borderItem.getType() == Material.STONE && !materialName.equalsIgnoreCase("WHITE_STAINED_GLASS_PANE")) {
            debugManager.logWarning("[DEBUG - PreviewGUI] Invalid border material '" + materialName + "' in PreviewGUI.yml, using WHITE_STAINED_GLASS_PANE");
            failureReason = "Invalid material '" + materialName + "'";
            borderItem = VersionUtils.getXMaterialItemStack("WHITE_STAINED_GLASS_PANE");
        }

        ItemMeta borderMeta = borderItem.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            if (config.getBoolean("border.enchantment-glow", false)) {
                borderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                borderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            borderItem.setItemMeta(borderMeta);
        } else {
            debugManager.logWarning("[DEBUG - PreviewGUI] Failed to get ItemMeta for border item");
            failureReason = "Failed to get ItemMeta";
        }

        List<Integer> borderSlots = config.getIntegerList("border.slots");
        if (borderSlots.isEmpty()) {
            borderSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
        }

        int totalItems = borderSlots.size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (int slot : borderSlots) {
            if (slot >= 0 && slot < 54) {
                if (failureReason == null) {
                    inventory.setItem(slot, borderItem.clone());
                    protectedSlots.add(slot);
                    successfulItems++;
                } else {
                    failures.add("border-slot-" + slot + " Reason: " + failureReason);
                }
            } else {
                debugManager.logWarning("[DEBUG - PreviewGUI] Invalid slot " + slot + " in PreviewGUI.yml border configuration");
                failures.add("border-slot-" + slot + " Reason: Invalid slot " + slot);
            }
        }

        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - PreviewGUI] All border items created");
        } else {
            String failureMessage = "[DEBUG - PreviewGUI] " + successfulItems + "/" + totalItems + " border items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("PreviewGUI_border_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds the main bounty information item to the GUI
     * // note: Displays bounty details like money, XP, and items
     */
    private void addBountyInfoItem() {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getPreviewGUIConfig();
        int slot = config.getInt("bounty-info.slot", 49);
        String failureReason = null;

        if (slot < 0 || slot > 53) {
            debugManager.logWarning("[DEBUG - PreviewGUI] Invalid bounty-info.slot " + slot + " in PreviewGUI.yml, using default 49");
            failureReason = "Invalid slot " + slot;
            slot = 49;
        }

        String materialName = config.getString("bounty-info.material", "PAPER");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("PAPER")) {
            debugManager.logWarning("[DEBUG - PreviewGUI] Invalid material '" + materialName + "' for bounty-info, using PAPER");
            failureReason = "Invalid material '" + materialName + "'";
            item = VersionUtils.getXMaterialItemStack("PAPER");
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - PreviewGUI] Failed to get ItemMeta for bounty-info item");
            failureReason = "Failed to get ItemMeta";
        } else {
            Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .target(targetUUID)
                    .bountyCount(bounty != null ? bounty.getSponsors().size() : 0)
                    .moneyValue(bounty != null ? bounty.getCurrentMoney() : 0.0)
                    .expValue(bounty != null ? bounty.getCurrentXP() : 0)
                    .timeValue(bounty != null ? bounty.getFormattedCurrentDuration() : "Permanent")
                    .itemCount(bounty != null ? bounty.getCurrentItems().size() : 0)
                    .itemValue(bounty != null ? bounty.getCurrentItemValue() : 0.0)
                    .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                    .moneyLine("&7Money: &a$" + (bounty != null ? String.format("%.2f", bounty.getCurrentMoney()) : "0.00"))
                    .experienceLine("&7Experience: &e" + (bounty != null ? (bounty.getCurrentXP() == 0 ? "0 XP Levels" : bounty.getCurrentXP() + " XP Level" + (bounty.getCurrentXP() > 1 ? "s" : "")) : "0 XP Levels"));

            String name = config.getString("bounty-info.name", "&6%bountiesplus_target% Bounty");
            if (name == null || name.isEmpty()) {
                debugManager.logWarning("[DEBUG - PreviewGUI] Invalid or empty bounty-info.name, using default");
                name = "&6%bountiesplus_target% Bounty";
            }
            String processedName = Placeholders.apply(name, context);
            meta.setDisplayName(processedName);

            List<String> lore = config.getStringList("bounty-info.lore");
            if (lore.isEmpty()) {
                debugManager.logWarning("[DEBUG - PreviewGUI] Empty bounty-info.lore, using default");
                lore = Arrays.asList(
                        "&7Top Sponsors: %bountiesplus_top3_sponsors_numbered%",
                        "",
                        "&7Original Money: &a$%bountiesplus_original_money%",
                        "&7Current Money: &a$%bountiesplus_money_value%",
                        "&7Money Increase: &e%bountiesplus_price_increase_percent%%",
                        "",
                        "&7Original Items: &b%bountiesplus_original_item_count% ($%bountiesplus_original_item_value%)",
                        "&7Current Items: &b%bountiesplus_item_count% ($%bountiesplus_item_value%)",
                        "&7Item Increase: &e%bountiesplus_item_increase_percent%%",
                        "",
                        "&7Original XP: &e%bountiesplus_original_xp% Levels",
                        "&7Current XP: &e%bountiesplus_exp_value%",
                        "&7XP Increase: &e%bountiesplus_xplevel_increase_percent%%",
                        "",
                        "&7Original Duration: &9%bountiesplus_original_duration%",
                        "&7Current Duration: &9%bountiesplus_time_value%",
                        "&7Duration Increase: &e%bountiesplus_bountyduration_increase_percent%%",
                        "",
                        "&7Original Pool: &6$%bountiesplus_original_pool%",
                        "&7Current Pool: &6$%bountiesplus_total_pool%",
                        "&7Pool Increase: &e%bountiesplus_pool_increase_percent%%"
                );
            }
            List<String> processedLore = Placeholders.apply(lore, context);
            meta.setLore(processedLore);

            boolean enchantmentGlow = config.getBoolean("bounty-info.enchantment-glow", false);
            if (enchantmentGlow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (VersionUtils.isPost19()) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            item.setItemMeta(meta);
        }

        int totalItems = 1;
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        if (failureReason == null && meta != null) {
            inventory.setItem(slot, item);
            protectedSlots.add(slot);
            successfulItems++;
        } else {
            failures.add("bounty-info Reason: " + (failureReason != null ? failureReason : "Failed to create item"));
        }

        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - PreviewGUI] All bounty info items created");
        } else {
            String failureMessage = "[DEBUG - PreviewGUI] " + successfulItems + "/" + totalItems + " bounty info items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("PreviewGUI_bounty_info_" + System.currentTimeMillis(), failureMessage);
        }
    }
    /**
     * Adds custom items to the GUI from PreviewGUI.yml
     * // note: Populates custom decorative or interactive items with placeholders
     */
    private void addCustomItems() {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getPreviewGUIConfig();
        ConfigurationSection customItemsSection = config.getConfigurationSection("custom-items");
        if (customItemsSection == null) {
            debugManager.logDebug("[DEBUG] No custom-items section found in PreviewGUI.yml");
            return;
        }

        int totalItems = customItemsSection.getKeys(false).size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(targetUUID)
                .bountyCount(bounty != null ? bounty.getSponsors().size() : 0)
                .moneyValue(bounty != null ? bounty.getCurrentMoney() : 0.0)
                .expValue(bounty != null ? bounty.getCurrentXP() : 0)
                .timeValue(bounty != null ? bounty.getFormattedCurrentDuration() : "Permanent")
                .itemCount(bounty != null ? bounty.getCurrentItems().size() : 0)
                .itemValue(bounty != null ? bounty.getCurrentItemValue() : 0.0);

        for (String key : customItemsSection.getKeys(false)) {
            String path = "custom-items." + key;
            String materialName = config.getString(path + ".material", "STONE");
            int slot = config.getInt(path + ".slot", -1);
            String failureReason = null;

            if (slot < 0 || slot >= 54) {
                debugManager.logWarning("Invalid slot " + slot + " for custom item " + key + " in PreviewGUI.yml");
                failureReason = "Invalid slot " + slot;
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("slot", String.valueOf(slot));
                placeholders.put("item", key);
                MessageUtils.sendFormattedMessage(player, "invalid-custom-slot");
                failures.add(key + " Reason: " + failureReason);
                continue;
            }

            if (protectedSlots.contains(slot)) {
                debugManager.logWarning("Slot " + slot + " for custom item " + key + " is already occupied");
                failureReason = "Slot " + slot + " occupied";
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("slot", String.valueOf(slot));
                placeholders.put("item", key);
                MessageUtils.sendFormattedMessage(player, "invalid-custom-slot");
                failures.add(key + " Reason: " + failureReason);
                continue;
            }

            Material material = VersionUtils.getMaterialSafely(materialName, "STONE");
            if (material == null) {
                debugManager.logWarning("[DEBUG - PreviewGUI] Invalid material '" + materialName + "' for custom item " + key + ", using STONE");
                failureReason = "Invalid material '" + materialName + "'";
                material = Material.STONE;
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - PreviewGUI] Failed to get ItemMeta for custom item " + key);
                failureReason = "Failed to get ItemMeta";
                failures.add(key + " Reason: " + failureReason);
                continue;
            }

            String name = config.getString(path + ".name", "&6Custom Item");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));

            List<String> lore = config.getStringList(path + ".lore");
            if (!lore.isEmpty()) {
                List<String> processedLore = Placeholders.apply(lore, context).stream()
                        .map(line -> line.replace("%item_value%", String.format("%.2f", plugin.getItemValueCalculator().calculateItemValue(item))))
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }

            boolean enchantmentGlow = config.getBoolean(path + ".enchantment-glow", false);
            if (enchantmentGlow && VersionUtils.supportsGlowingEffect()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);

            if (failureReason == null) {
                inventory.setItem(slot, item);
                protectedSlots.add(slot);
                successfulItems++;
            }

            debugManager.logDebug("Added custom item " + key + " to slot " + slot);
        }

        // Log consolidated debug message
        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - PreviewGUI] All custom items created");
        } else {
            String failureMessage = "[DEBUG - PreviewGUI] " + successfulItems + "/" + totalItems + " custom items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("PreviewGUI_custom_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds bounty items to the GUI
     * // note: Populates available slots with items contributed by sponsors to the bounty
     */
    private void addBountyItems() {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getPreviewGUIConfig();
        List<Integer> contentSlots = config.getIntegerList("bounty-items.content-slots");
        if (contentSlots.isEmpty()) {
            contentSlots = new ArrayList<>();
            for (int row = 1; row <= 4; row++) {
                for (int col = 1; col <= 7; col++) {
                    int slot = (row * 9) + col;
                    contentSlots.add(slot);
                }
            }
            debugManager.logDebug("[DEBUG - PreviewGUI] Using default content slots for bounty items: " + contentSlots);
        } else {
            contentSlots = contentSlots.stream()
                    .filter(slot -> slot >= 0 && slot < 54 && !protectedSlots.contains(slot))
                    .collect(Collectors.toList());
            if (contentSlots.isEmpty()) {
                debugManager.logWarning("[DEBUG - PreviewGUI] No valid content slots defined in PreviewGUI.yml for bounty items, using default");
                for (int row = 1; row <= 4; row++) {
                    for (int col = 1; col <= 7; col++) {
                        int slot = (row * 9) + col;
                        if (!protectedSlots.contains(slot)) {
                            contentSlots.add(slot);
                        }
                    }
                }
            }
        }

        int itemsPerPage = contentSlots.size();
        int startIndex = currentPage * itemsPerPage;
        Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
        List<Bounty.BountyItem> items = (bounty != null && !bounty.getCurrentItems().isEmpty()) ?
                bounty.getSortedItems() : new ArrayList<>();

        int totalItems = Math.min(items.size() - startIndex, itemsPerPage);
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        debugManager.logDebug("Adding bounty items: Total items=" + items.size() +
                ", Page=" + (currentPage + 1) + ", StartIndex=" + startIndex +
                ", ItemsPerPage=" + itemsPerPage + ", AvailableSlots=" + contentSlots.size());

        for (int slot : contentSlots) {
            inventory.setItem(slot, null);
        }

        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        PlaceholderContext baseContext = PlaceholderContext.create()
                .player(player)
                .target(targetUUID);

        for (int i = startIndex; i < endIndex; i++) {
            Bounty.BountyItem bountyItem = items.get(i);
            ItemStack item = bountyItem.getItem().clone();
            ItemMeta meta = item.getItemMeta();
            String failureReason = null;

            if (meta == null) {
                debugManager.logWarning("[DEBUG - PreviewGUI] Failed to get ItemMeta for bounty item at index " + i +
                        ", Material=" + item.getType().name());
                failureReason = "Failed to get ItemMeta";
                failures.add("bounty-item-" + i + " Reason: " + failureReason);
                continue;
            }

            String contributorName = bountyItem.isAnonymous() ? "&k|||||||" :
                    Bukkit.getOfflinePlayer(bountyItem.getContributor()).getName();
            if (contributorName == null) {
                contributorName = "Unknown";
                debugManager.logWarning("[DEBUG - PreviewGUI] Contributor name is null for UUID: " +
                        bountyItem.getContributor());
                failureReason = "Unknown contributor";
            }

            double itemValue = plugin.getItemValueCalculator().calculateItemValue(item);
            List<String> lore = config.getStringList("bounty-items.lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7Contributor: %contributor%",
                        "&7Value: &a$%item_value%",
                        "&7Click to view details"
                );
            }

            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                String processedLine = Placeholders.apply(line, baseContext)
                        .replace("%contributor%", contributorName)
                        .replace("%item_value%", String.format("%.2f", itemValue));
                processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(processedLore);

            boolean enchantmentGlow = config.getBoolean("bounty-items.enchantment-glow", false);
            if (enchantmentGlow && VersionUtils.supportsGlowingEffect()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);

            int slotIndex = i - startIndex;
            if (slotIndex < contentSlots.size()) {
                int slot = contentSlots.get(slotIndex);
                if (failureReason == null) {
                    inventory.setItem(slot, item);
                    protectedSlots.add(slot);
                    successfulItems++;
                    debugManager.logDebug("[DEBUG - PreviewGUI] Placed bounty item (Material=" + item.getType().name() +
                            ", Contributor=" + contributorName + ") in slot " + slot);
                } else {
                    failures.add("bounty-item-" + i + " Reason: " + failureReason);
                }
            } else {
                debugManager.logWarning("Slot index " + slotIndex +
                        " exceeds available content slots for item at index " + i);
                failures.add("bounty-item-" + i + " Reason: Invalid slot index " + slotIndex);
            }
        }

        // Add pagination buttons if necessary
        if (items.size() > itemsPerPage) {
            addPaginationButtons(itemsPerPage, items.size());
        } else {
            int prevSlot = config.getInt("pagination.previous.slot", 45);
            int nextSlot = config.getInt("pagination.next.slot", 53);
            inventory.setItem(prevSlot, null);
            inventory.setItem(nextSlot, null);
            protectedSlots.remove(prevSlot);
            protectedSlots.remove(nextSlot);
        }

        // Log consolidated debug message
        if (totalItems == 0) {
            debugManager.logDebug("[DEBUG - PreviewGUI] No bounty items to create");
        } else if (successfulItems == totalItems) {
        } else {
            String failureMessage = "[DEBUG - PreviewGUI] " + successfulItems + "/" + totalItems + " bounty items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("PreviewGUI_bounty_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds pagination buttons to the GUI
     * // note: Places Previous and Next buttons for navigating bounty item pages
     */
    private void addPaginationButtons(int itemsPerPage, int totalItems) {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getPreviewGUIConfig();
        int totalButtons = (currentPage > 0 ? 1 : 0) + (currentPage < (int) Math.ceil((double) totalItems / itemsPerPage) - 1 ? 1 : 0);
        int successfulButtons = 0;
        List<String> failures = new ArrayList<>();

        if (currentPage > 0) {
            ItemStack prevButton = VersionUtils.getXMaterialItemStack("ARROW");
            String failureReason = null;
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                String name = config.getString("pagination.previous.name", "&ePrevious Page");
                prevMeta.setDisplayName(Placeholders.apply(name, null));
                List<String> lore = config.getStringList("pagination.previous.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Page %current_page% of %total_pages%",
                            "&7Click to go to previous page"
                    );
                }
                List<String> processedLore = new ArrayList<>();
                int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
                for (String line : lore) {
                    line = line.replace("%current_page%", String.valueOf(currentPage))
                            .replace("%total_pages%", String.valueOf(totalPages));
                    processedLore.add(Placeholders.apply(line, null));
                }
                prevMeta.setLore(processedLore);
                boolean glow = config.getBoolean("pagination.previous.enchantment-glow", false);
                if (glow) {
                    prevMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                    prevMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                prevButton.setItemMeta(prevMeta);
            } else {
                debugManager.logWarning("[DEBUG - PreviewGUI] Failed to get ItemMeta for previous pagination button");
                failureReason = "Failed to get ItemMeta";
            }

            int slot = config.getInt("pagination.previous.slot", 45);
            if (failureReason == null) {
                inventory.setItem(slot, prevButton);
                protectedSlots.add(slot);
                successfulButtons++;
            } else {
                failures.add("previous-button Reason: " + failureReason);
            }
        }

        if (currentPage < (int) Math.ceil((double) totalItems / itemsPerPage) - 1) {
            ItemStack nextButton = VersionUtils.getXMaterialItemStack("ARROW");
            String failureReason = null;
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                String name = config.getString("pagination.next.name", "&eNext Page");
                nextMeta.setDisplayName(Placeholders.apply(name, null));
                List<String> lore = config.getStringList("pagination.next.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Page %current_page% of %total_pages%",
                            "&7Click to go to next page"
                    );
                }
                List<String> processedLore = new ArrayList<>();
                int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
                for (String line : lore) {
                    line = line.replace("%current_page%", String.valueOf(currentPage + 2))
                            .replace("%total_pages%", String.valueOf(totalPages));
                    processedLore.add(Placeholders.apply(line, null));
                }
                nextMeta.setLore(processedLore);
                boolean glow = config.getBoolean("pagination.next.enchantment-glow", false);
                if (glow) {
                    nextMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                    nextMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                nextButton.setItemMeta(nextMeta);
            } else {
                debugManager.logWarning("[DEBUG - PreviewGUI] Failed to get ItemMeta for next pagination button");
                failureReason = "Failed to get ItemMeta";
            }

            int slot = config.getInt("pagination.next.slot", 53);
            if (failureReason == null) {
                inventory.setItem(slot, nextButton);
                protectedSlots.add(slot);
                successfulButtons++;
            } else {
                failures.add("next-button Reason: " + failureReason);
            }
        }

        if (totalButtons == 0) {
            debugManager.logDebug("[DEBUG - PreviewGUI] No pagination buttons to create");
        } else if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG - PreviewGUI] All pagination buttons created");
        } else {
            String failureMessage = "[DEBUG - PreviewGUI] " + successfulButtons + "/" + totalButtons + " pagination buttons created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("PreviewGUI_pagination_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Handles inventory click events for the GUI // note: Processes clicks on pagination buttons and custom items
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();
        if (protectedSlots.contains(slot)) {
            event.setCancelled(true);
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            FileConfiguration config = plugin.getPreviewGUIConfig();
            if (slot == config.getInt("pagination.previous.slot", 45) && clickedItem.getType() == Material.ARROW) {
                if (currentPage > 0) {
                    currentPage--;
                    refreshGUI();
                }
            } else if (slot == config.getInt("pagination.next.slot", 53) && clickedItem.getType() == Material.ARROW) {
                int totalItems = plugin.getBountyManager().getBounty(targetUUID).getCurrentItems().size();
                int itemsPerPage = 28;
                int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshGUI();
                }
            } else {
                ConfigurationSection customItemsSection = config.getConfigurationSection("custom-items");
                if (customItemsSection != null) {
                    for (String key : customItemsSection.getKeys(false)) {
                        int customSlot = config.getInt("custom-items." + key + ".slot", -1);
                        if (slot == customSlot) {
                            handleCustomItemClick(clickingPlayer, key);
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles clicks on custom items // note: Processes actions for custom items like closing the GUI
     */
    private void handleCustomItemClick(Player player, String itemKey) {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        String path = "custom-items." + itemKey;
        String action = config.getString(path + ".action", "none");
        switch (action.toLowerCase()) {
            case "close":
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;
        boolean hasProtectedSlots = false;
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize() && protectedSlots.contains(slot)) {
                hasProtectedSlots = true;
                break;
            }
        }
        if (hasProtectedSlots) {
            event.setCancelled(true);
            FileConfiguration config = plugin.getPreviewGUIConfig();
            String errorMessage = config.getString("messages.cannot-place-items", "&cYou cannot place items in this GUI!");
            PlaceholderContext context = PlaceholderContext.create().player(player);
            player.sendMessage(Placeholders.apply(errorMessage, context));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!event.getPlayer().equals(player)) return;
        cleanup();
    }

    private void cleanup() {
        HandlerList.unregisterAll(this);
    }
}