package tony26.bountiesPlus.utils;

import com.cryptomorin.xseries.XMaterial;
import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.GUIs.HunterDenGUI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages integration with ShopGUIPlus for the Hunters Den
 * // note: Handles hooking into ShopGUIPlus and opening the Hunters Den shop
 */
public class ShopGuiPlusIntegration {
    private final BountiesPlus plugin;
    private boolean isShopGuiPlusEnabled;
    private File shopFile;
    private FileConfiguration shopConfig;

    /**
     * Initializes ShopGUIPlus integration
     * // note: Checks for ShopGUIPlus, loads or creates shop configuration
     */
    public ShopGuiPlusIntegration(BountiesPlus plugin) {
        this.plugin = plugin;
        this.isShopGuiPlusEnabled = Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus");
        if (isShopGuiPlusEnabled) {
            plugin.getLogger().info("[DEBUG] ShopGUIPlus detected, initializing integration");
            initializeShopConfig();
        } else {
            plugin.getLogger().warning("[DEBUG] ShopGUIPlus not found, Hunters Den will use default GUI");
        }
    }

    /**
     * Checks if ShopGUIPlus integration is enabled
     * // note: Returns true if ShopGUIPlus is installed and use-shop-gui-plus is true
     */
    public boolean isEnabled() {
        return isShopGuiPlusEnabled && plugin.getConfig().getBoolean("shop.use-shop-gui-plus", false);
    }

    /**
     * Initializes the ShopGUIPlus shop configuration
     * // note: Creates or loads HuntersDen.yml for ShopGUIPlus, mapping items from HuntersDen.yml
     */
    private void initializeShopConfig() {
        shopFile = new File(plugin.getDataFolder(), "shops/HuntersDen.yml");
        if (!shopFile.exists()) {
            try {
                shopFile.getParentFile().mkdirs();
                shopFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[DEBUG] Failed to create shops/HuntersDen.yml: " + e.getMessage());
                isShopGuiPlusEnabled = false;
                return;
            }
        }
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        convertHuntersDenItems();
        saveShopConfig();
    }

    /**
     * Converts items from HuntersDen.yml to ShopGUIPlus format
     * // note: Maps Plugin-Items and Custom-Items to ShopGUIPlus shop format
     */
    private void convertHuntersDenItems() {
        FileConfiguration huntersDenConfig = plugin.getHuntersDenConfig();
        shopConfig.set("shopName", "HuntersDen");
        shopConfig.set("shopDisplayName", huntersDenConfig.getString("gui-title", "&dHunters Den"));
        shopConfig.set("shopSize", huntersDenConfig.getInt("size", 54));

        List<String> items = new ArrayList<>();
        ConfigurationSection pluginItems = huntersDenConfig.getConfigurationSection("Plugin-Items");
        if (pluginItems != null) {
            for (String key : pluginItems.getKeys(false)) {
                String path = "Plugin-Items." + key;
                ItemStack item = createItemFromConfig(huntersDenConfig, path);
                if (item != null) {
                    addShopItem(key, item, huntersDenConfig.getDouble(path + ".price", 100.0));
                }
            }
        }

        ConfigurationSection customItems = huntersDenConfig.getConfigurationSection("Custom-Items");
        if (customItems != null) {
            for (String key : customItems.getKeys(false)) {
                String path = "Custom-Items." + key;
                ItemStack item = createItemFromConfig(huntersDenConfig, path);
                if (item != null) {
                    addShopItem(key, item, huntersDenConfig.getDouble(path + ".price", 100.0));
                }
            }
        }
    }

    /**
     * Creates an ItemStack from a configuration section
     * // note: Builds item with material, name, lore, and enchantments from config
     */
    private ItemStack createItemFromConfig(FileConfiguration config, String path) {
        String materialName = config.getString(path + ".material", "STONE");
        ItemStack item = VersionUtils.getXMaterialItemStack(materialName);
        if (item.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            plugin.getLogger().warning("[DEBUG] Invalid material in " + path + ": " + materialName + ", using STONE");
        }
        return item;
    }

    /**
     * Adds an item to the ShopGUIPlus configuration
     * // note: Adds item with price to the shop configuration
     */
    private void addShopItem(String key, ItemStack item, double price) {
        String path = "items." + key;
        shopConfig.set(path + ".type", "item");
        shopConfig.set(path + ".item.material", item.getType().name());
        shopConfig.set(path + ".item.amount", item.getAmount());
        shopConfig.set(path + ".buyPrice", price);
        shopConfig.set(path + ".sellPrice", null); // Hunters Den items are typically not sellable
    }

    /**
     * Saves the ShopGUIPlus shop configuration
     * // note: Persists shops/HuntersDen.yml to disk
     */
    private void saveShopConfig() {
        try {
            shopConfig.save(shopFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[DEBUG] Failed to save shops/HuntersDen.yml: " + e.getMessage());
        }
    }

    /**
     * Opens the ShopGUIPlus shop or falls back to default GUI
     * // note: Attempts to open ShopGUIPlus shop, falls back to HunterDenGUI if disabled or unavailable
     */
    public void openShop(Player player) {
        if (!plugin.getConfig().getBoolean("shop.use-shop-gui-plus", false)) {
            HunterDenGUI gui = new HunterDenGUI(player, plugin.getEventManager());
            gui.openInventory(player);
            plugin.getDebugManager().logDebug("[ShopGuiPlusIntegration] Opened default HunterDenGUI for " + player.getName());
            return;
        }
        try {
            ShopGuiPlusApi.openShop(player, "HuntersDen", 1);
            plugin.getDebugManager().logDebug("[ShopGuiPlusIntegration] Opened ShopGUIPlus shop 'HuntersDen' for " + player.getName());
        } catch (Exception e) {
            PlaceholderContext context = PlaceholderContext.create().player(player).error(e.getMessage());
            String errorMessage = plugin.getMessagesConfig().getString("shop-gui-plus-error", "&c&lShopGUIPlus Error: &7Failed to open ShopGUIPlus shop. Using default Hunters Den.");
            player.sendMessage(Placeholders.apply(errorMessage, context));
            HunterDenGUI gui = new HunterDenGUI(player, plugin.getEventManager());
            gui.openInventory(player);
            plugin.getDebugManager().logDebug("[ShopGuiPlusIntegration] Failed to open ShopGUIPlus shop for " + player.getName() + ": " + e.getMessage() + ", opened default HunterDenGUI");
        }
    }

    /**
     * Processes a purchase with bounty skulls
     * // note: Validates and deducts skulls for ShopGUIPlus purchases
     */
    private boolean processSkullPurchase(Player player, String itemId, int skullCount, double minSkullValue) {
        FileConfiguration config = plugin.getHuntersDenConfig();
        boolean allowExpiredSkulls = plugin.getConfig().getBoolean("shop.allow-expired-skulls", true);
        int validSkulls = 0;
        List<ItemStack> toRemove = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && HunterDenGUI.isBountySkull(item)) {
                double skullValue = HunterDenGUI.extractBountyValueFromSkull(item);
                if (skullValue >= minSkullValue && (allowExpiredSkulls || plugin.getBountyManager().hasBounty(SkullUtils.getKilledPlayerUUID(item)))) {
                    validSkulls += item.getAmount();
                    toRemove.add(item);
                    if (validSkulls >= skullCount) {
                        break;
                    }
                }
            }
        }

        if (validSkulls < skullCount) {
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .itemCount(skullCount)
                    .withAmount(minSkullValue);
            String message = config.getString("messages.insufficient-skulls", "&c&lYou need %required_count% bounty skulls (minimum $%min_value% each)!");
            player.sendMessage(Placeholders.apply(message, context));
            plugin.getDebugManager().logDebug("[ShopGuiPlusIntegration] Insufficient skulls for " + player.getName() + ": needed " + skullCount + ", found " + validSkulls);
            return false;
        }

        int remaining = skullCount;
        for (ItemStack item : toRemove) {
            if (remaining <= 0) break;
            int amount = item.getAmount();
            int toRemoveAmount = Math.min(amount, remaining);
            if (toRemoveAmount == amount) {
                item.setType(XMaterial.AIR.parseMaterial());
            } else {
                item.setAmount(amount - toRemoveAmount);
            }
            remaining -= toRemoveAmount;
        }

        player.updateInventory();
        plugin.getDebugManager().logDebug("[ShopGuiPlusIntegration] Successfully processed skull purchase for " + player.getName() + ": " + skullCount + " skulls");
        return true;
    }

    /**
     * Reloads the shop configuration
     * // note: Reloads shops/HuntersDen.yml and reprocesses items
     */
    public void reload() {
        if (isShopGuiPlusEnabled) {
            initializeShopConfig();
        }
    }

    /**
     * Cleans up on plugin disable
     * // note: Saves shop configuration
     */
    public void cleanup() {
        if (isShopGuiPlusEnabled) {
            saveShopConfig();
        }
    }
}