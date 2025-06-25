// file: src/main/java/tony26/bountiesPlus/BountiesPlus.java
package tony26.bountiesPlus;
import java.io.IOException;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import tony26.bountiesPlus.Items.*;
import tony26.bountiesPlus.utils.*;
import tony26.bountiesPlus.utils.ShopGuiPlusIntegration;
import tony26.bountiesPlus.GUIs.BountyGUI;

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
    private Map<String, ConfigWrapper> configWrappers = new HashMap<>();
    private MySQL mySQL;
    private ExecutorService executorService;
    private EventManager eventManager;
    private ShopGuiPlusIntegration shopGuiPlusIntegration;

    public ShopGuiPlusIntegration getShopGuiPlusIntegration() {
        return shopGuiPlusIntegration;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    /**
     * Initializes a configuration wrapper for a specific file
     * // note: Loads or creates a config file with defaults
     */
    private class ConfigWrapper {
        private final String name;
        private final String fullPath;
        private File file;
        private FileConfiguration config;

        /**
         * Initializes a configuration wrapper for a specific file
         * // note: Loads or creates a config file with defaults from resources/GUIs/
         */
        /**
         * Initializes a configuration wrapper for a specific file
         * // note: Loads or creates a config file with defaults from resources/
         */
        public ConfigWrapper(String name) throws IOException {
            this.name = name;
            // Normalize path for file system (e.g., Storage/BountyStorage -> Storage/BountyStorage.yml)
            this.fullPath = name.endsWith(".yml") ? name : name + ".yml";
            // Adjust resource path to include 'resources/' prefix as per JAR structure
            String resourcePath = "resources/" + fullPath;
            // Create file in appropriate subfolder (e.g., plugins/BountiesPlus/Storage/)
            file = new File(getDataFolder(), fullPath.replace("/", File.separator));
            File parentDir = file.getParentFile();
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                debugManager.logWarning("[DEBUG] Failed to create directory: " + parentDir.getPath());
                throw new IOException("Failed to create directory: " + parentDir.getPath());
            }

            // Check if file exists; if not, attempt to copy from resources
            if (!file.exists()) {
                InputStream resourceStream = getResource(resourcePath);
                if (resourceStream == null) {
                    debugManager.logWarning("[DEBUG] Resource not found in JAR: " + resourcePath + ", creating empty file for " + fullPath);
                    if (!file.createNewFile()) {
                        throw new IOException("Failed to create file: " + file.getPath());
                    }
                    config = new YamlConfiguration();
                    // Initialize minimal default content for critical files
                    if (fullPath.contains("BountyStorage")) {
                        config.createSection("bounties");
                        config.createSection("anonymous-bounties");
                    } else if (fullPath.contains("StatStorage")) {
                        config.createSection("players");
                    }
                    config.save(file);
                } else {
                    debugManager.logDebug("[DEBUG] Copying default resource for " + fullPath + " from " + resourcePath);
                    // Copy resource to file
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = resourceStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        resourceStream.close();
                    } catch (IOException e) {
                        debugManager.logWarning("[DEBUG] Failed to copy resource " + resourcePath + ": " + e.getMessage());
                        throw e;
                    }
                }
            }

            // Load the configuration
            try {
                config = YamlConfiguration.loadConfiguration(file);
                debugManager.logDebug("[DEBUG] Successfully loaded configuration: " + fullPath);
                // Load defaults if resource exists
                InputStream defConfigStream = getResource(resourcePath);
                if (defConfigStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(defConfigStream)) {
                        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                        config.setDefaults(defConfig);
                        debugManager.logDebug("[DEBUG] Set defaults for " + fullPath + " from resource");
                    }
                } else {
                    debugManager.logWarning("[DEBUG] No default resource found for " + resourcePath);
                }
            } catch (Exception e) {
                debugManager.logWarning("[DEBUG] Failed to parse " + fullPath + ": " + e.getMessage());
                throw new IOException("Failed to parse " + fullPath + ": " + e.getMessage());
            }
        }

        /**
         * Reloads the configuration from disk
         * // note: Refreshes the config file and reapplies defaults
         */
        public void reload() {
            try {
                config = YamlConfiguration.loadConfiguration(file);
                String resourcePath = "resources/" + fullPath;
                InputStream defConfigStream = getResource(resourcePath);
                if (defConfigStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(defConfigStream)) {
                        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                        config.setDefaults(defConfig);
                        debugManager.logDebug("[DEBUG] Reloaded " + fullPath + " with defaults from " + resourcePath);
                    }
                } else {
                    debugManager.logWarning("[DEBUG] No default resource found for " + resourcePath + " during reload");
                }
            } catch (Exception e) {
                debugManager.logWarning("[DEBUG] Failed to reload " + fullPath + ": " + e.getMessage());
            }
        }

        /**
         * Saves the configuration to disk
         * // note: Persists the current config state to file
         */
        public void save() {
            try {
                config.save(file);
                debugManager.logDebug("[DEBUG] Saved configuration: " + fullPath);
            } catch (IOException e) {
                debugManager.logWarning("[DEBUG] Could not save " + fullPath + ": " + e.getMessage());
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

    public static BountiesPlus getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return economy;
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

    /**
     * Retrieves the BountyStorage configuration
     * // note: Provides access to BountyStorage.yml for bounty data
     */
    public FileConfiguration getBountiesConfig() {
        ConfigWrapper wrapper = configWrappers.get("Storage/BountyStorage");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] Storage/BountyStorage ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("Storage/BountyStorage", new ConfigWrapper("Storage/BountyStorage"));
                wrapper = configWrappers.get("Storage/BountyStorage");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create Storage/BountyStorage ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] Storage/BountyStorage configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the stats configuration
     * // note: Provides access to StatStorage.yml
     */
    public FileConfiguration getStatsConfig() {
        ConfigWrapper wrapper = configWrappers.get("Storage/StatStorage");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] Storage/StatStorage ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("Storage/StatStorage", new ConfigWrapper("Storage/StatStorage"));
                wrapper = configWrappers.get("Storage/StatStorage");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create Storage/StatStorage ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] Storage/StatStorage configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the messages configuration
     * // note: Provides access to messages.yml for player messages
     */
    public FileConfiguration getMessagesConfig() {
        ConfigWrapper wrapper = configWrappers.get("messages");
        return wrapper != null ? wrapper.getConfig() : null;
    }

    /**
     * Retrieves the BountyGUI configuration
     * // note: Provides access to GUIs/BountyGUI.yml for GUI settings
     */
    public FileConfiguration getBountyGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/BountyGUI");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] GUIs/BountyGUI ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/BountyGUI", new ConfigWrapper("GUIs/BountyGUI"));
                wrapper = configWrappers.get("GUIs/BountyGUI");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create GUIs/BountyGUI ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] GUIs/BountyGUI configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the CreateGUI configuration
     * // note: Provides access to GUIs/CreateGUI.yml for GUI settings
     */
    public FileConfiguration getCreateGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/CreateGUI");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] GUIs/CreateGUI ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/CreateGUI", new ConfigWrapper("GUIs/CreateGUI"));
                wrapper = configWrappers.get("GUIs/CreateGUI");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create GUIs/CreateGUI ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] GUIs/CreateGUI configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the PreviewGUI configuration
     * // note: Provides access to GUIs/PreviewGUI.yml for GUI settings
     */
    public FileConfiguration getPreviewGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/PreviewGUI");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] GUIs/PreviewGUI ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/PreviewGUI", new ConfigWrapper("GUIs/PreviewGUI"));
                wrapper = configWrappers.get("GUIs/PreviewGUI");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create GUIs/PreviewGUI ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] GUIs/PreviewGUI configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the AddItemsGUI configuration
     * // note: Provides access to GUIs/AddItemsGUI.yml for GUI settings
     */
    public FileConfiguration getAddItemsGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/AddItemsGUI");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] GUIs/AddItemsGUI ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/AddItemsGUI", new ConfigWrapper("GUIs/AddItemsGUI"));
                wrapper = configWrappers.get("GUIs/AddItemsGUI");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create GUIs/AddItemsGUI ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] GUIs/AddItemsGUI configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the HuntersDen configuration
     * // note: Provides access to GUIs/HuntersDen.yml for GUI settings
     */
    public FileConfiguration getHuntersDenConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/HuntersDen");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] GUIs/HuntersDen ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/HuntersDen", new ConfigWrapper("GUIs/HuntersDen"));
                wrapper = configWrappers.get("GUIs/HuntersDen");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create GUIs/HuntersDen ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] GUIs/HuntersDen configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the BountyCancelGUI configuration
     * // note: Provides access to GUIs/BountyCancelGUI.yml for GUI settings
     */
    public FileConfiguration getBountyCancelGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("GUIs/BountyCancelGUI");
        if (wrapper == null) {
            getLogger().warning("[DEBUG] GUIs/BountyCancelGUI ConfigWrapper not found, attempting to load");
            try {
                configWrappers.put("GUIs/BountyCancelGUI", new ConfigWrapper("GUIs/BountyCancelGUI"));
                wrapper = configWrappers.get("GUIs/BountyCancelGUI");
            } catch (IOException e) {
                getLogger().warning("[DEBUG] Failed to create GUIs/BountyCancelGUI ConfigWrapper: " + e.getMessage());
                return null;
            }
        }
        FileConfiguration config = wrapper.getConfig();
        if (config == null) {
            getLogger().warning("[DEBUG] GUIs/BountyCancelGUI configuration is null");
        }
        return config;
    }

    /**
     * Retrieves the ItemValue configuration
     * // note: Provides access to ItemValue.yml for item pricing
     */
    public FileConfiguration getItemValueConfig() {
        ConfigWrapper wrapper = configWrappers.get("ItemValue");
        return wrapper != null ? wrapper.getConfig() : null;
    }

    /**
     * Retrieves the items configuration
     * // note: Provides access to items.yml for item settings
     */
    public FileConfiguration getItemsConfig() {
        ConfigWrapper wrapper = configWrappers.get("items");
        return wrapper != null ? wrapper.getConfig() : null;
    }

    /**
     * Retrieves the TopGUI configuration
     * // note: Provides access to TopGUI.yml for GUI settings
     */
    public FileConfiguration getTopGUIConfig() {
        ConfigWrapper wrapper = configWrappers.get("TopGUI");
        return wrapper != null ? wrapper.getConfig() : null;
    }

    /**
     * Retrieves the BountyTeamChecks configuration
     * // note: Provides access to BountyTeamChecks.yml for team settings
     */
    public FileConfiguration getTeamChecksConfig() {
        ConfigWrapper wrapper = configWrappers.get("BountyTeamChecks");
        return wrapper != null ? wrapper.getConfig() : null;
    }

    /**
     * Called when the plugin is enabled
     * // note: Initializes configurations, managers, listeners, commands, and MySQL
     */
    @Override
    public void onEnable() {
        // Clear filter states for reload
        BountyGUI.clearPlayerShowOnlyOnline();
        BountyGUI.clearPlayerFilterHighToLow();

        // Plugin Instance and Core Setup
        instance = this;

        // Configuration Files
        String[] configNames = {
                "config", "Storage/BountyStorage", "Storage/StatStorage", "messages", "items", "ItemValue",
                "GUIs/BountyGUI", "GUIs/CreateGUI", "GUIs/PreviewGUI", "GUIs/TopGUI",
                "GUIs/AddItemsGUI", "GUIs/HuntersDen", "BountyTeamChecks", "GUIs/BountyCancelGUI"
        };
        List<String> failedConfigs = new ArrayList<>();
        int loadedConfigs = 0;
        for (String name : configNames) {
            try {
                configWrappers.put(name, new ConfigWrapper(name));
                loadedConfigs++;
                getLogger().info("[DEBUG] Loaded configuration: " + name + ".yml");
            } catch (IOException e) {
                failedConfigs.add(name + ".yml");
                getLogger().warning("[DEBUG] Failed to load " + name + ".yml: " + e.getMessage());
            }
        }

        // Clean up incorrectly placed BountyStorage.yml and StatStorage.yml in root directory
        File rootBountyStorage = new File(getDataFolder(), "BountyStorage.yml");
        File rootStatStorage = new File(getDataFolder(), "StatStorage.yml");
        if (rootBountyStorage.exists()) {
            if (rootBountyStorage.delete()) {
                getLogger().info("[DEBUG] Deleted misplaced BountyStorage.yml from plugins/BountiesPlus/");
            } else {
                getLogger().warning("[DEBUG] Failed to delete misplaced BountyStorage.yml from plugins/BountiesPlus/");
            }
        }
        if (rootStatStorage.exists()) {
            if (rootStatStorage.delete()) {
                getLogger().info("[DEBUG] Deleted misplaced StatStorage.yml from plugins/BountiesPlus/");
            } else {
                getLogger().warning("[DEBUG] Failed to delete misplaced StatStorage.yml from plugins/BountiesPlus/");
            }
        }

        // Check for critical config failures
        if (failedConfigs.contains("Storage/BountyStorage.yml") || failedConfigs.contains("messages.yml")) {
            getLogger().severe("[DEBUG] Critical config(s) failed to load: " + String.join(", ", failedConfigs) + ", disabling plugin!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check for money and shop sections in config.yml
        if (!getConfig().contains("money")) {
            getLogger().warning("[DEBUG] 'money' section missing in config.yml! Using defaults for allow-zero-dollar-bounties, min-bounty-amount, and max-bounty-amount.");
        }
        if (!getConfig().contains("shop")) {
            getLogger().warning("[DEBUG] 'shop' section missing in config.yml! Using defaults for enable-shop, allow-expired-skulls, and use-shop-gui-plus.");
        }

        // Message Utility
        MessageUtils.initialize(this);

        // Core Managers and Services
        debugManager = new DebugManager(this);
        executorService = Executors.newFixedThreadPool(4);
        eventManager = new EventManager(this);

        // MySQL Database
        mySQL = new MySQL(this);
        mySQL.initialize();
        mySQL.migrateData();
        mySQL.migrateStatsData();

        // Gameplay Managers
        taxManager = new TaxManager(this);
        itemValueCalculator = new ItemValueCalculator(this);
        List<String> warnings = new ArrayList<>();
        bountyManager = new BountyManager(this, warnings);
        anonymousBounty = new AnonymousBounty(this);
        frenzy = new Frenzy(this, warnings);
        boostedBounty = new BoostedBounty(this, warnings);
        getLogger().info("[DEBUG] BoostedBounty initialized: " + (boostedBounty != null));
        tablistManager = new TablistManager(this, eventManager);
        if (tablistManager == null) {
            warnings.add("Failed to initialize TablistManager!");
        }

        // ShopGUIPlus Integration
        shopGuiPlusIntegration = new ShopGuiPlusIntegration(this);

        // Item Systems
        uav = new UAV(this, eventManager);
        tracker = new Tracker(this, eventManager);
        jammer = new Jammer(this, eventManager);
        manualBoost = new ManualBoost(this, eventManager);
        manualFrenzy = new ManualFrenzy(this, eventManager);
        reverseBounty = new ReverseBounty(this, eventManager);
        decreaseTime = new DecreaseTime(this, eventManager);

        // Event Listeners
        eventManager.registerGlobalListeners();
        eventManager.register(this);

        // Commands
        PluginCommand bountyCommand = getCommand("bounty");
        if (bountyCommand != null) {
            bountyCommand.setExecutor(new BountyCommand(this));
            bountyCommand.setTabCompleter(new BountyTabCompleter());
        } else {
            warnings.add("Failed to register bounty command!");
        }

        // External Dependencies
        boolean placeholderAPIHooked = false;
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Placeholders(this).register();
            placeholderAPIHooked = true;
        } else {
            warnings.add("PlaceholderAPI not found; placeholders will not work.");
        }

        boolean economyHooked = setupEconomy();
        if (!economyHooked) {
            warnings.add("No economy plugin found. Some features may not work.");
        }

        // Add skull config warning
        if (!getConfig().contains("bounty-skull")) {
            warnings.add("Bounty skull configuration not found in config.yml! Using defaults.");
        }

        // Final Setup
        reloadEverything();

        // Console Initialization Message
        boolean useAnsi = MessageUtils.isAnsiSupported();
        String green = useAnsi ? "\u001B[92m" : "";
        String red = useAnsi ? "\u001B[91m" : "";
        String white = useAnsi ? "\u001B[97m" : "";
        String reset = useAnsi ? "\u001B[0m" : "";
        List<String> initMessages = new ArrayList<>(Arrays.asList(
                "BountiesPlus enabling...",
                "----------------------------------------",
                "",
                green + "BountiesPlus Enabled" + reset,
                green + "   MySQL:" + reset,
                white + "      - MySQL Status: " + (mySQL.isEnabled() ? green + "Enabled" : red + "Disabled") + reset,
                white + "      - PlaceholderAPI " + (placeholderAPIHooked ? green + "Found - Expansion Registered" : red + "Not Found") + reset,
                green + "   Dependencies:" + reset,
                white + "      - Economy support " + (economyHooked ? green + "enabled with Vault" : red + "disabled") + reset,
                white + "      - PlaceholderAPI " + (placeholderAPIHooked ? green + "Found - Expansion Registered" : red + "Not Found") + reset,
                white + "      - ShopGUIPlus " + (shopGuiPlusIntegration.isEnabled() ? green + "Found - Integration Enabled" : red + "Not Found or Disabled") + reset,
                green + "   Config Files:" + reset,
                failedConfigs.isEmpty()
                        ? white + "      - " + green + "All config files loaded (" + loadedConfigs + "/" + configNames.length + ")" + reset
                        : white + "      - " + red + loadedConfigs + "/" + configNames.length + " files loaded, Missing: " + String.join(", ", failedConfigs) + reset,
                red + "   Warnings:" + reset,
                warnings.isEmpty() ? white + "      - None" + reset : ""
        ));
        initMessages.addAll(warnings.stream().map(w -> white + "      - " + red + w + reset).toList());
        initMessages.addAll(Arrays.asList(
                "",
                green + "Join my Discord for Support" + reset,
                white + "   - https://discord.gg/U8WMbB4n" + reset,
                "",
                "----------------------------------------"
        ));

        for (String message : initMessages) {
            if (!message.isEmpty()) {
                getLogger().info(message);
            } else {
                getLogger().info("");
            }
        }
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
        ConfigWrapper statsWrapper = configWrappers.get("stats");
        if (statsWrapper != null) {
            statsWrapper.save();
        }
    }

    /**
     * Reloads all configurations and data
     * // note: Refreshes configs, MySQL, and all systems
     */
    public void reloadEverything() {
        List<String> warnings = new ArrayList<>();
        reloadAllConfigs(); // Reloads all configs via configWrappers
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
     * Gets the executor service for asynchronous tasks
     * // note: Returns the thread pool used for running async database operations
     */
    public ExecutorService getExecutorService() {
        return executorService;
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
}