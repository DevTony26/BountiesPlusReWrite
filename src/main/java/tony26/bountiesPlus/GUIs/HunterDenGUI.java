package tony26.bountiesPlus.GUIs;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.EventManager;
import tony26.bountiesPlus.utils.SkullUtils;
import tony26.bountiesPlus.utils.TaxManager;
import tony26.bountiesPlus.utils.*;
import net.md_5.bungee.api.chat.TextComponent;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.*;
import java.util.stream.Collectors;

public class HunterDenGUI implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final Player player;
    private final BountiesPlus plugin;
    private String GUI_TITLE;
    private transient InventoryClickEvent event; // Store event for click type access
    private final FileConfiguration messagesConfig;
    private final Set<Integer> protectedSlots = new HashSet<>();

    /**
     * Constructs the HunterDenGUI for a player
     * // note: Initializes the shop GUI with items, borders, and registers listeners
     */
    public HunterDenGUI(Player player, EventManager eventManager) {
        this.player = player;
        this.plugin = BountiesPlus.getInstance();
        this.messagesConfig = plugin.getMessagesConfig();
        FileConfiguration config = plugin.getHuntersDenConfig();
        File configFile = new File(plugin.getDataFolder(), "GUIs/HuntersDen.yml");

        // Verify configuration integrity
        if (!configFile.exists() || config.getConfigurationSection("Plugin-Items") == null) {
            plugin.getDebugManager().logWarning("[DEBUG - HunterDenGUI] HuntersDen.yml is missing or invalid, reloading default");
            try {
                if (configFile.exists()) configFile.delete(); // Remove invalid file
                plugin.saveResource("GUIs/HuntersDen.yml", false); // Copy default
                config = YamlConfiguration.loadConfiguration(configFile);
                plugin.getDebugManager().logDebug("[DEBUG - HunterDenGUI] Reloaded default HuntersDen.yml");
            } catch (IllegalArgumentException e) {
                plugin.getDebugManager().logWarning("[DEBUG - HunterDenGUI] Failed to reload default HuntersDen.yml: " + e.getMessage());
            }
        }

        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&4&l⚔ Hunter's Den ⚔"));
        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE);
        eventManager.register(this);
        initializeGUI();
    }

    /**
     * Initializes the GUI with shop items, extra items, back button, and borders
     * // note: Populates GUI in correct order to prevent slot conflicts
     */
    private void initializeGUI() {
        FileConfiguration config = plugin.getHuntersDenConfig();

        // Add shop items first to reserve their slots
        addShopItems(config);

        // Add extra items
        addExtraItems(config);

        // Add back button
        addBackButton(config);

        // Add borders last, skipping occupied slots
        addBorders(config);
    }

    /**
     * Adds borders to the GUI, skipping occupied slots
     * // note: Populates border slots with configured material, avoiding shop items, extra items, and back button
     */
    private void addBorders(FileConfiguration config) {
        DebugManager debugManager = plugin.getDebugManager();
        if (!config.getBoolean("border.enabled", true)) {
            debugManager.logDebug("[DEBUG] Borders disabled in HuntersDen.yml");
            return;
        }

        String materialName = config.getString("border.material", "RED_STAINED_GLASS_PANE");
        String name = config.getString("border.name", " ");
        List<String> lore = config.getStringList("border.lore");
        boolean enchantmentGlow = config.getBoolean("border.enchantment-glow", false);
        List<Integer> borderSlots = config.getIntegerList("border.slots");

        if (borderSlots.isEmpty()) {
            for (int i = 0; i < 9; i++) borderSlots.add(i);
            for (int i = 45; i < 54; i++) borderSlots.add(i);
            for (int i = 9; i < 45; i += 9) borderSlots.add(i);
            for (int i = 17; i < 45; i += 9) borderSlots.add(i);
        }

        Material borderMaterial = VersionUtils.getMaterialSafely(materialName, "GLASS_PANE");
        String failureReason = null;
        if (!VersionUtils.isGlassPane(new ItemStack(borderMaterial))) {
            debugManager.logWarning("Invalid border material '" + materialName + "' in HuntersDen.yml, using RED_STAINED_GLASS_PANE");
            failureReason = "Invalid material '" + materialName + "'";
            borderMaterial = VersionUtils.getMaterialSafely("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE");
        }

        PlaceholderContext context = PlaceholderContext.create().player(this.player);
        name = Placeholders.apply(name, context);
        lore = Placeholders.apply(lore, context);

        ItemStack borderItem = createConfigurableItem(borderMaterial, name, lore, enchantmentGlow);
        ItemMeta meta = borderItem.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("Failed to get ItemMeta for border item");
            failureReason = "Failed to get ItemMeta";
        }

        int totalItems = borderSlots.size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (int slot : borderSlots) {
            if (slot >= 0 && slot < 54) {
                if (failureReason == null && inventory.getItem(slot) == null) {
                    inventory.setItem(slot, borderItem);
                    protectedSlots.add(slot);
                    successfulItems++;
                } else if (failureReason != null) {
                    failures.add("border-slot-" + slot + " Reason: " + failureReason);
                } else {
                    failures.add("border-slot-" + slot + " Reason: Slot occupied");
                }
            } else {
                debugManager.logWarning("Invalid slot " + slot + " in HuntersDen.yml border configuration");
                failures.add("border-slot-" + slot + " Reason: Invalid slot " + slot);
            }
        }

        // Log consolidated debug message
        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG] All border items created");
        } else {
            String failureMessage = "[DEBUG] " + successfulItems + "/" + totalItems + " border items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("HunterDenGUI_border_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds shop items to the GUI from configuration
     * // note: Populates purchasable items from Plugin-Items with configurable properties and placeholders
     */
    private void addShopItems(FileConfiguration config) {
        DebugManager debugManager = plugin.getDebugManager();
        Set<String> shopItemKeys = config.getConfigurationSection("Plugin-Items") != null ?
                config.getConfigurationSection("Plugin-Items").getKeys(false) : new HashSet<>();
        shopItemKeys.remove("border"); // Exclude border item
        shopItemKeys.remove("back-button"); // Exclude back button
        int totalItems = shopItemKeys.size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (String itemId : shopItemKeys) {
            String basePath = "Plugin-Items." + itemId;
            int slot = config.getInt(basePath + ".slot", -1);
            String failureReason = null;

            if (slot < 0 || slot >= 54) {
                debugManager.logWarning(messagesConfig.getString("warnings.invalid-item-slot",
                                "Invalid slot %slot% for item %item% in HuntersDen.yml")
                        .replace("%slot%", String.valueOf(slot))
                        .replace("%item%", itemId));
                failures.add(itemId + " Reason: Invalid slot " + slot);
                continue;
            }

            String materialName = config.getString(basePath + ".material", "STONE");
            ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
            if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                debugManager.logWarning(messagesConfig.getString("warnings.invalid-item-material",
                                "Invalid material '%material%' for item %item% in HuntersDen.yml, using STONE")
                        .replace("%material%", materialName)
                        .replace("%item%", itemId));
                failureReason = "Invalid material '" + materialName + "'";
                item = new ItemStack(Material.STONE);
            }

            String name = config.getString(basePath + ".name", "&f" + itemId);
            List<String> lore = config.getStringList(basePath + ".lore");
            boolean enchantmentGlow = config.getBoolean(basePath + ".enchantment-glow", false);

            PlaceholderContext context = PlaceholderContext.create()
                    .player(this.player)
                    .moneyValue(config.getDouble(basePath + ".price", 0.0))
                    .expValue(config.getInt(basePath + ".xp-price", 0))
                    .itemCount(config.getInt(basePath + ".skull-count", 0))
                    .withAmount(config.getDouble(basePath + ".min-skull-value", 0.0));

            name = Placeholders.apply(name, context);
            lore = Placeholders.apply(lore, context);

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                debugManager.logWarning(messagesConfig.getString("warnings.item-load-error",
                                "Error loading shop item %item% from HuntersDen.yml: %error%")
                        .replace("%item%", itemId)
                        .replace("%error%", "Failed to get ItemMeta"));
                failureReason = "Failed to get ItemMeta";
            } else {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                if (lore != null && !lore.isEmpty()) {
                    List<String> coloredLore = lore.stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                            .collect(Collectors.toList());
                    meta.setLore(coloredLore);
                }
                if (enchantmentGlow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }

            if (failureReason == null && inventory.getItem(slot) == null) {
                inventory.setItem(slot, item);
                protectedSlots.add(slot);
                successfulItems++;
            } else if (failureReason != null) {
                failures.add(itemId + " Reason: " + failureReason);
            } else {
                debugManager.logWarning(messagesConfig.getString("warnings.slot-occupied",
                                "Slot %slot% for %item% is already occupied")
                        .replace("%slot%", String.valueOf(slot))
                        .replace("%item%", itemId));
                failures.add(itemId + " Reason: Slot " + slot + " occupied");
            }
        }

        if (totalItems == 0) {
            debugManager.logDebug("[DEBUG] No shop items to create");
        } else if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG] All shop items created");
        } else {
            String failureMessage = "[DEBUG] " + successfulItems + "/" + totalItems + " shop items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("HunterDenGUI_shop_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds extra decorative items to the GUI
     * // note: Populates non-purchasable decorative items from Custom-Items with placeholders
     */
    private void addExtraItems(FileConfiguration config) {
        DebugManager debugManager = plugin.getDebugManager();
        Set<String> extraItemKeys = config.getConfigurationSection("Custom-Items") != null ?
                config.getConfigurationSection("Custom-Items").getKeys(false) : new HashSet<>();
        int totalItems = extraItemKeys.size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (String itemId : extraItemKeys) {
            String basePath = "Custom-Items." + itemId;
            List<Integer> slots = config.contains(basePath + ".slots") ?
                    config.getIntegerList(basePath + ".slots") :
                    (config.contains(basePath + ".slot") ? Collections.singletonList(config.getInt(basePath + ".slot", -1)) : Collections.emptyList());
            String failureReason = null;

            String materialName = config.getString(basePath + ".material", "STONE");
            ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
            if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                debugManager.logWarning(messagesConfig.getString("warnings.invalid-extra-item-material",
                                "Invalid material '%material%' for extra item %item% in HuntersDen.yml, using STONE")
                        .replace("%material%", materialName)
                        .replace("%item%", itemId));
                failureReason = "Invalid material '" + materialName + "'";
                item = new ItemStack(Material.STONE);
            }

            String name = config.getString(basePath + ".name", "&f" + itemId);
            List<String> lore = config.getStringList(basePath + ".lore");
            boolean enchantmentGlow = config.getBoolean(basePath + ".enchantment-glow", false);

            PlaceholderContext context = PlaceholderContext.create()
                    .player(this.player)
                    .expValue(player.getLevel())
                    .moneyValue(BountiesPlus.getEconomy() != null ? BountiesPlus.getEconomy().getBalance(player) : 0.0);

            name = Placeholders.apply(name, context);
            lore = Placeholders.apply(lore, context);

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                debugManager.logWarning(messagesConfig.getString("warnings.extra-item-load-error",
                                "Error loading extra item %item% from HuntersDen.yml: %error%")
                        .replace("%item%", itemId)
                        .replace("%error%", "Failed to get ItemMeta"));
                failureReason = "Failed to get ItemMeta";
            } else {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
                if (lore != null && !lore.isEmpty()) {
                    List<String> coloredLore = lore.stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                            .collect(Collectors.toList());
                    meta.setLore(coloredLore);
                }
                if (enchantmentGlow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }

            for (int slot : slots) {
                if (slot < 0 || slot >= 54) {
                    debugManager.logWarning(messagesConfig.getString("warnings.invalid-extra-item-slot",
                                    "Invalid slot %slot% for extra item %item% in HuntersDen.yml")
                            .replace("%slot%", String.valueOf(slot))
                            .replace("%item%", itemId));
                    failures.add(itemId + " at slot " + slot + " Reason: Invalid slot");
                    continue;
                }

                if (failureReason == null && inventory.getItem(slot) == null) {
                    inventory.setItem(slot, item.clone());
                    protectedSlots.add(slot);
                    successfulItems++;
                } else if (failureReason != null) {
                    failures.add(itemId + " at slot " + slot + " Reason: " + failureReason);
                } else {
                    debugManager.logWarning(messagesConfig.getString("warnings.slot-occupied",
                                    "Slot %slot% for %item% is already occupied")
                            .replace("%slot%", String.valueOf(slot))
                            .replace("%item%", itemId));
                    failures.add(itemId + " at slot " + slot + " Reason: Slot occupied");
                }
            }
        }

        if (totalItems == 0) {
            debugManager.logDebug("[DEBUG] No extra items to create");
        } else if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG] All extra items created");
        } else {
            String failureMessage = "[DEBUG] " + successfulItems + "/" + totalItems + " extra items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("HunterDenGUI_extra_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds the back button to the GUI
     * // note: Creates a back button to return to the main bounty GUI
     */
    private void addBackButton(FileConfiguration config) {
        DebugManager debugManager = plugin.getDebugManager();
        String buttonName = "back-button";
        int slot = config.getInt("back-button.slot", 49);
        String failureReason = null;

        if (slot < 0 || slot >= 54) {
            debugManager.logWarning("Invalid slot " + slot + " for back button in HuntersDen.yml, defaulting to 49");
            slot = 49;
            failureReason = "Invalid slot " + slot;
        }

        String materialName = config.getString("back-button.material", "BARRIER");
        ItemStack backButton = VersionUtils.getXMaterialItemStack(materialName);
        if (backButton.getType() == Material.STONE && !materialName.equalsIgnoreCase("BARRIER")) {
            debugManager.logWarning("Invalid back button material '" + materialName + "' in HuntersDen.yml, using BARRIER");
            failureReason = "Invalid material '" + materialName + "'";
            backButton = VersionUtils.getXMaterialItemStack("BARRIER");
        }

        String name = config.getString("back-button.name", "&c&lBack to Bounties");
        List<String> lore = config.getStringList("back-button.lore");
        boolean enchantmentGlow = config.getBoolean("back-button.enchantment-glow", false);

        PlaceholderContext context = PlaceholderContext.create().player(this.player);
        name = Placeholders.apply(name, context);
        lore = Placeholders.apply(lore, context);

        ItemMeta meta = backButton.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("Failed to get ItemMeta for back button");
            failureReason = "Failed to get ItemMeta";
        } else {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(coloredLore);
            }
            if (enchantmentGlow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            backButton.setItemMeta(meta);
        }

        int totalButtons = 1;
        int successfulButtons = 0;

        if (failureReason == null && (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR)) {
            inventory.setItem(slot, backButton);
            protectedSlots.add(slot);
            successfulButtons++;
        } else if (failureReason != null) {
            debugManager.logWarning("Failed to create back button: " + failureReason);
        } else {
            debugManager.logWarning("Slot " + slot + " for back button is already occupied");
            failureReason = "Slot " + slot + " occupied";
        }

        if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG] All buttons created");
        } else {
            String failureMessage = "[DEBUG] " + successfulButtons + "/" + totalButtons + " buttons created";
            if (failureReason != null) {
                failureMessage += ", failed to create: " + buttonName + " Reason: " + failureReason;
            }
            debugManager.bufferFailure("HunterDenGUI_back_button_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Handles inventory click events for the GUI
     * // note: Processes clicks on shop items, extra items, and back button
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!event.getWhoClicked().equals(this.player)) return;

        event.setCancelled(true); // Prevent item movement
        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        FileConfiguration config = plugin.getHuntersDenConfig();

        // Check for back button by slot
        int backButtonSlot = config.getInt("back-button.slot", 49);
        if (slot == backButtonSlot) {
            clickingPlayer.closeInventory();
            cleanup();
            BountyGUI.openBountyGUI(clickingPlayer, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), 0);
            plugin.getDebugManager().bufferDebug("Back button clicked by " + clickingPlayer.getName() + ", opened BountyGUI");
            clickingPlayer.updateInventory();
            return;
        }

        // Check for shop items
        for (String itemId : config.getConfigurationSection("shop-items").getKeys(false)) {
            int itemSlot = config.getInt("shop-items." + itemId + ".slot", -1);
            if (slot == itemSlot) {
                this.event = event; // Store event for click type access
                handleShopItemClick(clickingPlayer, itemId, config);
                this.event = null; // Clear event
                clickingPlayer.updateInventory();
                return;
            }
        }

        // Check for extra items
        for (String itemId : config.getConfigurationSection("extra-items").getKeys(false)) {
            int itemSlot = config.getInt("extra-items." + itemId + ".slot", -1);
            if (slot == itemSlot) {
                handleExtraItemClick(clickingPlayer, itemId, clickedItem);
                clickingPlayer.updateInventory();
                return;
            }
        }

        clickingPlayer.updateInventory();
    }

    /**
     * Handles shop item clicks in the Hunter's Den GUI
     * // note: Processes purchases with money, XP, or skulls based on click type, respecting permissions and inventory space
     */
    private void handleShopItemClick(Player player, String itemId, FileConfiguration config) {
        String basePath = "Plugin-Items." + itemId;
        DebugManager debugManager = plugin.getDebugManager();
        PlaceholderContext context = PlaceholderContext.create().player(player).itemName(itemId);

        // Check shop permission
        if (!player.hasPermission(config.getString("permissions.use-shop", "bountiesplus.shop.use"))) {
            player.sendMessage(Placeholders.apply(messagesConfig.getString("no-permission", "&cYou don't have permission to purchase this item!"), context));
            debugManager.logDebug("[DEBUG] Player " + player.getName() + " lacks permission for shop purchase: " + itemId);
            return;
        }

        // Check premium permission for elite items
        boolean isPremiumItem = itemId.equals("elite_pack");
        if (isPremiumItem && !player.hasPermission(config.getString("permissions.buy-premium", "bountiesplus.shop.premium"))) {
            player.sendMessage(Placeholders.apply(messagesConfig.getString("no-permission", "&cYou don't have permission to purchase this item!"), context));
            debugManager.logDebug("[DEBUG] Player " + player.getName() + " lacks premium permission for: " + itemId);
            return;
        }

        String currencyType = config.getString(basePath + ".currency-type", "money").toLowerCase();
        double price = config.getDouble(basePath + ".price", 0.0);
        int xpPrice = config.getInt(basePath + ".xp-price", 0);
        int skullCount = config.getInt(basePath + ".skull-count", 0);
        double minSkullValue = config.getDouble(basePath + ".min-skull-value", 0.0);
        List<String> commands = config.getStringList(basePath + ".commands.money"); // Default to money commands
        ClickType clickType = event.getClick();

        // Determine selected currency for multi-currency items
        String selectedCurrency = currencyType;
        if (currencyType.equals("multi")) {
            if (clickType == ClickType.LEFT) {
                selectedCurrency = "money";
                commands = config.getStringList(basePath + ".commands.money");
            } else if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                selectedCurrency = "xp";
                commands = config.getStringList(basePath + ".commands.xp");
            } else if (clickType == ClickType.RIGHT) {
                selectedCurrency = "skulls";
                commands = config.getStringList(basePath + ".commands.skulls");
            } else {
                player.sendMessage(Placeholders.apply(messagesConfig.getString("invalid-currency-type", "&cInvalid click type for multi-currency item!"), context));
                return;
            }
        } else if (currencyType.equals("money")) {
            if (clickType != ClickType.LEFT) {
                player.sendMessage(Placeholders.apply(messagesConfig.getString("invalid-currency-type", "&cUse left-click to purchase with money!"), context));
                return;
            }
        }

        // Validate commands
        if (commands.isEmpty()) {
            debugManager.logWarning(messagesConfig.getString("warnings.purchase-error",
                            "Error processing purchase for %player%: %error%")
                    .replace("%player%", player.getName())
                    .replace("%error%", "No commands defined for " + itemId));
            player.sendMessage(Placeholders.apply(messagesConfig.getString("purchase-failed", "&c&l✗ Failed to purchase %item%!"), context));
            return;
        }

        // Validate inventory space
        if (!hasEnoughInventorySpace(player, commands)) {
            player.sendMessage(Placeholders.apply(messagesConfig.getString("inventory-full-shop", "&c&lYour inventory is too full to receive this item!"), context));
            debugManager.logDebug("[DEBUG] Player " + player.getName() + " has insufficient inventory space for: " + itemId);
            return;
        }

        // Calculate tax
        TaxManager taxManager = plugin.getTaxManager();
        double taxAmount = taxManager.calculateTax(price, null);

        // Validate currency requirements
        context = context.moneyValue(price).expValue(xpPrice).itemCount(skullCount).withAmount(minSkullValue).taxAmount(taxAmount);
        if (!checkCurrencyRequirements(player, selectedCurrency, price + taxAmount, xpPrice, config, itemId)) {
            String messageKey = selectedCurrency.equals("money") ? "insufficient-funds" :
                    selectedCurrency.equals("xp") ? "insufficient-xp" : "insufficient-skulls";
            player.sendMessage(Placeholders.apply(messagesConfig.getString(messageKey, "&cYou need %required_amount% to purchase this item!"), context));
            return;
        }

        // Process purchase
        if (processPurchase(player, itemId, selectedCurrency, price + taxAmount, xpPrice)) {
            executeCommands(player, commands);
            player.sendMessage(Placeholders.apply(messagesConfig.getString("purchase-success", "&a&l✓ Successfully purchased %item%!"), context));
            taxManager.sendTaxMessages(player, null, price, taxAmount);
            debugManager.logDebug("[DEBUG] Player " + player.getName() + " purchased " + itemId + " with " + selectedCurrency);
        } else {
            player.sendMessage(Placeholders.apply(messagesConfig.getString("purchase-failed", "&c&l✗ Failed to purchase %item%!"), context));
            debugManager.logDebug("[DEBUG] Purchase failed for " + player.getName() + ": " + itemId);
        }
    }

    /**
     * Checks if player has enough inventory space for command-executed items
     * // note: Validates space based on the number of items given by commands
     */
    private boolean hasEnoughInventorySpace(Player player, List<String> commands) {
        int itemsToGive = 0;
        for (String command : commands) {
            if (command.contains("bounty give")) {
                String[] parts = command.split(" ");
                if (parts.length >= 5) {
                    try {
                        itemsToGive += Integer.parseInt(parts[4]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots >= itemsToGive;
    }

    /**
     * Handle extra item click interactions
     */
    private void handleExtraItemClick(Player player, String itemId, ItemStack clickedItem) {
        FileConfiguration config = plugin.getHuntersDenConfig(); // Fixed method name
        String basePath = "extra-items." + itemId;

        // Get click action
        String action = config.getString(basePath + ".click-action", "none");

        switch (action.toLowerCase()) {
            case "close":
                player.closeInventory();
                break;
            case "command":
                List<String> commands = config.getStringList(basePath + ".commands");
                executeCommands(player, commands);
                break;
            case "message":
                String message = config.getString(basePath + ".message", "");
                if (!message.isEmpty()) {
                    sendMessage(player, message);
                }
                break;
            default:
                // No action defined
                break;
        }
    }

    /**
     * Checks if an item is a bounty skull // note: Identifies bounty skulls by lore containing BOUNTY_SKULL identifier or matching config.yml bounty-skull format
     */
    public static boolean isBountySkull(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material playerHeadMaterial = VersionUtils.getPlayerHeadMaterial();
        if (item.getType() != playerHeadMaterial) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        List<String> lore = meta.getLore();
        if (lore == null) return false;
        // Check for explicit BOUNTY_SKULL identifier
        for (String line : lore) {
            if (line.contains(ChatColor.DARK_GRAY + "" + ChatColor.MAGIC + "BOUNTY_SKULL")) {
                return true;
            }
        }
        // Fallback: Check for lore matching config.yml bounty-skull format
        BountiesPlus plugin = BountiesPlus.getInstance();
        FileConfiguration config = plugin.getConfig();
        List<String> expectedLore = config.getStringList("bounty-skull.lore");
        if (expectedLore.isEmpty()) {
            return false;
        }
        // Basic check: lore contains at least one expected line (e.g., "This is the head of a wanted criminal!")
        for (String expectedLine : expectedLore) {
            String cleanExpected = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', expectedLine))
                    .replace("%target%", "").replace("%total_bounty%", "")
                    .replace("%bounty_count%", "").replace("%killer%", "")
                    .replace("%death_time%", "").trim();
            if (cleanExpected.isEmpty()) continue;
            for (String actualLine : lore) {
                String cleanActual = ChatColor.stripColor(actualLine).trim();
                if (cleanActual.contains(cleanExpected)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extract bounty value from skull lore // note: Parses lore for bounty amount
     */
    public static double extractBountyValueFromSkull(ItemStack skull) {
        if (!isBountySkull(skull)) return 0.0;
        ItemMeta meta = skull.getItemMeta();
        if (meta == null || !meta.hasLore()) return 0.0;
        List<String> lore = meta.getLore();
        if (lore == null) return 0.0;
        for (String line : lore) {
            if (line.contains("Bounty Value:")) {
                String cleanLine = ChatColor.stripColor(line);
                String[] parts = cleanLine.split(":");
                if (parts.length > 1) {
                    String valueStr = parts[1].trim().replace("$", "").replace(",", "");
                    try {
                        return Double.parseDouble(valueStr);
                    } catch (NumberFormatException e) {
                        // Silently ignore parsing errors to avoid static context issues
                    }
                }
            }
        }
        return 0.0;
    }

    /**
     * Checks if a bounty skull is tied to an active bounty // note: Verifies if the killed player has an active bounty for shop purchase validation
     */
    private boolean isSkullActive(ItemStack skull) {
        UUID killedUUID = SkullUtils.getKilledPlayerUUID(skull);
        return killedUUID != null && plugin.getBountyManager().hasBounty(killedUUID);
    }

    /**
     * Checks if player has enough valid bounty skulls with minimum value // note: Validates skull count and status (active or expired/claimed based on allow-expired-skulls setting)
     */
    private boolean checkSkullRequirements(Player player, int requiredCount, double minValue) {
        FileConfiguration config = plugin.getConfig();
        boolean allowExpiredSkulls = config.getBoolean("allow-expired-skulls", true);
        int validSkullCount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isBountySkull(item)) {
                double skullValue = extractBountyValueFromSkull(item);
                if (skullValue >= minValue && (allowExpiredSkulls || isSkullActive(item))) {
                    validSkullCount += item.getAmount();
                    if (validSkullCount >= requiredCount) {
                        return true;
                    }
                }
            }
        }

        if (!allowExpiredSkulls) {
            sendMessage(player, Placeholders.apply(messagesConfig.getString("invalid-skull", "&cOnly active bounty skulls can be used for this purchase!"), PlaceholderContext.create().player(player)));
        }

        return false;
    }

    /**
     * Removes required valid bounty skulls from player inventory // note: Deducts skulls meeting value and status requirements (active or expired/claimed based on allow-expired-skulls setting)
     */
    private boolean removeSkullsFromInventory(Player player, int requiredCount, double minValue) {
        FileConfiguration config = plugin.getConfig();
        boolean allowExpiredSkulls = config.getBoolean("allow-expired-skulls", true);
        int remainingToRemove = requiredCount;

        for (ItemStack item : player.getInventory().getContents()) {
            if (remainingToRemove <= 0) break;

            if (isBountySkull(item)) {
                double skullValue = extractBountyValueFromSkull(item);
                if (skullValue >= minValue && (allowExpiredSkulls || isSkullActive(item))) {
                    int currentAmount = item.getAmount();
                    int toRemove = Math.min(currentAmount, remainingToRemove);

                    if (toRemove >= currentAmount) {
                        item.setType(Material.AIR);
                    } else {
                        item.setAmount(currentAmount - toRemove);
                    }

                    remainingToRemove -= toRemove;
                }
            }
        }

        if (!allowExpiredSkulls && remainingToRemove > 0) {
            sendMessage(player, Placeholders.apply(messagesConfig.getString("invalid-skull", "&cOnly active bounty skulls can be used for this purchase!"), PlaceholderContext.create().player(player)));
        }

        player.updateInventory();
        return remainingToRemove <= 0;
    }

    /**
     * Execute commands for successful purchase
     */
    private void executeCommands(Player player, List<String> commands) {
        for (String command : commands) {
            String processedCommand = command.replace("%player%", player.getName());

            if (processedCommand.startsWith("player:")) {
                // Execute as player
                String playerCommand = processedCommand.substring(7);
                player.performCommand(playerCommand);
            } else if (processedCommand.startsWith("console:")) {
                // Execute as console
                String consoleCommand = processedCommand.substring(8);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
            } else {
                // Default to console execution
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
        }
    }

    /**
     * Creates a configurable item with proper version compatibility
     */
    private ItemStack createConfigurableItem(Material material, String name, List<String> lore, boolean enchantmentGlow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }

            if (enchantmentGlow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Returns the inventory for this GUI
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Opens the inventory for the player
     */
    public void openInventory(Player player) {
        if (!player.equals(this.player)) {
            return; // Safety check
        }
        player.openInventory(inventory);
    }


    /**
     * Check if player meets all currency requirements
     */
    private boolean checkCurrencyRequirements(Player player, String currencyType, double price, int xpPrice, FileConfiguration config, String itemId) {
        String basePath = "shop-items." + itemId;

        switch (currencyType.toLowerCase()) {
            case "money":
                return hasEnoughMoney(player, price);

            case "xp":
                return hasEnoughXP(player, xpPrice);

            case "skulls":
                int skullCount = config.getInt(basePath + ".skull-count", 1);
                double minSkullValue = config.getDouble(basePath + ".min-skull-value", 100.0);
                return checkSkullRequirements(player, skullCount, minSkullValue);

            case "multi":
                // For multi, player only needs ONE of the currency types
                return hasEnoughMoney(player, price) ||
                        hasEnoughXP(player, xpPrice) ||
                        checkSkullRequirements(player,
                                config.getInt(basePath + ".skull-count", 1),
                                config.getDouble(basePath + ".min-skull-value", 100.0));

            case "combined":
                // NEW: For combined, player needs ALL currency types
                int combinedSkullCount = config.getInt(basePath + ".skull-count", 1);
                double combinedMinSkullValue = config.getDouble(basePath + ".min-skull-value", 100.0);

                return hasEnoughMoney(player, price) &&
                        hasEnoughXP(player, xpPrice) &&
                        checkSkullRequirements(player, combinedSkullCount, combinedMinSkullValue);

            default:
                return hasEnoughMoney(player, price);
        }
    }

    /**
     * Processes a purchase based on the selected currency type // note: Deducts money, XP, or skulls and returns true on success
     */
    private boolean processPurchase(Player player, String itemId, String currencyType, double price, int xpPrice) {
        FileConfiguration config = plugin.getHuntersDenConfig();
        String basePath = "shop-items." + itemId;

        switch (currencyType.toLowerCase()) {
            case "money":
                if (hasEnoughMoney(player, price)) {
                    plugin.getEconomy().withdrawPlayer(player, price);
                    return true;
                }
                return false;

            case "xp":
                if (hasEnoughXP(player, xpPrice)) {
                    removeExperience(player, xpPrice);
                    return true;
                }
                return false;

            case "skulls":
                int skullCount = config.getInt(basePath + ".skull-count", 1);
                double minSkullValue = config.getDouble(basePath + ".min-skull-value", 100.0);
                if (checkSkullRequirements(player, skullCount, minSkullValue)) {
                    return removeSkullsFromInventory(player, skullCount, minSkullValue);
                }
                return false;

            case "combined":
                int combinedSkullCount = config.getInt(basePath + ".skull-count", 1);
                double combinedMinSkullValue = config.getDouble(basePath + ".min-skull-value", 100.0);
                if (hasEnoughMoney(player, price) &&
                        hasEnoughXP(player, xpPrice) &&
                        checkSkullRequirements(player, combinedSkullCount, combinedMinSkullValue)) {
                    plugin.getEconomy().withdrawPlayer(player, price);
                    removeExperience(player, xpPrice);
                    removeSkullsFromInventory(player, combinedSkullCount, combinedMinSkullValue);
                    return true;
                }
                return false;

            default:
                if (hasEnoughMoney(player, price)) {
                    plugin.getEconomy().withdrawPlayer(player, price);
                    return true;
                }
                return false;
        }
    }

    /**
     * Cleans up the GUI by unregistering listeners
     * // note: Prevents memory leaks when GUI is closed
     */
    public void cleanup() {
        HandlerList.unregisterAll(this);
        plugin.getDebugManager().logDebug("Cleaned up HunterDenGUI for player: " + player.getName());
    }

    /**
     * Check if player has enough money using Vault economy
     */
    private boolean hasEnoughMoney(Player player, double amount) {
        if (plugin.getEconomy() == null) return false;
        return plugin.getEconomy().getBalance(player) >= amount;
    }

    /**
     * Check if player has enough XP
     */
    private boolean hasEnoughXP(Player player, int amount) {
        return CurrencyUtil.hasEnoughXP(player, amount);
    }

    /**
     * Removes experience from the player
     */
    private void removeExperience(Player player, int amount) {
        CurrencyUtil.removeExperience(player, amount);
    }

    /**
     * Sends a message to the player with color codes and placeholders
     * // note: Formats message with colors and placeholders, sending as BaseComponent
     */
    private void sendMessage(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                message = PlaceholderAPI.setPlaceholders(player, message);
            }
            message = ChatColor.translateAlternateColorCodes('&', message);
            BaseComponent[] components = TextComponent.fromLegacyText(message);
            player.spigot().sendMessage(components);
        }
    }
}