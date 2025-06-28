package tony26.bountiesPlus.GUIs;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tony26.bountiesPlus.*;
import tony26.bountiesPlus.utils.*;
import net.milkbowl.vault.economy.Economy;
import tony26.bountiesPlus.utils.MessageUtils;
import net.md_5.bungee.api.chat.TextComponent;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;


import java.util.stream.Collectors;

public class CreateGUI implements InventoryHolder, Listener {

    private String GUI_TITLE; // Remove static and final keywords
    private final Inventory inventory;
    private final Player player;
    private final BountiesPlus plugin;
    private int currentPage = 0;
    private List<OfflinePlayer> availablePlayers = new ArrayList<>();
    private static final Map<UUID, CreateGUI> activeInstances = new HashMap<>();
    private Set<Integer> protectedSlots = new HashSet<>();
    private final BountyCreationSession session;
    private long openTime;
    private static int[] playerHeadSlots;
    private static final Map<UUID, Long> lastClickTimes = new HashMap<>();
    private boolean isTransitioningToAddItems = false;

    /**
     * Cleans up the CreateGUI instance
     * // note: Unregisters listeners and removes from active instances
     */
    public void cleanup() {
        HandlerList.unregisterAll(this);
        activeInstances.remove(player.getUniqueId());
    }

    /**
     * Gets the inventory associated with this GUI
     * // note: Returns the CreateGUI inventory for InventoryHolder interface
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Loads protected slots from configuration
     * // note: Initializes protectedSlots with slots from Plugin-Items, Custom-Items, and pause-button when enabled
     */
    private void loadProtectedSlots(FileConfiguration config) {
        protectedSlots.clear();
        if (inventory == null) {
            return;
        }

        // Add slots from Plugin-Items
        for (String button : Arrays.asList("confirm-button", "cancel-button", "add-items-button", "add-money-button", "total-bounty-value-button", "add-experience-button", "add-time-button", "pause-button")) {
            int slot = config.getInt(button + ".slot", -1);
            if (slot >= 0 && slot < 54) {
                protectedSlots.add(slot);
            }
        }

        // Add slots from Custom-Items
        if (config.contains("Custom-Items")) {
            for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
                String path = "Custom-Items." + key;
                List<Integer> slots = config.getIntegerList(path + ".slots");
                for (int slot : slots) {
                    if (slot >= 0 && slot < 54) {
                        protectedSlots.add(slot);
                    }
                }
            }
        }

        // Protect slots with existing items
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                protectedSlots.add(i);
            }
        }
    }

    private void initializeGUI() {

        // Load available players
        loadAvailablePlayers();

        // Set up the GUI
        refreshGUI();
    }

    /**
     * Constructs the CreateGUI for a player
     * // note: Initializes the bounty creation GUI with player heads, buttons, and paused session handling
     */
    public CreateGUI(Player player, EventManager eventManager) {
        this.player = player;
        this.plugin = BountiesPlus.getInstance();
        this.session = BountyCreationSession.getOrCreateSession(player); // Use getOrCreateSession to resume paused sessions
        FileConfiguration config = plugin.getCreateGUIConfig();
        File configFile = new File(plugin.getDataFolder(), "GUIs/CreateGUI.yml");

        // Verify configuration integrity
        if (!configFile.exists() || config.getConfigurationSection("Plugin-Items") == null) {
            try {
                if (configFile.exists()) configFile.delete();
                plugin.saveResource("GUIs/CreateGUI.yml", false);
                config = YamlConfiguration.loadConfiguration(configFile);
                plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Reloaded default CreateGUI.yml");
            } catch (Exception e) {
                plugin.getDebugManager().logWarning("[DEBUG - CreateGUI] Failed to reload default CreateGUI.yml: " + e.getMessage());
                config = new YamlConfiguration(); // Fallback to empty config
            }
        }

        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&4&lCreate a Bounty"));
        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE); // Set holder to this
        this.protectedSlots = new HashSet<>();
        this.availablePlayers = new ArrayList<>();
        this.currentPage = 0;
        this.openTime = System.currentTimeMillis();

        loadPlayerHeadSlots();
        addCustomFillerItems();
        addBottomRowButtons();
        refreshGUI();
        eventManager.register(this);

        // Resume paused session if exists
        if (session.isPaused()) {
            session.resumeSession();
            plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Resumed paused session for " + player.getName());
        }
    }

    /**
     * Loads configurable player head slots from config
     * // note: Initializes playerHeadSlots with validated slots from CreateGUI.yml
     */
    private void loadPlayerHeadSlots() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        List<Integer> slots = config.getIntegerList("bounty-skull-slots.slots");
        if (slots.isEmpty()) {
            debugManager.logWarning("[DEBUG - CreateGUI] No bounty-skull-slots defined in CreateGUI.yml, using default slots");
            slots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
        }
        Set<Integer> uniqueSlots = new HashSet<>();
        List<Integer> validSlots = new ArrayList<>();
        Set<Integer> reservedSlots = new HashSet<>(Arrays.asList(config.getInt("Plugin-Items.confirm-button.slot", 52), config.getInt("Plugin-Items.add-items-button.slot", 50), config.getInt("Plugin-Items.add-money-button.slot", 48), config.getInt("Plugin-Items.total-bounty-value-button.slot", 49), config.getInt("Plugin-Items.add-experience-button.slot", 47), config.getInt("Plugin-Items.add-time-button.slot", 51), config.getInt("Plugin-Items.cancel-button.slot", 46), config.getInt("Plugin-Items.pause-button.slot", 4)));
        if (config.contains("Custom-Items")) {
            for (String key : config.getConfigurationSection("Custom-Items").getKeys(false)) {
                reservedSlots.addAll(config.getIntegerList("Custom-Items." + key + ".slots"));
            }
        }
        for (int slot : slots) {
            if (slot >= 0 && slot < 54 && !reservedSlots.contains(slot) && uniqueSlots.add(slot)) {
                validSlots.add(slot);
            } else {
                debugManager.logWarning("[DEBUG - CreateGUI] Invalid or reserved bounty-skull-slot " + slot + " in CreateGUI.yml (must be 0-53, unique, not in Custom-Items or Plugin-Items)");
            }
        }
        if (validSlots.isEmpty()) {
            debugManager.logWarning("[DEBUG - CreateGUI] No valid bounty-skull-slots, using default slots");
            validSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
        }
        playerHeadSlots = validSlots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Opens the CreateGUI inventory for the player
     * // note: Refreshes GUI content and displays it to the player
     */
    public void openInventory(Player player) {
        updateProtectedSlots();
        if (!player.equals(this.player)) {
            plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Ignoring openInventory call for " + player.getName() + ": player mismatch");
            return; // Safety check
        }
        refreshGUI();
        player.openInventory(inventory);
        plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Opened CreateGUI for player " + player.getName());
    }

    /**
     * Gets the active CreateGUI instance for a player
     * // note: Returns the current CreateGUI instance for the specified player, or null if none exists
     */
    public static CreateGUI getActiveInstance(UUID playerUUID) {
        return activeInstances.get(playerUUID);
    }

    /**
     * Loads the list of players available for bounty creation
     * // note: Filters and sorts players for GUI display
     */
    private void loadAvailablePlayers() {
        boolean showOfflinePlayers = plugin.getConfig().getBoolean("bounties.allow-offline-players", true);
        Set<OfflinePlayer> uniquePlayers = new HashSet<>();
        BoostedBounty boostedBounty = plugin.getBoostedBounty();
        boolean isFrenzyActive = plugin.getFrenzy() != null && plugin.getFrenzy().isFrenzyActive();

        // Skip sorting during Frenzy Mode
        if (!isFrenzyActive && showOfflinePlayers) {
            List<OfflinePlayer> boostedPlayers = new ArrayList<>();
            List<OfflinePlayer> normalPlayers = new ArrayList<>();
            OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

            for (OfflinePlayer offlinePlayer : allPlayers) {
                if (offlinePlayer == null || offlinePlayer.getName() == null || offlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                    continue;
                }
                boolean isBoosted = boostedBounty != null && boostedBounty.getCurrentBoostedTarget() != null && boostedBounty.getCurrentBoostedTarget().equals(offlinePlayer.getUniqueId());
                if (isBoosted) {
                    boostedPlayers.add(offlinePlayer);
                } else {
                    normalPlayers.add(offlinePlayer);
                }
            }

            // Sort boosted and normal players alphabetically
            boostedPlayers.sort(Comparator.comparing(p -> p.getName() != null ? p.getName().toLowerCase() : p.getUniqueId().toString()));
            normalPlayers.sort(Comparator.comparing(p -> p.getName() != null ? p.getName().toLowerCase() : p.getUniqueId().toString()));

            uniquePlayers.addAll(boostedPlayers);
            uniquePlayers.addAll(normalPlayers);
        } else {
            OfflinePlayer[] allPlayers = showOfflinePlayers ? Bukkit.getOfflinePlayers() : new OfflinePlayer[]{};
            for (OfflinePlayer offlinePlayer : allPlayers) {
                if (offlinePlayer == null || offlinePlayer.getName() == null || offlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                    continue;
                }
                uniquePlayers.add(offlinePlayer);
            }
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                uniquePlayers.add(onlinePlayer);
            }
        }

        availablePlayers = new ArrayList<>(uniquePlayers);

        if (!isFrenzyActive && showOfflinePlayers) {
            // Sorting already handled above
        } else {
            availablePlayers.sort((p1, p2) -> {
                if (p1.isOnline() && !p2.isOnline()) return -1;
                if (!p1.isOnline() && p2.isOnline()) return 1;
                return p1.getName().compareToIgnoreCase(p2.getName());
            });
        }
    }

    /**
     * Refreshes the CreateGUI content
     * // note: Updates inventory with player heads, buttons, and custom filler items
     */
    private void refreshGUI() {
        // Clear the inventory
        inventory.clear();
        protectedSlots.clear();

        // Add custom filler items (including borders) first
        addCustomFillerItems();

        // Add player heads with pagination
        addPlayerHeads();

        // Add bottom row buttons last to ensure precedence
        addBottomRowButtons();

        // Update GUI Items based on session state (if exists)
        updateSessionDisplay();
    }

    private void updateProtectedSlots() {
        FileConfiguration protectionConfig = BountiesPlus.getInstance().getCreateGUIConfig();

        // Reload the basic protection (borders, buttons)
        loadProtectedSlots(protectionConfig);

        // NOTE: This will automatically protect all content slots too
    }

    /**
     * Updates the GUI to reflect the current session state // note: Refreshes dynamic buttons and player heads based on bounty session
     */
    private void updateSessionDisplay() {
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        addBottomRowButtons(); // Refresh all bottom row buttons, including total-bounty-value-button
        addPlayerHeads(); // Refresh player heads to reflect target selection
    }

    /**
     * Adds custom filler items to empty slots based on configuration
     * // note: Populates non-protected slots with configurable items, applies enchantment glow when bounty session is active if enabled
     */
    private void addCustomFillerItems() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        boolean hasActiveSession = session != null && session.hasChanges();

        if (!config.contains("Custom-Items")) {
            debugManager.logDebug("[DEBUG - CreateGUI] No Custom-Items section found in CreateGUI.yml");
            addDefaultFillerItem();
            return;
        }

        int totalItems = config.getConfigurationSection("Custom-Items").getKeys(false).size();
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (String itemKey : config.getConfigurationSection("Custom-Items").getKeys(false)) {
            String path = "Custom-Items." + itemKey;
            String materialName = config.getString(path + ".material", "WHITE_STAINED_GLASS_PANE");
            String name = config.getString(path + ".name", " ");
            List<String> lore = config.getStringList(path + ".lore");
            boolean enchantmentGlow = config.getBoolean(path + ".enchantment-glow", false);
            boolean bountySessionGlow = config.getBoolean(path + ".bounty-session-glow", false);
            List<Integer> slots = config.getIntegerList(path + ".slots");

            if (slots.isEmpty()) {
                debugManager.logWarning("[DEBUG - CreateGUI] No slots defined for custom item '" + itemKey + "' in CreateGUI.yml, skipping");
                failures.add(itemKey + " Reason: No slots defined");
                continue;
            }

            Material material = VersionUtils.getMaterialSafely(materialName, "WHITE_STAINED_GLASS_PANE");
            if (material == Material.STONE && !materialName.equalsIgnoreCase("WHITE_STAINED_GLASS_PANE")) {
                debugManager.logWarning("[DEBUG - CreateGUI] Invalid material '" + materialName + "' for custom item '" + itemKey + "' in CreateGUI.yml, using WHITE_STAINED_GLASS_PANE");
                material = VersionUtils.getWhiteGlassPaneMaterial();
                failures.add(itemKey + " Reason: Invalid material '" + materialName + "'");
            }

            ItemStack fillerItem = new ItemStack(material);
            ItemMeta meta = fillerItem.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to get ItemMeta for custom item '" + itemKey + "' in CreateGUI.yml");
                failures.add(itemKey + " Reason: Failed to get ItemMeta");
                continue;
            }

            PlaceholderContext context = PlaceholderContext.create().player(player);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));
            if (!lore.isEmpty()) {
                meta.setLore(Placeholders.apply(lore, context).stream().map(line -> ChatColor.translateAlternateColorCodes('&', line)).collect(Collectors.toList()));
            }

            VersionUtils.applyGlow(fillerItem, enchantmentGlow || (bountySessionGlow && hasActiveSession));
            if (bountySessionGlow && hasActiveSession) {
                debugManager.logDebug("[DEBUG - CreateGUI] Applied bounty-session-glow for custom item '" + itemKey + "'");
            }

            if (VersionUtils.isPost19()) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            fillerItem.setItemMeta(meta);

            boolean placed = false;
            for (int slot : slots) {
                if (slot < 0 || slot >= inventory.getSize()) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid slot " + slot + " for custom item '" + itemKey + "' in CreateGUI.yml, must be 0-" + (inventory.getSize() - 1));
                    failures.add(itemKey + " Reason: Invalid slot " + slot);
                    continue;
                }
                ItemStack existingItem = inventory.getItem(slot);
                if (existingItem == null || existingItem.getType() == Material.AIR || !protectedSlots.contains(slot)) {
                    inventory.setItem(slot, fillerItem.clone());
                    protectedSlots.add(slot);
                    placed = true;
                } else {
                    debugManager.logDebug("[DEBUG - CreateGUI] Skipped slot " + slot + " for custom item '" + itemKey + "': already occupied by protected item");
                }
            }

            if (placed) {
                successfulItems++;
                debugManager.logDebug("[DEBUG - CreateGUI] Loaded custom item '" + itemKey + "' into slots " + slots);
            }
        }

        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - CreateGUI] All custom items loaded successfully");
        } else {
            String failureMessage = "[DEBUG - CreateGUI] " + successfulItems + "/" + totalItems + " custom items loaded";
            if (!failures.isEmpty()) {
                failureMessage += ", failed: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("CreateGUI_custom_items_" + System.currentTimeMillis(), failureMessage);
        }

        addDefaultFillerItem();
    }

    /**
     * Adds default stained glass pane filler to empty slots // note: Fills non-protected slots when custom items are missing
     */
    private void addDefaultFillerItem() {
        ItemStack fillerItem = new ItemStack(VersionUtils.getWhiteGlassPaneMaterial());
        ItemMeta meta = fillerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            fillerItem.setItemMeta(meta);
        }
        for (int slot = 0; slot < 54; slot++) {
            if (!protectedSlots.contains(slot)) {
                ItemStack existingItem = inventory.getItem(slot);
                if (existingItem == null || existingItem.getType() == Material.AIR) {
                    inventory.setItem(slot, fillerItem);
                    protectedSlots.add(slot);
                }
            }
        }
    }

    /**
     * Adds player heads to the GUI
     * // note: Populates the inventory with player heads for bounty target selection, moves selected target to first slot if enabled
     */
    private void addPlayerHeads() {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getCreateGUIConfig();
        String configPath = "player-head";
        boolean moveToFirstSlot = config.getBoolean(configPath + ".move-to-first-slot", true);
        String onlineStatusPath = configPath + ".online-status";
        String selectedName = config.getString(configPath + ".name.selected", "&6» &a%bountiesplus_target% &6«");
        String onlineName = config.getString(configPath + ".name.online", "&a%bountiesplus_target%");
        String offlineName = config.getString(configPath + ".name.offline", "&7%bountiesplus_target%");
        List<String> selectedLore = config.getStringList(configPath + ".lore.selected");
        List<String> notSelectedLore = config.getStringList(configPath + ".lore.not-selected");
        boolean selectedGlow = config.getBoolean(configPath + ".enchantment-glow.selected", true);
        boolean notSelectedGlow = config.getBoolean(configPath + ".enchantment-glow.not-selected", false);
        String onlineStatus = config.getString(onlineStatusPath + ".online", "&7Status: &aOnline");
        String offlineStatus = config.getString(onlineStatusPath + ".offline", "&7Status: &cOffline");
        String lastSeenStatus = config.getString(onlineStatusPath + ".last-seen", "&7Last Seen: &e%last_seen% ago");

        int startIndex = currentPage * playerHeadSlots.length;
        int endIndex = Math.min(startIndex + playerHeadSlots.length, availablePlayers.size());
        UUID selectedTargetUUID = session.hasTarget() ? session.getTargetUUID() : null;
        int selectedSlot = moveToFirstSlot && selectedTargetUUID != null ? playerHeadSlots[0] : -1;

        if (moveToFirstSlot && selectedTargetUUID != null) {
            int selectedIndex = -1;
            for (int i = 0; i < availablePlayers.size(); i++) {
                if (availablePlayers.get(i).getUniqueId().equals(selectedTargetUUID)) {
                    selectedIndex = i;
                    break;
                }
            }
            if (selectedIndex != -1 && selectedIndex >= startIndex && selectedIndex < endIndex) {
                Collections.swap(availablePlayers, selectedIndex, startIndex);
                debugManager.logDebug("[DEBUG - CreateGUI] Moved selected player " + availablePlayers.get(startIndex).getName() + " to first slot " + playerHeadSlots[0]);
            } else if (selectedIndex != -1) {
                availablePlayers.remove(selectedIndex);
                availablePlayers.add(0, Bukkit.getOfflinePlayer(selectedTargetUUID));
                startIndex = 0;
                currentPage = 0;
                endIndex = Math.min(playerHeadSlots.length, availablePlayers.size());
                debugManager.logDebug("[DEBUG - CreateGUI] Moved selected player to first position and reset to page 0");
            }
        }

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            OfflinePlayer targetPlayer = availablePlayers.get(i);
            ItemStack head = SkullUtils.createVersionAwarePlayerHead(targetPlayer);
            if (!VersionUtils.isPlayerHead(head)) {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to create PLAYER_HEAD for player " + targetPlayer.getName());
                continue;
            }
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to get SkullMeta for player " + targetPlayer.getName());
                continue;
            }
            boolean isSelected = selectedTargetUUID != null && selectedTargetUUID.equals(targetPlayer.getUniqueId());
            int bountyCount = plugin.getBountyManager().getBountiesOnTarget(targetPlayer.getUniqueId()).size();
            String status;
            if (targetPlayer.isOnline()) {
                status = onlineStatus;
            } else {
                long lastPlayed = targetPlayer.getLastPlayed();
                status = lastPlayed > 0 ? lastSeenStatus.replace("%last_seen%", TimeFormatter.formatTimestampToAgo(lastPlayed)) : offlineStatus;
            }
            PlaceholderContext context = PlaceholderContext.create().player(player).target(targetPlayer.getUniqueId()).bountyCount(bountyCount).onlineStatus(status);
            String name = isSelected ? selectedName : (targetPlayer.isOnline() ? onlineName : offlineName);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));
            List<String> lore = isSelected ? selectedLore : notSelectedLore;
            meta.setLore(Placeholders.apply(lore, context));
            VersionUtils.applyGlow(head, isSelected ? selectedGlow : notSelectedGlow);
            head.setItemMeta(meta);
            inventory.setItem(playerHeadSlots[slotIndex], head);
        }
    }

    /**
     * Formats the time difference into a human-readable string
     */
    private String getOnlineStatusText(OfflinePlayer targetPlayer, FileConfiguration config) {
        if (targetPlayer.isOnline()) {
            return config.getString("player-head.online-status.online", "&7Status: &aOnline");
        } else {
            long lastPlayed = targetPlayer.getLastPlayed();
            if (lastPlayed > 0) {
                String lastSeenTime = formatTimeDifference(System.currentTimeMillis() - lastPlayed);
                String lastSeenFormat = config.getString("player-head.online-status.last-seen", "&7Last Seen: &e%last_seen% ago");
                return lastSeenFormat.replace("%last_seen%", lastSeenTime);
            } else {
                return config.getString("player-head.online-status.offline", "&7Status: &cOffline");
            }
        }
    }

    /**
     * Checks if the skull has a valid owner (for skin verification)
     */
    @SuppressWarnings("deprecation")
    private static boolean hasValidOwner(SkullMeta skullMeta, String expectedOwner) {
        try {
            String owner = skullMeta.getOwner(); // Legacy API, available in 1.8.8
            return owner != null && owner.equalsIgnoreCase(expectedOwner); // Validates owner
        } catch (Exception e) {
            return false; // Fallback on error
        }
    }

    /**
     * Checks whether the server version is at least the given (major.minor).
     */
    private boolean isServerVersionAtLeast(int requiredMajor, int requiredMinor) {
        String version = Bukkit.getBukkitVersion(); // e.g. "1.21.4-R0.1-SNAPSHOT"
        String[] parts = version.split("\\.");
        try {
            int serverMajor = Integer.parseInt(parts[0]);
            int serverMinor = Integer.parseInt(parts[1]);
            if (serverMajor > requiredMajor) return true;
            if (serverMajor < requiredMajor) return false;
            return (serverMinor >= requiredMinor);
        } catch (Exception e) {
            // If anything unexpected happens, assume modern (so we go down the 1.13+ path).
            return true;
        }
    }

    /**
     * Injects the OfflinePlayer's internal GameProfile (with skin) into skullMeta via reflection.
     * This is only used as a fallback if setOwningPlayer or setOwner didn't exist or failed.
     */
    private void injectGameProfileViaReflection(SkullMeta skullMeta, OfflinePlayer target) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        try {
            // Try to get the GameProfile from the OfflinePlayer
            Object profile = null;

            // Try to access the 'profile' field on CraftOfflinePlayer
            try {
                Field profileField = target.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profile = profileField.get(target);
                plugin.getLogger().info("[DEBUG - CreateGUI] Got GameProfile from OfflinePlayer for " + target.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[DEBUG - CreateGUI] Could not get GameProfile from OfflinePlayer for " + target.getName() + ": " + e.getMessage());
            }

            if (profile == null) {
                plugin.getLogger().warning("[DEBUG - CreateGUI] No GameProfile available for " + target.getName() + ", skipping reflection injection");
                return;
            }

            // Now inject that GameProfile into the SkullMeta
            try {
                // Try setProfile method first (newer versions)
                Method setter = skullMeta.getClass().getDeclaredMethod("setProfile", profile.getClass());
                setter.setAccessible(true);
                setter.invoke(skullMeta, profile);
            } catch (NoSuchMethodException nsme) {
                // Fallback: set the private "profile" field directly
                try {
                    Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, profile);
                } catch (Exception e) {
                    plugin.getLogger().warning("[DEBUG - CreateGUI] Failed to inject GameProfile for " + target.getName() + ": " + e.getMessage());
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[DEBUG - CreateGUI] Reflection injection failed for " + target.getName() + ": " + t.getMessage());
        }
    }

    private String formatTimeDifference(long timeDiff) {
        return TimeFormatter.formatTimeAgo(timeDiff);
    }

    /**
     * Adds bottom row buttons to the GUI
     * // note: Places configurable buttons like confirm, cancel, add-items, and pause-button when session is active, applies bounty-session-glow for confirm-button-filler
     */
    private void addBottomRowButtons() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        BountyCreationSession session = BountyCreationSession.getSession(player);
        boolean hasSession = session != null && session.hasChanges();

        // Clear any previous button failures
        if (session != null) {
            session.clearButtonFailures();
        }

        // Calculate session values for placeholders
        double moneyValue = session != null ? session.getMoney() : 0.0;
        int expValue = session != null ? session.getExperience() : 0;
        String timeValue = session != null ? session.getFormattedTime() : "Default";
        List<ItemStack> items = session != null ? session.getItemRewards() : new ArrayList<>();
        int itemCount = items.size();
        double itemValue = items.stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum();
        double totalValue = moneyValue + itemValue;
        String duration = timeValue != null && !timeValue.equals("Not set") && !timeValue.isEmpty() ? timeValue : "Default";

        // List of all configurable buttons with their default slots
        Map<String, Integer> buttonDefaults = new HashMap<>();
        buttonDefaults.put(hasSession ? "confirm-button" : "confirm-button-filler", 52);
        buttonDefaults.put("cancel-button", 46);
        buttonDefaults.put("add-items-button", 50);
        buttonDefaults.put("add-money-button", 48);
        buttonDefaults.put("total-bounty-value-button", 49);
        buttonDefaults.put("add-experience-button", 47);
        buttonDefaults.put("add-time-button", 51);
        if (config.getBoolean("pause-button.enabled", false) && hasSession) {
            buttonDefaults.put("pause-button", 4);
        }

        int totalButtons = buttonDefaults.size();
        int successfulButtons = 0;

        // Create each configurable button
        for (Map.Entry<String, Integer> entry : buttonDefaults.entrySet()) {
            String buttonName = entry.getKey();
            int defaultSlot = entry.getValue();
            int slot = config.getInt(buttonName.equals("confirm-button-filler") ? "confirm-button.slot" : buttonName + ".slot", defaultSlot);

            if (slot < 0 || slot >= inventory.getSize()) {
                debugManager.logWarning("[DEBUG - CreateGUI] Invalid slot " + slot + " for button " + buttonName + " in CreateGUI.yml");
                if (session != null) {
                    session.addButtonFailure(buttonName, "Invalid slot " + slot);
                }
                continue;
            }

            ItemStack button;
            // Handle cancel button: use no-session config if no active session
            if (buttonName.equals("cancel-button") && !hasSession) {
                String configPath = "cancel-button.no-session";
                String materialName = config.getString(configPath + ".material", "BARRIER");
                button = VersionUtils.getXMaterialItemStack(materialName);
                if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid material '" + materialName + "' for cancel-button.no-session, using BARRIER");
                    if (session != null) {
                        session.addButtonFailure(buttonName, "Invalid material '" + materialName + "'");
                    }
                    button = VersionUtils.getXMaterialItemStack("BARRIER");
                }

                ItemMeta meta = button.getItemMeta();
                if (meta == null) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Failed to get ItemMeta for cancel-button.no-session");
                    if (session != null) {
                        session.addButtonFailure(buttonName, "Failed to get ItemMeta");
                    }
                    inventory.setItem(slot, button);
                    successfulButtons++;
                    protectedSlots.add(slot);
                    continue;
                }

                PlaceholderContext context = PlaceholderContext.create().player(player).bountyCount(0).moneyValue(moneyValue).expValue(expValue).timeValue(duration).itemCount(itemCount).itemValue(itemValue).taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).taxAmount(moneyValue * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).moneyLine("&7Money: &a" + CurrencyUtil.formatMoney(moneyValue)).experienceLine("&7Experience: &e" + (expValue == 0 ? "0 XP Levels" : expValue + " XP Level" + (expValue > 1 ? "s" : ""))).totalBountyAmount(totalValue).currentPage(0).totalPages(1);

                String name = config.getString(configPath + ".name", "&c&lReturn to the Bounty GUI");
                if (name == null || name.isEmpty()) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid or empty name at " + configPath + ".name, using default");
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", configPath + ".name")));
                    name = "&c&lReturn to the Bounty GUI";
                }
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));

                List<String> lore = config.getStringList(configPath + ".lore");
                if (lore.isEmpty()) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Empty lore at " + configPath + ".lore, using default");
                    lore = Arrays.asList("&7Click to return to Bounty GUI");
                }
                meta.setLore(Placeholders.apply(lore, context));

                boolean enchantmentGlow = config.getBoolean(configPath + ".enchantment-glow", false);
                if (enchantmentGlow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                if (VersionUtils.isPost19()) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
                button.setItemMeta(meta);
            }
            // Handle confirm button filler: use confirm-button-filler if no session
            else if (buttonName.equals("confirm-button-filler")) {
                String configPath = "confirm-button.confirm-button-filler";
                String materialName = config.getString(configPath + ".material", "WHITE_STAINED_GLASS_PANE");
                button = VersionUtils.getXMaterialItemStack(materialName);
                if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid material '" + materialName + "' for confirm-button-filler, using WHITE_STAINED_GLASS_PANE");
                    if (session != null) {
                        session.addButtonFailure(buttonName, "Invalid material '" + materialName + "'");
                    }
                    button = VersionUtils.getXMaterialItemStack("WHITE_STAINED_GLASS_PANE");
                }

                ItemMeta meta = button.getItemMeta();
                if (meta == null) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Failed to get ItemMeta for confirm-button-filler");
                    if (session != null) {
                        session.addButtonFailure(buttonName, "Failed to get ItemMeta");
                    }
                    inventory.setItem(slot, button);
                    successfulButtons++;
                    protectedSlots.add(slot);
                    continue;
                }

                String name = config.getString(configPath + ".name", " ");
                if (name == null) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid name at " + configPath + ".name, using default");
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", configPath + ".name")));
                    name = " ";
                }
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

                List<String> lore = config.getStringList(configPath + ".lore");
                if (lore.isEmpty()) {
                    lore = new ArrayList<>();
                }
                meta.setLore(lore);

                boolean enchantmentGlow = config.getBoolean(configPath + ".enchantment-glow", false);
                boolean bountySessionGlow = config.getBoolean(configPath + ".bounty-session-glow", false);
                if (enchantmentGlow || (bountySessionGlow && session != null && session.hasChanges())) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    if (bountySessionGlow && session != null && session.hasChanges()) {
                        debugManager.logDebug("[DEBUG - CreateGUI] Applied bounty-session-glow for confirm-button-filler");
                    }
                }
                if (VersionUtils.isPost19()) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
                button.setItemMeta(meta);
            }
            // Handle pause button: only create if enabled and session is active
            else if (buttonName.equals("pause-button")) {
                String configPath = "pause-button";
                String materialName = config.getString(configPath + ".material", "CLOCK");
                button = VersionUtils.getXMaterialItemStack(materialName);
                if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid material '" + materialName + "' for pause-button, using CLOCK");
                    if (session != null) {
                        session.addButtonFailure(buttonName, "Invalid material '" + materialName + "'");
                    }
                    button = VersionUtils.getXMaterialItemStack("CLOCK");
                }

                ItemMeta meta = button.getItemMeta();
                if (meta == null) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Failed to get ItemMeta for pause-button");
                    if (session != null) {
                        session.addButtonFailure(buttonName, "Failed to get ItemMeta");
                    }
                    inventory.setItem(slot, button);
                    successfulButtons++;
                    protectedSlots.add(slot);
                    continue;
                }

                PlaceholderContext context = PlaceholderContext.create().player(player).bountyCount(0).moneyValue(moneyValue).expValue(expValue).timeValue(duration).itemCount(itemCount).itemValue(itemValue).taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).taxAmount(moneyValue * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).moneyLine("&7Money: &a" + CurrencyUtil.formatMoney(moneyValue)).experienceLine("&7Experience: &e" + (expValue == 0 ? "0 XP Levels" : expValue + " XP Level" + (expValue > 1 ? "s" : ""))).totalBountyAmount(totalValue).currentPage(0).totalPages(1);

                String name = config.getString(configPath + ".name", "&e&lPause Session");
                if (name == null || name.isEmpty()) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Invalid or empty name at " + configPath + ".name, using default");
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", configPath + ".name")));
                    name = "&e&lPause Session";
                }
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));

                List<String> lore = config.getStringList(configPath + ".lore");
                if (lore.isEmpty()) {
                    debugManager.logWarning("[DEBUG - CreateGUI] Empty lore at " + configPath + ".lore, using default");
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", configPath + ".lore")));
                    lore = Arrays.asList("&7Click to pause your bounty creation", "&7session and return to the main menu");
                }
                meta.setLore(Placeholders.apply(lore, context));

                boolean enchantmentGlow = config.getBoolean(configPath + ".enchantment-glow", true);
                if (enchantmentGlow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                if (VersionUtils.isPost19()) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                }
                button.setItemMeta(meta);
            }
            // Handle special button types
            else if (buttonName.equals("add-items-button")) {
                button = createAddItemsButton(config, session, moneyValue, expValue, timeValue, itemCount, itemValue, totalValue, duration);
            } else {
                button = createConfigurableButton(buttonName, config, session, moneyValue, expValue, timeValue, itemCount, itemValue, totalValue, duration);
            }

            if (button != null && button.getType() != Material.AIR) {
                inventory.setItem(slot, button);
                successfulButtons++;
                protectedSlots.add(slot);
            } else {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to place button " + buttonName + " in slot " + slot);
                if (session != null) {
                    session.addButtonFailure(buttonName, "Failed to create button");
                }
            }
        }

        // Log consolidated debug message
        if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG - CreateGUI] All buttons created");
        } else {
            StringBuilder failureMessage = new StringBuilder("[DEBUG - CreateGUI] " + successfulButtons + "/" + totalButtons + " buttons created");
            List<String> failures = session != null ? session.getButtonFailures() : new ArrayList<>();
            if (!failures.isEmpty()) {
                failureMessage.append(", failed to create: ").append(String.join(", ", failures));
            }
            debugManager.bufferFailure("CreateGUI_buttons_" + System.currentTimeMillis(), failureMessage.toString());
        }
    }

    /**
     * Creates a configurable button with full placeholder support
     * // note: Generates GUI button with customizable appearance from CreateGUI.yml Plugin-Items
     */
    private ItemStack createConfigurableButton(String buttonName, FileConfiguration config, BountyCreationSession session, double moneyValue, int expValue, String timeValue, int itemCount, double itemValue, double totalValue, String duration) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String path = "Plugin-Items." + buttonName;
        ItemStack button = null;
        String failureReason = null;

        boolean isTargetSelected = session != null && session.getTargetUUID() != null;
        String materialPath = isTargetSelected && buttonName.equals("total-bounty-value-button") ? path + ".target-selected.material" : path + ".material";
        String namePath = isTargetSelected && buttonName.equals("total-bounty-value-button") ? path + ".target-selected.name" : path + ".name";
        String lorePath = isTargetSelected && buttonName.equals("total-bounty-value-button") ? path + ".target-selected.lore" : path + ".lore";
        String glowPath = isTargetSelected && buttonName.equals("total-bounty-value-button") ? path + ".target-selected.enchantment-glow" : path + ".enchantment-glow";

        boolean isNoSession = session != null && !session.hasChanges() && buttonName.equals("cancel-button");
        if (isNoSession) {
            materialPath = path + ".no-session.material";
            namePath = path + ".no-session.name";
            lorePath = path + ".no-session.lore";
            glowPath = path + ".no-session.enchantment-glow";
        }

        Material defaultMaterial;
        switch (buttonName) {
            case "add-money-button":
                defaultMaterial = Material.EMERALD;
                break;
            case "add-experience-button":
                defaultMaterial = VersionUtils.getExperienceBottleMaterial();
                break;
            case "add-time-button":
                defaultMaterial = VersionUtils.getClockMaterial();
                break;
            case "total-bounty-value-button":
                defaultMaterial = isTargetSelected ? VersionUtils.getPlayerHeadMaterial() : Material.PAPER;
                break;
            case "confirm-button":
                defaultMaterial = Material.LIME_DYE;
                break;
            case "cancel-button":
                defaultMaterial = isNoSession ? Material.BARRIER : Material.RED_DYE;
                break;
            default:
                defaultMaterial = Material.STONE;
        }

        String materialName = config.getString(materialPath, defaultMaterial.name());
        button = new ItemStack(VersionUtils.getMaterialSafely(materialName, defaultMaterial.name()));
        if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            debugManager.logWarning("[DEBUG - CreateGUI] Invalid material '" + materialName + "' at " + materialPath + ", using default " + defaultMaterial.name());
            failureReason = "Invalid material '" + materialName + "'";
            button = new ItemStack(defaultMaterial);
        }

        if (buttonName.equals("total-bounty-value-button") && isTargetSelected) {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(session.getTargetUUID());
            button = SkullUtils.createVersionAwarePlayerHead(targetPlayer);
            if (button == null || !VersionUtils.isPlayerHead(button)) {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to create player head for " + (targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown") + ", using fallback material PAPER");
                button = new ItemStack(Material.PAPER);
                failureReason = "Failed to create player head";
            }
        }

        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - CreateGUI] Failed to get ItemMeta for button " + buttonName + " at " + materialPath);
            failureReason = "Failed to get ItemMeta";
            button = new ItemStack(defaultMaterial);
            meta = Bukkit.getItemFactory().getItemMeta(button.getType());
            if (meta == null) {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to create default ItemMeta for " + buttonName + ", using " + defaultMaterial.name() + " with default meta");
                failureReason = "Failed to create ItemMeta";
            }
        }

        PlaceholderContext context = PlaceholderContext.create().player(player).bountyCount(0).moneyValue(moneyValue).expValue(expValue).timeValue(duration).itemCount(itemCount).itemValue(itemValue).taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).taxAmount(moneyValue * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).moneyLine("&7Money: &a" + CurrencyUtil.formatMoney(moneyValue)).experienceLine("&7Experience: &e" + (expValue == 0 ? "0 XP Levels" : expValue + " XP Level" + (expValue > 1 ? "s" : ""))).totalBountyAmount(totalValue).currentPage(0).totalPages(1);
        if (isTargetSelected) {
            context = context.target(session.getTargetUUID());
        }

        String defaultName = isNoSession ? "&c&lMain Menu" : "&c" + buttonName.replace("-", " ");
        String name = config.getString(namePath, defaultName);
        if (name == null || name.isEmpty()) {
            debugManager.logWarning("[DEBUG - CreateGUI] Invalid or empty name at " + namePath + ", using default '" + defaultName + "'");
            name = defaultName;
            failureReason = failureReason != null ? failureReason + ", Invalid name" : "Invalid name";
        }
        meta.setDisplayName(MessageUtils.formatMessage(Placeholders.apply(name, context), player));

        List<String> lore = config.getStringList(lorePath);
        if (lore.isEmpty()) {
            debugManager.logWarning("[DEBUG - CreateGUI] Empty lore at " + lorePath + ", using default for " + buttonName);
            lore = isNoSession ? Arrays.asList("&7Return to the Main", "&7Bounty Menu", "", "&c&l(!) Click to return to the Main Menu!") : Collections.singletonList("&7" + buttonName.replace("-", " "));
            failureReason = failureReason != null ? failureReason + ", Empty lore" : "Empty lore";
        }
        meta.setLore(Placeholders.apply(lore, context).stream().map(line -> MessageUtils.formatMessage(line, player)).collect(Collectors.toList()));

        boolean enchantmentGlow = config.getBoolean(glowPath, buttonName.equals("total-bounty-value-button") || buttonName.equals("confirm-button"));
        if (!config.contains(glowPath)) {
            debugManager.logWarning("[DEBUG - CreateGUI] Missing enchantment-glow setting at " + glowPath + ", using default " + enchantmentGlow);
            MessageUtils.sendFormattedMessage(player, "missing-config", PlaceholderContext.create().player(player).error(glowPath));
        }
        VersionUtils.applyGlow(button, enchantmentGlow);

        if (VersionUtils.isPost19()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        button.setItemMeta(meta);

        if (failureReason != null && session != null) {
            session.addButtonFailure(buttonName, failureReason);
        }

        debugManager.logDebug("[DEBUG - CreateGUI] Created button '" + buttonName + "' with material " + button.getType().name());
        return button;
    }

    /**
     * Creates the special add-items button with dynamic content based on item count
     * // note: Generates button for managing item rewards with configurable appearance from CreateGUI.yml Plugin-Items
     */
    private ItemStack createAddItemsButton(FileConfiguration config, BountyCreationSession session, double moneyValue, int expValue, String timeValue, int itemCount, double itemValue, double totalValue, String duration) {
        DebugManager debugManager = BountiesPlus.getInstance().getDebugManager();
        String buttonName = "add-items-button";
        String path = "Plugin-Items." + buttonName;
        String failureReason = null;

        boolean hasItems = itemCount > 0;
        String configPath = hasItems ? path + ".has-items" : path + ".no-items";
        String materialPath = hasItems ? configPath + ".material" : path + ".material";
        String materialName = config.getString(materialPath, hasItems ? "ENDER_CHEST" : "CHEST");
        Material defaultMaterial = hasItems ? Material.ENDER_CHEST : Material.CHEST;
        ItemStack button = new ItemStack(VersionUtils.getMaterialSafely(materialName, defaultMaterial.name()));

        if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            debugManager.logWarning("[DEBUG - CreateGUI] Invalid material '" + materialName + "' at " + materialPath + ", using default " + defaultMaterial.name());
            button = new ItemStack(defaultMaterial);
            failureReason = "Invalid material '" + materialName + "'";
        }

        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - CreateGUI] Failed to get ItemMeta for add-items-button at " + materialPath);
            failureReason = "Failed to get ItemMeta";
            button = new ItemStack(defaultMaterial);
            meta = Bukkit.getItemFactory().getItemMeta(button.getType());
            if (meta == null) {
                debugManager.logWarning("[DEBUG - CreateGUI] Failed to create default ItemMeta for add-items-button, using " + defaultMaterial.name() + " with default meta");
                failureReason = "Failed to create ItemMeta";
            }
        }

        PlaceholderContext context = PlaceholderContext.create().player(player).bountyCount(0).moneyValue(moneyValue).expValue(expValue).timeValue(duration).itemCount(itemCount).itemValue(itemValue).taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).taxAmount(moneyValue * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0)).moneyLine("&7Money: &a" + CurrencyUtil.formatMoney(moneyValue)).experienceLine("&7Experience: &e" + (expValue == 0 ? "0 XP Levels" : expValue + " XP Level" + (expValue > 1 ? "s" : ""))).totalBountyAmount(totalValue).currentPage(0).totalPages(1);
        if (session != null && session.getTargetUUID() != null) {
            context = context.target(session.getTargetUUID());
        }

        String namePath = configPath + ".name";
        String name = config.getString(namePath, hasItems ? "&6&lEdit Items (&e%bountiesplus_item_count%&6&l)" : "&b&lAdd Items");
        if (name == null || name.isEmpty()) {
            debugManager.logWarning("[DEBUG - CreateGUI] Invalid or empty name at " + namePath + ", using default");
            name = hasItems ? "&6&lEdit Items (&e%bountiesplus_item_count%&6&l)" : "&b&lAdd Items";
            failureReason = failureReason != null ? failureReason + ", Invalid name" : "Invalid name";
        }
        meta.setDisplayName(MessageUtils.formatMessage(Placeholders.apply(name, context), player));

        String lorePath = configPath + ".lore";
        List<String> lore = config.getStringList(lorePath);
        if (lore.isEmpty()) {
            debugManager.logWarning("[DEBUG - CreateGUI] Empty lore at " + lorePath + ", using default for add-items-button");
            lore = hasItems ? Arrays.asList("&7You have &e%bountiesplus_item_count% items", "&7in this bounty worth &e%bountiesplus_item_value%", "", "&7Drag and Drop more items", "&7into the GUI to add them", "", "&7Right-click items to remove them", "&7Left-click here to view all items", "", "&6&l(!) Click to edit items in the Bounty!") : Arrays.asList("&7Drag and Drop items", "&7into the GUI to add them", "&7to the bounty", "", "&7You can place items in the", "&7empty slots inside the GUI", "", "&7Right-click items to remove them", "", "&b&l(!) Click to add items to the Bounty!");
            failureReason = failureReason != null ? failureReason + ", Empty lore" : "Empty lore";
        }
        meta.setLore(Placeholders.apply(lore, context).stream().map(line -> MessageUtils.formatMessage(line, player)).collect(Collectors.toList()));

        boolean enchantmentGlow = config.getBoolean(configPath + ".enchantment-glow", hasItems);
        if (!config.contains(configPath + ".enchantment-glow")) {
            debugManager.logWarning("[DEBUG - CreateGUI] Missing enchantment-glow setting at " + configPath + ".enchantment-glow, using default " + enchantmentGlow);
            MessageUtils.sendFormattedMessage(player, "missing-config", PlaceholderContext.create().player(player).error(configPath + ".enchantment-glow"));
        }
        VersionUtils.applyGlow(button, enchantmentGlow);

        if (VersionUtils.isPost19()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        button.setItemMeta(meta);

        if (failureReason != null && session != null) {
            session.addButtonFailure(buttonName, failureReason);
        }

        debugManager.logDebug("[DEBUG - CreateGUI] Created button 'add-items-button' with material " + button.getType().name());
        return button;
    }

    /**
     * Handles inventory click events for the CreateGUI
     * // note: Manages button, player head, and pause-button interactions
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getWhoClicked().equals(this.player)) return;

        // Ignore clicks if AddItemsGUI is active for this player
        if (AddItemsGUI.getActiveInstance(player.getUniqueId()) != null) {
            plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Click ignored for " + player.getName() + ": AddItemsGUI is active");
            return;
        }

        if (event.getInventory().getHolder() != this) {
            plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Click ignored for " + player.getName() + ": inventory holder is not this CreateGUI, title='" + event.getView().getTitle() + "', expected='" + GUI_TITLE + "'");
            return;
        }

        event.setCancelled(true); // Cancel all clicks to prevent item movement
        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Click detected by " + clickingPlayer.getName() + " on slot " + slot + ", item: " + (clickedItem != null ? clickedItem.getType().name() : "null") + ", hasMeta: " + (clickedItem != null && clickedItem.hasItemMeta()));

        // Prevent rapid clicks
        long currentTime = System.currentTimeMillis();
        Long lastClickTime = lastClickTimes.get(clickingPlayer.getUniqueId());
        if (lastClickTime != null && currentTime - lastClickTime < 500) {
            debugManager.logDebug("[DEBUG - CreateGUI] Ignored rapid click by " + clickingPlayer.getName() + " on slot " + slot);
            return;
        }
        lastClickTimes.put(clickingPlayer.getUniqueId(), currentTime);

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            debugManager.logDebug("[DEBUG - CreateGUI] Click ignored on slot " + slot + " by " + clickingPlayer.getName() + ": item is null or lacks ItemMeta");
            return;
        }

        FileConfiguration config = plugin.getCreateGUIConfig();
        int confirmSlot = config.getInt("confirm-button.slot", 52);
        BountyCreationSession session = BountyCreationSession.getSession(clickingPlayer);

        // Ignore clicks on filler-item slot when no session is active
        if (slot == confirmSlot && (session == null || !session.hasChanges())) {
            debugManager.logDebug("[DEBUG - CreateGUI] Ignored click on filler-item slot " + slot + " by " + clickingPlayer.getName() + ": no active session");
            return;
        }

        if (slot == 45 && clickedItem.getType() == Material.ARROW) {
            if (currentPage > 0) {
                currentPage--;
                refreshGUI();
                debugManager.bufferDebug("[DEBUG - CreateGUI] Previous page clicked by " + clickingPlayer.getName());
            }
        } else if (slot == 53 && clickedItem.getType() == Material.ARROW) {
            int totalPages = (int) Math.ceil((double) availablePlayers.size() / 28.0);
            if (currentPage < totalPages - 1) {
                currentPage++;
                refreshGUI();
                debugManager.bufferDebug("[DEBUG - CreateGUI] Next page clicked by " + clickingPlayer.getName());
            }
        } else if (slot == config.getInt("confirm-button.slot", 52)) {
            if (session == null) {
                debugManager.logWarning("[DEBUG - CreateGUI] No session found for confirm button click by " + clickingPlayer.getName());
                MessageUtils.sendFormattedMessage(clickingPlayer, "bounty-session-not-started");
                return;
            }
            handleConfirmButtonDirect(clickingPlayer, session);
        } else if (slot == config.getInt("add-money-button.slot", 48)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handleAddMoneyButton(clickingPlayer, newSession);
        } else if (slot == config.getInt("add-experience-button.slot", 47)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handleAddExperienceButton(clickingPlayer, newSession);
        } else if (slot == config.getInt("add-time-button.slot", 51)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handleAddTimeButton(clickingPlayer, newSession);
        } else if (slot == config.getInt("total-bounty-value-button.slot", 49)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handleTotalValueButton(clickingPlayer, newSession);
        } else if (slot == config.getInt("add-items-button.slot", 50)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handleAddItemsButton(clickingPlayer, newSession);
        } else if (slot == config.getInt("cancel-button.slot", 46)) {
            handleCancelButton(clickingPlayer);
        } else if (slot == config.getInt("pause-button.slot", 4) && config.getBoolean("pause-button.enabled", false)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handlePauseButton(clickingPlayer, newSession);
        } else if (VersionUtils.isPlayerHead(clickedItem)) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(clickingPlayer);
            handlePlayerHeadClick(clickingPlayer, clickedItem, newSession);
        }
        clickingPlayer.updateInventory();
    }

    /**
     * Processes the confirm button directly without anonymous prompt
     * // note: Places the bounty after validation and deductions, broadcasts big bounties
     */
    public void handleConfirmButtonDirect(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        if (!session.isComplete()) {
            String validationError = session.getValidationError();
            MessageUtils.sendFormattedMessage(player, validationError);
            debugManager.logDebug("[CreateGUI] Invalid bounty for " + player.getName() + ": " + validationError);
            return;
        }

        UUID targetUUID = session.getTargetUUID();
        if (targetUUID == null) {
            MessageUtils.sendFormattedMessage(player, "no-target-selected");
            debugManager.logDebug("[CreateGUI] No target selected for " + player.getName());
            return;
        }

        double money = session.getMoney();
        int experienceLevels = session.getExperience();
        List<ItemStack> itemRewards = session.getItemRewards();
        int durationMinutes = session.getTimeMinutes();
        boolean isAnonymous = session.isAnonymous();

        TaxManager taxManager = plugin.getTaxManager();
        double taxAmount = taxManager.calculateTax(money, itemRewards);
        double totalCost = money + taxAmount;
        Economy economy = BountiesPlus.getEconomy();
        if (economy != null && !economy.has(player, totalCost)) {
            PlaceholderContext context = PlaceholderContext.create().player(player).moneyValue(totalCost).taxAmount(taxAmount);
            MessageUtils.sendFormattedMessage(player, "bounty-insufficient-funds", context);
            debugManager.logDebug("[CreateGUI] Insufficient funds for " + player.getName() + ": needs $" + totalCost);
            return;
        }

        boolean useXpLevels = plugin.getConfig().getBoolean("bounties.use-xp-levels", false);
        int playerXp = useXpLevels ? player.getLevel() : player.getTotalExperience();
        if (experienceLevels > 0 && playerXp < experienceLevels) {
            PlaceholderContext context = PlaceholderContext.create().player(player).expValue(experienceLevels).expValue(playerXp);
            MessageUtils.sendFormattedMessage(player, "no-experience-levels", context);
            debugManager.logDebug("[CreateGUI] Insufficient XP for " + player.getName() + ": needs " + experienceLevels);
            return;
        }

        if (economy != null && totalCost > 0) {
            economy.withdrawPlayer(player, totalCost);
        }

        if (experienceLevels > 0) {
            if (useXpLevels) {
                player.setLevel(player.getLevel() - experienceLevels);
            } else {
                player.setTotalExperience(player.getTotalExperience() - experienceLevels);
            }
        }

        for (ItemStack item : itemRewards) {
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().removeItem(item);
            }
        }

        long expireTime = durationMinutes > 0 ? System.currentTimeMillis() + (durationMinutes * 60 * 1000L) : -1;
        if (isAnonymous) {
            plugin.getBountyManager().addAnonymousBounty(targetUUID, player.getUniqueId(), money, experienceLevels, durationMinutes, itemRewards);
        } else {
            plugin.getBountyManager().setBounty(player.getUniqueId(), targetUUID, (int) money, expireTime);
        }

        if (experienceLevels > 0 || !itemRewards.isEmpty() || !session.isPermanent()) {
            Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
            if (bounty != null) {
                bounty.addContribution(player.getUniqueId(), 0, experienceLevels, durationMinutes, itemRewards, isAnonymous, false);
                FileConfiguration config = plugin.getBountiesConfig();
                String path = "bounties." + targetUUID + "." + player.getUniqueId();
                config.set(path + ".xp", experienceLevels);
                config.set(path + ".duration", durationMinutes);
                List<String> itemStrings = itemRewards.stream().filter(item -> item != null && !item.getType().equals(Material.AIR)).map(item -> item.getType().name() + ":" + item.getAmount()).collect(Collectors.toList());
                config.set(path + ".items", itemStrings);
                config.set("anonymous-bounties." + targetUUID + "." + player.getUniqueId(), isAnonymous);
                plugin.saveEverything();
            }
            debugManager.logDebug("[CreateGUI] Complex bounty created by " + player.getName() + " on " + session.getTargetName() + " - Money: $" + money + ", XP: " + experienceLevels + " levels, Items: " + itemRewards.size() + ", Anonymous: " + isAnonymous);
        }

        String messageKey = durationMinutes > 0 ? "bounty-set-success-timed" : "bounty-set-success";
        PlaceholderContext context = PlaceholderContext.create().player(player).target(targetUUID).moneyValue(money).taxAmount(taxAmount);
        if (durationMinutes > 0) {
            long timeValue = durationMinutes * 60;
            String unit = timeValue >= 24 * 60 * 60 ? "Days" : timeValue >= 60 * 60 ? "Hours" : "Minutes";
            long displayTime = timeValue >= 24 * 60 * 60 ? timeValue / (24 * 60 * 60) : timeValue >= 60 * 60 ? timeValue / (60 * 60) : timeValue / 60;
            context = context.time(String.valueOf(displayTime)).unit(unit);
        }
        MessageUtils.sendFormattedMessage(player, messageKey, context);

        taxManager.sendTaxMessages(player, targetUUID, money, taxAmount);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null && BountiesPlus.getInstance().getNotifySettings().getOrDefault(targetUUID, true)) {
            MessageUtils.sendFormattedMessage(targetPlayer, "bounty-received", PlaceholderContext.create().player(targetPlayer).moneyValue(money).setter(player.getUniqueId()));
            debugManager.logDebug("[CreateGUI] Sent bounty-received message to " + targetPlayer.getName());
        } else {
            debugManager.logDebug("[CreateGUI] Skipped bounty-received message for target " + targetUUID + ": offline or notifications disabled");
        }

        if (plugin.isBountySoundEnabled()) {
            try {
                player.getWorld().playSound(player.getLocation(), Sound.valueOf(plugin.getBountySoundName()), plugin.getBountySoundVolume(), plugin.getBountySoundPitch());
            } catch (IllegalArgumentException e) {
                debugManager.logWarning("[CreateGUI] Invalid sound name: " + plugin.getBountySoundName());
            }
        }

        BountyCreationSession.removeSession(player);
        player.closeInventory();
    }

    /**
     * Handles the Add Items button click, opening the AddItemsGUI
     * // note: Transitions to item management GUI
     */
    private void handleAddItemsButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Add Items button clicked by " + player.getName());

        try {
            // Set transition flag to prevent onInventoryClose from reopening BountyGUI
            this.isTransitioningToAddItems = true;

            // Close current inventory first
            player.closeInventory();

            // Create and open AddItemsGUI
            AddItemsGUI addItemsGUI = new AddItemsGUI(player, plugin.getEventManager());

            // Schedule opening of AddItemsGUI
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                addItemsGUI.openInventory(player);
                debugManager.logDebug("[DEBUG - CreateGUI] Opened AddItemsGUI for player: " + player.getName());
            }, 3L); // Increased to 3 ticks for stability
        } catch (Exception e) {
            debugManager.logWarning("[DEBUG - CreateGUI] Failed to open AddItemsGUI for player " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Failed to open item management GUI. Please try again.");
            this.isTransitioningToAddItems = false; // Reset flag on failure
        }
    }

    /**
     * Handles clicks on player heads in the GUI
     * // note: Selects or deselects bounty targets and refreshes GUI for move-to-first-slot
     */
    private void handlePlayerHeadClick(Player player, ItemStack headItem, BountyCreationSession session) {
        if (!headItem.hasItemMeta() || !(headItem.getItemMeta() instanceof SkullMeta)) {
            return;
        }
        SkullMeta skullMeta = (SkullMeta) headItem.getItemMeta();
        OfflinePlayer targetPlayer = null;
        if (skullMeta.getOwner() != null) {
            targetPlayer = Bukkit.getOfflinePlayer(skullMeta.getOwner());
        }
        if (targetPlayer != null && targetPlayer.getUniqueId() != null) {
            boolean isValidTarget = false;
            for (OfflinePlayer p : availablePlayers) {
                if (p.getUniqueId().equals(targetPlayer.getUniqueId())) {
                    isValidTarget = true;
                    break;
                }
            }
            if (!isValidTarget) {
                targetPlayer = null;
            }
        }
        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
            MessageUtils.sendFormattedMessage(player, "player-not-found");
            plugin.getLogger().warning("[DEBUG - CreateGUI] Failed to identify target player for skull click by " + player.getName());
            return;
        }
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
        boolean isAlreadySelected = session.hasTarget() && session.getTargetUUID() != null && session.getTargetUUID().equals(targetPlayer.getUniqueId());
        if (isAlreadySelected) {
            session.setTargetPlayer(null);
            session.setTargetPlayerOffline(null);
            plugin.getLogger().info("[DEBUG - CreateGUI] Deselected target " + targetName + " for " + player.getName());
        } else {
            if (targetPlayer.isOnline()) {
                session.setTargetPlayer(targetPlayer.getPlayer());
                plugin.getLogger().info("[DEBUG - CreateGUI] Selected online target " + targetName + " for " + player.getName());
            } else {
                session.setTargetPlayerOffline(targetPlayer);
                plugin.getLogger().info("[DEBUG - CreateGUI] Selected offline target " + targetName + " for " + player.getName());
                MessageUtils.sendFormattedMessage(player, "player-offline");
            }
        }
        refreshGUI(); // Refresh to apply move-to-first-slot
        player.updateInventory(); // Ensure inventory sync
    }

    /**
     * Handles inventory drag events for the CreateGUI
     * // note: Prevents all item additions or removals in the GUI
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!player.equals(this.player)) return;
        if (!(event.getInventory().getHolder() instanceof CreateGUI)) {
            plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Drag ignored for " + player.getName() + ": inventory holder is not CreateGUI, title='" + event.getView().getTitle() + "', expected='" + GUI_TITLE + "'");
            return;
        }

        event.setCancelled(true); // Cancel all drag events to protect GUI
        plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Drag event cancelled for " + player.getName() + " to prevent item movement");
        player.sendMessage(ChatColor.RED + "You cannot place or move items in this GUI!");
    }

    /**
     * Processes bounty creation with tax handling // note: Finalizes bounty placement with money, XP, and item deductions
     */
    private void processBountyCreation(Player player, OfflinePlayer target, double totalMoney, int experienceAmount, List<ItemStack> items) {
        // Calculate tax based on config setting
        double taxRate = plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0) / 100.0;
        boolean taxTotalValue = plugin.getConfig().getBoolean("tax-total-value", false);
        double itemValue = 0.0;
        if (items != null && !items.isEmpty()) {
            ItemValueCalculator calculator = plugin.getItemValueCalculator();
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    itemValue += calculator.calculateItemValue(item);
                }
            }
        }
        double taxableAmount = taxTotalValue ? (totalMoney + itemValue) : totalMoney;
        double taxAmount = taxableAmount * taxRate;
        double totalCost = totalMoney + taxAmount;

        // Check if player has enough money (including tax)
        if (totalCost > 0 && plugin.getEconomy().getBalance(player) < totalCost) {
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String errorMessage = messagesConfig.getString("bounty-insufficient-funds", "&cInsufficient funds! You need $%cost% (includes $%tax% tax)");
            PlaceholderContext context = PlaceholderContext.create().player(player).withAmount(totalCost).taxAmount(taxAmount);
            player.sendMessage(Placeholders.apply(errorMessage, context));
            return;
        }

        // Check XP requirements
        if (experienceAmount > 0 && !CurrencyUtil.hasEnoughXP(player, experienceAmount)) {
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String errorMessage = messagesConfig.getString("no-experience-levels", "&cYou don't have enough experience levels!");
            PlaceholderContext context = PlaceholderContext.create().player(player);
            player.sendMessage(Placeholders.apply(errorMessage, context));
            return;
        }

        // Withdraw total cost (bounty + tax)
        if (totalCost > 0) {
            plugin.getEconomy().withdrawPlayer(player, totalCost);
        }

        // Remove XP
        if (experienceAmount > 0) {
            CurrencyUtil.removeExperience(player, experienceAmount);
        }

        // Remove items from inventory
        if (items != null && !items.isEmpty()) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().removeItem(item);
                }
            }
        }

        // Calculate final bounty amount
        int bountyAmount = (int) totalMoney; // Money portion
        bountyAmount += experienceAmount; // Add XP value

        // Set the bounty
        plugin.getBountyManager().addBounty(target.getUniqueId(), player.getUniqueId(), bountyAmount);

        // Send success message
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        String successMessage = messagesConfig.getString("bounty-set-success", "&aYou placed a bounty of &e%amount%&a on &e%target%&a! Tax of &e%tax%&a was deducted.");
        PlaceholderContext context = PlaceholderContext.create().player(player).target(target.getUniqueId()).withAmount(totalMoney).taxAmount(taxAmount);
        player.sendMessage(Placeholders.apply(successMessage, context));

        // Send tax notification if applicable
        if (taxAmount > 0) {
            String taxMessage = messagesConfig.getString("bounty-cancel-tax", "&eA &c%tax_rate%% &etax has been applied. &eTax amount: &c$%tax_amount%");
            context = context.taxRate(taxRate * 100).taxAmount(taxAmount);
            player.sendMessage(Placeholders.apply(taxMessage, context));
        }

        // Close GUI and cleanup
        player.closeInventory();
    }

    /**
     * Handles the Add Money button click, prompting for input validation
     * // note: Initiates monetary input for bounty or displays no-money title/message
     */
    private void handleAddMoneyButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        Economy economy = BountiesPlus.getEconomy();
        FileConfiguration config = plugin.getCreateGUIConfig();
        debugManager.logDebug("[DEBUG - CreateGUI] Add Money button clicked by " + player.getName());

        if (economy == null) {
            MessageUtils.sendFormattedMessage(player, "bounty-no-economy");
            debugManager.logDebug("[DEBUG - CreateGUI] No economy plugin found for add-money-button action by " + player.getName());
            return;
        }

        // Check if player can afford minimum bounty amount plus tax
        double minBountyAmount = plugin.getConfig().getDouble("min-bounty-amount", 100.0);
        TaxManager taxManager = plugin.getTaxManager();
        double minTaxAmount = taxManager.calculateTax(minBountyAmount, session != null ? session.getItemRewards() : new ArrayList<>());
        double minTotalCost = minBountyAmount + minTaxAmount;
        boolean allowZeroDollarBounties = plugin.getConfig().getBoolean("allow-zero-dollar-bounties", false);
        if (!allowZeroDollarBounties && economy.getBalance(player) < minTotalCost) {
            String title = config.getString("add-money-button.no-money-title", "&cYou don't have any money to add");
            title = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(title, PlaceholderContext.create().player(player)));
            int fadeIn = config.getInt("add-money-button.title-duration.fade-in", 20);
            int stay = config.getInt("add-money-button.title-duration.stay", 60);
            int fadeOut = config.getInt("add-money-button.title-duration.fade-out", 20);
            long configReopenDelay = config.getLong("add-money-button.reopen-delay", 100);
            final long reopenDelay = configReopenDelay < 0 ? 100 : configReopenDelay;

            if (configReopenDelay < 0) {
                plugin.getLogger().warning("[DEBUG - CreateGUI] Invalid add-money-button.reopen-delay " + configReopenDelay + ", using default 100");
            }

            PlaceholderContext context = PlaceholderContext.create().player(player).withAmount(minTotalCost).taxAmount(minTaxAmount);
            MessageUtils.sendFormattedMessage(player, "bounty-insufficient-funds", context);
            if (session != null) {
                session.setAwaitingInput(BountyCreationSession.InputType.NO_MONEY_TITLE);
            }
            player.closeInventory();
            VersionUtils.sendTitle(player, title, "", fadeIn, stay, fadeOut);

            long totalDuration = VersionUtils.isPost111() ? (fadeIn + stay + fadeOut) : reopenDelay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                newGui.openInventory(player);
                if (session != null) {
                    session.clearAwaitingInput();
                }
                debugManager.logDebug("[DEBUG - CreateGUI] Reopened CreateGUI for " + player.getName() + " after no-money title with reopen-delay " + totalDuration + " ticks");
            }, totalDuration);

            return;
        }

        // Delay session creation until response in BountyCreationChatListener
        if (session == null || !session.hasChanges()) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(player);
            newSession.setAwaitingInput(BountyCreationSession.InputType.MONEY);
            debugManager.logDebug("[DEBUG - CreateGUI] Set MONEY input for player: " + player.getName() + ", session awaiting: " + newSession.getAwaitingInput().name());
        } else {
            session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
            debugManager.logDebug("[DEBUG - CreateGUI] Set MONEY input for player: " + player.getName() + ", session awaiting: " + session.getAwaitingInput().name());
        }

        PlaceholderContext context = PlaceholderContext.create().player(player).moneyValue(session != null ? session.getMoney() : 0.0);
        MessageUtils.sendFormattedMessage(player, "add-money-prompt", context);
        player.closeInventory();
    }

    /**
     * Handles the Add Experience button click, prompting for input or showing error
     * // note: Initiates experience level input for bounty or displays no-XP title/message
     */
    private void handleAddExperienceButton(Player player, BountyCreationSession session) {
        FileConfiguration config = plugin.getCreateGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Add Experience button clicked by " + player.getName());

        boolean useXpLevels = plugin.getConfig().getBoolean("use-xp-levels", false);
        int playerXp = useXpLevels ? player.getLevel() : player.getTotalExperience();
        if (playerXp <= 0) {
            String title = config.getString("add-experience-button.no-experience-title", "&cYou don't have any XP levels to add");
            title = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(title, PlaceholderContext.create().player(player)));
            int fadeIn = config.getInt("add-experience-button.title-duration.fade-in", 10);
            int stay = config.getInt("add-experience-button.title-duration.stay", 40);
            int fadeOut = config.getInt("add-experience-button.title-duration.fade-out", 10);
            long reopenDelay = config.getLong("add-experience-button.reopen-delay", 60);

            PlaceholderContext context = PlaceholderContext.create().player(player).expValue(session != null ? session.getExperience() : 0);
            MessageUtils.sendFormattedMessage(player, "no-experience-levels", context);
            if (session != null) {
                session.setAwaitingInput(BountyCreationSession.InputType.NO_EXPERIENCE_TITLE);
            }
            player.closeInventory();
            VersionUtils.sendTitle(player, title, "", fadeIn, stay, fadeOut);

            long totalDuration = VersionUtils.isPost111() ? (fadeIn + stay + fadeOut) : reopenDelay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player, plugin.getEventManager());
                newGui.openInventory(player);
                if (session != null) {
                    session.clearAwaitingInput();
                }
                debugManager.logDebug("[DEBUG - CreateGUI] Reopened CreateGUI for " + player.getName() + " after no-XP title/message");
            }, totalDuration);

            return;
        }

        // Delay session creation until response in BountyCreationChatListener
        if (session == null || !session.hasChanges()) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(player);
            newSession.setAwaitingInput(BountyCreationSession.InputType.EXPERIENCE);
        } else {
            session.setAwaitingInput(BountyCreationSession.InputType.EXPERIENCE);
        }
        PlaceholderContext context = PlaceholderContext.create().player(player).expValue(session != null ? session.getExperience() : 0);
        MessageUtils.sendFormattedMessage(player, "add-xp-prompt", context);
        debugManager.logDebug("[DEBUG - CreateGUI] Prompted " + player.getName() + " for experience input");
        player.closeInventory();
    }

    /**
     * Handles the Add Time button click, prompting for duration input
     * // note: Initiates time duration input for bounty or displays error
     */
    private void handleAddTimeButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Add Time button clicked by " + player.getName());

        if (!plugin.getConfig().getBoolean("time.allow-time", true)) {
            MessageUtils.sendFormattedMessage(player, "time-disabled");
            debugManager.logDebug("[DEBUG - CreateGUI] Time settings disabled for " + player.getName());
            return;
        }

        // Delay session creation until response in BountyCreationChatListener
        if (session == null || !session.hasChanges()) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(player);
            newSession.setAwaitingInput(BountyCreationSession.InputType.TIME);
        } else {
            session.setAwaitingInput(BountyCreationSession.InputType.TIME);
        }
        PlaceholderContext context = PlaceholderContext.create().player(player).timeValue(session != null ? session.getFormattedTime() : "Default");
        MessageUtils.sendFormattedMessage(player, "add-time-prompt", context);
        player.closeInventory();
    }

    /**
     * Handles the Total Bounty Value button click, prompting for player name input
     * // note: Initiates chat prompt for selecting bounty target and refreshes GUI for move-to-first-slot
     */
    private void handleTotalValueButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Total Value button clicked by " + player.getName());

        // Prevent duplicate prompts if already awaiting input
        if (session != null && session.getAwaitingInput() == BountyCreationSession.InputType.PLAYER_NAME) {
            return;
        }

        // Delay session creation until response in BountyCreationChatListener
        if (session == null || !session.hasChanges()) {
            BountyCreationSession newSession = BountyCreationSession.getOrCreateSession(player);
            newSession.setAwaitingInput(BountyCreationSession.InputType.PLAYER_NAME);
        } else {
            session.setAwaitingInput(BountyCreationSession.InputType.PLAYER_NAME);
        }
        debugManager.logDebug("[DEBUG - CreateGUI] Set PLAYER_NAME input for " + player.getName());

        PlaceholderContext context = PlaceholderContext.create().player(player).target(session != null ? session.getTargetUUID() : null);
        MessageUtils.sendFormattedMessage(player, "add-player-prompt", context);
        player.closeInventory();
        debugManager.logDebug("[DEBUG - CreateGUI] Sent player name prompt and closed GUI for " + player.getName());
    }

    /**
     * Handles the Cancel button click, prompting for confirmation if changes exist
     * // note: Initiates bounty creation cancellation with confirmation prompt or returns to BountyGUI
     */
    private void handleCancelButton(Player player) {
        BountyCreationSession session = BountyCreationSession.getSession(player);
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Cancel button clicked by " + player.getName());

        if (session == null || !session.hasChanges()) {
            player.closeInventory();
            BountyCreationSession.removeSession(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BountyGUI.openBountyGUI(player, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage());
                debugManager.logDebug("[DEBUG - CreateGUI] Returned to BountyGUI for " + player.getName() + ": no active session");
            }, 3L);
            MessageUtils.sendFormattedMessage(player, "no-changes");
        } else {
            session.setAwaitingInput(BountyCreationSession.InputType.CANCEL_CONFIRMATION);
            debugManager.logDebug("[DEBUG - CreateGUI] Set CANCEL_CONFIRMATION input for " + player.getName());
            MessageUtils.sendFormattedMessage(player, "cancel-confirmation-prompt");
            player.closeInventory();
        }
    }

    /**
     * Handles inventory close events, managing session state
     * // note: Controls session cleanup, forces GUI reopen for active sessions, and allows pause to persist
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof CreateGUI)) {
            plugin.getDebugManager().logDebug("[DEBUG - CreateGUI] Close ignored for " + event.getPlayer().getName() + ": inventory holder is not CreateGUI, title='" + event.getView().getTitle() + "', expected='" + GUI_TITLE + "'");
            return;
        }
        if (!event.getPlayer().equals(player)) return;

        Player closingPlayer = (Player) event.getPlayer();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Inventory closed by " + closingPlayer.getName());

        // Ignore closures within 100ms of opening to prevent race conditions
        if (System.currentTimeMillis() - openTime < 100) {
            debugManager.logDebug("[DEBUG - CreateGUI] Ignoring early inventory close for " + closingPlayer.getName());
            return;
        }

        // Skip processing if transitioning to AddItemsGUI
        if (isTransitioningToAddItems) {
            debugManager.logDebug("[DEBUG - CreateGUI] Skipping close processing for " + closingPlayer.getName() + ": transitioning to AddItemsGUI");
            return;
        }

        BountyCreationSession session = BountyCreationSession.getSession(closingPlayer);
        if (session == null) {
            debugManager.logDebug("[DEBUG - CreateGUI] No session found for " + closingPlayer.getName() + ", allowing close");
            cleanup();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BountyGUI.openBountyGUI(closingPlayer, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage());
            }, 3L);
            return;
        }

        // Allow closing if session is paused
        if (session.isPaused()) {
            debugManager.logDebug("[DEBUG - CreateGUI] Allowing close for " + closingPlayer.getName() + ": session is paused");
            cleanup();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BountyGUI.openBountyGUI(closingPlayer, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage());
            }, 3L);
            return;
        }

        // Allow closing if awaiting input for prompts
        if (session.isAwaitingInput()) {
            debugManager.logDebug("[DEBUG - CreateGUI] Preserving session for " + closingPlayer.getName() + ": awaiting input type " + session.getAwaitingInput().name());
            return;
        }

        // Allow closing if confirm button was pressed and bounty is valid
        if (session.isConfirmPressed() && session.isValid()) {
            debugManager.logDebug("[DEBUG - CreateGUI] Skipping GUI reopen for " + closingPlayer.getName() + ": confirm pressed with valid bounty");
            cleanup();
            return;
        }

        // Prevent closing with active session
        if (session.hasChanges()) {
            FileConfiguration config = plugin.getCreateGUIConfig();
            boolean titleEnabled = config.getBoolean("close-with-session.enabled", true);

            if (titleEnabled) {
                String title = config.getString("close-with-session.title", "&c&l&nCANCEL&c you're Bounty");
                String subtitle = config.getString("close-with-session.subtitle", "&7Before closing the GUI");
                int fadeIn = config.getInt("close-with-session.title-duration.fade-in", 20);
                int stay = config.getInt("close-with-session.title-duration.stay", 60);
                int fadeOut = config.getInt("close-with-session.title-duration.fade-out", 20);
                long reopenDelay = config.getLong("close-with-session.reopen-delay", 100);

                if (reopenDelay < 0) {
                    plugin.getLogger().warning("[DEBUG - CreateGUI] Invalid close-with-session.reopen-delay " + reopenDelay + ", using default 100");
                    reopenDelay = 100;
                }

                title = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(title, PlaceholderContext.create().player(closingPlayer)));
                subtitle = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(subtitle, PlaceholderContext.create().player(closingPlayer)));
                session.setAwaitingInput(BountyCreationSession.InputType.CLOSE_WITH_SESSION_TITLE);
                VersionUtils.sendTitle(closingPlayer, title, subtitle, fadeIn, stay, fadeOut);

                long totalDuration = VersionUtils.isPost111() ? (fadeIn + stay + fadeOut) : reopenDelay;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(closingPlayer, plugin.getEventManager());
                    newGui.openInventory(closingPlayer);
                    session.clearAwaitingInput();
                    debugManager.logDebug("[DEBUG - CreateGUI] Reopened CreateGUI for " + closingPlayer.getName() + " after close-with-session title with duration " + totalDuration + " ticks");
                }, totalDuration);

                debugManager.logDebug("[DEBUG - CreateGUI] Prevented GUI close for " + closingPlayer.getName() + ": active session with changes, showing title");
            } else {
                debugManager.logDebug("[DEBUG - CreateGUI] Forcing CreateGUI reopen for " + closingPlayer.getName() + ": active session with changes, title disabled");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    CreateGUI newGui = new CreateGUI(closingPlayer, plugin.getEventManager());
                    newGui.openInventory(closingPlayer);
                }, 3L);
            }
            return;
        }

        // Allow closing and return to BountyGUI if no changes
        BountyCreationSession.removeSession(closingPlayer);
        cleanup();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BountyGUI.openBountyGUI(closingPlayer, BountyGUI.getFilterHighToLow(), BountyGUI.getShowOnlyOnline(), BountyGUI.getCurrentPage());
        }, 3L);
    }

    /**
     * Handles the Pause button click, pausing the session
     * // note: Pauses the bounty creation session and closes the GUI
     */
    private void handlePauseButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("[DEBUG - CreateGUI] Pause button clicked by " + player.getName());

        if (session == null || !session.hasChanges()) {
            debugManager.logDebug("[DEBUG - CreateGUI] Ignored pause button click by " + player.getName() + ": no active session");
            return;
        }

        FileConfiguration config = plugin.getCreateGUIConfig();
        String messagePath = "pause-button.send-alert.session-paused";
        String message = config.getString(messagePath, "&aBounty creation session paused. Reopen /bounty to resume.");
        if (!config.contains(messagePath)) {
            debugManager.logWarning("[DEBUG - CreateGUI] Missing message at " + messagePath + " in CreateGUI.yml, using default");
        }
        session.pauseSession();
        String formattedMessage = MessageUtils.formatMessage(message, player, PlaceholderContext.create().player(player));
        player.spigot().sendMessage(TextComponent.fromLegacyText(formattedMessage));
        player.closeInventory();
        debugManager.logDebug("[DEBUG - CreateGUI] Closed GUI for " + player.getName() + " after pausing session");
    }
}