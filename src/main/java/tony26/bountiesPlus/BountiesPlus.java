// file: src/main/java/tony26/bountiesPlus/BountiesPlus.java
package tony26.bountiesPlus;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import tony26.bountiesPlus.GUIs.BountyCancel;
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.TopGUI;
import tony26.bountiesPlus.Items.*;
import tony26.bountiesPlus.utils.*;
import tony26.bountiesPlus.utils.MessageUtils;
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

    private class ConfigWrapper {
        private final String name;
        private File file;
        private FileConfiguration config;

        public ConfigWrapper(String name) {
            this.name = name;
            file = new File(getDataFolder(), name + ".yml");
            if (!file.exists()) {
                saveResource(name + ".yml", false);
                getLogger().info("Created default " + name + ".yml");
            }
            config = YamlConfiguration.loadConfiguration(file);
            InputStream defConfigStream = getResource(name + ".yml");
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
                config.setDefaults(defConfig);
            }
            getLogger().info("Loaded " + name + ".yml");
        }

        public void reload() {
            config = YamlConfiguration.loadConfiguration(file);
            InputStream defConfigStream = getResource(name + ".yml");
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
                config.setDefaults(defConfig);
            }
        }

        public void save() {
            try {
                config.save(file);
            } catch (IOException e) {
                getLogger().warning("Could not save " + name + ".yml: " + e.getMessage());
            }
        }

        public FileConfiguration getConfig() {
            return config;
        }
    }

    public static BountiesPlus getInstance() { return instance; }
    public static Economy getEconomy() { return economy; }
    public BountyManager getBountyManager() { return bountyManager; }
    public BoostedBounty getBoostedBounty() { return boostedBounty; }
    public AnonymousBounty getAnonymousBounty() { return anonymousBounty; }
    public TaxManager getTaxManager() { return taxManager; }
    public Frenzy getFrenzy() { return frenzy; }
    public Tracker getTracker() { return tracker; }
    public Jammer getJammer() { return jammer; }
    public UAV getUAV() { return uav; }
    public ManualBoost getManualBoost() { return manualBoost; }
    public ManualFrenzy getManualFrenzy() { return manualFrenzy; }
    public DecreaseTime getDecreaseTime() { return decreaseTime; }
    public ReverseBounty getReverseBounty() { return reverseBounty; }
    public ItemValueCalculator getItemValueCalculator() { return itemValueCalculator; }
    public TablistManager getTablistManager() { return tablistManager; }
    public DebugManager getDebugManager() { return debugManager; }
    public boolean isBountySoundEnabled() { return bountySoundEnabled; }
    public String getBountySoundName() { return bountySoundName; }
    public float getBountySoundVolume() { return bountySoundVolume; }
    public float getBountySoundPitch() { return bountySoundPitch; }
    public MySQL getMySQL() { return mySQL; }

    public FileConfiguration getMessagesConfig() {
        return configWrappers.get("messages").getConfig();
    }

    public FileConfiguration getBountiesConfig() {
        return configWrappers.get("bounties").getConfig();
    }

    public FileConfiguration getStatsConfig() {
        return configWrappers.get("stats").getConfig();
    }

    public FileConfiguration getBountyGUIConfig() {
        return configWrappers.get("BountyGUI").getConfig();
    }

    public FileConfiguration getCreateGUIConfig() {
        return configWrappers.get("CreateGUI").getConfig();
    }

    public FileConfiguration getPreviewGUIConfig() {
        return configWrappers.get("PreviewGUI").getConfig();
    }

    public FileConfiguration getAddItemsGUIConfig() {
        return configWrappers.get("AddItemsGUI").getConfig();
    }

    public FileConfiguration getHuntersDenConfig() {
        return configWrappers.get("HuntersDen").getConfig();
    }

    public FileConfiguration getItemValueConfig() {
        return configWrappers.get("items").getConfig();
    }

    public FileConfiguration getItemsConfig() {
        return configWrappers.get("items").getConfig();
    }

    public FileConfiguration getTopGUIConfig() {
        return configWrappers.get("TopGUI").getConfig();
    }

    public FileConfiguration getTeamChecksConfig() {
        return configWrappers.get("teamchecks").getConfig();
    }

    /**
     * Called when the plugin is enabled
     * // note: Initializes configurations, managers, listeners, commands, and MySQL
     */
    @Override
    public void onEnable() {
        // --------------------------------------
        // Plugin Instance and Core Setup
        // note: Sets up plugin instance and core dependencies
        // --------------------------------------
        instance = this;
        getLogger().info("Starting BountiesPlus initialization...");

        // --------------------------------------
        // Configuration Files
        // note: Loads default config and all YAML configuration files
        // --------------------------------------
        saveDefaultConfig();
        configWrappers.put("bounties", new ConfigWrapper("BountyStorage"));
        configWrappers.put("stats", new ConfigWrapper("StatStorage"));
        configWrappers.put("messages", new ConfigWrapper("messages"));
        configWrappers.put("items", new ConfigWrapper("items"));
        configWrappers.put("BountyGUI", new ConfigWrapper("BountyGUI"));
        configWrappers.put("CreateGUI", new ConfigWrapper("CreateGUI"));
        configWrappers.put("PreviewGUI", new ConfigWrapper("PreviewGUI"));
        configWrappers.put("TopGUI", new ConfigWrapper("TopGUI"));
        configWrappers.put("AddItemsGUI", new ConfigWrapper("AddItemsGUI"));
        configWrappers.put("HuntersDen", new ConfigWrapper("HuntersDen"));
        configWrappers.put("teamchecks", new ConfigWrapper("BountyTeamChecks"));
        getLogger().info("Configuration files loaded successfully.");

        // --------------------------------------
        // Message Utility
        // note: Initializes message formatting utility
        // --------------------------------------
        MessageUtils.initialize(this);
        getLogger().info("MessageUtils initialized.");

        // --------------------------------------
        // Core Managers and Services
        // note: Sets up essential managers and async executor
        // --------------------------------------
        debugManager = new DebugManager(this);
        executorService = Executors.newFixedThreadPool(4); // Thread pool for async tasks
        getLogger().info("DebugManager and ExecutorService initialized.");

        // --------------------------------------
        // MySQL Database
        // note: Initializes MySQL connection and migrates data
        // --------------------------------------
        mySQL = new MySQL(this);
        mySQL.initialize();
        mySQL.migrateData();
        mySQL.migrateStatsData();
        getLogger().info("MySQL initialized and data migrated.");

        // --------------------------------------
        // Gameplay Managers
        // note: Initializes managers for bounties, taxes, and special modes
        // --------------------------------------
        taxManager = new TaxManager(this);
        itemValueCalculator = new ItemValueCalculator(this);
        bountyManager = new BountyManager(this);
        anonymousBounty = new AnonymousBounty(this);
        frenzy = new Frenzy(this);
        boostedBounty = new BoostedBounty(this);
        tablistManager = new TablistManager(this);
        if (tablistManager != null) {
            getLogger().info("TablistManager initialized successfully.");
        } else {
            getLogger().severe("Failed to initialize TablistManager!");
        }
        getLogger().info("Gameplay managers initialized.");

        // --------------------------------------
        // Item Systems
        // note: Initializes custom item functionalities
        // --------------------------------------
        uav = new UAV(this);
        tracker = new Tracker(this);
        jammer = new Jammer(this);
        manualBoost = new ManualBoost(this);
        manualFrenzy = new ManualFrenzy(this);
        reverseBounty = new ReverseBounty(this);
        decreaseTime = new DecreaseTime(this);
        getLogger().info("Item systems initialized.");

        // --------------------------------------
        // Graphical User Interfaces (GUIs)
        // note: Initializes all GUI components
        // --------------------------------------
        new TopGUI(this);
        new BountyGUI(this);
        new BountyCancel(this);
        getLogger().info("GUIs initialized.");

        // --------------------------------------
        // Event Listeners
        // note: Registers all event listeners for player and game events
        // --------------------------------------
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BountyCreationChatListener(this), this);
        getServer().getPluginManager().registerEvents(this, this); // BountiesPlus as listener
        getLogger().info("Event listeners registered.");

        // --------------------------------------
        // Commands
        // note: Sets up command executors and tab completers
        // --------------------------------------
        PluginCommand bountyCommand = getCommand("bounty");
        if (bountyCommand != null) {
            bountyCommand.setExecutor(new BountyCommand(this));
            bountyCommand.setTabCompleter(new BountyTabCompleter());
            getLogger().info("Bounty command registered.");
        } else {
            getLogger().severe("Failed to register bounty command!");
        }

        // --------------------------------------
        // External Dependencies
        // note: Integrates with PlaceholderAPI and Vault economy
        // --------------------------------------
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Placeholders(this).register();
            getLogger().info("PlaceholderAPI hooked successfully.");
            // Optional: Use delayed registration if load order issues occur
            // registerPlaceholdersWithDelay();
        } else {
            getLogger().warning("PlaceholderAPI not found; placeholders will not work.");
        }

        if (!setupEconomy()) {
            getLogger().warning("No economy plugin found. Some features may not work.");
        } else {
            getLogger().info("Vault economy hooked successfully.");
        }

        // --------------------------------------
        // Final Setup
        // note: Reloads all configurations and ensures plugin is ready
        // --------------------------------------
        reloadEverything();
        getLogger().info("BountiesPlus fully enabled!");
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
        reloadAllConfigs(); // Reloads all configs via configWrappers
        mySQL.initialize();
        mySQL.migrateData();
        mySQL.migrateStatsData();
        if (frenzy != null) frenzy.reload();
        if (boostedBounty != null) boostedBounty.reload();
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
     * Saves all data to storage // note: Persists bounties, stats, and configurations
     */
    public void saveEverything() {
        saveConfig();
        for (ConfigWrapper wrapper : configWrappers.values()) {
            wrapper.save();
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
     * Gets the executor service for asynchronous tasks
     * // note: Returns the thread pool used for running async database operations
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Called when the plugin is disabled
     * // note: Saves data, cancels tasks, and closes connections
     */
    @Override
    public void onDisable() {
        saveEverything();
        if (frenzy != null) frenzy.cleanup();
        if (boostedBounty != null) boostedBounty.cleanup();
        if (uav != null) uav.cleanup();
        if (tracker != null) tracker.cleanup();
        if (jammer != null) jammer.cleanup();
        if (manualBoost != null) manualBoost.cleanup();
        if (manualFrenzy != null) manualFrenzy.cleanup();
        if (reverseBounty != null) reverseBounty.cleanup();
        if (decreaseTime != null) decreaseTime.cleanup();
        if (mySQL != null) mySQL.closeConnection();
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    getLogger().warning("Some async tasks did not terminate gracefully.");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                getLogger().severe("Interrupted while shutting down executor service: " + e.getMessage());
            }
        }
        if (debugManager != null) debugManager.stopDebugLoggingTask();
    }

    /**
     * Loads skull configuration from config.yml
     * // note: Verifies bounty skull settings
     */
    public void loadSkullConfig() {
        if (!getConfig().contains("bounty-skull")) {
            getLogger().warning("Bounty skull configuration not found in config.yml! Using defaults.");
        } else {
            getLogger().info("Bounty skull configuration loaded successfully.");
        }
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
        bountyGUITitle = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&dBounty Hunter"));
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
     * // note: Removes player from boosted bounty and frenzy systems
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (boostedBounty != null) {
            boostedBounty.removePlayer(event.getPlayer());
        }
        if (frenzy != null) {
            frenzy.removePlayer(event.getPlayer());
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
}