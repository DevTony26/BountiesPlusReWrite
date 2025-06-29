package tony26.bountiesPlus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
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
    private boolean bountySoundEnabled;
    private String bountySoundName;
    private float bountySoundVolume;
    private float bountySoundPitch;
    private String bountyGUITitle;
    private MySQL mySQL;
    private ExecutorService executorService;
    private EventManager eventManager;
    private ShopGuiPlusIntegration shopGuiPlusIntegration;
    private BountyStats bountyStats;
    private final ConfigWrapper CONFIG;
    private final ConfigWrapper MESSAGES_CONFIG;
    private final ConfigWrapper ITEM_VALUE_CONFIG;
    private final ConfigWrapper ITEMS_CONFIG;
    private final ConfigWrapper BOUNTY_TEAM_CHECK_CONFIG;
    private final ConfigWrapper STAT_STORAGE_CONFIG;
    private final ConfigWrapper BOUNTY_STORAGE_CONFIG;
    private final ConfigWrapper BOUNTY_GUI_CONFIG;
    private final ConfigWrapper TOP_GUI_CONFIG;
    private final ConfigWrapper CREATE_GUI_CONFIG;
    private final ConfigWrapper HUNTER_DEN_CONFIG;
    private final ConfigWrapper PREVIEW_GUI_CONFIG;
    private final ConfigWrapper ADD_ITEMS_CONFIG;
    private final ConfigWrapper BOUNTY_CANCEL_CONFIG;
    private static Map<UUID, Boolean> notifySettings = new HashMap<>();


    public static BountiesPlus getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public FileConfiguration getConfig() {
        return CONFIG.getConfig();
    }

    public FileConfiguration getMessagesConfig() {
        return MESSAGES_CONFIG.getConfig();
    }

    public FileConfiguration getItemValueConfig() {
        return ITEM_VALUE_CONFIG.getConfig();
    }

    public FileConfiguration getItemsConfig() {
        return ITEMS_CONFIG.getConfig();
    }

    public FileConfiguration getTeamChecksConfig() {
        return BOUNTY_TEAM_CHECK_CONFIG.getConfig();
    }

    public FileConfiguration getStatsConfig() {
        return STAT_STORAGE_CONFIG.getConfig();
    }

    public FileConfiguration getBountiesConfig() {
        return BOUNTY_STORAGE_CONFIG.getConfig();
    }

    public FileConfiguration getBountyGUIConfig() {
        return BOUNTY_GUI_CONFIG.getConfig();
    }

    public FileConfiguration getTopGUIConfig() {
        return TOP_GUI_CONFIG.getConfig();
    }

    public FileConfiguration getCreateGUIConfig() {
        return CREATE_GUI_CONFIG.getConfig();
    }

    public FileConfiguration getHuntersDenConfig() {
        return HUNTER_DEN_CONFIG.getConfig();
    }

    public FileConfiguration getPreviewGUIConfig() {
        return PREVIEW_GUI_CONFIG.getConfig();
    }

    public FileConfiguration getAddItemsGUIConfig() {
        return ADD_ITEMS_CONFIG.getConfig();
    }

    public FileConfiguration getBountyCancelGUIConfig() {
        return BOUNTY_CANCEL_CONFIG.getConfig();
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
     * Gets the notification settings map
     * // note: Returns the map of player UUIDs to their notification toggle states
     */
    public Map<UUID, Boolean> getNotifySettings() {
        return notifySettings;
    }

    /**
     * Initializes the plugin on server startup
     * // note: Sets up DebugManager, configurations, managers, commands, and listeners
     */
    @Override
    public void onEnable() {
        instance = this;

        // Initialize DebugManager first to ensure it's available for ConfigWrapper
        debugManager = new DebugManager(this);

        // Initialize MessageUtils to load messages.yml
        MessageUtils.initialize(this);

        // Initialize Vault economy
        if (!setupEconomy()) {
            getLogger().warning("[DEBUG - BountiesPlus] Failed to hook into Vault economy");
        } else {
            debugManager.logDebug("[DEBUG - BountiesPlus] Vault economy hooked successfully");
        }

        // Initialize managers
        eventManager = new EventManager(this);
        List<String> warnings = new ArrayList<>();
        mySQL = new MySQL(this); // Moved before bountyManager to prevent null reference
        bountyManager = new BountyManager(this, warnings);
        taxManager = new TaxManager(this);
        itemValueCalculator = new ItemValueCalculator(this);
        tablistManager = new TablistManager(this, eventManager);
        anonymousBounty = new AnonymousBounty(this);
        boostedBounty = new BoostedBounty(this, warnings);
        frenzy = new Frenzy(this, warnings);
        bountyStats = new BountyStats(this);
        tracker = new Tracker(this, eventManager);
        jammer = new Jammer(this, eventManager);
        uav = new UAV(this, eventManager);
        manualBoost = new ManualBoost(this, eventManager);
        manualFrenzy = new ManualFrenzy(this, eventManager);
        decreaseTime = new DecreaseTime(this, eventManager);
        reverseBounty = new ReverseBounty(this, eventManager);

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
        if (getConfig().getBoolean("mysql.enabled", false)) {
            mySQL.initialize();
            if (debugManager != null) {
                debugManager.logDebug("[DEBUG - BountiesPlus] MySQL connection attempted");
            }
        }

        // Start cleanup task
        Bukkit.getScheduler().runTaskTimer(this, () -> bountyManager.cleanup(), 0L, 20L * 60L * 5L);
    }

    /**
     * Constructs the BountiesPlus plugin
     * // note: Initializes configuration wrappers
     */
    public BountiesPlus() {
        this.CONFIG = new ConfigWrapper("config.yml");
        this.MESSAGES_CONFIG = new ConfigWrapper("messages.yml");
        this.ITEM_VALUE_CONFIG = new ConfigWrapper("ItemValue.yml");
        this.ITEMS_CONFIG = new ConfigWrapper("items.yml");
        this.BOUNTY_TEAM_CHECK_CONFIG = new ConfigWrapper("BountyTeamChecks.yml");
        this.STAT_STORAGE_CONFIG = new ConfigWrapper("Storage/StatStorage.yml");
        this.BOUNTY_STORAGE_CONFIG = new ConfigWrapper("Storage/BountyStorage.yml");
        this.BOUNTY_GUI_CONFIG = new ConfigWrapper("GUIs/BountyGUI.yml");
        this.TOP_GUI_CONFIG = new ConfigWrapper("GUIs/TopGUI.yml");
        this.CREATE_GUI_CONFIG = new ConfigWrapper("GUIs/CreateGUI.yml");
        this.HUNTER_DEN_CONFIG = new ConfigWrapper("GUIs/HuntersDen.yml");
        this.PREVIEW_GUI_CONFIG = new ConfigWrapper("GUIs/PreviewGUI.yml");
        this.ADD_ITEMS_CONFIG = new ConfigWrapper("GUIs/AddItemsGUI.yml");
        this.BOUNTY_CANCEL_CONFIG = new ConfigWrapper("GUIs/BountyCancelGUI.yml");
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
     * Saves the stats configuration
     * // note: Persists StatStorage.yml to disk
     */
    public void saveStatsConfig() {
        STAT_STORAGE_CONFIG.save();
    }

    /**
     * Reloads all plugin configurations and systems
     * // note: Refreshes all managers and configurations from disk
     */
    public void reloadEverything() {
        try {
            reloadConfig();
            MessageUtils.reloadMessages();
            getBountyManager().reload(new ArrayList<>());
            FileConfiguration bountyConfig = getBountiesConfig();
            notifySettings.clear();
            if (bountyConfig.contains("players")) {
                for (String uuidStr : bountyConfig.getConfigurationSection("players").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        boolean notifyEnabled = bountyConfig.getBoolean("players." + uuidStr + ".notify-enabled", true);
                        notifySettings.put(uuid, notifyEnabled);
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("[DEBUG - BountiesPlus] Invalid UUID in BountyStorage.yml: " + uuidStr);
                    }
                }
            }
            getLogger().info("[DEBUG - BountiesPlus] Reloaded all configurations and data");
        } catch (Exception e) {
            getLogger().warning("[DEBUG - BountiesPlus] Error reloading configurations: " + e.getMessage());
        }
    }

    /**
     * Reloads all plugin configurations
     * // note: Updates all config files and dependent settings
     */
    public void reloadAllConfigs() {
        reloadConfig();
        CONFIG.reload();
        MESSAGES_CONFIG.reload();
        ITEM_VALUE_CONFIG.reload();
        ITEMS_CONFIG.reload();
        BOUNTY_TEAM_CHECK_CONFIG.reload();
        STAT_STORAGE_CONFIG.reload();
        BOUNTY_STORAGE_CONFIG.reload();
        BOUNTY_GUI_CONFIG.reload();
        TOP_GUI_CONFIG.reload();
        CREATE_GUI_CONFIG.reload();
        HUNTER_DEN_CONFIG.reload();
        PREVIEW_GUI_CONFIG.reload();
        ADD_ITEMS_CONFIG.reload();
        BOUNTY_CANCEL_CONFIG.reload();
        loadBountySoundConfig();
        loadBountyGUITitle();
    }

    /**
     * Loads the BountyGUI configuration file
     * // note: Initializes and reloads BountyGUI.yml with slot validation
     */
    private FileConfiguration loadBountyGUIConfig() {
        FileConfiguration config = BOUNTY_GUI_CONFIG.getConfig();
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
            BOUNTY_GUI_CONFIG.save();
        }
        if (searchSingleSlot < 0 || searchSingleSlot >= 54 || borderSlots.contains(searchSingleSlot) || pluginItemSlots.contains(searchSingleSlot) || skullSlots.contains(searchSingleSlot)) {
            debugManager.logWarning("[DEBUG - BountyGUI] Invalid or reserved search-results.single-slot " + searchSingleSlot + " in BountyGUI.yml, using default 22");
            config.set("search-results.single-slot", 22);
            BOUNTY_GUI_CONFIG.save();
        }

        debugManager.logDebug("[DEBUG] Loaded BountyGUI.yml: contains Plugin-Items=" + config.contains("Plugin-Items"));
        if (config.contains("Plugin-Items")) {
            debugManager.logDebug("[DEBUG] Plugin-Items sections: " + config.getConfigurationSection("Plugin-Items").getKeys(false));
        }
        return config;
    }
    /**
     * Saves all plugin data to disk
     * // note: Persists configurations, bounties, and player settings
     */
    public void saveEverything() {
        try {
            saveConfig();
            getBountyManager().saveBounties();
            getStatsConfig().save(new File(getDataFolder(), "Storage/StatStorage.yml"));
            FileConfiguration bountyConfig = getBountiesConfig();
            for (Map.Entry<UUID, Boolean> entry : notifySettings.entrySet()) {
                bountyConfig.set("players." + entry.getKey() + ".notify-enabled", entry.getValue());
            }
            BOUNTY_STORAGE_CONFIG.save(); // Save BountyStorage.yml directly
            getLogger().info("[DEBUG - BountiesPlus] Saved all configurations and data");
        } catch (Exception e) {
            getLogger().warning("[DEBUG - BountiesPlus] Error saving configurations: " + e.getMessage());
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
     * Called when the plugin is disabled
     * // note: Cleans up resources, saves data, and unregisters listeners
     */
    @Override
    public void onDisable() {
        // Clear filter states for shutdown
        BountyGUI.clearPlayerShowOnlyOnline();
        BountyGUI.clearPlayerFilterHighToLow();

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
     * Loads bounty sound configuration from config.yml
     * // note: Sets up sound effects for bounty events
     */
    public void loadBountySoundConfig() {
        bountySoundEnabled = getConfig().getBoolean("bounty-sound.enabled", true);
        bountySoundName = getConfig().getString("bounty-sound.sound", "ENTITY_BLAZE_SHOOT");
        bountySoundVolume = (float) getConfig().getDouble("bounty-sound.volume", 1.0);
        bountySoundPitch = (float) getConfig().getDouble("bounty-sound.pitch", 1.0);
    }

    /**
     * Loads GUI title from BountyGUI.yml
     * // note: Sets the title for the bounty GUI
     */
    public void loadBountyGUITitle() {
        FileConfiguration config = getBountyGUIConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] Failed to load GUI title: GUIs/BountyGUI.yml is null");
            bountyGUITitle = ChatColor.translateAlternateColorCodes('&', "&dBounty Hunter");
        } else {
            bountyGUITitle = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
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

    private class ConfigWrapper {
        private final String name;
        private final String fullPath;
        private File file;
        private FileConfiguration config;

        /**
         * Initializes a configuration wrapper for a specific file
         * // note: Loads or creates a config file with defaults
         */
        private ConfigWrapper(String configName) {
            this.name = configName;
            this.fullPath = configName;
            this.file = new File(getDataFolder(), configName);
            if (!file.exists()) {
                try {
                    saveResource(configName, false);
                    if (BountiesPlus.this.debugManager != null) {
                        BountiesPlus.this.debugManager.logDebug("[DEBUG - ConfigWrapper] Created default " + configName);
                    } else {
                        getLogger().info("[DEBUG - ConfigWrapper] Created default " + configName + " (DebugManager not initialized)");
                    }
                } catch (IllegalArgumentException e) {
                    if (BountiesPlus.this.debugManager != null) {
                        BountiesPlus.this.debugManager.logWarning("[DEBUG - ConfigWrapper] Failed to save default " + configName + ": " + e.getMessage());
                    } else {
                        getLogger().warning("[DEBUG - ConfigWrapper] Failed to save default " + configName + ": " + e.getMessage());
                    }
                }
            }
            this.config = YamlConfiguration.loadConfiguration(file);
            // Validate configuration integrity
            if (config.getKeys(false).isEmpty()) {
                if (BountiesPlus.this.debugManager != null) {
                    BountiesPlus.this.debugManager.logWarning("[DEBUG - ConfigWrapper] " + configName + " is empty, reloading default");
                } else {
                    getLogger().warning("[DEBUG - ConfigWrapper] " + configName + " is empty, reloading default");
                }
                try {
                    file.delete();
                    saveResource(configName, false);
                    this.config = YamlConfiguration.loadConfiguration(file);
                } catch (IllegalArgumentException e) {
                    if (BountiesPlus.this.debugManager != null) {
                        BountiesPlus.this.debugManager.logWarning("[DEBUG - ConfigWrapper] Failed to reload default " + configName + ": " + e.getMessage());
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
                config = YamlConfiguration.loadConfiguration(file);
                String resourcePath = fullPath;
                InputStream defConfigStream = getResource(resourcePath);
                if (defConfigStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(defConfigStream)) {
                        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                        config.setDefaults(defConfig);
                        if (BountiesPlus.this.debugManager != null) {
                            BountiesPlus.this.debugManager.logDebug("[DEBUG] Reloaded " + fullPath + " with defaults from " + resourcePath);
                        } else {
                            getLogger().info("[DEBUG] Reloaded " + fullPath + " with defaults from " + resourcePath);
                        }
                    }
                } else {
                    if (BountiesPlus.this.debugManager != null) {
                        BountiesPlus.this.debugManager.logWarning("[DEBUG] No default resource found for " + resourcePath + " during reload");
                    } else {
                        getLogger().warning("[DEBUG] No default resource found for " + resourcePath + " during reload");
                    }
                }
            } catch (Exception e) {
                if (BountiesPlus.this.debugManager != null) {
                    BountiesPlus.this.debugManager.logWarning("[DEBUG] Failed to reload " + fullPath + ": " + e.getMessage());
                } else {
                    getLogger().warning("[DEBUG] Failed to reload " + fullPath + ": " + e.getMessage());
                }
            }
        }

        /**
         * Saves the configuration to disk
         * // note: Persists the current config state to file
         */
        public void save() {
            try {
                config.save(file);
                if (BountiesPlus.this.debugManager != null) {
                    BountiesPlus.this.debugManager.logDebug("[DEBUG] Saved configuration: " + fullPath);
                } else {
                    getLogger().info("[DEBUG] Saved configuration: " + fullPath);
                }
            } catch (IOException e) {
                if (BountiesPlus.this.debugManager != null) {
                    BountiesPlus.this.debugManager.logWarning("[DEBUG] Could not save " + fullPath + ": " + e.getMessage());
                } else {
                    getLogger().warning("[DEBUG] Could not save " + fullPath + ": " + e.getMessage());
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