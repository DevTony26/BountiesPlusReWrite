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
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.TopGUI;
import tony26.bountiesPlus.Items.*;
import tony26.bountiesPlus.utils.*;
import tony26.bountiesPlus.utils.MessageUtils;

import java.io.*;
import java.util.*;

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
    private boolean bountyParticleEnabled;
    private String bloodParticleName;
    private String smokeParticleName;
    private int bloodParticleCountLow;
    private int bloodParticleCountHigh;
    private int smokeParticleCountLow;
    private int smokeParticleCountHigh;
    private int particleInterval;
    private int displayRadius;
    private boolean bountySoundEnabled;
    private String bountySoundName;
    private float bountySoundVolume;
    private float bountySoundPitch;
    private String bountyGUITitle;
    private int taskID;
    private Map<String, ConfigWrapper> configWrappers = new HashMap<>();
    private MySQL mySQL;

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
    public boolean isBountyParticleEnabled() { return bountyParticleEnabled; }
    public String getBloodParticleName() { return bloodParticleName; }
    public String getSmokeParticleName() { return smokeParticleName; }
    public int getParticleInterval() { return particleInterval; }
    public int getDisplayRadius() { return displayRadius; }
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
        return configWrappers.get("ItemValue").getConfig();
    }

    public FileConfiguration getItemsConfig() {
        return configWrappers.get("items").getConfig();
    }

    public FileConfiguration getTopGUIConfig() {
        return configWrappers.get("TopGUI").getConfig();
    }

    /**
     * Initializes the plugin and its components
     * // note: Sets up configurations, managers, commands, and listeners
     */
    @Override
    public void onEnable() {
        instance = this;
        MessageUtils.initialize(this);

        // Handle config.yml separately
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveDefaultConfig();
            getLogger().info("Created default config.yml");
        } else {
            reloadConfig();
            getLogger().info("Loaded existing config.yml");
        }

        // Initialize all other configs
        String[] configNames = {
                "messages", "bounties", "stats", "BountyGUI", "CreateGUI",
                "PreviewGUI", "AddItemsGUI", "HuntersDen", "ItemValue", "items",
                "TopGUI", "BountyCancelGUI", "BountyStorage", "StatStorage"
        };
        for (String name : configNames) {
            configWrappers.put(name, new ConfigWrapper(name));
        }

        // Initialize MySQL
        mySQL = new MySQL(this);
        if (mySQL.isConnected() && !getConfig().getBoolean("mysql.data-migrated", false)) {
            mySQL.migrateBountiesAsync().thenRun(() -> {
                getConfig().set("mysql.data-migrated", true);
                saveConfig();
                getLogger().info("Bounty data migration completed.");
            }).exceptionally(e -> {
                getLogger().warning("[DEBUG] MySQL Error: Migration failed: " + e.getMessage());
                return null;
            });
        }

        tracker = new Tracker(this);
        jammer = new Jammer(this);
        uav = new UAV(this);
        manualBoost = new ManualBoost(this);
        manualFrenzy = new ManualFrenzy(this);
        decreaseTime = new DecreaseTime(this);
        reverseBounty = new ReverseBounty(this);
        itemValueCalculator = new ItemValueCalculator(this);
        getServer().getPluginManager().registerEvents(new BountyCreationChatListener(this), this);
        if (!setupEconomy()) {
            getLogger().warning("No Vault-compatible economy plugin found! Bounty payments will not work.");
        }
        loadBountyParticleConfig();
        loadBountySoundConfig();
        loadBountyGUITitle();
        loadSkullConfig();
        debugManager = new DebugManager(this);
        taxManager = new TaxManager(this);
        bountyManager = new BountyManager(this);
        if (getConfig().getBoolean("boosted-bounties.enabled", true)) {
            boostedBounty = new BoostedBounty(this);
        } else {
            boostedBounty = null;
            getLogger().info("Boosted Bounties are disabled in config.yml");
        }
        if (getConfig().getBoolean("anonymous-bounties.enabled", true)) {
            anonymousBounty = new AnonymousBounty(this);
        } else {
            anonymousBounty = null;
            getLogger().info("Anonymous bounties are disabled in config.yml");
        }
        tablistManager = new TablistManager(this);
        boolean tablistEnabled = getConfig().getBoolean("tablist-modification.enabled", false);
        debugManager.logDebug("Tablist modification enabled: " + tablistEnabled);
        new BountyGUI(this);
        new TopGUI(this);
        BountyCommand bountyCommand = new BountyCommand(this);
        PluginCommand bountyCmd = getCommand("bounty");
        Objects.requireNonNull(bountyCmd).setExecutor(bountyCommand);
        ParticleCommand particleCommand = new ParticleCommand(this);
        PluginCommand particleCmd = getCommand("particle");
        Objects.requireNonNull(particleCmd).setExecutor(particleCommand);
        BountyTabCompleter tabCompleter = new BountyTabCompleter();
        Objects.requireNonNull(getCommand("bounty")).setTabCompleter(tabCompleter);
        new PlayerDeathListener(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Using Placeholders class: " + Placeholders.class.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("CooldownsPlus")) {
            getLogger().warning("CooldownsPlus detected, potential placeholder conflict with BountiesPlus");
        }
        registerPlaceholdersWithDelay();
        startParticleTask();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (tablistManager != null) {
                tablistManager.updateAllTablistNames();
                debugManager.logDebug("Initial tablist update executed for all online players");
            }
        }, 20L);
        Placeholders.startDebugLoggingTask(this);
        getLogger().info("BountiesPlus enabled! Running on " + VersionUtils.getVersionString());
    }

    /**
     * Reloads all plugin configurations and systems
     * // note: Updates configs, MySQL connection, and dependent settings
     */
    public void reloadEverything() {
        reloadConfig();
        for (ConfigWrapper wrapper : configWrappers.values()) {
            wrapper.reload();
        }
        loadBountyParticleConfig();
        loadBountySoundConfig();
        loadBountyGUITitle();
        if (boostedBounty != null) {
            boostedBounty.reload();
        }
        if (frenzy != null) {
            frenzy.reload();
        }
        if (mySQL != null) {
            mySQL.reconnect();
        }
        bountyManager.reload();
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
        loadBountyParticleConfig();
        loadBountySoundConfig();
        loadBountyGUITitle();
    }

    /**
     * Saves all plugin data
     * // note: Persists all data files and MySQL records
     */
    public void saveEverything() {
        configWrappers.get("bounties").save();
        configWrappers.get("stats").save();
        configWrappers.get("BountyStorage").save();
        configWrappers.get("StatStorage").save();
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
     */
    private void registerPlaceholdersWithDelay() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            Placeholders.registerPlaceholders();
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
                        Placeholders.registerPlaceholders();
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
     * Cleans up resources on plugin disable
     * // note: Saves data, cancels tasks, and cleans up managers
     */
    @Override
    public void onDisable() {
        if (boostedBounty != null) {
            boostedBounty.cleanup();
        }
        if (bountyManager != null) {
            bountyManager.cleanup();
        }
        if (frenzy != null) {
            frenzy.cleanup();
        }
        if (anonymousBounty != null) {
            anonymousBounty.cleanup();
        }
        if (tablistManager != null) {
            tablistManager.cleanup();
        }
        if (debugManager != null) {
            debugManager.stopDebugLoggingTask();
        }
        if (mySQL != null) {
            mySQL.closeConnection();
        }
        stopParticleTask();
        Placeholders.stopDebugLoggingTask();
        getLogger().info("BountiesPlus disabled!");
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
     * Loads bounty particle configuration from config.yml
     * // note: Sets up particle effects for bountied players
     */
    public void loadBountyParticleConfig() {
        bountyParticleEnabled = getConfig().getBoolean("bounty-particle.enabled", true);
        bloodParticleName = getConfig().getString("bounty-particle.blood-particle", "REDSTONE");
        smokeParticleName = getConfig().getString("bounty-particle.smoke-particle", "REDSTONE");
        bloodParticleCountLow = getConfig().getInt("bounty-particle.blood-particle-count-low", 15);
        bloodParticleCountHigh = getConfig().getInt("bounty-particle.blood-particle-count-high", 10);
        smokeParticleCountLow = getConfig().getInt("bounty-particle.smoke-particle-count-low", 15);
        smokeParticleCountHigh = getConfig().getInt("bounty-particle.smoke-particle-count-high", 10);
        particleInterval = getConfig().getInt("bounty-particle.particle-interval", 40);
        displayRadius = getConfig().getInt("bounty-particle.display-radius", 30);

        if (!VersionUtils.isPost19()) {
            getLogger().info("Particles are not supported in this Minecraft version (" + VersionUtils.getVersionString() + "), disabling particle effects.");
            bountyParticleEnabled = false;
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
     * Checks if particle visibility is enabled for a player
     * // note: Retrieves player-specific particle visibility setting
     */
    public boolean getParticleVisibility(Player player) {
        String path = "particle-visibility." + player.getUniqueId().toString();
        return getBountiesConfig().getBoolean(path, true);
    }

    /**
     * Sets particle visibility for a player
     * // note: Updates and saves player-specific particle visibility setting
     */
    public void setParticleVisibility(Player player, boolean enabled) {
        String path = "particle-visibility." + player.getUniqueId().toString();
        getBountiesConfig().set(path, enabled);
        configWrappers.get("bounties").save();
    }

    /**
     * Starts the particle task for bountied players
     * // note: Schedules repeating task to display particles around players with bounties
     */
    private void startParticleTask() {
        if (!bountyParticleEnabled) {
            getLogger().info("Particle effects are disabled.");
            return;
        }

        taskID = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player bountyTarget : Bukkit.getOnlinePlayers()) {
                UUID targetUUID = bountyTarget.getUniqueId();
                Map<UUID, Integer> bounties = bountyManager.getBountiesOnTarget(targetUUID);
                if (!bounties.isEmpty()) {
                    for (Player observer : Bukkit.getOnlinePlayers()) {
                        boolean observerParticlesEnabled = getParticleVisibility(observer);
                        if (!observerParticlesEnabled) {
                            continue;
                        }
                        if (observer.getWorld().equals(bountyTarget.getWorld())) {
                            try {
                                double distance = observer.getLocation().distance(bountyTarget.getLocation());
                                if (distance <= displayRadius) {
                                    spawnParticles(bountyTarget, observer);
                                }
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            }
        }, 0L, particleInterval);
    }

    /**
     * Stops the particle task
     * // note: Cancels the scheduled particle task
     */
    private void stopParticleTask() {
        if (taskID != 0) {
            Bukkit.getScheduler().cancelTask(taskID);
        }
    }

    /**
     * Spawns particles around a target player for an observer
     * // note: Displays visual effects for bountied players
     */
    private void spawnParticles(Player target, Player observer) {
        VersionUtils.spawnParticle(observer, bloodParticleName, target.getLocation().add(0, 1, 0), bloodParticleCountLow, 0.4, 0.4, 0.4, 0.02);
        VersionUtils.spawnParticle(observer, bloodParticleName, target.getLocation().add(0, 1.5, 0), bloodParticleCountHigh, 0.3, 0.3, 0.3, 0.02);
        VersionUtils.spawnParticle(observer, smokeParticleName, target.getLocation().add(0, 1, 0), smokeParticleCountLow, 0.5, 1.0, 0.5, 0);
        VersionUtils.spawnParticle(observer, smokeParticleName, target.getLocation().add(0, 1.5, 0), smokeParticleCountHigh, 0.5, 1.0, 0.5, 0);
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