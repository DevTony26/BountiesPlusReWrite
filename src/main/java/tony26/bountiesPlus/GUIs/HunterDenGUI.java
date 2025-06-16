package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.CurrencyUtil;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;
import tony26.bountiesPlus.utils.VersionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HunterDenGUI implements InventoryHolder, Listener {

    private final Inventory inventory;
    private final Player player;
    private final BountiesPlus plugin;
    private String GUI_TITLE;

    public HunterDenGUI(Player player) {
        this.player = player;
        this.plugin = BountiesPlus.getInstance();

        FileConfiguration config = plugin.getHuntersDenConfig(); // Fixed method name
        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&',
                config.getString("gui-title", "&4&l⚔ Hunter's Den ⚔"));

        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE);

        // Register this as a listener
        BountiesPlus.getInstance().getServer().getPluginManager().registerEvents(this, BountiesPlus.getInstance());

        initializeGUI();
    }

    private void initializeGUI() {
        FileConfiguration config = plugin.getHuntersDenConfig(); // Fixed method name

        // Add borders
        addBorders(config);

        // Add shop items
        addShopItems(config);

        // Add extra items
        addExtraItems(config);

        // Add back button
        addBackButton(config);
    }

    private void addBorders(FileConfiguration config) {
        if (!config.getBoolean("border.enabled", true)) {
            return;
        }

        String materialName = config.getString("border.material", "RED_STAINED_GLASS_PANE");
        String name = config.getString("border.name", " ");
        List<String> lore = config.getStringList("border.lore");
        boolean enchantmentGlow = config.getBoolean("border.enchantment-glow", false);
        List<Integer> borderSlots = config.getIntegerList("border.slots");

        // Default border slots if none configured
        if (borderSlots.isEmpty()) {
            for (int i = 0; i < 9; i++) borderSlots.add(i);
            for (int i = 45; i < 54; i++) borderSlots.add(i);
            for (int i = 9; i < 45; i += 9) borderSlots.add(i);
            for (int i = 17; i < 45; i += 9) borderSlots.add(i);
        }

        Material borderMaterial = VersionUtils.getMaterialSafely(materialName, "GLASS_PANE");
        ItemStack borderItem = createConfigurableItem(borderMaterial, name, lore, enchantmentGlow);

        for (int slot : borderSlots) {
            if (slot >= 0 && slot < 54) {
                inventory.setItem(slot, borderItem);
            }
        }
    }

    /**
     * Check if player has enough bounty skulls with minimum value
     */
    private boolean checkSkullRequirements(Player player, int requiredCount, double minValue) {
        int validSkullCount = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isBountySkull(item)) {
                double skullValue = extractBountyValueFromSkull(item);
                if (skullValue >= minValue) {
                    validSkullCount += item.getAmount();
                    if (validSkullCount >= requiredCount) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Remove required skulls from player inventory
     */
    private boolean removeSkullsFromInventory(Player player, int requiredCount, double minValue) {
        int remainingToRemove = requiredCount;

        for (ItemStack item : player.getInventory().getContents()) {
            if (remainingToRemove <= 0) break;

            if (isBountySkull(item)) {
                double skullValue = extractBountyValueFromSkull(item);
                if (skullValue >= minValue) {
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
     * Check if item is a bounty skull // note: Identifies bounty skulls by lore
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
        for (String line : lore) {
            if (line.contains("Bounty Value:") || line.contains("BOUNTY_SKULL")) {
                return true;
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
     * Adds extra decorative items to the GUI // note: Populates non-purchasable decorative items with placeholders
     */
    private void addExtraItems(FileConfiguration config) {
        Set<String> extraItemKeys = config.getConfigurationSection("extra-items") != null ?
                config.getConfigurationSection("extra-items").getKeys(false) : null;

        if (extraItemKeys == null) {
            return;
        }

        for (String itemId : extraItemKeys) {
            String basePath = "extra-items." + itemId;
            int slot = config.getInt(basePath + ".slot", -1);

            if (slot >= 0 && slot < 54) {
                String materialName = config.getString(basePath + ".material", "STONE");
                String name = config.getString(basePath + ".name", "&fExtra Item");
                List<String> lore = config.getStringList(basePath + ".lore");
                boolean enchantmentGlow = config.getBoolean(basePath + ".enchantment-glow", false);

                Material material = VersionUtils.getMaterialSafely(materialName, "STONE");
                if (material == null) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for extra item " + itemId + ", using STONE");
                    material = Material.STONE;
                }

                // Create PlaceholderContext for the player
                PlaceholderContext context = PlaceholderContext.create()
                        .player(player);

                // Apply placeholders to name and lore
                name = Placeholders.apply(name, context);
                lore = Placeholders.apply(lore, context);

                ItemStack item = createConfigurableItem(material, name, lore, enchantmentGlow);

                if (inventory.getItem(slot) != null) {
                    plugin.getLogger().warning("Slot " + slot + " for extra item " + itemId + " is already occupied");
                } else {
                    inventory.setItem(slot, item);
                }
            } else {
                plugin.getLogger().warning("Invalid slot " + slot + " for extra item " + itemId + " in HuntersDen.yml");
            }
        }
    }

    /**
     * Adds shop items to the GUI from configuration // note: Populates shop items with configurable properties and placeholders
     */
    private void addShopItems(FileConfiguration config) {
        Set<String> shopItemKeys = config.getConfigurationSection("shop-items") != null ?
                config.getConfigurationSection("shop-items").getKeys(false) : null;

        if (shopItemKeys == null) {
            plugin.getLogger().warning("No shop-items section found in HuntersDen.yml");
            return;
        }

        for (String itemId : shopItemKeys) {
            String basePath = "shop-items." + itemId;
            int slot = config.getInt(basePath + ".slot", -1);

            if (slot >= 0 && slot < 54) {
                String materialName = config.getString(basePath + ".material", "STONE");
                String name = config.getString(basePath + ".name", "&fShop Item");
                List<String> lore = config.getStringList(basePath + ".lore");
                boolean enchantmentGlow = config.getBoolean(basePath + ".enchantment-glow", false);

                Material material = VersionUtils.getMaterialSafely(materialName, "STONE");
                if (material == null) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for shop item " + itemId + ", using STONE");
                    material = Material.STONE;
                }

                // Create PlaceholderContext for the player
                PlaceholderContext context = PlaceholderContext.create()
                        .player(player)
                        .moneyValue(config.getDouble(basePath + ".price", 0.0))
                        .expValue(config.getInt(basePath + ".xp-price", 0))
                        .itemCount(config.getInt(basePath + ".skull-count", 1));

                // Apply placeholders to name and lore
                name = Placeholders.apply(name, context);
                lore = Placeholders.apply(lore, context);

                ItemStack item = createConfigurableItem(material, name, lore, enchantmentGlow);

                if (inventory.getItem(slot) != null) {
                    plugin.getLogger().warning("Slot " + slot + " for shop item " + itemId + " is already occupied");
                } else {
                    inventory.setItem(slot, item);
                }
            } else {
                plugin.getLogger().warning("Invalid slot " + slot + " for shop item " + itemId + " in HuntersDen.yml");
            }
        }
    }

    /**
     * Adds the back button to the GUI // note: Creates a back button to return to the main bounty GUI
     */
    private void addBackButton(FileConfiguration config) {
        int slot = config.getInt("back-button.slot", 49);
        String materialName = config.getString("back-button.material", "BARRIER");
        String name = config.getString("back-button.name", "&c&lBack to Bounties");
        List<String> lore = config.getStringList("back-button.lore");
        boolean enchantmentGlow = config.getBoolean("back-button.enchantment-glow", false);

        Material material = VersionUtils.getMaterialSafely(materialName, "BARRIER");
        if (material == null) {
            plugin.getLogger().warning("Invalid back button material '" + materialName + "' in HuntersDen.yml, using BARRIER");
            material = Material.BARRIER;
        }

        // Create PlaceholderContext for the player
        PlaceholderContext context = PlaceholderContext.create()
                .player(player);

        // Apply placeholders to name and lore
        name = Placeholders.apply(name, context);
        lore = Placeholders.apply(lore, context);

        ItemStack backButton = createConfigurableItem(material, name, lore, enchantmentGlow);

        if (slot >= 0 && slot < 54) {
            if (inventory.getItem(slot) != null) {
                plugin.getLogger().warning("Slot " + slot + " for back button is already occupied");
            } else {
                inventory.setItem(slot, backButton);
            }
        } else {
            plugin.getLogger().warning("Invalid slot " + slot + " for back button in HuntersDen.yml");
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

    public void openInventory(Player player) {
        if (!player.equals(this.player)) {
            return; // Safety check
        }
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Handles inventory click events for the GUI // note: Processes clicks on shop items, extra items, and back button
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!event.getWhoClicked().equals(this.player)) return;

        event.setCancelled(true);

        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        FileConfiguration config = plugin.getHuntersDenConfig();

        // Check for back button by slot
        int backButtonSlot = config.getInt("back-button.slot", 49);
        if (slot == backButtonSlot) {
            clickingPlayer.closeInventory();
            BountyGUI.openBountyGUI(clickingPlayer, false, 0);
            return;
        }

        // Check for shop items
        for (String itemId : config.getConfigurationSection("shop-items").getKeys(false)) {
            int itemSlot = config.getInt("shop-items." + itemId + ".slot", -1);
            if (slot == itemSlot) {
                handleShopItemClick(clickingPlayer, itemId, config);
                return;
            }
        }

        // Check for extra items
        for (String itemId : config.getConfigurationSection("extra-items").getKeys(false)) {
            int itemSlot = config.getInt("extra-items." + itemId + ".slot", -1);
            if (slot == itemSlot) {
                handleExtraItemClick(clickingPlayer, itemId, clickedItem);
                return;
            }
        }
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
     * Process the purchase based on currency type
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
                // NEW: Process combined purchase - charge ALL currencies
                int combinedSkullCount = config.getInt(basePath + ".skull-count", 1);
                double combinedMinSkullValue = config.getDouble(basePath + ".min-skull-value", 100.0);

                // Check if player has all required currencies
                if (hasEnoughMoney(player, price) &&
                        hasEnoughXP(player, xpPrice) &&
                        checkSkullRequirements(player, combinedSkullCount, combinedMinSkullValue)) {

                    // Charge all three currencies
                    plugin.getEconomy().withdrawPlayer(player, price);
                    removeExperience(player, xpPrice);
                    removeSkullsFromInventory(player, combinedSkullCount, combinedMinSkullValue);
                    return true;
                }
                return false;

            case "multi":
                // For multi-currency, we need to determine which currency was used
                // This would need additional logic based on click type
                return false;

            default:
                if (hasEnoughMoney(player, price)) {
                    plugin.getEconomy().withdrawPlayer(player, price);
                    return true;
                }
                return false;
        }
    }

    // Update the handleShopItemClick method to use the corrected signature:
    private void handleShopItemClick(Player player, String itemId, FileConfiguration config) {
        String basePath = "shop-items." + itemId;
        String currencyType = config.getString(basePath + ".currency-type", "money");
        double price = config.getDouble(basePath + ".price", 0.0);
        int xpPrice = config.getInt(basePath + ".xp-price", 0);

        // Check currency requirements (pass itemId parameter)
        if (!checkCurrencyRequirements(player, currencyType, price, xpPrice, config, itemId)) {
            // Send insufficient funds message
            String insufficientMessage = config.getString(basePath + ".insufficient-message", "&cInsufficient funds!");
            sendMessage(player, insufficientMessage);
            return;
        }

        // Process purchase
        if (processPurchase(player, itemId, currencyType, price, xpPrice)) {
            // Execute success commands
            List<String> commands = config.getStringList(basePath + ".commands");
            executeCommands(player, commands);

            // Send success message
            String successMessage = config.getString(basePath + ".success-message", "&aPurchase successful!");
            sendMessage(player, successMessage);
        }
    }

    private void sendMessage(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    /**
     * Check if player has enough money using Vault economy
     */
    private boolean hasEnoughMoney(Player player, double amount) {
        if (plugin.getEconomy() == null) return false;
        return plugin.getEconomy().getBalance(player) >= amount;
    }

    private boolean hasEnoughXP(Player player, int amount) {
        return CurrencyUtil.hasEnoughXP(player, amount);
    }

    private void removeExperience(Player player, int amount) {
        CurrencyUtil.removeExperience(player, amount);
    }

}