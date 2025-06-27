package tony26.bountiesPlus.GUIs;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.BountyCreationSession;
import tony26.bountiesPlus.utils.EventManager;
import tony26.bountiesPlus.utils.*;
import net.md_5.bungee.api.chat.TextComponent;
import me.clip.placeholderapi.PlaceholderAPI;
import java.util.stream.Collectors;
import tony26.bountiesPlus.wrappers.VersionWrapper;
import tony26.bountiesPlus.wrappers.VersionWrapperFactory;
import java.util.*;

public class AddItemsGUI implements Listener, InventoryHolder {
    private static final Map<UUID, AddItemsGUI> activeInstances = new HashMap<>();
    private final Set<Integer> protectedSlots = new HashSet<>();
    private final Set<Material> blacklistedItems;
    private static final Map<UUID, List<ItemStack>> originalItemsState = new HashMap<>();
    private final Player player;
    private final Inventory inventory;
    private final boolean stackItems;
    private final boolean sortByValue;
    private final BountiesPlus plugin;
    private boolean isTransitioningFromCreateGUI = false;
    private long openTime; // Timestamp when the GUI was opened
    private static final Map<UUID, Long> lastClickTimes = new HashMap<>();


    /**
     * Gets the inventory associated with this GUI
     * // note: Returns the AddItemsGUI inventory for InventoryHolder interface
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    /**
     * Constructs the AddItemsGUI for a player
     * // note: Initializes GUI, loads blacklist, available slots, stacking/sorting settings, and registers listeners
     */
    public AddItemsGUI(Player player, EventManager eventManager) {
        this.plugin = BountiesPlus.getInstance();
        this.player = player;
        this.isTransitioningFromCreateGUI = true; // Set flag to indicate transition from CreateGUI
        FileConfiguration config = plugin.getAddItemsGUIConfig();
        File configFile = new File(plugin.getDataFolder(), "GUIs/AddItemsGUI.yml");

        // Verify configuration integrity
        if (!configFile.exists() || config.getConfigurationSection("buttons") == null) {
            plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] AddItemsGUI.yml is missing or invalid, reloading default");
            try {
                if (configFile.exists()) configFile.delete();
                plugin.saveResource("GUIs/AddItemsGUI.yml", false);
                config = YamlConfiguration.loadConfiguration(configFile);
                plugin.getDebugManager().logDebug("[DEBUG - AddItemsGUI] Reloaded default AddItemsGUI.yml");
            } catch (IllegalArgumentException e) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Failed to reload default AddItemsGUI.yml: " + e.getMessage());
            }
        }

        // Determine GUI title based on session state
        BountyCreationSession session = BountyCreationSession.getSession(player);
        String titleKey = (session != null && session.hasItemRewards()) ? "edit-title" : "gui-title";
        String title = config.getString(titleKey, "          &4&l⚔ &4&l&nAdd Items&4&l &4&l⚔");
        if (!config.contains(titleKey)) {
            plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] No " + titleKey + " defined in AddItemsGUI.yml, using default: " + title);
        }
        this.inventory = Bukkit.createInventory(this, 54, ChatColor.translateAlternateColorCodes('&', title));

        // Load blacklisted items from config.yml
        FileConfiguration mainConfig = plugin.getConfig();
        this.blacklistedItems = new HashSet<>();
        List<String> blacklist = mainConfig.getStringList("bounties.blacklisted-items");
        for (String materialName : blacklist) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                blacklistedItems.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Invalid material in bounties.blacklisted-items: " + materialName);
            }
        }

        // Load available slots from AddItemsGUI.yml
        List<Integer> availableSlots = config.getIntegerList("content-area.available-slots");
        if (availableSlots.isEmpty()) {
            availableSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
            plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] No content-area.available-slots defined in AddItemsGUI.yml, using defaults: " + availableSlots);
        }

        // Load stacking and sorting settings
        this.stackItems = config.getBoolean("content-area.stack-items", true);
        this.sortByValue = config.getBoolean("content-area.stack-items.sort-by-value", true);
        if (!config.contains("content-area.stack-items")) {
            plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] No content-area.stack-items defined in AddItemsGUI.yml, defaulting to true");
        }
        if (!config.contains("content-area.stack-items.sort-by-value")) {
            plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] No content-area.stack-items.sort-by-value defined in AddItemsGUI.yml, defaulting to true");
        }

        // Initialize protected slots (all slots except available-slots and including Custom-Items/buttons slots)
        this.protectedSlots.clear();
        Set<Integer> customItemSlots = new HashSet<>();
        Set<Integer> buttonSlots = new HashSet<>();
        boolean isEditMode = session != null && session.hasItemRewards();

        // Load custom item slots
        if (config.contains("Custom-Items")) {
            for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
                String basePath = "Custom-Items." + key;
                String slotsPath = isEditMode && config.contains(basePath + ".edit-mode.slots") ? basePath + ".edit-mode.slots" : basePath + ".slots";
                if (config.contains(slotsPath)) {
                    customItemSlots.addAll(config.getIntegerList(slotsPath));
                }
            }
        }

        // Load button slots
        for (String button : config.getConfigurationSection("buttons").getKeys(false)) {
            String basePath = "buttons." + button;
            String slotPath = isEditMode && config.contains(basePath + ".edit-mode.slot") ? basePath + ".edit-mode.slot" : basePath + ".slot";
            int slot = config.getInt(slotPath, -1);
            if (slot >= 0 && slot < 54) {
                buttonSlots.add(slot);
            }
        }

        // Set protected slots
        for (int i = 0; i < 54; i++) {
            if (!availableSlots.contains(i)) {
                protectedSlots.add(i);
            }
        }
        for (int slot : customItemSlots) {
            if (availableSlots.contains(slot)) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Custom item slot " + slot + " overlaps with available-slots in AddItemsGUI.yml" + (isEditMode ? " (edit-mode)" : ""));
            }
            protectedSlots.add(slot);
        }
        for (int slot : buttonSlots) {
            if (availableSlots.contains(slot)) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Button slot " + slot + " overlaps with available-slots in AddItemsGUI.yml" + (isEditMode ? " (edit-mode)" : ""));
            }
            protectedSlots.add(slot);
        }

        // Log protected slots for debugging
        plugin.getDebugManager().logDebug("[DEBUG - AddItemsGUI] Protected slots initialized for " + player.getName() + ": " + protectedSlots);

        // Validate slots
        for (int slot : availableSlots) {
            if (slot < 0 || slot >= 54) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Invalid content-area.available-slot " + slot + " in AddItemsGUI.yml (must be 0-53)");
            }
        }
        for (int slot : customItemSlots) {
            if (slot < 0 || slot >= 54) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Invalid Custom-Items slot " + slot + " in AddItemsGUI.yml" + (isEditMode ? " (edit-mode)" : "") + " (must be 0-53)");
            }
        }
        for (int slot : buttonSlots) {
            if (slot < 0 || slot >= 54) {
                plugin.getDebugManager().logWarning("[DEBUG - AddItemsGUI] Invalid button slot " + slot + " in AddItemsGUI.yml" + (isEditMode ? " (edit-mode)" : "") + " (must be 0-53)");
            }
        }

        cleanup(player);
        activeInstances.put(player.getUniqueId(), this);
        eventManager.register(this);
        initializeGUI();
    }

    /**
     * Places custom items in the AddItemsGUI
     * // note: Populates the inventory with decorative items defined in AddItemsGUI.yml, using edit-mode settings when applicable
     */
    private void placeCustomItems() {
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        boolean isEditMode = session != null && session.hasItemRewards();

        if (!config.contains("Custom-Items")) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] No Custom-Items section found in AddItemsGUI.yml");
            return;
        }

        int totalItems = 0;
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
            String basePath = "Custom-Items." + key;
            String configPath = isEditMode && config.contains(basePath + ".edit-mode") ? basePath + ".edit-mode" : basePath;
            String materialName = config.getString(configPath + ".material", "STONE");
            ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
            String failureReason = null;

            if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Invalid material '" + materialName + "' for custom item " + key + (isEditMode ? " (edit-mode)" : "") + ", using STONE");
                failureReason = "Invalid material '" + materialName + "'";
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Failed to get ItemMeta for custom item " + key + (isEditMode ? " (edit-mode)" : ""));
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

            List<Integer> slots = config.getIntegerList(configPath + ".slots");
            totalItems += slots.size();
            for (int slot : slots) {
                if (slot >= 0 && slot < inventory.getSize()) {
                    if (failureReason == null) {
                        inventory.setItem(slot, item.clone());
                        successfulItems++;
                    } else {
                        failures.add(key + " slot " + slot + " Reason: " + failureReason);
                    }
                } else {
                    debugManager.logWarning("[DEBUG - AddItemsGUI] Invalid slot " + slot + " for custom item " + key + (isEditMode ? " (edit-mode)" : "") + " in AddItemsGUI.yml");
                    failures.add(key + " slot " + slot + " Reason: Invalid slot");
                }
            }
        }

        // Log consolidated debug message
        if (successfulItems == totalItems && totalItems > 0) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] All " + totalItems + " custom items created" + (isEditMode ? " (edit-mode)" : ""));
        } else if (totalItems > 0) {
            String failureMessage = "[DEBUG - AddItemsGUI] " + successfulItems + "/" + totalItems + " custom items created" + (isEditMode ? " (edit-mode)" : "");
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("AddItemsGUI_custom_items_" + System.currentTimeMillis(), failureMessage);
        } else {
            debugManager.logDebug("[DEBUG - AddItemsGUI] No custom item slots defined" + (isEditMode ? " (edit-mode)" : ""));
        }
    }

    /**
     * Validates the total bounty value against max-bounty-amount // note: Ensures money and item values do not exceed configured maximum when confirming items
     */
    private boolean validateTotalBountyValue(Player player, Inventory inventory, BountyCreationSession session) {
        FileConfiguration config = BountiesPlus.getInstance().getConfig();
        FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
        double maxBountyAmount = config.getDouble("max-bounty-amount", 1000000.0);
        double totalMoney = session.getMoney();
        double totalItemValue = 0.0;

        ItemValueCalculator calculator = BountiesPlus.getInstance().getItemValueCalculator();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    totalItemValue += calculator.calculateItemValue(item);
                }
            }
        }

        double totalValue = totalMoney + totalItemValue;
        if (totalValue > maxBountyAmount) {
            String errorMessage = messagesConfig.getString("bounty-invalid-amount", "&cTotal bounty value cannot exceed $%bountiesplus_max_amount%!");
            PlaceholderContext context = PlaceholderContext.create().player(player).withAmount(maxBountyAmount);
            player.sendMessage(Placeholders.apply(errorMessage.replace("%prefix%", messagesConfig.getString("prefix", "")), context));
            return false;
        }
        return true;
    }

    /**
     * Checks if an item is blacklisted
     * // note: Returns true if the item's material or NBT tags match the blacklist
     */
    private boolean isBlacklisted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (blacklistedItems.contains(item.getType())) {
            return true;
        }
        FileConfiguration config = BountiesPlus.getInstance().getConfig();
        List<Map<?, ?>> nbtBlacklist = config.getMapList("bounties.blacklisted-nbt-items");
        for (Map<?, ?> entry : nbtBlacklist) {
            String nbtKey = (String) entry.get("nbt_key");
            String nbtValue = (String) entry.get("nbt_value");
            String itemNbtValue = VersionUtils.getNBTString(item, nbtKey);
            if (nbtValue.equals(itemNbtValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an item to the player's inventory or drops it if full // note: Adds item to inventory or drops at player's location
     */
    private void returnItemToPlayer(Player player, ItemStack item) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        if (item == null || item.getType() == Material.AIR) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] Attempted to return null or AIR item to " + player.getName());
            return;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        player.updateInventory();
        if (!overflow.isEmpty()) {
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                debugManager.logDebug("[DEBUG - AddItemsGUI] Dropped blacklisted item " + overflowItem.getType().name() + " x" + overflowItem.getAmount() + " for " + player.getName() + " due to full inventory");
            }
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String inventoryFull = messagesConfig.getString("inventory-full", "&eYour inventory is full. Some items were dropped on the ground.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', inventoryFull));
        } else {
            debugManager.logDebug("[DEBUG - AddItemsGUI] Returned blacklisted item " + item.getType().name() + " x" + item.getAmount() + " to " + player.getName() + "'s inventory");
        }
    }

    public static void cleanup(Player player) {
        UUID playerUUID = player.getUniqueId();
        AddItemsGUI existingInstance = activeInstances.get(playerUUID);
        if (existingInstance != null) {
            // Unregister the old listener
            InventoryClickEvent.getHandlerList().unregister(existingInstance);
            InventoryDragEvent.getHandlerList().unregister(existingInstance);
            InventoryCloseEvent.getHandlerList().unregister(existingInstance);
            activeInstances.remove(playerUUID);
        }
        // Clean up tracking data
        originalItemsState.remove(playerUUID);
    }

    /**
     * Initializes the AddItemsGUI
     * // note: Sets up buttons, custom items, clears available slots, and loads existing items
     */
    private void initializeGUI() {
        FileConfiguration config = plugin.getAddItemsGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        boolean isEditMode = session != null && session.hasItemRewards();

        // Clear available slots
        List<Integer> availableSlots = config.getIntegerList("content-area.available-slots");
        for (int slot : availableSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, null);
            }
        }

        // Place custom items
        placeCustomItems();

        // Place buttons
        addBottomRowButtons(inventory);

        // Load existing items from session
        loadExistingItems(player, inventory);

        // Store original items state
        storeOriginalItemsState(player, inventory);

        // Schedule inventory open with a delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openTime = System.currentTimeMillis(); // Record open time
            player.openInventory(inventory);
            // Set flag to false after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                isTransitioningFromCreateGUI = false;
            }, 5L); // 5 ticks delay
            debugManager.logDebug("[DEBUG - AddItemsGUI] Opened AddItemsGUI for player: " + player.getName() + (isEditMode ? " (edit-mode)" : "") + ", title: '" + inventory.getViewers().get(0).getOpenInventory().getTitle() + "', protected slots: " + protectedSlots);
        }, 2L);
    }
    /**
     * Loads existing items from the session into the GUI
     * // note: Places items from BountyCreationSession into available slots, respecting stacking and sorting settings
     */
    private void loadExistingItems(Player player, Inventory inventory) {
        BountyCreationSession session = BountyCreationSession.getSession(player);
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        List<Integer> availableSlots = config.getIntegerList("content-area.available-slots");

        if (session == null || !session.hasItemRewards()) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] No session or item rewards for " + player.getName() + ", skipping loadExistingItems");
            return;
        }

        List<ItemStack> existingItems = session.getItemRewards();
        List<ItemStack> processedItems;

        // Apply stacking if enabled
        if (stackItems) {
            processedItems = new ArrayList<>();
            Map<String, ItemStack> itemMap = new HashMap<>();
            for (ItemStack item : existingItems) {
                if (item == null || item.getType() == Material.AIR) continue;
                String key = item.getType().name() + ":" + (item.hasItemMeta() ? item.getItemMeta().toString() : "");
                if (item.getMaxStackSize() > 1) {
                    ItemStack existing = itemMap.get(key);
                    if (existing != null && existing.isSimilar(item)) {
                        int newAmount = existing.getAmount() + item.getAmount();
                        existing.setAmount(Math.min(newAmount, existing.getMaxStackSize()));
                    } else {
                        ItemStack clonedItem = item.clone();
                        clonedItem.setAmount(Math.min(clonedItem.getAmount(), clonedItem.getMaxStackSize()));
                        itemMap.put(key, clonedItem);
                    }
                } else {
                    itemMap.put(UUID.randomUUID().toString(), item.clone());
                }
            }
            processedItems.addAll(itemMap.values());
        } else {
            processedItems = existingItems.stream()
                    .filter(item -> item != null && item.getType() != Material.AIR)
                    .map(ItemStack::clone)
                    .collect(Collectors.toList());
        }

        // Apply sorting
        ItemValueCalculator calculator = BountiesPlus.getInstance().getItemValueCalculator();
        if (sortByValue) {
            processedItems.sort((a, b) -> {
                double valueA = calculator.calculateItemValue(a);
                double valueB = calculator.calculateItemValue(b);
                return Double.compare(valueB, valueA); // Highest to lowest
            });
        } else {
            processedItems.sort(Comparator.comparing(item -> item.getType().name()));
        }

        // Place items in available slots
        int slotIndex = 0;
        for (ItemStack item : processedItems) {
            while (slotIndex < availableSlots.size()) {
                int slot = availableSlots.get(slotIndex);
                if (!protectedSlots.contains(slot)) {
                    inventory.setItem(slot, item.clone());
                    slotIndex++;
                    break;
                }
                slotIndex++;
            }
            if (slotIndex >= availableSlots.size()) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Not enough available slots to load all items for " + player.getName());
                break;
            }
        }

        debugManager.logDebug("[DEBUG - AddItemsGUI] Loaded " + processedItems.size() + " items for " + player.getName() + " with stackItems=" + stackItems + ", sortByValue=" + sortByValue);
    }

    private void storeOriginalItemsState(Player player, Inventory inventory) {
        List<ItemStack> originalItems = new ArrayList<>();

        for (int i = 0; i < 54; i++) {
            if (!protectedSlots.contains(i)) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    originalItems.add(item.clone());
                } else {
                    originalItems.add(null);
                }
            }
        }

        originalItemsState.put(player.getUniqueId(), originalItems);
    }

    public void openInventory(Player player) {
        if (!player.equals(this.player)) {
            return; // Safety check - only the intended player can open this GUI
        }
        player.openInventory(inventory);
    }

    private boolean hasItemsChanged(Player player, Inventory gui) {
        List<ItemStack> originalItems = originalItemsState.get(player.getUniqueId());
        if (originalItems == null) {
            return true; // If no original state, assume changes were made
        }

        List<ItemStack> currentItems = new ArrayList<>();
        for (int i = 0; i < 54; i++) {
            if (!protectedSlots.contains(i)) {
                ItemStack item = gui.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    currentItems.add(item.clone());
                } else {
                    currentItems.add(null);
                }
            }
        }

        // Compare the two lists
        if (originalItems.size() != currentItems.size()) {
            return true;
        }

        for (int i = 0; i < originalItems.size(); i++) {
            ItemStack original = originalItems.get(i);
            ItemStack current = currentItems.get(i);

            if (original == null && current == null) {
                continue; // Both null, no change
            }

            if (original == null || current == null) {
                return true; // One is null, other isn't
            }

            if (!original.isSimilar(current) || original.getAmount() != current.getAmount()) {
                return true; // Items are different
            }
        }
        return false; // No changes detected
    }
    /**
     * Adds bottom row buttons to the GUI
     * // note: Places Cancel, Info, and Confirm buttons
     */
    private void addBottomRowButtons(Inventory gui) {
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        int totalButtons = 3;
        int successfulButtons = 0;
        List<String> failures = new ArrayList<>();

        // Cancel button
        if (config.getBoolean("buttons.cancel.enabled", true)) {
            int slot = config.getInt("buttons.cancel.slot", 47);
            String materialName = config.getString("buttons.cancel.material", "REDSTONE");
            ItemStack cancelButton = VersionUtils.getXMaterialItemStack(materialName);
            String failureReason = null;
            if (cancelButton.getType() == Material.STONE && !materialName.equalsIgnoreCase("REDSTONE")) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Invalid material '" + materialName + "' for cancel button, using REDSTONE");
                failureReason = "Invalid material '" + materialName + "'";
                cancelButton = VersionUtils.getXMaterialItemStack("REDSTONE");
            }
            ItemMeta cancelMeta = cancelButton.getItemMeta();
            if (cancelMeta != null) {
                cancelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("buttons.cancel.name", "&c&lCancel")));
                List<String> lore = config.getStringList("buttons.cancel.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Click to cancel and return",
                            "&7to the Create Bounty GUI",
                            "",
                            "&eNo items will be returned!",
                            "&eUse Confirm to save changes."
                    );
                }
                cancelMeta.setLore(lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList()));
                if (VersionUtils.isPost19()) {
                    cancelMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
                cancelButton.setItemMeta(cancelMeta);
            } else {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Failed to get ItemMeta for cancel button");
                failureReason = "Failed to get ItemMeta";
            }
            if (failureReason == null) {
                gui.setItem(slot, cancelButton);
                protectedSlots.add(slot);
                successfulButtons++;
            } else {
                failures.add("cancel-button Reason: " + failureReason);
            }
        }

        // How to use (Info) button
        if (config.getBoolean("buttons.info.enabled", true)) {
            int slot = config.getInt("buttons.info.slot", 49);
            String materialName = config.getString("buttons.info.material", "PAPER");
            ItemStack howToUseButton = VersionUtils.getXMaterialItemStack(materialName);
            String failureReason = null;
            if (howToUseButton.getType() == Material.STONE && !materialName.equalsIgnoreCase("PAPER")) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Invalid material '" + materialName + "' for info button, using PAPER");
                failureReason = "Invalid material '" + materialName + "'";
                howToUseButton = VersionUtils.getXMaterialItemStack("PAPER");
            }
            ItemMeta howToUseMeta = howToUseButton.getItemMeta();
            if (howToUseMeta != null) {
                howToUseMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("buttons.info.name", "&e&lHow to Use")));
                List<String> lore = config.getStringList("buttons.info.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Drag items from your inventory",
                            "&7into the empty slots above",
                            "",
                            "&7These items will be given as",
                            "&7rewards to whoever claims",
                            "&7the bounty!",
                            "",
                            "&aClick &2Confirm &ato save changes!",
                            "",
                            "&7Current items: &f%bountiesplus_item_count%",
                            "&7Total value: &a$%bountiesplus_item_value%"
                    );
                }
                BountyCreationSession session = BountyCreationSession.getSession(player);
                int itemCount = session != null ? session.getItemRewards().size() : 0;
                double itemValue = session != null ? session.getItemRewards().stream()
                        .mapToDouble(item -> BountiesPlus.getInstance().getItemValueCalculator().calculateItemValue(item)).sum() : 0.0;
                PlaceholderContext context = PlaceholderContext.create().player(player).itemCount(itemCount).itemValue(itemValue);
                howToUseMeta.setLore(Placeholders.apply(lore, context));
                if (VersionUtils.isPost19()) {
                    howToUseMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
                howToUseButton.setItemMeta(howToUseMeta);
            } else {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Failed to get ItemMeta for info button");
                failureReason = "Failed to get ItemMeta";
            }
            if (failureReason == null) {
                gui.setItem(slot, howToUseButton);
                protectedSlots.add(slot);
                successfulButtons++;
            } else {
                failures.add("info-button Reason: " + failureReason);
            }
        }

        // Confirm button
        if (config.getBoolean("buttons.confirm.enabled", true)) {
            int slot = config.getInt("buttons.confirm.slot", 51);
            String materialName = config.getString("buttons.confirm.material", "EMERALD");
            ItemStack confirmButton = VersionUtils.getXMaterialItemStack(materialName);
            String failureReason = null;
            if (confirmButton.getType() == Material.STONE && !materialName.equalsIgnoreCase("EMERALD")) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Invalid material '" + materialName + "' for confirm button, using EMERALD");
                failureReason = "Invalid material '" + materialName + "'";
                confirmButton = VersionUtils.getXMaterialItemStack("EMERALD");
            }
            ItemMeta confirmMeta = confirmButton.getItemMeta();
            if (confirmMeta != null) {
                confirmMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("buttons.confirm.name", "&a&lConfirm")));
                List<String> lore = config.getStringList("buttons.confirm.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Click to confirm these items",
                            "&7and return to Create Bounty GUI",
                            "",
                            "&aItems will be saved!",
                            "",
                            "&7Total items: &f%bountiesplus_gui_item_count%",
                            "&7Total value: &a$%bountiesplus_gui_item_value%"
                    );
                }
                PlaceholderContext context = PlaceholderContext.create().player(player);
                confirmMeta.setLore(Placeholders.apply(lore, context));
                if (config.getBoolean("buttons.confirm.enchantment-glow", true)) {
                    confirmMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                    confirmMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                if (VersionUtils.isPost19()) {
                    confirmMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
                confirmButton.setItemMeta(confirmMeta);
            } else {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Failed to get ItemMeta for confirm button");
                failureReason = "Failed to get ItemMeta";
            }
            if (failureReason == null) {
                gui.setItem(slot, confirmButton);
                protectedSlots.add(slot);
                successfulButtons++;
            } else {
                failures.add("confirm-button Reason: " + failureReason);
            }
        }

        if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] All buttons created");
        } else {
            String failureMessage = "[DEBUG - AddItemsGUI] " + successfulButtons + "/" + totalButtons + " buttons created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("AddItemsGUI_buttons_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Updates the Confirm button's lore with current GUI item count and value
     * // note: Refreshes Confirm button to reflect unconfirmed items, using edit-mode settings when applicable
     */
    private void updateConfirmButton(Inventory gui) {
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        boolean isEditMode = session != null && session.hasItemRewards();
        int slot = config.getInt(isEditMode ? "buttons.confirm.edit-mode.slot" : "buttons.confirm.slot", 51);
        ItemStack confirmButton = gui.getItem(slot);
        if (confirmButton == null || confirmButton.getType() == Material.AIR) {
            return;
        }
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        if (confirmMeta != null) {
            String configPath = isEditMode ? "buttons.confirm.edit-mode" : "buttons.confirm";
            List<String> lore = config.getStringList(configPath + ".lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7Click to confirm these items",
                        "&7and return to Create Bounty GUI",
                        "",
                        "&aItems will be saved!",
                        "",
                        "&7Total items: &f%bountiesplus_gui_item_count%",
                        "&7Total value: &a%bountiesplus_gui_item_value%"
                );
            }
            PlaceholderContext context = PlaceholderContext.create().player(player);
            confirmMeta.setLore(Placeholders.apply(lore, context));
            confirmButton.setItemMeta(confirmMeta);
        }
        gui.setItem(slot, confirmButton);
        player.updateInventory();
    }

    /**
     * Handles a blacklisted item attempt
     * // note: Returns item, sends message, closes GUI, and reopens after delay
     */
    private void handleBlacklistedItem(Player player, ItemStack item) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        debugManager.logDebug("[DEBUG - AddItemsGUI] Blacklisted item detected: " + item.getType().name() + " by " + player.getName());

        returnItemToPlayer(player, item);

        FileConfiguration guiConfig = BountiesPlus.getInstance().getAddItemsGUIConfig();
        FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
        int fadeIn = guiConfig.getInt("title-duration.fade-in", 20);
        int stay = guiConfig.getInt("title-duration.stay", 60);
        int fadeOut = guiConfig.getInt("title-duration.fade-out", 20);
        long reopenDelay = guiConfig.getLong("reopen-delay", 100);

        fadeIn = Math.max(0, fadeIn);
        stay = Math.max(0, stay);
        fadeOut = Math.max(0, fadeOut);
        reopenDelay = Math.max(20, reopenDelay);

        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                item.getItemMeta().getDisplayName() : item.getType().name().toLowerCase().replace('_', ' ');
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .itemName(itemName);

        VersionWrapper wrapper = VersionWrapperFactory.getWrapper();
        if (VersionUtils.isPost111()) {
            String title = Placeholders.apply(messagesConfig.getString("blacklisted-item-title", "&cBlacklisted Item!"), context);
            String subtitle = Placeholders.apply(messagesConfig.getString("blacklisted-item-subtitle", "&e%item_name% &7cannot be added."), context);
            wrapper.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
        } else {
            String message = messagesConfig.getString("blacklisted-item-message", "&c%item_name% &7is blacklisted and cannot be added to the bounty!");
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                message = PlaceholderAPI.setPlaceholders(player, message);
            }
            message = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(message, context));
            BaseComponent[] components = TextComponent.fromLegacyText(message);
            player.spigot().sendMessage(components);
        }

        try {
            player.playSound(player.getLocation(), wrapper.getErrorSound(), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            debugManager.logWarning("[DEBUG - AddItemsGUI] Failed to play error sound for " + player.getName() + ": " + e.getMessage());
        }

        player.closeInventory();

        Bukkit.getScheduler().runTaskLater(BountiesPlus.getInstance(), () -> {
            if (player.isOnline()) {
                new AddItemsGUI(player, BountiesPlus.getInstance().getEventManager()).openInventory(player);
            }
        }, reopenDelay);
    }

// file: java/tony26/bountiesPlus/GUIs/AddItemsGUI.java

    /**
     * Handles inventory click events for the AddItemsGUI
     * // note: Manages item placement, removal, button interactions, and blacklist checks in available slots, applies stacking and sorting, ignores player inventory interactions
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!player.equals(this.player)) return;

        // Verify the inventory is the correct one
        FileConfiguration config = plugin.getAddItemsGUIConfig();
        boolean isEditMode = BountyCreationSession.getSession(player) != null && BountyCreationSession.getSession(player).hasItemRewards();
        String expectedTitle = ChatColor.translateAlternateColorCodes('&', config.getString(
                isEditMode ? "edit-title" : "gui-title", "          &4&l⚔ &4&l&nAdd Items&4&l &4&l⚔"));
        if (!event.getView().getTitle().equals(expectedTitle) || event.getInventory().getHolder() != this) {
            plugin.getDebugManager().bufferDebug("[DEBUG - AddItemsGUI] Ignored click by " + player.getName() + ": title mismatch, expected '" + expectedTitle + "', got '" + event.getView().getTitle() + "' or invalid holder");
            return;
        }

        // Prevent rapid clicks
        long currentTime = System.currentTimeMillis();
        Long lastClickTime = lastClickTimes.get(player.getUniqueId());
        if (lastClickTime != null && currentTime - lastClickTime < 500) {
            plugin.getDebugManager().logDebug("[DEBUG - AddItemsGUI] Ignored rapid click by " + player.getName() + " on slot " + event.getSlot());
            return;
        }
        lastClickTimes.put(player.getUniqueId(), currentTime);

        int rawSlot = event.getRawSlot();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        ClickType clickType = event.getClick();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - AddItemsGUI] Click by " + player.getName() + " on rawSlot " + rawSlot + ", clickType=" + clickType + ", currentItem=" + (currentItem != null ? currentItem.getType().name() : "null") + ", cursorItem=" + (cursorItem != null ? cursorItem.getType().name() : "null"));

        // Completely ignore player inventory interactions (raw slots >= inventory size)
        if (rawSlot >= inventory.getSize()) {
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Ignoring player inventory interaction on rawSlot " + rawSlot + " by " + player.getName());
            return;
        }

        // Cancel all GUI interactions to control manually
        event.setCancelled(true);

        // Block any interaction with protected slots (buttons, custom items)
        if (protectedSlots.contains(rawSlot)) {
            int cancelSlot = config.getInt(isEditMode ? "buttons.cancel.edit-mode.slot" : "buttons.cancel.slot", 47);
            int confirmSlot = config.getInt(isEditMode ? "buttons.confirm.edit-mode.slot" : "buttons.confirm.slot", 51);
            int infoSlot = config.getInt(isEditMode ? "buttons.info.edit-mode.slot" : "buttons.info.slot", 49);

            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Protected slot " + rawSlot + " accessed by " + player.getName() + ", cancelSlot=" + cancelSlot + ", confirmSlot=" + confirmSlot + ", infoSlot=" + infoSlot);

            // Handle button clicks
            if (rawSlot == cancelSlot && clickType == ClickType.LEFT) {
                debugManager.logDebug("[DEBUG - AddItemsGUI] Cancel button clicked by " + player.getName());
                handleCancelButton(player, event.getInventory());
            } else if (rawSlot == infoSlot && clickType == ClickType.LEFT) {
                debugManager.logDebug("[DEBUG - AddItemsGUI] Info button clicked by " + player.getName());
                // Informational only, no action
            } else if (rawSlot == confirmSlot && clickType == ClickType.LEFT) {
                debugManager.logDebug("[DEBUG - AddItemsGUI] Confirm button clicked by " + player.getName());
                handleConfirmButton(player, event.getInventory());
            } else {
                // Protected slot (e.g., custom items or invalid button action), send feedback
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String message = messagesConfig.getString("cannot-place-items", "&cYou cannot place or remove items in this slot!");
                MessageUtils.sendFormattedMessage(player, message);
                debugManager.bufferDebug("[DEBUG - AddItemsGUI] Blocked interaction with protected slot " + rawSlot + " by " + player.getName());
            }
            return;
        }

        // Get or create session
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        List<Integer> availableSlots = config.getIntegerList("content-area.available-slots");

        // Verify slot is in available slots
        if (!availableSlots.contains(rawSlot)) {
            debugManager.logWarning("[DEBUG - AddItemsGUI] Click on non-available slot " + rawSlot + " by " + player.getName() + ", available slots: " + availableSlots);
            return;
        }

        // Handle available slots (defined in content-area.available-slots)
        List<ItemStack> collectedItems = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!protectedSlots.contains(i) && availableSlots.contains(i)) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    collectedItems.add(item.clone());
                }
            }
        }

        if (clickType == ClickType.LEFT && cursorItem != null && cursorItem.getType() != Material.AIR) {
            // Left-click with cursor item: place or swap
            if (isBlacklisted(cursorItem)) {
                handleBlacklistedItem(player, cursorItem.clone());
                return;
            }
            ItemStack itemToPlace = cursorItem.clone();
            if (itemToPlace.getAmount() > itemToPlace.getMaxStackSize()) {
                itemToPlace.setAmount(itemToPlace.getMaxStackSize());
            }
            if (stackItems && itemToPlace.getMaxStackSize() > 1) {
                // Try to stack with existing items
                boolean stacked = false;
                for (ItemStack existing : collectedItems) {
                    if (existing.isSimilar(itemToPlace) && existing.getAmount() < existing.getMaxStackSize()) {
                        int totalAmount = existing.getAmount() + itemToPlace.getAmount();
                        if (totalAmount <= existing.getMaxStackSize()) {
                            existing.setAmount(totalAmount);
                            event.setCursor(null);
                            stacked = true;
                            break;
                        } else {
                            int remaining = totalAmount - existing.getMaxStackSize();
                            existing.setAmount(existing.getMaxStackSize());
                            itemToPlace.setAmount(remaining);
                        }
                    }
                }
                if (!stacked && collectedItems.size() < availableSlots.size()) {
                    collectedItems.add(itemToPlace);
                    event.setCursor(null);
                } else if (!stacked) {
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String message = messagesConfig.getString("no-empty-slots", "&cNo empty slots available in the item GUI!");
                    MessageUtils.sendFormattedMessage(player, message);
                    debugManager.bufferDebug("[DEBUG - AddItemsGUI] No empty slots for left-click placement by " + player.getName());
                    return;
                }
            } else {
                // Place in the clicked slot or swap
                if (currentItem == null || currentItem.getType() == Material.AIR) {
                    if (collectedItems.size() < availableSlots.size()) {
                        collectedItems.add(itemToPlace);
                        event.setCursor(null);
                    } else {
                        FileConfiguration messagesConfig = plugin.getMessagesConfig();
                        String message = messagesConfig.getString("no-empty-slots", "&cNo empty slots available in the item GUI!");
                        MessageUtils.sendFormattedMessage(player, message);
                        debugManager.bufferDebug("[DEBUG - AddItemsGUI] No empty slots for left-click placement by " + player.getName());
                        return;
                    }
                } else {
                    collectedItems.removeIf(item -> item == currentItem); // Remove the current item
                    collectedItems.add(itemToPlace);
                    event.setCursor(currentItem.clone());
                }
            }
        } else if (clickType == ClickType.RIGHT && cursorItem != null && cursorItem.getType() != Material.AIR) {
            // Right-click with cursor item: place one item
            if (isBlacklisted(cursorItem)) {
                handleBlacklistedItem(player, cursorItem.clone());
                return;
            }
            ItemStack itemToPlace = cursorItem.clone();
            itemToPlace.setAmount(1);
            if (stackItems && itemToPlace.getMaxStackSize() > 1) {
                boolean stacked = false;
                for (ItemStack existing : collectedItems) {
                    if (existing.isSimilar(itemToPlace) && existing.getAmount() < existing.getMaxStackSize()) {
                        existing.setAmount(existing.getAmount() + 1);
                        cursorItem.setAmount(cursorItem.getAmount() - 1);
                        event.setCursor(cursorItem.getAmount() <= 0 ? null : cursorItem);
                        stacked = true;
                        break;
                    }
                }
                if (!stacked && collectedItems.size() < availableSlots.size()) {
                    collectedItems.add(itemToPlace);
                    cursorItem.setAmount(cursorItem.getAmount() - 1);
                    event.setCursor(cursorItem.getAmount() <= 0 ? null : cursorItem);
                } else if (!stacked) {
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String message = messagesConfig.getString("no-empty-slots", "&cNo empty slots available in the item GUI!");
                    MessageUtils.sendFormattedMessage(player, message);
                    debugManager.bufferDebug("[DEBUG - AddItemsGUI] No empty slots for right-click placement by " + player.getName());
                    return;
                }
            } else {
                if (currentItem == null || currentItem.getType() == Material.AIR) {
                    if (collectedItems.size() < availableSlots.size()) {
                        collectedItems.add(itemToPlace);
                        cursorItem.setAmount(cursorItem.getAmount() - 1);
                        event.setCursor(cursorItem.getAmount() <= 0 ? null : cursorItem);
                    } else {
                        FileConfiguration messagesConfig = plugin.getMessagesConfig();
                        String message = messagesConfig.getString("no-empty-slots", "&cNo empty slots available in the item GUI!");
                        MessageUtils.sendFormattedMessage(player, message);
                        debugManager.bufferDebug("[DEBUG - AddItemsGUI] No empty slots for right-click placement by " + player.getName());
                        return;
                    }
                } else {
                    // Right-click does not swap; ignore if slot is occupied
                    debugManager.bufferDebug("[DEBUG - AddItemsGUI] Ignored right-click on occupied slot " + rawSlot + " by " + player.getName());
                    return;
                }
            }
        } else if (clickType == ClickType.LEFT && currentItem != null && currentItem.getType() != Material.AIR && (cursorItem == null || cursorItem.getType() == Material.AIR)) {
            // Left-click with empty cursor: remove item from GUI (do not return to player)
            collectedItems.removeIf(item -> item == currentItem);
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Removed item " + currentItem.getType().name() + " x" + currentItem.getAmount() + " from slot " + rawSlot + " by " + player.getName() + " (not returned, pending Confirm)");
        } else {
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Ignored click type " + clickType + " on slot " + rawSlot + " by " + player.getName());
            return;
        }

        // Sort items
        ItemValueCalculator calculator = plugin.getItemValueCalculator();
        if (sortByValue) {
            collectedItems.sort((a, b) -> {
                double valueA = calculator.calculateItemValue(a);
                double valueB = calculator.calculateItemValue(b);
                return Double.compare(valueB, valueA); // Highest to lowest
            });
        } else {
            collectedItems.sort(Comparator.comparing(item -> item.getType().name()));
        }

        // Clear available slots
        for (int slot : availableSlots) {
            inventory.setItem(slot, null);
        }

        // Place items in available slots
        int slotIndex = 0;
        for (ItemStack item : collectedItems) {
            if (slotIndex >= availableSlots.size()) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Not enough available slots to place all items for " + player.getName());
                returnItemToPlayer(player, item);
                continue;
            }
            int slot = availableSlots.get(slotIndex);
            inventory.setItem(slot, item.clone());
            slotIndex++;
        }

        // Schedule Confirm button update and inventory sync
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateConfirmButton(inventory);
            player.updateInventory();
        });
    }
    /**
     * Handles inventory drag events for the AddItemsGUI
     * // note: Manages drag interactions and blacklist checks in available slots, applies stacking and sorting, allows player inventory drags
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(inventory.getViewers().get(0).getOpenInventory().getTitle())) {
            plugin.getDebugManager().bufferDebug("[DEBUG - AddItemsGUI] Ignored drag by " + ((Player) event.getWhoClicked()).getName() + ": title mismatch, expected '" + inventory.getViewers().get(0).getOpenInventory().getTitle() + "', got '" + event.getView().getTitle() + "'");
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Set<Integer> dragSlots = event.getRawSlots();
        ItemStack cursorItem = event.getOldCursor();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - AddItemsGUI] Drag by " + player.getName() + " on slots " + dragSlots + ", cursorItem=" + (cursorItem != null ? cursorItem.getType().name() : "null"));

        // Allow drags entirely in player inventory (all slots >= 54)
        boolean allPlayerInventory = dragSlots.stream().allMatch(slot -> slot >= 54);
        if (allPlayerInventory) {
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Allowing drag in player inventory on slots " + dragSlots + " by " + player.getName());
            return;
        }

        // Cancel drags involving protected slots
        boolean hasProtectedSlots = dragSlots.stream().anyMatch(slot -> slot < 54 && protectedSlots.contains(slot));
        if (hasProtectedSlots) {
            event.setCancelled(true);
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String message = messagesConfig.getString("cannot-place-items", "&cYou cannot place or remove items in this slot!");
            MessageUtils.sendFormattedMessage(player, message);
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Drag cancelled due to protected slots " + dragSlots + " by " + player.getName());
            return;
        }

        // Check for blacklisted items
        if (isBlacklisted(cursorItem)) {
            event.setCancelled(true);
            handleBlacklistedItem(player, cursorItem.clone());
            return;
        }

        // Process drag across available slots
        Inventory topInventory = event.getView().getTopInventory();
        FileConfiguration config = plugin.getAddItemsGUIConfig();
        List<Integer> availableSlots = config.getIntegerList("content-area.available-slots");
        List<Integer> validDragSlots = dragSlots.stream()
                .filter(slot -> slot < 54 && availableSlots.contains(slot))
                .collect(Collectors.toList());
        if (validDragSlots.isEmpty()) {
            event.setCancelled(true);
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Drag cancelled: no valid available slots in " + dragSlots + " by " + player.getName());
            return;
        }

        // Verify all drag slots are in available slots
        if (!dragSlots.stream().allMatch(slot -> slot >= 54 || availableSlots.contains(slot))) {
            event.setCancelled(true);
            debugManager.logWarning("[DEBUG - AddItemsGUI] Drag cancelled: some slots " + dragSlots + " not in available slots " + availableSlots + " by " + player.getName());
            return;
        }

        // Collect current items
        List<ItemStack> collectedItems = new ArrayList<>();
        for (int i = 0; i < topInventory.getSize(); i++) {
            if (!protectedSlots.contains(i) && availableSlots.contains(i)) {
                ItemStack item = topInventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    collectedItems.add(item.clone());
                }
            }
        }

        int totalAmount = cursorItem.getAmount();
        int slotsToFill = validDragSlots.size();
        if (slotsToFill == 0) {
            event.setCancelled(true);
            return;
        }

        int amountPerSlot = totalAmount / slotsToFill;
        int remainder = totalAmount % slotsToFill;
        boolean noSpace = false;

        if (stackItems && cursorItem.getMaxStackSize() > 1) {
            // Try to stack with existing items
            for (ItemStack existing : collectedItems) {
                if (existing.isSimilar(cursorItem) && existing.getAmount() < existing.getMaxStackSize()) {
                    int maxAdd = existing.getMaxStackSize() - existing.getAmount();
                    int addAmount = Math.min(totalAmount, maxAdd);
                    if (addAmount > 0) {
                        existing.setAmount(existing.getAmount() + addAmount);
                        totalAmount -= addAmount;
                    }
                }
            }
            // Add remaining items to new slots if available
            while (totalAmount > 0 && collectedItems.size() < availableSlots.size()) {
                ItemStack newItem = cursorItem.clone();
                int amount = Math.min(totalAmount, newItem.getMaxStackSize());
                newItem.setAmount(amount);
                collectedItems.add(newItem);
                totalAmount -= amount;
            }
        } else {
            // Add items to new slots without stacking
            for (int i = 0; i < slotsToFill && totalAmount > 0; i++) {
                if (collectedItems.size() >= availableSlots.size()) {
                    noSpace = true;
                    break;
                }
                ItemStack newItem = cursorItem.clone();
                int amount = Math.min(amountPerSlot + (remainder > 0 ? 1 : 0), newItem.getMaxStackSize());
                newItem.setAmount(amount);
                collectedItems.add(newItem);
                totalAmount -= amount;
                if (remainder > 0) remainder--;
            }
        }

        if (noSpace || totalAmount == cursorItem.getAmount()) {
            event.setCancelled(true);
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String message = messagesConfig.getString("no-empty-slots", "&cNo empty slots available in the item GUI!");
            MessageUtils.sendFormattedMessage(player, message);
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Drag cancelled due to no available slots by " + player.getName());
            return;
        }

        // Sort items
        ItemValueCalculator calculator = plugin.getItemValueCalculator();
        if (sortByValue) {
            collectedItems.sort((a, b) -> {
                double valueA = calculator.calculateItemValue(a);
                double valueB = calculator.calculateItemValue(b);
                return Double.compare(valueB, valueA); // Highest to lowest
            });
        } else {
            collectedItems.sort(Comparator.comparing(item -> item.getType().name()));
        }

        // Clear available slots
        for (int slot : availableSlots) {
            topInventory.setItem(slot, null);
        }

        // Place items in available slots
        int slotIndex = 0;
        for (ItemStack item : collectedItems) {
            if (slotIndex >= availableSlots.size()) {
                debugManager.logWarning("[DEBUG - AddItemsGUI] Not enough available slots to place all items for " + player.getName());
                returnItemToPlayer(player, item);
                continue;
            }
            int slot = availableSlots.get(slotIndex);
            topInventory.setItem(slot, item.clone());
            slotIndex++;
        }

        // Update cursor
        if (totalAmount > 0) {
            cursorItem.setAmount(totalAmount);
            event.setCursor(cursorItem);
        } else {
            event.setCursor(null);
        }

        // Update session
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        session.setItemRewards(collectedItems);

        // Schedule Confirm button update and inventory sync
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateConfirmButton(topInventory);
            player.updateInventory();
        });
        debugManager.bufferDebug("[DEBUG - AddItemsGUI] Drag completed for " + player.getName() + ", placed items in slots " + validDragSlots);
    }

// file: java/tony26/bountiesPlus/GUIs/AddItemsGUI.java

    /**
     * Handles inventory close events for the AddItemsGUI
     * // note: Cleans up the GUI instance when the player closes the inventory, with checks for premature closure
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player closingPlayer = (Player) event.getPlayer();
        if (!closingPlayer.equals(this.player)) return;
        if (!inventory.getViewers().contains(closingPlayer)) return;

        // Verify the inventory title matches
        String expectedTitle = ChatColor.translateAlternateColorCodes('&', plugin.getAddItemsGUIConfig().getString(
                (BountyCreationSession.getSession(player) != null && BountyCreationSession.getSession(player).hasItemRewards()) ?
                        "edit-title" : "gui-title", "          &4&l⚔ &4&l&nAdd Items&4&l &4&l⚔"));
        if (!event.getView().getTitle().equals(expectedTitle)) {
            plugin.getDebugManager().bufferDebug("[DEBUG - AddItemsGUI] Ignored close by " + closingPlayer.getName() + ": title mismatch, expected '" + expectedTitle + "', got '" + event.getView().getTitle() + "'");
            return;
        }

        DebugManager debugManager = plugin.getDebugManager();
        long closeTime = System.currentTimeMillis();
        debugManager.logDebug("[DEBUG - AddItemsGUI] Inventory closed by " + closingPlayer.getName() + ", title: '" + event.getView().getTitle() + "', isTransitioningFromCreateGUI: " + isTransitioningFromCreateGUI + ", time since open: " + (closeTime - openTime) + "ms");

        // Skip cleanup if within 100ms of opening or transitioning
        if (isTransitioningFromCreateGUI || (closeTime - openTime < 100)) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] Skipping close processing for " + closingPlayer.getName() + ": transitioning or premature close");
            return;
        }

        // Clear non-protected slots without returning items
        List<Integer> availableSlots = plugin.getAddItemsGUIConfig().getIntegerList("content-area.available-slots");
        for (int slot : availableSlots) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    inventory.setItem(slot, null); // Clear the slot
                    debugManager.bufferDebug("[DEBUG - AddItemsGUI] Cleared item " + item.getType().name() + " x" + item.getAmount() + " from slot " + slot + " for " + closingPlayer.getName() + " on close (not returned)");
                }
            }
        }

        // Send discard message
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        String message = messagesConfig.getString("changes-discarded", "&eChanges discarded. Use Confirm to save item changes.");
        MessageUtils.sendFormattedMessage(closingPlayer, message);

        // Perform cleanup
        cleanup(closingPlayer);
        debugManager.logDebug("[DEBUG - AddItemsGUI] Completed cleanup for " + closingPlayer.getName());

        // Return to CreateGUI
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(closingPlayer);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.returnToCreateGUI();
        }, 3L);
    }

// file: java/tony26/bountiesPlus/GUIs/AddItemsGUI.java

    /**
     * Handles the Cancel button click in the AddItemsGUI
     * // note: Discards unsaved changes, preserves session items, and returns to CreateGUI
     */
    private void handleCancelButton(Player player, Inventory inventory) {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration messagesConfig = plugin.getMessagesConfig();

        // Clear non-protected slots without returning items
        List<Integer> availableSlots = plugin.getAddItemsGUIConfig().getIntegerList("content-area.available-slots");
        for (int slot : availableSlots) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    inventory.setItem(slot, null); // Clear the slot
                    debugManager.bufferDebug("[DEBUG - AddItemsGUI] Cleared item " + item.getType().name() + " x" + item.getAmount() + " from slot " + slot + " for " + player.getName() + " on cancel (not returned)");
                }
            }
        }

        // Send cancellation message
        String message = messagesConfig.getString("changes-discarded", "&eChanges discarded. Use Confirm to save item changes.");
        MessageUtils.sendFormattedMessage(player, message);

        // Clean up and return to CreateGUI
        cleanup(player);
        player.closeInventory();
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.returnToCreateGUI();
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Cancelled item selection for " + player.getName() + ", returning to CreateGUI");
        }, 3L);
    }

// file: java/tony26/bountiesPlus/GUIs/AddItemsGUI.java

    /**
     * Handles the Confirm button click in the AddItemsGUI
     * // note: Saves selected items to the bounty session, starts session if new, and returns to CreateGUI
     */
    private void handleConfirmButton(Player player, Inventory inventory) {
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration messagesConfig = plugin.getMessagesConfig();

        if (!validateTotalBountyValue(player, inventory, session)) {
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Bounty value validation failed for " + player.getName());
            return;
        }

        // Collect items from non-protected slots
        List<ItemStack> collectedItems = new ArrayList<>();
        ItemValueCalculator calculator = plugin.getItemValueCalculator();
        List<Integer> availableSlots = plugin.getAddItemsGUIConfig().getIntegerList("content-area.available-slots");

        for (int slot : availableSlots) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    ItemStack clonedItem = item.clone();
                    // Enforce stack size limits
                    if (clonedItem.getAmount() > clonedItem.getMaxStackSize()) {
                        clonedItem.setAmount(clonedItem.getMaxStackSize());
                    }
                    collectedItems.add(clonedItem);
                }
            }
        }

        // Sort items
        if (sortByValue) {
            collectedItems.sort((a, b) -> {
                double valueA = calculator.calculateItemValue(a);
                double valueB = calculator.calculateItemValue(b);
                return Double.compare(valueB, valueA); // Highest to lowest
            });
        } else {
            collectedItems.sort(Comparator.comparing(item -> item.getType().name()));
        }

        // Check if items were removed compared to session
        boolean hadItemsBefore = session.hasItemRewards();
        boolean itemsRemoved = hadItemsBefore && collectedItems.size() < session.getItemRewards().size();

        // Update session
        session.setItemRewards(collectedItems);
        if (!hadItemsBefore && !collectedItems.isEmpty()) {
            debugManager.logDebug("[DEBUG - AddItemsGUI] Started bounty creation session for " + player.getName() + " due to item addition");
        }

        // Send appropriate message
        String messageKey = itemsRemoved ? "items-updated" : "item-selection-confirmed";
        String message = messagesConfig.getString(messageKey, itemsRemoved ? "&aItems updated for bounty!" : "&aItems confirmed and saved to bounty session!");
        MessageUtils.sendFormattedMessage(player, message);

        // Clean up and return to CreateGUI
        cleanup(player);
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.returnToCreateGUI();
            debugManager.bufferDebug("[DEBUG - AddItemsGUI] Confirmed items for " + player.getName() + ", returning to CreateGUI");
        }, 3L);
    }

    /**
     * Gets the active GUI instance for a player // note: Retrieves the AddItemsGUI instance associated with the player's UUID
     */
    public static AddItemsGUI getActiveInstance(UUID playerUUID) {
        return activeInstances.get(playerUUID);
    }

    /**
     * Gets the number of items in the GUI's content slots // note: Counts non-null, non-AIR items in non-protected slots
     */
    public int getItemCount() {
        int count = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Gets the total value of items in the GUI's content slots // note: Calculates value of non-null, non-AIR items using ItemValueCalculator
     */
    public double getItemValue() {
        double value = 0.0;
        ItemValueCalculator calculator = BountiesPlus.getInstance().getItemValueCalculator();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    value += calculator.calculateItemValue(item);
                }
            }
        }
        return value;
    }
}