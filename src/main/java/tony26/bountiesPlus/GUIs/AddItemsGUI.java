package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.BountyCreationSession;
import tony26.bountiesPlus.utils.*;

import java.util.stream.Collectors;
import org.bukkit.Sound;
import tony26.bountiesPlus.wrappers.VersionWrapper;
import tony26.bountiesPlus.wrappers.VersionWrapperFactory;

import java.util.*;

public class AddItemsGUI implements Listener {

    private static final String GUI_TITLE = ChatColor.translateAlternateColorCodes('&', "&6Add Items to Bounty");

    // Track instances to prevent multiple listeners
    private static final Map<UUID, AddItemsGUI> activeInstances = new HashMap<>();

    // Define protected slots that cannot be modified by players (loaded dynamically)
    private final Set<Integer> protectedSlots = new HashSet<>();

    private final Set<Material> blacklistedItems; // Stores blacklisted materials

    // Track original items state for each player to detect changes
    private static final Map<UUID, List<ItemStack>> originalItemsState = new HashMap<>();

    private final Player player;
    private final Inventory inventory;

    /**
     * Constructs the AddItemsGUI for a player // note: Initializes GUI, loads blacklist, and registers listeners
     */
    public AddItemsGUI(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Load blacklisted items from config.yml
        FileConfiguration config = BountiesPlus.getInstance().getConfig();
        this.blacklistedItems = new HashSet<>();
        List<String> blacklist = config.getStringList("blacklisted-items");
        for (String materialName : blacklist) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                blacklistedItems.add(material);
            } catch (IllegalArgumentException e) {
                BountiesPlus.getInstance().getLogger().warning("Invalid material in blacklisted-items: " + materialName);
            }
        }

        // Load protected slots from AddItemsGUI.yml
        config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        List<Integer> borderSlots = config.getIntegerList("border.slots");
        if (borderSlots.isEmpty()) {
            borderSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 48, 50, 52, 53);
        }
        protectedSlots.addAll(borderSlots);
        // Add button slots
        protectedSlots.add(config.getInt("buttons.cancel.slot", 47));
        protectedSlots.add(config.getInt("buttons.info.slot", 49));
        protectedSlots.add(config.getInt("buttons.confirm.slot", 51));

        // Clean up any existing instance for this player
        cleanup(player);

        // Register this new instance
        activeInstances.put(player.getUniqueId(), this);
        BountiesPlus.getInstance().getServer().getPluginManager().registerEvents(this, BountiesPlus.getInstance());

        // Initialize the GUI
        initializeGUI();
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
     * Checks if an item is blacklisted // note: Returns true if the item's material or NBT tags match the blacklist
     */
    private boolean isBlacklisted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (blacklistedItems.contains(item.getType())) {
            return true;
        }
        FileConfiguration config = BountiesPlus.getInstance().getConfig();
        List<Map<?, ?>> nbtBlacklist = config.getMapList("blacklisted-nbt-items");
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
            debugManager.logDebug("Attempted to return null or AIR item to " + player.getName());
            return;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        player.updateInventory();
        if (!overflow.isEmpty()) {
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                debugManager.logDebug("Dropped blacklisted item " + overflowItem.getType().name() + " x" + overflowItem.getAmount() + " for " + player.getName() + " due to full inventory");
            }
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String inventoryFull = messagesConfig.getString("inventory-full", "&eYour inventory is full. Some items were dropped on the ground.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', inventoryFull));
        } else {
            debugManager.logDebug("Returned blacklisted item " + item.getType().name() + " x" + item.getAmount() + " to " + player.getName() + "'s inventory");
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

    private void initializeGUI() {
        // Create border
        addBorders();

        // Add bottom row buttons
        addBottomRowButtons(inventory);

        // Load existing items from session
        loadExistingItems(player, inventory);

        // Store the initial state
        storeOriginalItemsState(player, inventory);
    }

    private void loadExistingItems(Player player, Inventory inventory) {
        BountyCreationSession session = BountyCreationSession.getSession(player);
        if (session != null && session.hasItemRewards()) {
            List<ItemStack> existingItems = session.getItemRewards();

            // Place existing items in content area slots
            int slotIndex = 0;
            for (int i = 0; i < 54 && slotIndex < existingItems.size(); i++) {
                if (!protectedSlots.contains(i)) {
                    ItemStack item = existingItems.get(slotIndex);
                    if (item != null) {
                        inventory.setItem(i, item.clone());
                    }
                    slotIndex++;
                }
            }
        }
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

    /**
     * Adds border items to the GUI based on configuration // note: Populates border slots with configured material
     */
    private void addBorders() {
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        debugManager.logDebug("Adding borders to AddItemsGUI for " + player.getName());

        // Check if borders are enabled
        if (!config.getBoolean("border.enabled", true)) {
            debugManager.logDebug("Borders disabled in AddItemsGUI.yml");
            return;
        }

        // Get border configuration
        String materialName = config.getString("border.material", "WHITE_STAINED_GLASS_PANE");
        String name = config.getString("border.name", " ");
        List<String> lore = config.getStringList("border.lore");
        boolean enchantmentGlow = config.getBoolean("border.enchantment-glow", false);
        List<Integer> borderSlots = config.getIntegerList("border.slots");

        // Use default slots if none configured
        if (borderSlots.isEmpty()) {
            borderSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 48, 50, 52, 53);
            debugManager.logDebug("Using default border slots: " + borderSlots);
        }

        // Create border item with configured material
        Material borderMaterial = VersionUtils.getMaterialSafely(materialName, "STAINED_GLASS_PANE");
        if (!VersionUtils.isGlassPane(new ItemStack(borderMaterial))) {
            debugManager.logWarning("Invalid border material '" + materialName + "' in AddItemsGUI.yml, using WHITE_STAINED_GLASS_PANE");
            borderMaterial = VersionUtils.getWhiteGlassPaneMaterial();
        }

        ItemStack borderItem = new ItemStack(borderMaterial);
        ItemMeta borderMeta = borderItem.getItemMeta();
        if (borderMeta != null) {
            // Set display name and lore
            borderMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (!lore.isEmpty()) {
                PlaceholderContext context = PlaceholderContext.create().player(player);
                List<String> processedLore = Placeholders.apply(lore, context);
                borderMeta.setLore(processedLore);
            }

            // Add enchantment glow if enabled
            if (enchantmentGlow) {
                borderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                borderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            borderItem.setItemMeta(borderMeta);
        }

        // Apply borders to configured slots
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, borderItem);
                debugManager.logDebug("Placed border item in slot " + slot);
            } else {
                debugManager.logWarning("Invalid slot " + slot + " in AddItemsGUI.yml border configuration (must be 0-" + (inventory.getSize() - 1) + ")");
            }
        }
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
                BountiesPlus.getInstance().getLogger().info("[AddItemsGUI DEBUG] Items changed - slot " + i + " has different null state");
                return true; // One is null, other isn't
            }

            if (!original.isSimilar(current) || original.getAmount() != current.getAmount()) {
                BountiesPlus.getInstance().getLogger().info("[AddItemsGUI DEBUG] Items changed - slot " + i + " has different item or amount");
                return true; // Items are different
            }
        }
        return false; // No changes detected
    }
    /**
     * Adds bottom row buttons to the GUI // note: Places Cancel, Info, and Confirm buttons
     */
    private void addBottomRowButtons(Inventory gui) {
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        debugManager.logDebug("Adding bottom row buttons to AddItemsGUI for " + player.getName());

        // Cancel button
        if (config.getBoolean("buttons.cancel.enabled", true)) {
            int slot = config.getInt("buttons.cancel.slot", 47);
            ItemStack cancelButton = VersionUtils.getXMaterialItemStack(config.getString("buttons.cancel.material", "REDSTONE"));
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
            }
            gui.setItem(slot, cancelButton);
            debugManager.logDebug("Placed Cancel button in slot " + slot);
        }

        // How to use (Info) button
        if (config.getBoolean("buttons.info.enabled", true)) {
            int slot = config.getInt("buttons.info.slot", 49);
            ItemStack howToUseButton = VersionUtils.getXMaterialItemStack(config.getString("buttons.info.material", "PAPER"));
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
            }
            gui.setItem(slot, howToUseButton);
            debugManager.logDebug("Placed Info button in slot " + slot);
        }

        // Confirm button
        if (config.getBoolean("buttons.confirm.enabled", true)) {
            int slot = config.getInt("buttons.confirm.slot", 51);
            ItemStack confirmButton = VersionUtils.getXMaterialItemStack(config.getString("buttons.confirm.material", "EMERALD"));
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
            }
            gui.setItem(slot, confirmButton);
            debugManager.logDebug("Placed Confirm button in slot " + slot);
        }
    }

    /**
     * Updates the Confirm button's lore with current GUI item count and value // note: Refreshes Confirm button to reflect unconfirmed items
     */
    private void updateConfirmButton(Inventory gui) {
        FileConfiguration config = BountiesPlus.getInstance().getAddItemsGUIConfig();
        int slot = config.getInt("buttons.confirm.slot", 51);
        ItemStack confirmButton = gui.getItem(slot);
        if (confirmButton == null || confirmButton.getType() == Material.AIR) {
            return;
        }
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        if (confirmMeta != null) {
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
            confirmButton.setItemMeta(confirmMeta);
        }
        gui.setItem(slot, confirmButton);
        player.updateInventory();
    }

    /**
     * Handles a blacklisted item attempt // note: Returns item, sends message, closes GUI, and reopens after delay
     */
    private void handleBlacklistedItem(Player player, ItemStack item) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        debugManager.logDebug("Blacklisted item detected: " + item.getType().name() + " by " + player.getName());

        // Return the blacklisted item to the player's inventory
        returnItemToPlayer(player, item);

        // Get configuration for timing and messages
        FileConfiguration guiConfig = BountiesPlus.getInstance().getAddItemsGUIConfig();
        FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
        int fadeIn = guiConfig.getInt("title-duration.fade-in", 20);
        int stay = guiConfig.getInt("title-duration.stay", 60);
        int fadeOut = guiConfig.getInt("title-duration.fade-out", 20);
        long reopenDelay = guiConfig.getLong("reopen-delay", 100);

        // Ensure non-negative timing values
        fadeIn = Math.max(0, fadeIn);
        stay = Math.max(0, stay);
        fadeOut = Math.max(0, fadeOut);
        reopenDelay = Math.max(20, reopenDelay); // Minimum 1 second to avoid glitches

        // Prepare placeholder context with item name
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                item.getItemMeta().getDisplayName() : item.getType().name().toLowerCase().replace('_', ' ');
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .itemName(itemName);

        // Send title or chat message based on version
        VersionWrapper wrapper = VersionWrapperFactory.getWrapper();
        if (VersionUtils.isPost111()) {
            String title = Placeholders.apply(messagesConfig.getString("blacklisted-item-title", "&cBlacklisted Item!"), context);
            String subtitle = Placeholders.apply(messagesConfig.getString("blacklisted-item-subtitle", "&e%item_name% &7cannot be added."), context);
            wrapper.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
            debugManager.logDebug("Sent title for blacklisted item to " + player.getName());
        } else {
            String message = Placeholders.apply(messagesConfig.getString("blacklisted-item-message", "&c%item_name% &7is blacklisted and cannot be added to the bounty!"), context);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            debugManager.logDebug("Sent chat message for blacklisted item to " + player.getName());
        }

        // Play error sound
        try {
            player.playSound(player.getLocation(), wrapper.getErrorSound(), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            debugManager.logWarning("Failed to play error sound for " + player.getName() + ": " + e.getMessage());
        }

        // Close the GUI
        player.closeInventory();

        // Schedule GUI reopen
        Bukkit.getScheduler().runTaskLater(BountiesPlus.getInstance(), () -> {
            if (player.isOnline()) {
                new AddItemsGUI(player).openInventory(player);
                debugManager.logDebug("Reopened AddItemsGUI for " + player.getName() + " after blacklisted item rejection");
            }
        }, reopenDelay);
    }

    /**
     * Handles inventory click events for the AddItemsGUI // note: Manages item placement, button interactions, and blacklist checks
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();

        // Get or create session
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);

        // Check for blacklisted cursor item when placing in top inventory
        if (slot < 54 && !protectedSlots.contains(slot) && cursorItem != null && isBlacklisted(cursorItem)) {
            event.setCancelled(true);
            handleBlacklistedItem(player, cursorItem.clone());
            return;
        }

        // Protect clicks in top inventory (AddItemsGUI, slots 0-53)
        if (slot < 54) {
            if (protectedSlots.contains(slot)) {
                event.setCancelled(true);
                if (currentItem == null || !currentItem.hasItemMeta()) return;

                // Handle button clicks
                if (slot == 47) { // Cancel button
                    debugManager.bufferDebug("Cancel button clicked by " + player.getName());
                    handleCancelButton(player, event.getInventory());
                } else if (slot == 49) { // Info button
                    debugManager.bufferDebug("Info button clicked by " + player.getName());
                    // Informational only, no action needed
                } else if (slot == 51) { // Confirm button
                    debugManager.bufferDebug("Confirm button clicked by " + player.getName());
                    handleConfirmButton(player, event.getInventory());
                }
                return;
            } else {
                // Allow item interaction in content area
                Bukkit.getScheduler().runTask(BountiesPlus.getInstance(), () -> updateConfirmButton(event.getInventory()));
            }
        } else {
            // Allow clicks in player inventory
            Bukkit.getScheduler().runTask(BountiesPlus.getInstance(), () -> updateConfirmButton(event.getInventory()));
        }

        // Handle shift-clicking from player inventory
        if (event.isShiftClick() && slot >= 54 && currentItem != null && currentItem.getType() != Material.AIR) {
            if (isBlacklisted(currentItem)) {
                event.setCancelled(true);
                handleBlacklistedItem(player, currentItem.clone());
                return;
            }

            // Find first available slot in content area
            Inventory topInventory = event.getInventory();
            for (int i = 10; i <= 43; i++) {
                if (!protectedSlots.contains(i) && (topInventory.getItem(i) == null || topInventory.getItem(i).getType() == Material.AIR)) {
                    Bukkit.getScheduler().runTask(BountiesPlus.getInstance(), () -> updateConfirmButton(topInventory));
                    return;
                }
            }

            event.setCancelled(true);
            FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
            String noEmptySlots = messagesConfig.getString("no-empty-slots", "&cNo empty slots available in the item GUI!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noEmptySlots));
        }
    }
    /**
     * Handles inventory drag events for the AddItemsGUI // note: Manages drag interactions and blacklist checks
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Player player = (Player) event.getWhoClicked();
        Set<Integer> dragSlots = event.getRawSlots();
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();

        // Check for protected slots in top inventory
        boolean hasProtectedSlots = dragSlots.stream().anyMatch(slot -> slot < 54 && protectedSlots.contains(slot));
        if (hasProtectedSlots) {
            event.setCancelled(true);
            debugManager.bufferDebug("Drag cancelled for " + player.getName() + " due to protected slots");
            return;
        }

        // Check for blacklisted items
        ItemStack cursorItem = event.getOldCursor();
        if (isBlacklisted(cursorItem)) {
            event.setCancelled(true);
            handleBlacklistedItem(player, cursorItem.clone());
            return;
        }

        // Allow dragging to content area
        Bukkit.getScheduler().runTask(BountiesPlus.getInstance(), () -> updateConfirmButton(event.getView().getTopInventory()));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Only handle events for this specific player and inventory
        if (!event.getPlayer().equals(this.player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        // Clean up when inventory closes
        cleanup(this.player);
    }

    /**
     * Handles the Cancel button click in the AddItemsGUI // note: Discards changes and returns to CreateGUI
     */
    private void handleCancelButton(Player player, Inventory inventory) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();

        if (hasItemsChanged(player, inventory)) {
            String message = messagesConfig.getString("changes-discarded", "&eChanges discarded. Use Confirm to save item changes.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            debugManager.bufferDebug("Changes discarded for " + player.getName());
        } else {
            String message = messagesConfig.getString("no-changes", "&eReturned to Create Bounty GUI. No changes were made.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            debugManager.bufferDebug("No changes detected for " + player.getName());
        }

        cleanup(player);
        player.closeInventory();
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        session.returnToCreateGUI();
    }

    /**
     * Handles the Confirm button click in the AddItemsGUI // note: Saves selected items to the bounty session and returns to CreateGUI
     */
    private void handleConfirmButton(Player player, Inventory inventory) {
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();

        if (!validateTotalBountyValue(player, inventory, session)) {
            debugManager.bufferDebug("Bounty value validation failed for " + player.getName());
            return;
        }

        // Collect items from non-protected slots
        List<ItemStack> collectedItems = new ArrayList<>();
        double totalValue = 0.0;
        ItemValueCalculator calculator = BountiesPlus.getInstance().getItemValueCalculator();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!protectedSlots.contains(slot)) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    collectedItems.add(item.clone());
                    totalValue += calculator.calculateItemValue(item);
                }
            }
        }

        // Update session
        session.setItemRewards(collectedItems);
        FileConfiguration messagesConfig = BountiesPlus.getInstance().getMessagesConfig();
        String message = totalValue > 0 ?
                messagesConfig.getString("items-added", "&aItems added to bounty! Total value: &e$%bountiesplus_item_value%") :
                messagesConfig.getString("items-updated", "&aItems updated for bounty!");
        PlaceholderContext context = PlaceholderContext.create().player(player).itemValue(totalValue);
        player.sendMessage(Placeholders.apply(message, context));

        // Clean up and return to CreateGUI
        cleanup(player);
        player.closeInventory();
        session.returnToCreateGUI();
        debugManager.bufferDebug("Confirmed items for " + player.getName() + ", returning to CreateGUI");
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