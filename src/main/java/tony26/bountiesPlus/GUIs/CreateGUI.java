package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import java.util.*;


import java.util.*;

public class CreateGUI implements InventoryHolder, Listener {

    private String GUI_TITLE; // Remove static and final keywords

    // Define protected slots that cannot be modified by players
    private Set<Integer> protectedSlots = new HashSet<>();

    public void cleanup() {
        // Unregister this instance as a listener to prevent memory leaks and duplicate events
        HandlerList.unregisterAll(this);
    }

    private void loadProtectedSlots(FileConfiguration config) {
        protectedSlots.clear();

        // ADD SAFETY CHECK - Don't proceed if inventory isn't ready
        if (inventory == null) {
            return; // Exit early if inventory not created yet
        }

        // Use the same slots as borders for protected slots
        List<Integer> configSlots = config.getIntegerList("border.slots");

        if (!configSlots.isEmpty()) {
            protectedSlots.addAll(configSlots);
        } else {
            // Use default protected slots if none configured
            List<Integer> defaultSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
            protectedSlots.addAll(defaultSlots);
        }

        // FIXED VERSION - Java 8 compatible check for non-empty slots
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            // Use Material.AIR check instead of isAir() for Java 8 compatibility
            if (item != null && item.getType() != Material.AIR) {
                protectedSlots.add(i);
            }
        }
    }

    private final Inventory inventory;
    private final Player player;
    private final BountiesPlus plugin; // ADD THIS LINE - you were missing it!
    private int currentPage = 0;
    private List<OfflinePlayer> availablePlayers = new ArrayList<>();

    public CreateGUI(Player player) {
        this.player = player;
        this.plugin = BountiesPlus.getInstance();

        FileConfiguration config = plugin.getCreateGUIConfig();
        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&',
                config.getString("gui-title", "&6Create Bounty"));

        // MOVE inventory creation FIRST
        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE);

        // Register this as a listener when created
        BountiesPlus.getInstance().getServer().getPluginManager().registerEvents(this, BountiesPlus.getInstance());

        // Initialize the GUI AFTER inventory is created
        initializeGUI();

        // REMOVE this line completely - we'll call it later when GUI is ready
        // loadProtectedSlots(config);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void openInventory(Player player) {
        updateProtectedSlots();
        if (!player.equals(this.player)) {
            return; // Safety check
        }

        // Refresh the GUI before opening
        refreshGUI();
        player.openInventory(inventory);
    }

    private void initializeGUI() {

        // Load available players
        loadAvailablePlayers();

        // Set up the GUI
        refreshGUI();
    }

    /**
     * Loads the list of players available for bounty creation // note: Filters and sorts players for GUI display
     */
    private void loadAvailablePlayers() {
        boolean showOfflinePlayers = plugin.getConfig().getBoolean("allow-offline-players", true);
        Set<OfflinePlayer> uniquePlayers = new HashSet<>();
        BoostedBounty boostedBounty = plugin.getBoostedBounty();
        boolean isFrenzyActive = plugin.getFrenzy() != null && plugin.getFrenzy().isFrenzyActive();
        plugin.getLogger().info("Loading available players. Show offline: " + showOfflinePlayers + ", GUI opener: " + player.getName());

        // Skip sorting during Frenzy Mode
        if (!isFrenzyActive && showOfflinePlayers) {
            List<OfflinePlayer> boostedPlayers = new ArrayList<>();
            List<OfflinePlayer> normalPlayers = new ArrayList<>();
            OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();
            plugin.getLogger().info("Found " + allPlayers.length + " total offline players");

            for (OfflinePlayer offlinePlayer : allPlayers) {
                if (offlinePlayer == null || offlinePlayer.getName() == null || offlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                    if (offlinePlayer != null && offlinePlayer.getName() != null) {
                        plugin.getLogger().info("Skipping GUI opener: " + offlinePlayer.getName());
                    }
                    continue;
                }
                if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                    continue;
                }
                boolean isBoosted = boostedBounty != null && boostedBounty.getCurrentBoostedTarget() != null &&
                        boostedBounty.getCurrentBoostedTarget().equals(offlinePlayer.getUniqueId());
                if (isBoosted) {
                    boostedPlayers.add(offlinePlayer);
                } else {
                    normalPlayers.add(offlinePlayer);
                }
                plugin.getLogger().info("Added player: " + offlinePlayer.getName() + " (Online: " + offlinePlayer.isOnline() + ", Boosted: " + isBoosted + ")");
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
                    if (offlinePlayer != null && offlinePlayer.getName() != null) {
                        plugin.getLogger().info("Skipping GUI opener: " + offlinePlayer.getName());
                    }
                    continue;
                }
                if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                    continue;
                }
                uniquePlayers.add(offlinePlayer);
                plugin.getLogger().info("Added player: " + offlinePlayer.getName() + " (Online: " + offlinePlayer.isOnline() + ")");
            }
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                uniquePlayers.add(onlinePlayer);
                plugin.getLogger().info("Added online player: " + onlinePlayer.getName());
            }
        }

        availablePlayers = new ArrayList<>(uniquePlayers);
        plugin.getLogger().info("Total players before sorting: " + availablePlayers.size());

        if (!isFrenzyActive && showOfflinePlayers) {
            // Sorting already handled above
        } else {
            availablePlayers.sort((p1, p2) -> {
                if (p1.isOnline() && !p2.isOnline()) return -1;
                if (!p1.isOnline() && p2.isOnline()) return 1;
                return p1.getName().compareToIgnoreCase(p2.getName());
            });
        }

        plugin.getLogger().info("Final available players count: " + availablePlayers.size());
        for (int i = 0; i < Math.min(5, availablePlayers.size()); i++) {
            OfflinePlayer p = availablePlayers.get(i);
            plugin.getLogger().info("Player " + i + ": " + p.getName() + " (Online: " + p.isOnline() + ")");
        }
    }

    // If you have a total value display, update it here
    private void updateTotalValueDisplay(BountyCreationSession session) {
        addBottomRowButtons();
    }

    private void refreshGUI() {
        // Clear the inventory
        inventory.clear();

        // Add borders
        addBorders();

        // Add player heads with pagination
        addPlayerHeads();

        // Add bottom row buttons
        addBottomRowButtons();

        // Update GUI Items based on session state (if exists)
        updateSessionDisplay();

        // ADD THIS LINE - Update protected slots after refreshing content
        updateProtectedSlots();
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
     * Adds border items to the GUI based on configuration // note: Populates border slots with configured material
     */
    private void addBorders() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        // Check if borders are enabled
        if (!config.getBoolean("border.enabled", true)) {
            return;
        }
        // Get border configuration
        String materialName = config.getString("border.material", "WHITE_STAINED_GLASS_PANE");
        boolean enchantmentGlow = config.getBoolean("border.enchantment-glow", false);
        List<Integer> borderSlots = config.getIntegerList("border.slots");
        // If no slots configured, use default border layout
        if (borderSlots.isEmpty()) {
            borderSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
        }
        // Create border item with configured material
        Material borderMaterial = VersionUtils.getMaterialSafely(materialName, "STAINED_GLASS_PANE");
        if (!VersionUtils.isGlassPane(new ItemStack(borderMaterial))) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "' in CreateGUI.yml, using WHITE_STAINED_GLASS_PANE");
            borderMaterial = VersionUtils.getWhiteGlassPaneMaterial();
        }
        ItemStack borderItem = new ItemStack(borderMaterial);
        ItemMeta borderMeta = borderItem.getItemMeta();
        if (borderMeta != null) {
            // Set empty display name (no customization)
            borderMeta.setDisplayName(" ");
            // Add enchantment glow if enabled
            if (enchantmentGlow) {
                borderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                borderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            borderItem.setItemMeta(borderMeta);
        }
        // Apply borders to configured slots
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < 54) {
                inventory.setItem(slot, borderItem);
            } else {
                plugin.getLogger().warning("Invalid slot " + slot + " in CreateGUI.yml border configuration (must be 0-53)");
            }
        }
        // Add custom filler items for empty slots
        addCustomFillerItems();
    }

    /**
     * Adds custom filler items to empty slots based on configuration // note: Populates non-protected slots with configurable items
     */
    private void addCustomFillerItems() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        if (!config.contains("Custom-Items")) {
            plugin.getLogger().warning("Missing 'Custom-Items' section in CreateGUI.yml, using default filler");
            addDefaultFillerItem();
            return;
        }
        // Get custom items configuration
        for (String itemKey : config.getConfigurationSection("Custom-Items").getKeys(false)) {
            String path = "Custom-Items." + itemKey;
            String materialName = config.getString(path + ".Material", "WHITE_STAINED_GLASS_PANE");
            Material material = VersionUtils.getMaterialSafely(materialName, "WHITE_STAINED_GLASS_PANE");
            if (!VersionUtils.isGlassPane(new ItemStack(material))) {
                plugin.getLogger().warning("Invalid material '" + materialName + "' for custom item '" + itemKey + "' in CreateGUI.yml, using WHITE_STAINED_GLASS_PANE");
                material = VersionUtils.getWhiteGlassPaneMaterial();
            }
            String name = config.getString(path + ".Name", " ");
            List<String> lore = config.getStringList(path + ".Lore");
            boolean enchantmentGlow = config.getBoolean(path + ".Enchantment-Glow", false);
            List<Integer> slots = config.getIntegerList(path + ".Slots");
            if (slots.isEmpty()) {
                plugin.getLogger().warning("No slots defined for custom item '" + itemKey + "' in CreateGUI.yml, skipping");
                continue;
            }
            ItemStack fillerItem = new ItemStack(material);
            ItemMeta meta = fillerItem.getItemMeta();
            if (meta != null) {
                PlaceholderContext context = PlaceholderContext.create().player(player);
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));
                if (!lore.isEmpty()) {
                    meta.setLore(Placeholders.apply(lore, context));
                }
                if (enchantmentGlow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                fillerItem.setItemMeta(meta);
            }
            // Apply filler item to specified slots, only if not already occupied
            for (int slot : slots) {
                if (slot >= 0 && slot < 54) {
                    ItemStack existingItem = inventory.getItem(slot);
                    if (existingItem == null || existingItem.getType() == Material.AIR) {
                        inventory.setItem(slot, fillerItem);
                        protectedSlots.add(slot);
                    }
                } else {
                    plugin.getLogger().warning("Invalid slot " + slot + " for custom item '" + itemKey + "' in CreateGUI.yml (must be 0-53)");
                }
            }
        }
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
        // Fill all non-protected, empty slots
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
     * Adds player heads to the GUI with configurable settings and placeholders // note: Populates GUI with player skulls for bounty target selection
     */
    private void addPlayerHeads() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        plugin.getLogger().info("Loading player-head settings from CreateGUI.yml");
        int baseSlot = config.getInt("player-head.slot", 10);
        if (baseSlot < 0 || baseSlot > 53) {
            plugin.getLogger().warning("Invalid player-head.slot " + baseSlot + ", using default 10");
            baseSlot = 10;
        }
        plugin.getLogger().info("Loaded player-head.slot: " + baseSlot);
        List<Integer> contentSlots = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 7; col++) {
                int slot = baseSlot + (row * 9) + col;
                if (slot >= 0 && slot < 54) {
                    contentSlots.add(slot);
                } else {
                    plugin.getLogger().warning("Calculated slot " + slot + " out of range (0-53), skipping");
                }
            }
        }
        int itemsPerPage = contentSlots.size();
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availablePlayers.size());
        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);
        UUID selectedTargetUUID = session.getTargetUUID();
        // Clear existing heads to ensure correct updates
        for (int slot : contentSlots) {
            inventory.setItem(slot, null);
        }
        String materialName = config.getString("player-head.material", "PLAYER_HEAD");
        Material skullMaterial = VersionUtils.getMaterialSafely(materialName, "PLAYER_HEAD");
        if (!VersionUtils.isPlayerHead(new ItemStack(skullMaterial))) {
            plugin.getLogger().warning("Invalid material " + materialName + " for player-head, using PLAYER_HEAD");
            skullMaterial = VersionUtils.getMaterialSafely("PLAYER_HEAD", "SKULL_ITEM");
        }
        plugin.getLogger().info("Loaded player-head.material: " + skullMaterial.name());
        boolean isFrenzyActive = plugin.getFrenzy() != null && plugin.getFrenzy().isFrenzyActive();
        boolean isBoosted;
        for (int i = startIndex; i < endIndex; i++) {
            OfflinePlayer targetPlayer = availablePlayers.get(i);
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
            ItemStack head = SkullUtils.createVersionAwarePlayerHead(targetPlayer);
            if (head == null || !VersionUtils.isPlayerHead(head)) {
                plugin.getLogger().warning("Failed to create player head for " + targetName + " (UUID: " + targetPlayer.getUniqueId() + "), using fallback");
                head = VersionUtils.getXMaterialItemStack("SKELETON_SKULL");
            }
            ItemMeta meta = head.getItemMeta();
            if (meta == null || !(meta instanceof SkullMeta)) {
                plugin.getLogger().warning("Failed to get SkullMeta for player head of " + targetName + " at slot " + contentSlots.get(i - startIndex));
                continue;
            }
            SkullMeta skullMeta = (SkullMeta) meta;
            plugin.getLogger().info("Skull for " + targetName + " before meta changes: owner=" + skullMeta.getOwner());
            boolean isSelected = selectedTargetUUID != null && selectedTargetUUID.equals(targetPlayer.getUniqueId());
            boolean isOnline = targetPlayer.isOnline();
            isBoosted = !isFrenzyActive && plugin.getBoostedBounty() != null &&
                    plugin.getBoostedBounty().getCurrentBoostedTarget() != null &&
                    plugin.getBoostedBounty().getCurrentBoostedTarget().equals(targetPlayer.getUniqueId());
            Map<UUID, Integer> targetBounties = plugin.getBountyManager().getBountiesOnTarget(targetPlayer.getUniqueId());
            int bountyCount = targetBounties != null ? targetBounties.size() : 0;
            double totalBounty = targetBounties != null ? targetBounties.values().stream().mapToDouble(Integer::doubleValue).sum() : 0.0;
            UUID setterUUID = player.getUniqueId();
            double bountyAmount = session.getMoney();
            String setTime = null;
            String expireTime = null;
            double multiplier = plugin.getBountyManager().getManualMoneyBoostMultiplier(targetPlayer.getUniqueId());
            if (targetBounties != null && !targetBounties.isEmpty()) {
                setterUUID = targetBounties.keySet().iterator().next();
                bountyAmount = targetBounties.get(setterUUID);
                setTime = plugin.getBountyManager().getBountySetTime(setterUUID, targetPlayer.getUniqueId());
                expireTime = plugin.getBountyManager().getBountyExpireTime(setterUUID, targetPlayer.getUniqueId());
                multiplier = plugin.getBountyManager().getBountyMultiplier(setterUUID, targetPlayer.getUniqueId());
            }
            double taxRate = plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0);
            double taxAmount = session.getMoney() * taxRate;
            String onlineStatus = getOnlineStatusText(targetPlayer, config);
            String lastSeen = !isOnline && targetPlayer.getLastPlayed() > 0 ?
                    TimeFormatter.formatTimestampToAgo(targetPlayer.getLastPlayed() / 1000) : "";
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .target(targetPlayer.getUniqueId())
                    .bountyCount(bountyCount)
                    .totalBountyAmount(totalBounty)
                    .moneyValue(session.getMoney())
                    .expValue(session.getExperience())
                    .timeValue(session.getFormattedTime())
                    .itemCount(session.getItemRewards().size())
                    .itemValue(session.getItemRewards().stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum())
                    .taxRate(taxRate)
                    .taxAmount(taxAmount)
                    .currentPage(currentPage)
                    .totalPages((int) Math.ceil((double) availablePlayers.size() / itemsPerPage))
                    .bountyAmount(bountyAmount)
                    .setter(setterUUID)
                    .setTime(setTime)
                    .expireTime(expireTime)
                    .multiplier(multiplier)
                    .moneyLine("&7Money: &a" + session.getFormattedMoney())
                    .experienceLine("&7Experience: &e" + session.getFormattedExperience())
                    .onlineStatus(onlineStatus)
                    .lastSeen(lastSeen);
            String nameKey = isFrenzyActive ? "frenzy-skull.name" :
                    (isBoosted ? "boosted-skull.name" :
                            (isSelected ? "player-head.name.selected" :
                                    (isOnline ? "player-head.name.online" : "player-head.name.offline")));
            String loreKey = isFrenzyActive ? "frenzy-skull.lore" :
                    (isBoosted ? "boosted-skull.lore" : (isSelected ? "player-head.lore.selected" : "player-head.lore.not-selected"));
            String glowKey = isFrenzyActive ? "frenzy-skull.enchantment-glow" :
                    (isBoosted ? "boosted-skull.enchantment-glow" :
                            (isSelected ? "player-head.enchantment-glow.selected" : "player-head.enchantment-glow.not-selected"));
            String nameFormat = config.getString(nameKey, isSelected ? "&6» &a%bountiesplus_target% &6«" : (isOnline ? "&a%bountiesplus_target%" : "&7%bountiesplus_target%"));
            if (nameFormat == null || nameFormat.isEmpty()) {
                plugin.getLogger().warning("Invalid or empty " + nameKey + ", using default");
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", nameKey)));
                nameFormat = "&a%bountiesplus_target%";
            }
            String displayName = Placeholders.apply(nameFormat, context);
            skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            plugin.getLogger().info("Applied placeholder name for " + targetName + ": raw=" + nameFormat + ", processed=" + displayName);
            List<String> loreLines = config.getStringList(loreKey);
            if (loreLines.isEmpty()) {
                plugin.getLogger().warning("Empty " + loreKey + ", using default");
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", loreKey)));
                loreLines = isSelected ?
                        Arrays.asList("&6Currently Selected Target", "", "&7Bounties: %bountiesplus_bounty_count%", "&7Items: &b%bountiesplus_item_count%", "%bountiesplus_online_status%", "", "&eClick to deselect this player") :
                        Arrays.asList("&7Bounties: %bountiesplus_bounty_count%", "&7Items: &b%bountiesplus_item_count%", "%bountiesplus_online_status%", "", "&eClick to set a bounty on this player!");
            }
            List<String> processedLore = new ArrayList<>();
            for (String line : loreLines) {
                String processedLine = Placeholders.apply(line, context);
                processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            skullMeta.setLore(processedLore);
            plugin.getLogger().info("Applied placeholder lore for " + targetName + ": raw=" + loreLines + ", processed=" + processedLore);
            boolean shouldGlow = config.getBoolean(glowKey, isSelected || isFrenzyActive || isBoosted);
            if (shouldGlow) {
                skullMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                skullMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                plugin.getLogger().info("Applied enchantment glow for player head of " + targetName);
            }
            head.setItemMeta(skullMeta);
            plugin.getLogger().info("Skull for " + targetName + " after meta changes: owner=" + skullMeta.getOwner());
            int slotIndex = i - startIndex;
            if (slotIndex < contentSlots.size()) {
                int slot = contentSlots.get(slotIndex);
                inventory.setItem(slot, head);
                plugin.getLogger().info("Placed player head for " + targetName + " in slot " + slot);
            }
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
                plugin.getLogger().info("Got GameProfile from OfflinePlayer for " + target.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("Could not get GameProfile from OfflinePlayer for " + target.getName() + ": " + e.getMessage());
            }

            if (profile == null) {
                plugin.getLogger().warning("No GameProfile available for " + target.getName() + ", skipping reflection injection");
                return;
            }

            // Now inject that GameProfile into the SkullMeta
            try {
                // Try setProfile method first (newer versions)
                Method setter = skullMeta.getClass().getDeclaredMethod("setProfile", profile.getClass());
                setter.setAccessible(true);
                setter.invoke(skullMeta, profile);
                plugin.getLogger().info("Injected GameProfile using setProfile for " + target.getName());
            } catch (NoSuchMethodException nsme) {
                // Fallback: set the private "profile" field directly
                try {
                    Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, profile);
                    plugin.getLogger().info("Injected GameProfile using field access for " + target.getName());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to inject GameProfile for " + target.getName() + ": " + e.getMessage());
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Reflection injection failed for " + target.getName() + ": " + t.getMessage());
        }
    }

    private String formatTimeDifference(long timeDiff) {
        return TimeFormatter.formatTimeAgo(timeDiff);
    }

    private void addBottomRowButtons() {
        FileConfiguration config = plugin.getCreateGUIConfig();
        BountyCreationSession session = BountyCreationSession.getSession(player);

        // Create session if it doesn't exist to avoid null pointer errors
        if (session == null) {
            session = BountyCreationSession.getOrCreateSession(player);
        }

        // Calculate session values for placeholders using correct method names
        double moneyValue = session.getMoney(); // Gets the monetary reward amount
        int expValue = session.getExperience(); // Gets the experience reward amount
        String timeValue = session.getFormattedTime(); // Gets the formatted duration (replaces getTimeString)
        List<ItemStack> items = session.getItemRewards(); // Gets the list of item rewards (replaces getItemsList)
        int itemCount = items.size(); // Counts the number of unique item stacks

        // Calculate item value using ItemValueCalculator
        double itemValue = 0.0;
        if (plugin.getItemValueCalculator() != null) {
            for (ItemStack item : items) {
                if (item != null) {
                    itemValue += plugin.getItemValueCalculator().calculateItemValue(item);
                }
            }
        }

        // Calculate total bounty value
        double totalValue = moneyValue + itemValue;

        // Format duration text using TimeFormatter
        String duration = session.getFormattedTime();
        if (duration == null || duration.equals("Not set") || duration.isEmpty()) {
            duration = "Default";
        }

        // List of all configurable buttons with their default slots
        Map<String, Integer> buttonDefaults = new HashMap<>();
        buttonDefaults.put("confirm-button", 52);
        buttonDefaults.put("cancel-button", 46);
        buttonDefaults.put("add-items-button", 50);
        buttonDefaults.put("add-money-button", 48);
        buttonDefaults.put("total-bounty-value-button", 49);
        buttonDefaults.put("add-experience-button", 47);
        buttonDefaults.put("add-time-button", 51);

        // Create each configurable button
        for (Map.Entry<String, Integer> entry : buttonDefaults.entrySet()) {
            String buttonName = entry.getKey();
            int defaultSlot = entry.getValue();

            // Get slot from config, use default if not specified
            int slot = config.getInt(buttonName + ".slot", defaultSlot);

            // Validate slot range
            if (slot >= 0 && slot < inventory.getSize()) {
                ItemStack button;

                // Handle special button types
                if (buttonName.equals("add-items-button")) {
                    button = createAddItemsButton(config, session, moneyValue, expValue, timeValue,
                            itemCount, itemValue, totalValue, duration);
                } else {
                    button = createConfigurableButton(buttonName, config, session, moneyValue,
                            expValue, timeValue, itemCount, itemValue, totalValue, duration);
                }

                if (button != null) {
                    inventory.setItem(slot, button);
                    protectedSlots.add(slot);
                }
            } else {
                plugin.getLogger().warning("Invalid slot " + slot + " for button " + buttonName + " in CreateGUI.yml");
            }
        }
    }

    /**
     * Creates a configurable button with full placeholder support // note: Generates GUI button with customizable appearance
     */
    private ItemStack createConfigurableButton(String buttonName, FileConfiguration config, BountyCreationSession session,
                                               double moneyValue, int expValue, String timeValue, int itemCount,
                                               double itemValue, double totalValue, String duration) {
        BountiesPlus.getInstance().getLogger().info("Creating button: " + buttonName);
        ItemStack button;
        // Handle total-bounty-value-button specially when a target is selected
        if (buttonName.equals("total-bounty-value-button") && session != null && session.getTargetUUID() != null) {
            String path = buttonName + ".target-selected";
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(session.getTargetUUID());
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
            String materialName = config.getString(path + ".material", "PLAYER_HEAD");
            if (!config.contains(path)) {
                plugin.getLogger().warning("Missing 'target-selected' section for " + buttonName + " in CreateGUI.yml, using default PLAYER_HEAD");
            }
            button = SkullUtils.createVersionAwarePlayerHead(targetPlayer);
            if (button == null || !VersionUtils.isPlayerHead(button)) {
                BountiesPlus.getInstance().getLogger().warning("Failed to create player head for " + targetName + ", using fallback material");
                button = VersionUtils.getXMaterialItemStack(materialName);
                if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                    BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' for " + buttonName + ".target-selected, using PAPER");
                    FileConfiguration messagesConfig = plugin.getMessagesConfig();
                    String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
                    errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", buttonName);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMessage));
                    button = VersionUtils.getXMaterialItemStack("PAPER");
                }
            }
        } else {
            String materialName = config.getString(buttonName + ".material", "STONE");
            button = VersionUtils.getXMaterialItemStack(materialName);
            if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
                BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' for " + buttonName + ", using STONE");
                FileConfiguration messagesConfig = plugin.getMessagesConfig();
                String errorMessage = messagesConfig.getString("invalid-material", "&cInvalid material %material% for %button%!");
                errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", buttonName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMessage));
            }
        }
        BountiesPlus.getInstance().getLogger().info("Loaded material for " + buttonName + ": " + button.getType().name());
        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            BountiesPlus.getInstance().getLogger().warning("Failed to get ItemMeta for button " + buttonName + ", using default item");
            return button;
        }
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .bountyCount(0)
                .moneyValue(moneyValue)
                .expValue(expValue)
                .timeValue(duration)
                .itemCount(itemCount)
                .itemValue(itemValue)
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(moneyValue * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .moneyLine("&7Money: &a" + CurrencyUtil.formatMoney(moneyValue))
                .experienceLine("&7Experience: &e" + (expValue == 0 ? "0 XP Levels" : expValue + " XP Level" + (expValue > 1 ? "s" : "")))
                .totalBountyAmount(totalValue)
                .currentPage(0)
                .totalPages(1);
        if (session != null && session.getTargetUUID() != null) {
            context = context.target(session.getTargetUUID());
        }
        BountiesPlus.getInstance().getLogger().info("Built PlaceholderContext for " + buttonName + ": moneyValue=" + moneyValue + ", expValue=" + expValue + ", targetUUID=" + (session != null ? session.getTargetUUID() : "none"));
        String namePath = buttonName + (buttonName.equals("total-bounty-value-button") && session != null && session.getTargetUUID() != null ? ".target-selected.name" : ".name");
        String lorePath = buttonName + (buttonName.equals("total-bounty-value-button") && session != null && session.getTargetUUID() != null ? ".target-selected.lore" : ".lore");
        String name = config.getString(namePath, "Button");
        if (name == null || name.isEmpty()) {
            BountiesPlus.getInstance().getLogger().warning("Invalid or empty name at " + namePath + ", using default");
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", namePath)));
            name = "Button";
        }
        String processedName = Placeholders.apply(name, context);
        meta.setDisplayName(processedName);
        BountiesPlus.getInstance().getLogger().info("Applied placeholder name for " + buttonName + ": raw=" + name + ", processed=" + processedName);
        List<String> lore = config.getStringList(lorePath);
        if (lore.isEmpty()) {
            BountiesPlus.getInstance().getLogger().warning("Empty lore at " + lorePath + ", using default");
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String warningMessage = messagesConfig.getString("missing-config", "Message not found: %path%");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', warningMessage.replace("%path%", lorePath)));
            lore = Arrays.asList("&7Click to interact with " + buttonName);
        }
        List<String> processedLore = Placeholders.apply(lore, context);
        meta.setLore(processedLore);
        BountiesPlus.getInstance().getLogger().info("Applied placeholder lore for " + buttonName + ": raw=" + lore + ", processed=" + processedLore);
        String glowPath = buttonName + (buttonName.equals("total-bounty-value-button") && session != null && session.getTargetUUID() != null ? ".target-selected.enchantment-glow" : ".enchantment-glow");
        boolean enchantmentGlow = config.getBoolean(glowPath, false);
        if (enchantmentGlow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            BountiesPlus.getInstance().getLogger().info("Applied enchantment glow for " + buttonName);
        }
        if (VersionUtils.isPost19()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        button.setItemMeta(meta);
        return button;
    }

    /**
     * Creates the special add-items button with dynamic content based on item count // note: Generates button for managing item rewards
     */
    private ItemStack createAddItemsButton(FileConfiguration config, BountyCreationSession session,
                                           double moneyValue, int expValue, String timeValue, int itemCount,
                                           double itemValue, double totalValue, String duration) {
        BountiesPlus.getInstance().getLogger().info("Creating button: add-items-button");
        boolean hasItems = itemCount > 0;
        String configPath = hasItems ? "add-items-button.has-items" : "add-items-button.no-items";
        String materialName = config.getString("add-items-button.material", "CHEST");
        ItemStack button = VersionUtils.getXMaterialItemStack(materialName);
        if (button.getType() == Material.STONE && !materialName.equalsIgnoreCase("CHEST")) {
            BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' for add-items-button, using CHEST");
            String errorMessage = config.getString("messages.invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", "add-items-button");
            PlaceholderContext errorContext = PlaceholderContext.create().player(player);
            player.sendMessage(Placeholders.apply(errorMessage, errorContext));
            button = VersionUtils.getXMaterialItemStack("CHEST");
        }
        BountiesPlus.getInstance().getLogger().info("Loaded material for add-items-button: " + button.getType().name());
        ItemMeta meta = button.getItemMeta();
        if (meta == null) {
            BountiesPlus.getInstance().getLogger().warning("Failed to get ItemMeta for add-items-button, using default item");
            return button;
        }
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .bountyCount(0)
                .moneyValue(moneyValue)
                .expValue(expValue)
                .timeValue(timeValue)
                .itemCount(itemCount)
                .itemValue(itemValue)
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(moneyValue * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .moneyLine("&7Money: &a" + CurrencyUtil.formatMoney(moneyValue))
                .experienceLine("&7Experience: &e" + (expValue == 0 ? "0 XP Levels" : expValue + " XP Level" + (expValue > 1 ? "s" : "")))
                .totalBountyAmount(totalValue)
                .currentPage(0)
                .totalPages(1);
        BountiesPlus.getInstance().getLogger().info("Built PlaceholderContext for add-items-button: itemCount=" + itemCount + ", itemValue=" + itemValue);
        String name = config.getString(configPath + ".name", "Add Items");
        if (name == null || name.isEmpty()) {
            BountiesPlus.getInstance().getLogger().warning("Invalid or empty name for add-items-button at " + configPath + ", using default");
            name = "Add Items";
        }
        String processedName = Placeholders.apply(name, context);
        meta.setDisplayName(processedName);
        BountiesPlus.getInstance().getLogger().info("Applied placeholder name for add-items-button: raw=" + name + ", processed=" + processedName);
        List<String> lore = config.getStringList(configPath + ".lore");
        if (lore.isEmpty()) {
            BountiesPlus.getInstance().getLogger().warning("Empty lore for add-items-button at " + configPath + ", using default");
            lore = Arrays.asList("&7Click to " + (hasItems ? "manage" : "add") + " item rewards", "&7Items: &b%bountiesplus_item_count%");
        }
        List<String> processedLore = Placeholders.apply(lore, context);
        meta.setLore(processedLore);
        BountiesPlus.getInstance().getLogger().info("Applied placeholder lore for add-items-button: raw=" + lore + ", processed=" + processedLore);
        boolean enchantmentGlow = config.getBoolean(configPath + ".enchantment-glow", hasItems);
        if (enchantmentGlow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            BountiesPlus.getInstance().getLogger().info("Applied enchantment glow for add-items-button");
        }
        if (VersionUtils.isPost19()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        button.setItemMeta(meta);
        return button;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(this.player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;


        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        // Always cancel click in protected slots
        if (protectedSlots.contains(slot)) {
            event.setCancelled(true);

            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            // Handle pagination buttons first
            if (slot == 45 && clickedItem.getType() == Material.ARROW) {
                // Previous page
                if (currentPage > 0) {
                    currentPage--;
                    refreshGUI();
                }
                return;
            } else if (slot == 53 && clickedItem.getType() == Material.ARROW) {
                // Next page
                int totalPages = (int) Math.ceil((double) availablePlayers.size() / 28.0);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshGUI();
                }
                return;
            }

            // Handle button clicks based on slot configuration
            FileConfiguration config = BountiesPlus.getInstance().getCreateGUIConfig();

            if (slot == config.getInt("confirm-button.slot", 52)) {
                // ==================== NEW LAZY SESSION CREATION ====================
                BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                handleConfirmButton(clickingPlayer, session);
            } else if (slot == config.getInt("add-money-button.slot", 48)) {
                // ==================== NEW LAZY SESSION CREATION ====================
                BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                handleAddMoneyButton(clickingPlayer, session);
            } else if (slot == config.getInt("add-experience-button.slot", 47)) {
                // ==================== NEW LAZY SESSION CREATION ====================
                BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                handleAddExperienceButton(clickingPlayer, session);
            } else if (slot == config.getInt("add-time-button.slot", 51)) {
                // ==================== NEW LAZY SESSION CREATION ====================
                BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                handleAddTimeButton(clickingPlayer, session);
            } else if (slot == config.getInt("total-bounty-value-button.slot", 49)) {
                // ==================== NEW LAZY SESSION CREATION ====================
                BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                handleTotalValueButton(clickingPlayer, session);
            } else if (slot == config.getInt("add-Items-button.slot", 50)) {
                // ==================== NEW LAZY SESSION CREATION ====================
                BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                handleAddItemsButton(clickingPlayer, session);
            } else if (slot == config.getInt("cancel-button.slot", 46)) {
                handleCancelButton(clickingPlayer);
            } else {
                // Check if the clicked item is a player head/skull
                String itemTypeName = clickedItem.getType().name();
                if (itemTypeName.contains("SKULL") || itemTypeName.contains("HEAD")) {
                    // Create or get existing session
                    BountyCreationSession session = BountyCreationSession.getOrCreateSession(clickingPlayer);
                    // Handle the player head click
                    handlePlayerHeadClick(clickingPlayer, clickedItem, session);
                }

            }
        }
        // Allow item placement in non-protected slots (though there shouldn't be any in this GUI)
    }

    /**
     * Handles the Add Items button click, opening the AddItemsGUI // note: Transitions to item management GUI
     */
    private void handleAddItemsButton(Player player, BountyCreationSession session) {
        if (session == null) {
            session = BountyCreationSession.getOrCreateSession(player);
        }
        this.cleanup();
        try {
            AddItemsGUI addItemsGUI = new AddItemsGUI(player);
            addItemsGUI.openInventory(player);
            plugin.getLogger().info("Opened AddItemsGUI for player: " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to open AddItemsGUI for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Failed to open item management GUI. Please try again.");
        }
    }

    /**
     * Handles clicks on player heads in the GUI // note: Selects or deselects bounty targets
     */
    private void handlePlayerHeadClick(Player player, ItemStack headItem, BountyCreationSession session) {
        if (!headItem.hasItemMeta() || headItem.getItemMeta().getDisplayName() == null) return;
        String displayName = headItem.getItemMeta().getDisplayName();
        String targetName;
        if (displayName.contains("»") && displayName.contains("«")) {
            String strippedName = ChatColor.stripColor(displayName);
            int startIndex = strippedName.indexOf("»") + 1;
            int endIndex = strippedName.indexOf("«");
            if (startIndex > 0 && endIndex > startIndex) {
                targetName = strippedName.substring(startIndex, endIndex).trim();
            } else {
                targetName = ChatColor.stripColor(displayName.replace("»", "").replace("«", "").trim());
            }
        } else {
            targetName = ChatColor.stripColor(displayName);
        }
        OfflinePlayer targetPlayer = null;
        // Search by name first
        for (OfflinePlayer offlinePlayer : availablePlayers) {
            if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(targetName)) {
                targetPlayer = offlinePlayer;
                break;
            }
        }
        // Fallback to Bukkit lookup if not found
        if (targetPlayer == null) {
            targetPlayer = Bukkit.getOfflinePlayer(targetName);
        }
        if (targetPlayer != null && (targetPlayer.hasPlayedBefore() || targetPlayer.isOnline())) {
            boolean isAlreadySelected = session.hasTarget() &&
                    session.getTargetUUID() != null &&
                    session.getTargetUUID().equals(targetPlayer.getUniqueId());
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            if (isAlreadySelected) {
                session.setTargetPlayer(null);
                session.setTargetPlayerOffline(null);
                String message = messagesConfig.getString("target-deselected", "&eTarget deselected.");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                BountiesPlus.getInstance().getLogger().info("Deselected target " + targetName + " for " + player.getName());
            } else {
                if (targetPlayer.isOnline()) {
                    session.setTargetPlayer(targetPlayer.getPlayer());
                    BountiesPlus.getInstance().getLogger().info("Selected online target " + targetName + " for " + player.getName());
                } else {
                    session.setTargetPlayerOffline(targetPlayer);
                    BountiesPlus.getInstance().getLogger().info("Selected offline target " + targetName + " for " + player.getName());
                }
                String targetMessage = messagesConfig.getString("target-selected", "&aTarget set to: &e%target%");
                targetMessage = targetMessage.replace("%target%", targetName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', targetMessage));
                if (!targetPlayer.isOnline()) {
                    String offlineMessage = messagesConfig.getString("player-offline", "&7Note: This player is currently offline.");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', offlineMessage));
                }
            }
            updateSessionDisplay(); // Refresh GUI to update skulls
        } else {
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            PlaceholderContext context = PlaceholderContext.create().player(player);
            String errorMessage = messagesConfig.getString("player-not-found", "&cPlayer not found or has never joined the server!");
            player.sendMessage(Placeholders.apply(errorMessage, context));
            BountiesPlus.getInstance().getLogger().warning("Failed to find player " + targetName + " for selection by " + player.getName());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!player.equals(this.player)) return;
        if (!event.getInventory().equals(this.inventory)) return;

        BountyCreationSession session = BountyCreationSession.getOrCreateSession(player);

        // Allow dragging in unprotected slots only
        boolean hasProtectedSlots = false;
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize() && protectedSlots.contains(slot)) {
                hasProtectedSlots = true;
                break;
            }
        }

        if (hasProtectedSlots) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot place Items in that slot!");
            return;
        }
    }

    /**
     * Handles the Confirm button click, initiating the anonymous bounty prompt if enabled
     */
    private void handleConfirmButton(Player player, BountyCreationSession session) {
        BountiesPlus.getInstance().getLogger().info("Confirm button pressed by " + player.getName()); // Logs button press
        // Validate the bounty before processing
        if (!session.isComplete()) {
            String validationError = session.getValidationError();
            player.sendMessage(ChatColor.RED + validationError);
            BountiesPlus.getInstance().getLogger().info("Invalid bounty for " + player.getName() + ": " + validationError); // Logs error
            return;
        }
        // Get the target player
        UUID targetUUID = session.getTargetUUID();
        if (targetUUID == null) {
            player.sendMessage(ChatColor.RED + "No target player selected!");
            BountiesPlus.getInstance().getLogger().info("No target selected for " + player.getName()); // Logs error
            return;
        }
        // Set confirm pressed flag
        session.setConfirmPressed(true);
        BountiesPlus.getInstance().getLogger().info("Set isConfirmPressed=true for " + player.getName()); // Logs flag
        // Close the GUI
        player.closeInventory();
        // Check if anonymous bounties are enabled
        if (plugin.getConfig().getBoolean("anonymous-bounties.enabled", true)) {
            // Start anonymous bounty prompt
            plugin.getAnonymousBounty().promptForAnonymity(player, session);
            BountiesPlus.getInstance().getLogger().info("Prompted " + player.getName() + " for anonymous bounty"); // Logs prompt
        } else {
            // Place bounty normally without anonymous option
            handleConfirmButtonDirect(player, session);
        }
    }

    /**
     * Processes the confirm button directly without anonymous prompt // note: Places the bounty after validation and deductions
     */
    public void handleConfirmButtonDirect(Player player, BountyCreationSession session) {
        // Validate the bounty before processing
        if (!session.isComplete()) {
            String validationError = session.getValidationError();
            player.sendMessage(ChatColor.RED + validationError);
            return;
        }

        // Get the target player
        UUID targetUUID = session.getTargetUUID();
        if (targetUUID == null) {
            player.sendMessage(ChatColor.RED + "No target player selected!");
            return;
        }

        // Calculate total monetary value and item rewards
        double money = session.getMoney();
        int experienceLevels = session.getExperience();
        List<ItemStack> itemRewards = session.getItemRewards();

        // Handle tax via TaxManager
        TaxManager taxManager = plugin.getTaxManager();
        double taxAmount = taxManager.calculateTax(money, itemRewards);
        if (!taxManager.canAffordTax(player, money, taxAmount)) {
            return;
        }

        // Deduct money and tax
        if (!taxManager.deductTax(player, money, taxAmount)) {
            player.sendMessage(ChatColor.RED + "Failed to deduct funds!");
            return;
        }

        // Validate and deduct experience
        if (experienceLevels > 0) {
            if (player.getLevel() < experienceLevels) {
                player.sendMessage(ChatColor.RED + "You don't have enough experience levels! Required: " + experienceLevels);
                player.sendMessage(ChatColor.YELLOW + "Your current level: " + player.getLevel());
                taxManager.refundTax(player, money, taxAmount); // Refund money and tax
                return;
            }

            // Deduct the experience levels
            player.setLevel(player.getLevel() - experienceLevels);
        }

        // Remove items from player inventory
        if (!itemRewards.isEmpty()) {
            for (ItemStack item : itemRewards) {
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().removeItem(item);
                }
            }
        }

        // Create the bounty
        int bountyAmount = (int) money; // Store as dollars, not cents
        plugin.getBountyManager().addBounty(targetUUID, player.getUniqueId(), bountyAmount);

        // Store additional bounty data (experience, items, time)
        if (experienceLevels > 0 || !itemRewards.isEmpty() || !session.isPermanent()) {
            plugin.getLogger().info("Complex bounty created by " + player.getName() + " on " + session.getTargetName() +
                    " - Money: $" + money + ", XP: " + experienceLevels + " levels, Items: " + itemRewards.size());
        }

        // Send tax messages
        taxManager.sendTaxMessages(player, targetUUID, money, taxAmount);

        // Clean up session
        BountyCreationSession.removeSession(player);
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
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .withAmount(totalCost)
                    .taxAmount(taxAmount);
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
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(target.getUniqueId())
                .withAmount(totalMoney)
                .taxAmount(taxAmount);
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
     * Handles the Add Money button click, prompting for input validation // note: Initiates monetary input for bounty or displays no-money title/message
     */
    private void handleAddMoneyButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        Economy economy = BountiesPlus.getEconomy();
        FileConfiguration config = plugin.getCreateGUIConfig();
        debugManager.logDebug("Add Money button clicked by " + player.getName());

        if (economy == null) {
            String message = config.getString("messages.bounty-no-economy", "&cNo economy plugin found!");
            player.sendMessage(Placeholders.apply(message, PlaceholderContext.create().player(player)));
            debugManager.logDebug("No economy plugin found for add-money-button action by " + player.getName());
            return;
        }

        // Check if player can afford minimum bounty amount plus tax
        double minBountyAmount = plugin.getConfig().getDouble("min-bounty-amount", 100.0);
        TaxManager taxManager = plugin.getTaxManager();
        double minTaxAmount = taxManager.calculateTax(minBountyAmount, session.getItemRewards());
        double minTotalCost = minBountyAmount + minTaxAmount;
        if (economy.getBalance(player) < minTotalCost) {
            // Load title and timing from CreateGUI.yml
            String title = config.getString("add-money-button.no-money-title", "&cYou don't have any money to add");
            title = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(title, PlaceholderContext.create().player(player)));
            int fadeIn = config.getInt("add-money-button.title-duration.fade-in", 20);
            int stay = config.getInt("add-money-button.title-duration.stay", 60);
            int fadeOut = config.getInt("add-money-button.title-duration.fade-out", 20);
            long configReopenDelay = config.getLong("add-money-button.reopen-delay", 100);
            final long reopenDelay = configReopenDelay < 0 ? 100 : configReopenDelay; // Ensure final value

            // Log warning if reopen-delay is invalid
            if (configReopenDelay < 0) {
                plugin.getLogger().warning("Invalid add-money-button.reopen-delay " + configReopenDelay + ", using default 100");
            }

            // Send insufficient funds message
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String errorMessage = messagesConfig.getString("bounty-insufficient-funds", "&cInsufficient funds! You need $%cost% (includes $%tax% tax)");
            PlaceholderContext context = PlaceholderContext.create()
                    .player(player)
                    .withAmount(minTotalCost)
                    .taxAmount(minTaxAmount);
            player.sendMessage(Placeholders.apply(errorMessage, context));

            // Close GUI and send title/message via VersionUtils
            player.closeInventory();
            VersionUtils.sendTitle(player, title, "", fadeIn, stay, fadeOut);
            debugManager.logDebug("Sent no-money title to " + player.getName() + ": " + title);

            // Schedule GUI reopen using reopen-delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player);
                newGui.openInventory(player);
                debugManager.logDebug("Reopened CreateGUI for " + player.getName() + " after no-money title with reopen-delay " + reopenDelay + " ticks");
            }, reopenDelay);

            return;
        }

        // Ensure session is created and persisted
        if (session == null) {
            session = BountyCreationSession.getOrCreateSession(player);
            debugManager.logDebug("Created new session for " + player.getName());
        }

        // Set awaiting input before closing inventory
        session.setAwaitingInput(BountyCreationSession.InputType.MONEY);
        debugManager.logDebug("Set MONEY input for player: " + player.getName() + ", session awaiting: " + session.getAwaitingInput().name());

        // Send prompt messages
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        List<String> headerLines = messagesConfig.getStringList("money-input-header");
        if (headerLines.isEmpty()) {
            headerLines = Arrays.asList(
                    "&6=== Add Money to Bounty ===",
                    "&eYour balance: &a%bountiesplus_player_balance%",
                    "&eCurrent bounty amount: &a%bountiesplus_money_value%",
                    "&aType the amount to add to the bounty:",
                    "&7- Type 'cancel' to abort and return to the GUI"
            );
        }
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .moneyValue(session.getMoney());
        for (String line : headerLines) {
            player.sendMessage(Placeholders.apply(line, context));
        }

        // Close inventory after sending prompt
        player.closeInventory();
    }

    /**
     * Handles the Add Experience button click, prompting for input or showing error // note: Initiates experience level input for bounty or displays no-XP title/message
     */
    private void handleAddExperienceButton(Player player, BountyCreationSession session) {
        FileConfiguration config = plugin.getCreateGUIConfig();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Add Experience button clicked by " + player.getName());

        if (player.getLevel() <= 0) {
            // Load title and timing from CreateGUI.yml
            String title = config.getString("add-experience-button.no-experience-title", "&cYou don't have any XP levels to add");
            title = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(title, PlaceholderContext.create().player(player)));
            int fadeIn = config.getInt("add-experience-button.title-duration.fade-in", 10);
            int stay = config.getInt("add-experience-button.title-duration.stay", 40);
            int fadeOut = config.getInt("add-experience-button.title-duration.fade-out", 10);
            long reopenDelay = config.getLong("add-experience-button.reopen-delay", 60);

            // Close GUI and send title/message via VersionUtils
            player.closeInventory();
            VersionUtils.sendTitle(player, title, "", fadeIn, stay, fadeOut);
            debugManager.logDebug("Sent no-XP title/message to " + player.getName() + ": " + title);

            // Schedule GUI reopen
            long totalDuration = VersionUtils.isPost111() ? (fadeIn + stay + fadeOut) : reopenDelay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                CreateGUI newGui = new CreateGUI(player);
                newGui.openInventory(player);
                debugManager.logDebug("Reopened CreateGUI for " + player.getName() + " after no-XP title/message");
            }, totalDuration);

            return;
        }

        if (session == null) {
            session = BountyCreationSession.getOrCreateSession(player);
        }
        session.setAwaitingInput(BountyCreationSession.InputType.EXPERIENCE);
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        List<String> headerLines = messagesConfig.getStringList("experience-input-header");
        if (headerLines.isEmpty()) {
            headerLines = Arrays.asList(
                    "&6=== Add Experience to Bounty ===",
                    "&eYour experience levels: %bountiesplus_player_level%",
                    "&eCurrent bounty experience: %bountiesplus_exp_value%",
                    "&aType the number of experience levels to add to the bounty:",
                    "&7Type 'cancel' to abort and return to the GUI"
            );
        }
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .expValue(session.getExperience())
                .timeValue(session.getFormattedTime())
                .moneyValue(session.getMoney())
                .itemCount(session.getItemRewards().size())
                .itemValue(session.getItemRewards().stream().mapToDouble(item -> plugin.getItemValueCalculator().calculateItemValue(item)).sum())
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .taxAmount(session.getMoney() * plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0));
        for (String line : headerLines) {
            String processedLine = Placeholders.apply(line, context);
            player.sendMessage(processedLine);
        }
        debugManager.logDebug("Prompted " + player.getName() + " for experience input");
        player.closeInventory();
    }
    private void handleAddTimeButton(Player player, BountyCreationSession session) {
        session.setAwaitingInput(BountyCreationSession.InputType.TIME);

        // Get message list from config
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        List<String> messages = messagesConfig.getStringList("set-bounty-duration");

        // If config is empty, use default messages
        if (messages.isEmpty()) {
            messages = Arrays.asList(
                    "&6=== Set Bounty Duration ===",
                    "&eCurrent duration: %current_duration%",
                    "&aType the duration (0 for permanent):",
                    "&7Examples: 30 Minutes, 30m, 30 min | 2 hours, 2h, 2 hr | 1 day, 1d",
                    "&6============================="
            );
        }

        // Send each line with placeholder replacement
        for (String line : messages) {
            String processedLine = line.replace("%current_duration%", session.getFormattedTime());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', processedLine));
        }

        if (session == null) {
            session = BountyCreationSession.getOrCreateSession(player);
        }


        player.closeInventory();
    }

    /**
     * Handles the Total Bounty Value button click, prompting for player name input // note: Initiates chat prompt for selecting bounty target
     */
    private void handleTotalValueButton(Player player, BountyCreationSession session) {
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Total Value button clicked by " + player.getName());

        // Prevent duplicate prompts if already awaiting input
        if (session != null && session.getAwaitingInput() == BountyCreationSession.InputType.PLAYER_NAME) {
            debugManager.logDebug("Skipping prompt for " + player.getName() + ": Already awaiting PLAYER_NAME input.");
            return;
        }

        // Set the session to wait for player name input
        if (session == null) {
            session = BountyCreationSession.getOrCreateSession(player);
        }
        session.setAwaitingInput(BountyCreationSession.InputType.PLAYER_NAME);
        debugManager.logDebug("Set PLAYER_NAME input for " + player.getName());

        // Get message list from config
        FileConfiguration messagesConfig = plugin.getMessagesConfig();
        List<String> messages = messagesConfig.getStringList("enter-player-name");

        // If config is empty, use default messages
        if (messages.isEmpty()) {
            messages = Arrays.asList(
                    "&6==========================================",
                    "&ePlease type the player name that you want",
                    "&eto set a bounty on in the chat:",
                    "&7(Type 'cancel' to return to the GUI)",
                    "&6=========================================="
            );
        }

        // Send each line with color code processing
        for (String line : messages) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Close the GUI last
        player.closeInventory();
        debugManager.logDebug("Sent player name prompt and closed GUI for " + player.getName());
    }


    /**
     * Handles the Cancel button click, prompting for confirmation if changes exist // note: Initiates bounty creation cancellation
     */
    private void handleCancelButton(Player player) {
        BountyCreationSession session = BountyCreationSession.getSession(player);
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Cancel button clicked by " + player.getName());

        if (session != null && session.hasChanges()) {
            // Player has made changes, confirm they want to cancel
            session.setAwaitingInput(BountyCreationSession.InputType.CANCEL_CONFIRMATION);
            debugManager.logDebug("Set CANCEL_CONFIRMATION input for " + player.getName());
            player.sendMessage(ChatColor.YELLOW + "You have unsaved changes to your bounty.");
            player.sendMessage(ChatColor.YELLOW + "Are you sure you want to cancel? Type 'yes' to confirm or 'no' to continue.");
            player.closeInventory();
        } else {
            // No changes made or no session, cancel normally
            if (session != null) {
                BountyCreationSession.removeSession(player);
            }
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Bounty creation cancelled.");
            debugManager.logDebug("Cancelled bounty creation for " + player.getName() + ": no changes or no session");
        }
    }

    /**
     * Handles inventory close events, managing session state // note: Controls session cleanup and prevents premature GUI reopen during active prompts
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!event.getPlayer().equals(player)) return;

        Player closingPlayer = (Player) event.getPlayer();
        DebugManager debugManager = plugin.getDebugManager();
        debugManager.logDebug("Inventory closed by " + closingPlayer.getName());

        BountyCreationSession session = BountyCreationSession.getSession(closingPlayer);
        if (session == null) {
            debugManager.logDebug("No session found for " + closingPlayer.getName() + ", cleaning up");
            cleanup();
            return;
        }

        // Allow closing if awaiting input for prompts (e.g., MONEY, EXPERIENCE, CANCEL_CONFIRMATION)
        if (session.isAwaitingInput()) {
            debugManager.logDebug("Preserving session for " + closingPlayer.getName() + ": awaiting input type " + session.getAwaitingInput().name());
            return; // Do not cleanup or reopen GUI
        }

        // Allow closing if confirm button was pressed and bounty is valid
        if (session.isConfirmPressed() && session.isValid()) {
            debugManager.logDebug("Skipping GUI reopen for " + closingPlayer.getName() + ": confirm pressed with valid bounty");
            cleanup();
            return;
        }

        // Remove session and cleanup if no changes were made
        if (!session.hasChanges()) {
            BountyCreationSession.removeSession(closingPlayer);
            FileConfiguration messagesConfig = plugin.getMessagesConfig();
            String message = messagesConfig.getString("bounty-cancelled", "&cBounty creation cancelled.");
            closingPlayer.sendMessage(Placeholders.apply(message, PlaceholderContext.create().player(closingPlayer)));
            debugManager.logDebug("Closed session for " + closingPlayer.getName() + ": no changes");
            cleanup();
            return;
        }

        // Re-open GUI if session is active and has changes
        debugManager.logDebug("Re-opening CreateGUI for " + closingPlayer.getName() + ": active session with changes");
        Bukkit.getScheduler().runTask(plugin, () -> {
            cleanup();
            CreateGUI newGui = new CreateGUI(closingPlayer);
            newGui.openInventory(closingPlayer);
        });
    }
}