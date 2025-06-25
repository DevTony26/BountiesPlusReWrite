package tony26.bountiesPlus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import tony26.bountiesPlus.GUIs.BountyCancel;
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.TopGUI;
import tony26.bountiesPlus.Items.*;
import tony26.bountiesPlus.utils.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BountiesPlus extends JavaPlugin implements Listener {

    // Fields
    private static BountiesPlus instance;
    private static Economy economy;
    private BountyManager bountyManager;
    private BoostedBounty boostedBounty;
    private AnonymousBounty anonymousBounty;
    private TaxManager taxManager;
    private Frenzy frenzy;
    private Tracker tracker;
    private Jammer jammer;
    private UAV uav;
    private ManualBoost manualBoost;
    private ManualFrenzy manualFrenzy;
    private DecreaseTime decreaseTime;
    private ReverseBounty reverseBounty;
    private ItemValueCalculator itemValueCalculator;
    private TablistManager tablistManager;
    private DebugManager debugManager;
    private BountyStats bountyStats;
    private boolean bountySoundEnabled;
    private String bountySoundName;
    private float bountySoundVolume;
    private float bountySoundPitch;
    private String bountyGUITitle;
    private Map<String, ConfigWrapper> configWrappers = new HashMap<>();
    private MySQL mySQL;
    private ExecutorService executorService;
    private EventManager eventManager;
    private ShopGuiPlusIntegration shopGuiPlusIntegration;
    private ConfigWrapper CONFIG;
    private ConfigWrapper MESSAGES_CONFIG;
    private ConfigWrapper ITEM_VALUE_CONFIG;
    private ConfigWrapper ITEMS_CONFIG;
    private ConfigWrapper BOUNTY_TEAM_CHECK_CONFIG;
    private ConfigWrapper STAT_STORAGE_CONFIG;
    private ConfigWrapper BOUNTY_STORAGE_CONFIG;
    private ConfigWrapper BOUNTY_GUI_CONFIG;
    private ConfigWrapper TOP_GUI_CONFIG;
    private ConfigWrapper CREATE_GUI_CONFIG;
    private ConfigWrapper HUNTER_DEN_CONFIG;
    private ConfigWrapper PREVIEW_GUI_CONFIG;
    private ConfigWrapper ADD_ITEMS_CONFIG;
    private ConfigWrapper BOUNTY_CANCEL_CONFIG;

    // Getters
    public static BountiesPlus getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return economy;
    }

    /**
     * Retrieves the plugin configuration wrapper
     * // note: Provides access to config.yml via ConfigWrapper
     */
    public ConfigWrapper getPluginConfig() {
        return CONFIG;
    }

    public ConfigWrapper getMessagesConfig() {
        return MESSAGES_CONFIG;
    }

    public ConfigWrapper getItemValueConfig() {
        return ITEM_VALUE_CONFIG;
    }

    public ConfigWrapper getItemsConfig() {
        return ITEMS_CONFIG;
    }

    public ConfigWrapper getTeamChecksConfig() {
        return BOUNTY_TEAM_CHECK_CONFIG;
    }

    public ConfigWrapper getStatsConfig() {
        return STAT_STORAGE_CONFIG;
    }

    public ConfigWrapper getBountiesConfig() {
        return BOUNTY_STORAGE_CONFIG;
    }

    public ConfigWrapper getBountyGUIConfig() {
        return BOUNTY_GUI_CONFIG;
    }

    public ConfigWrapper getTopGUIConfig() {
        return TOP_GUI_CONFIG;
    }

    public ConfigWrapper getCreateGUIConfig() {
        return CREATE_GUI_CONFIG;
    }

    public ConfigWrapper getHuntersDenConfig() {
        return HUNTER_DEN_CONFIG;
    }

    public ConfigWrapper getPreviewGUIConfig() {
        return PREVIEW_GUI_CONFIG;
    }

    public ConfigWrapper getAddItemsGUIConfig() {
        return ADD_ITEMS_CONFIG;
    }

    public ConfigWrapper getBountyCancelGUIConfig() {
        return BOUNTY_CANCEL_CONFIG;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public BoostedBounty getBoostedBounty() {
        return boostedBounty;
    }

    public AnonymousBounty getAnonymousBounty() {
        return anonymousBounty;
    }

    public TaxManager getTaxManager() {
        return taxManager;
    }

    public Frenzy getFrenzy() {
        return frenzy;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public Jammer getJammer() {
        return jammer;
    }

    public UAV getUAV() {
        return uav;
    }

    public ManualBoost getManualBoost() {
        return manualBoost;
    }

    public ManualFrenzy getManualFrenzy() {
        return manualFrenzy;
    }

    public DecreaseTime getDecreaseTime() {
        return decreaseTime;
    }

    public ReverseBounty getReverseBounty() {
        return reverseBounty;
    }

    public ItemValueCalculator getItemValueCalculator() {
        return itemValueCalculator;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }

    public DebugManager getDebugManager() {
        return debugManager;
    }

    public boolean isBountySoundEnabled() {
        return bountySoundEnabled;
    }

    public String getBountySoundName() {
        return bountySoundName;
    }

    public float getBountySoundVolume() {
        return bountySoundVolume;
    }

    public float getBountySoundPitch() {
        return bountySoundPitch;
    }

    public MySQL getMySQL() {
        return mySQL;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ShopGuiPlusIntegration getShopGuiPlusIntegration() {
        return shopGuiPlusIntegration;
    }

    /**
     * Initializes the plugin on server startup
     * // note: Sets up DebugManager, configurations, managers, commands, and listeners
     */
    @Override
    public void onEnable() {
        instance = this;

        // Initialize DebugManager first
        debugManager = new DebugManager(this);

        // Initialize configuration files
        CONFIG = new ConfigWrapper("config.yml");
        MESSAGES_CONFIG = new ConfigWrapper("messages.yml");
        ITEM_VALUE_CONFIG = new ConfigWrapper("ItemValue.yml");
        ITEMS_CONFIG = new ConfigWrapper("items.yml");
        BOUNTY_TEAM_CHECK_CONFIG = new ConfigWrapper("BountyTeamChecks.yml");
        STAT_STORAGE_CONFIG = new ConfigWrapper("Storage/StatStorage.yml");
        BOUNTY_STORAGE_CONFIG = new ConfigWrapper("Storage/BountyStorage.yml");
        BOUNTY_GUI_CONFIG = new ConfigWrapper("GUIs/BountyGUI.yml");
        TOP_GUI_CONFIG = new ConfigWrapper("GUIs/TopGUI.yml");
        CREATE_GUI_CONFIG = new ConfigWrapper("GUIs/CreateGUI.yml");
        HUNTER_DEN_CONFIG = new ConfigWrapper("GUIs/HuntersDen.yml");
        PREVIEW_GUI_CONFIG = new ConfigWrapper("GUIs/PreviewGUI.yml");
        ADD_ITEMS_CONFIG = new ConfigWrapper("GUIs/AddItemsGUI.yml");
        BOUNTY_CANCEL_CONFIG = new ConfigWrapper("GUIs/BountyCancelGUI.yml");

        // Log config initialization
        if (debugManager != null) {
            debugManager.logDebug("[DEBUG - BountiesPlus] All configuration files initialized");
        } else {
            getLogger().info("[DEBUG - BountiesPlus] All configuration files initialized");
        }

        // Initialize managers
        eventManager = new EventManager(this);
        List<String> warnings = new ArrayList<>();
        bountyManager = new BountyManager(this, warnings);
        taxManager = new TaxManager(this);
        itemValueCalculator = new ItemValueCalculator(this);
        tablistManager = new TablistManager(this, eventManager);
        anonymousBounty = new AnonymousBounty(this);
        boostedBounty = new BoostedBounty(this, warnings);
        frenzy = new Frenzy(this, warnings);
        bountyStats = new BountyStats(this);
        mySQL = new MySQL(this);

        // Register commands and tab completer
        BountyCommand bountyCommand = new BountyCommand(this);
        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(new BountyTabCompleter());

        // Register listeners
        eventManager.register(new BountyCreationChatListener(this, eventManager));
        eventManager.register(new PlayerDeathListener(this, eventManager));
        eventManager.register(new TopGUI(this, eventManager));
        eventManager.register(new BountyGUI(this, eventManager, null));
        eventManager.register(new BountyCancel(this, eventManager));

        // Initialize PlaceholderAPI integration
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Placeholders(this).register();
            if (debugManager != null) {
                debugManager.logDebug("[DEBUG - BountiesPlus] PlaceholderAPI hooked successfully");
            }
        }

        // Initialize ShopGUIPlus integration
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            shopGuiPlusIntegration = new ShopGuiPlusIntegration(this);
            if (debugManager != null) {
                debugManager.logDebug("[DEBUG - BountiesPlus] ShopGUIPlus hooked successfully");
            }
        }

        // Initialize MySQL if enabled
        if (CONFIG.getConfig().getBoolean("mysql.enabled", false)) {
            mySQL.connect();
            if (debugManager != null) {
                debugManager.logDebug("[DEBUG - BountiesPlus] MySQL connection attempted");
            }
        }

        // Start cleanup task
        Bukkit.getScheduler().runTaskTimer(this, () -> bountyManager.cleanup(warnings), 0L, 20L * 60L * 5L);
    }

    /**
     * Called when the plugin is disabled
     * // note: Cleans up resources, saves data, and unregisters listeners
     */
    @Override
    public void onDisable() {
        // Clear filter states for shutdown
        BountyGUI.clearPlayerShowOnlyOnline();

        // Cleanup Listeners
        if (eventManager != null) {
            eventManager.unregisterAll();
            getLogger().info("Unregistered all event listeners.");
        }

        // Save Data
        saveEverything();
        getLogger().info("Saved all plugin data.");

        // Cleanup Resources
        if (executorService != null) {
            executorService.shutdown();
            getLogger().info("Shutdown async executor service.");
        }
        if (mySQL != null && mySQL.isEnabled()) {
            mySQL.closeConnection();
            getLogger().info("Closed MySQL connection.");
        }
        getLogger().info("BountiesPlus fully disabled!");
    }

    /**
     * Sets up Vault economy integration
     * // note: Initializes economy provider if Vault is present
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Reloads all configurations and data
     * // note: Refreshes configs, MySQL, and all systems
     */
    public void reloadEverything() {
        List<String> warnings = new ArrayList<>();
        reloadAllConfigs();
        mySQL.initialize();
        mySQL.migrateData();
        mySQL.migrateStatsData();
        if (bountyManager != null) bountyManager.reload(warnings);
        if (frenzy != null) frenzy.reload(warnings);
        if (boostedBounty != null) boostedBounty.reload(warnings);
        if (shopGuiPlusIntegration != null) shopGuiPlusIntegration.reload();
        if (!warnings.isEmpty()) {
            getLogger().warning("Configuration reload warnings: " + String.join("; ", warnings));
        }
        getLogger().info("All configurations reloaded.");
    }

    /**
     * Reloads all plugin configurations
     * // note: Updates all config files and dependent settings
     */
    public void reloadAllConfigs() {
        reloadConfig();
        for (ConfigWrapper wrapper : configWrappers.values()) {
            wrapper.reload();
        }
        loadBountySoundConfig();
        loadBountyGUITitle();
    }

    /**
     * Saves all data to storage
     * // note: Persists bounties, stats, and configurations
     */
    public void saveEverything() {
        saveConfig();
        for (ConfigWrapper wrapper : configWrappers.values()) {
            wrapper.save();
        }
        if (shopGuiPlusIntegration != null) shopGuiPlusIntegration.cleanup();
    }

    /**
     * Saves the stats configuration
     * // note: Persists StatStorage.yml to disk
     */
    public void saveStatsConfig() {
        ConfigWrapper statsWrapper = configWrappers.get("stats");
        if (statsWrapper != null) {
            statsWrapper.save();
        }
    }

    /**
     * Loads bounty sound configuration from config.yml
     * // note: Sets up sound effects for bounty events
     */
    public void loadBountySoundConfig() {
        FileConfiguration config = CONFIG.getConfig();
        bountySoundEnabled = config.getBoolean("bounty-sound.enabled", true);
        bountySoundName = config.getString("bounty-sound.sound", "ENTITY_BLAZE_SHOOT");
        bountySoundVolume = (float) config.getDouble("bounty-sound.volume", 1.0);
        bountySoundPitch = (float) config.getDouble("bounty-sound.pitch", 1.0);
    }

    /**
     * Loads GUI title from BountyGUI.yml
     * // note: Sets the title for the bounty GUI
     */
    public void loadBountyGUITitle() {
        FileConfiguration config = BOUNTY_GUI_CONFIG.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] Failed to load GUI title: GUIs/BountyGUI.yml is null");
            bountyGUITitle = ChatColor.translateAlternateColorCodes('&', "&dBounty Hunter");
        } else {
            bountyGUITitle = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
        }
    }

    /**
     * Loads the BountyGUI configuration file
     * // note: Initializes and reloads BountyGUI.yml with slot validation
     */
    private FileConfiguration loadBountyGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/BountyGUI");
        if (wrapper == null) {
            debugManager.logWarning("[DEBUG] GUIs/BountyGUI ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/BountyGUI", new ConfigWrapper("GUIs/BountyGUI"));
                wrapper = configWrappers.get("GUIs/BountyGUI");
            } catch (IOException e) {
                debugManager.logWarning("[DEBUG] Failed to create GUIs/BountyGUI ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            debugManager.logWarning("[DEBUG] GUIs/BountyGUI configuration is null");
            return null;
        }

        // Validate bounty-skull-slots
        List<Integer> skullSlots = config.getIntegerList("bounty-skull-slots.slots");
        List<Integer> borderSlots = config.getIntegerList("border.slots");
        Set<Integer> pluginItemSlots = new HashSet<>();
        if (config.contains("Plugin-Items")) {
            for (String key : config.getConfigurationSection("Plugin-Items").getKeys(false)) {
                int slot = config.getInt("Plugin-Items." + key + ".slot", -1);
                if (slot >= 0 && slot < 54) {
                    pluginItemSlots.add(slot);
                }
            }
        }
        int searchSingleSlot = config.getInt("search-results.single-slot", -1);
        List<Integer> invalidSlots = new ArrayList<>();
        for (int slot : skullSlots) {
            if (slot < 0 || slot >= 54 || borderSlots.contains(slot) || pluginItemSlots.contains(slot) || slot == searchSingleSlot) {
                invalidSlots.add(slot);
            }
        }
        if (!invalidSlots.isEmpty()) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid or reserved bounty-skull-slots " + invalidSlots + " in BountyGUI.yml (must be 0-53, unique, not in border or Plugin-Items)");
        }
        if (skullSlots.isEmpty()) {
            debugManager.logWarning("[DEBUG - BountyGUI] No valid bounty-skull-slots defined in BountyGUI.yml, using defaults");
            config.set("bounty-skull-slots.slots", Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34));
            wrapper.save();
        }
        if (searchSingleSlot < 0 || searchSingleSlot >= 54 || borderSlots.contains(searchSingleSlot) || pluginItemSlots.contains(searchSingleSlot) || skullSlots.contains(searchSingleSlot)) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid or reserved search-results.single-slot " + searchSingleSlot + " in BountyGUI.yml, using default 22");
            config.set("search-results.single-slot", 22);
            wrapper.save();
        }

        debugManager.logDebug("[DEBUG] Loaded BountyGUI.yml: contains Plugin-Items=" + config.contains("Plugin-Items"));
        if (config.contains("Plugin-Items")) {
            debugManager.logDebug("[DEBUG] Plugin-Items sections: " + config.getConfigurationSection("Plugin-Items").getKeys(false));
        }
        return config;
    }

    /**
     * Registers placeholders with PlaceholderAPI, with delayed retry for load order
     * // note: Initializes PlaceholderAPI integration with retry mechanism
     */
    private void registerPlaceholdersWithDelay() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Placeholders(this).register();
            getLogger().info("PlaceholderAPI detected and placeholders registered");
        } else {
            getLogger().warning("PlaceholderAPI not found, scheduling delayed registration attempt");
            new BukkitRunnable() {
                private int attempts = 0;
                private final int maxAttempts = 3;
                @Override
                public void run() {
                    attempts++;
                    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        new Placeholders(BountiesPlus.this).register();
                        getLogger().info("Delayed registration successful: PlaceholderAPI detected and placeholders registered after " + attempts + " attempt(s)");
                        cancel();
                    } else if (attempts >= maxAttempts) {
                        getLogger().severe("Failed to register placeholders after " + maxAttempts + " attempts: PlaceholderAPI not found, placeholders will not work");
                        cancel();
                    } else {
                        getLogger().info("PlaceholderAPI not yet loaded, retrying registration (attempt " + attempts + " of " + maxAttempts + ")");
                    }
                }
            }.runTaskTimer(this, 20L, 20L);
        }
    }

    /**
     * Returns an item to the player's inventory or drops it if full
     * // note: Adds item to inventory or drops at player's location
     */
    public void returnItemToPlayer(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            getLogger().info("Attempted to return null or AIR item to " + player.getName());
            return;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        player.updateInventory();
        if (!overflow.isEmpty()) {
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                getLogger().info("Dropped item " + overflowItem.getType().name() + " x" + overflowItem.getAmount() + " for " + player.getName() + " due to full inventory");
            }
            MessageUtils.sendFormattedMessage(player, "inventory-full");
        } else {
            getLogger().info("Returned item " + item.getType().name() + " x" + item.getAmount() + " to " + player.getName() + "'s inventory");
        }
    }

    /**
     * Handles player join events
     * // note: Adds player to boosted bounty and frenzy systems
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (boostedBounty != null) {
            boostedBounty.addPlayer(event.getPlayer());
        }
        if (frenzy != null) {
            frenzy.addPlayer(event.getPlayer());
        }
    }

    /**
     * Handles player quit events
     * // note: Removes player from boosted bounty, frenzy systems, and filter states
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (boostedBounty != null) {
            boostedBounty.removePlayer(event.getPlayer());
        }
        if (frenzy != null) {
            frenzy.removePlayer(event.getPlayer());
        }
        UUID playerUUID = event.getPlayer().getUniqueId();
        BountyGUI.removePlayerShowOnlyOnline(playerUUID);
        BountyGUI.removePlayerFilterHighToLow(playerUUID);
    }

    /**
     * Cleans up expired bounties and data
     * // note: Removes outdated bounties and performs periodic maintenance
     */
    private void cleanup() {
        if (bountyManager != null) {
            bountyManager.cleanupExpiredBounties();
        }
    }

    // Inner Class
    /**
     * Initializes a configuration wrapper for a specific file
     * // note: Loads or creates a config file with defaults
     */
    private class ConfigWrapper {
        private final File configFile;
        private FileConfiguration config;

        /**
         * Loads or creates a configuration file
         * // note: Initializes a YAML configuration file, copying from resources if missing
         */
        ConfigWrapper(String configName) {
            this.configFile = new File(getDataFolder(), configName);
            if (!configFile.exists()) {
                try {
                    saveResource(configName, false);
                    if (debugManager != null) {
                        debugManager.logDebug("[DEBUG - ConfigWrapper] Created default " + configName);
                    } else {
                        getLogger().info("[DEBUG - ConfigWrapper] Created default " + configName + " (DebugManager not initialized)");
                    }
                } catch (IllegalArgumentException e) {
                    if (debugManager != null) {
                        debugManager.logWarning("[DEBUG - ConfigWrapper] Failed to save default " + configName + ": " + e.getMessage());
                    } else {
                        getLogger().warning("[DEBUG - ConfigWrapper] Failed to save default " + configName + ": " + e.getMessage());
                    }
                }
            }
            this.config = YamlConfiguration.loadConfiguration(configFile);
            // Validate configuration integrity
            if (config.getKeys(false).isEmpty()) {
                if (debugManager != null) {
                    debugManager.logWarning("[DEBUG - ConfigWrapper] " + configName + " is empty, reloading default");
                } else {
                    getLogger().warning("[DEBUG - ConfigWrapper] " + configName + " is empty, reloading default");
                }
                try {
                    configFile.delete();
                    saveResource(configName, false);
                    this.config = YamlConfiguration.loadConfiguration(configFile);
                } catch (IllegalArgumentException e) {
                    if (debugManager != null) {
                        debugManager.logWarning("[DEBUG - ConfigWrapper] Failed to reload default " + configName + ": " + e.getMessage());
                    } else {
                        getLogger().warning("[DEBUG - ConfigWrapper] Failed to reload default " + configName + ": " + e.getMessage());
                    }
                }
            }
        }

        /**
         * Reloads the configuration from disk
         * // note: Refreshes the config file and reapplies defaults
         */
        public void reload() {
            try {
                config = YamlConfiguration.loadConfiguration(configFile);
                String resourcePath = configFile.getPath().replace(getDataFolder().getPath() + File.separator, "");
                InputStream defConfigStream = getResource(resourcePath);
                if (defConfigStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(defConfigStream)) {
                        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                        config.setDefaults(defConfig);
                        if (debugManager != null) {
                            debugManager.logDebug("[DEBUG] Reloaded " + resourcePath + " with defaults from " + resourcePath);
                        }
                    }
                } else {
                    if (debugManager != null) {
                        debugManager.logWarning("[DEBUG] No default resource found for " + resourcePath + " during reload");
                    }
                }
            } catch (Exception e) {
                if (debugManager != null) {
                    debugManager.logWarning("[DEBUG] Failed to reload " + configFile.getPath() + ": " + e.getMessage());
                }
            }
        }

        /**
         * Saves the configuration to disk
         * // note: Persists the current config state to file
         */
        public void save() {
            try {
                config.save(configFile);
                if (debugManager != null) {
                    debugManager.logDebug("[DEBUG] Saved configuration: " + configFile.getPath());
                }
            } catch (IOException e) {
                if (debugManager != null) {
                    debugManager.logWarning("[DEBUG] Could not save " + configFile.getPath() + ": " + e.getMessage());
                }
            }
        }

        /**
         * Gets the configuration object
         * // note: Returns the loaded FileConfiguration
         */
        public FileConfiguration getConfig() {
            return config;
        }
    }
}