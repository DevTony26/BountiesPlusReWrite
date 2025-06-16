package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.Bounty;
import tony26.bountiesPlus.utils.MessageUtils;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.Placeholders;
import tony26.bountiesPlus.utils.VersionUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.event.HandlerList;
import tony26.bountiesPlus.GUIs.PreviewGUI;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Displays a preview of an active bounty when opened from BountyGUI
 */
public class PreviewGUI implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final Player player;
    private final BountiesPlus plugin;
    private final UUID targetUUID;
    private final String GUI_TITLE;
    private final Set<Integer> protectedSlots = new HashSet<>();
    private int currentPage = 0;

    public PreviewGUI(Player player, UUID targetUUID) {
        this.player = player;
        this.plugin = BountiesPlus.getInstance();
        this.targetUUID = targetUUID;
        FileConfiguration config = plugin.getPreviewGUIConfig();
        this.GUI_TITLE = ChatColor.translateAlternateColorCodes('&', config.getString("gui-title", "&6Bounty Preview"));
        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initializeGUI();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void openInventory(Player player) {
        updateProtectedSlots();
        if (!player.equals(this.player)) {
            return;
        }
        refreshGUI();
        player.openInventory(inventory);
    }

    private void initializeGUI() {
        refreshGUI();
    }

    /**
     * Refreshes the GUI with updated content // note: Clears and repopulates the inventory with borders, bounty info, bounty items, and custom items
     */
    private void refreshGUI() {
        inventory.clear();
        addBorders();
        addBountyInfoItem();
        addBountyItems();
        addCustomItems();
        updateProtectedSlots();
    }

    private void loadProtectedSlots(FileConfiguration config) {
        protectedSlots.clear();
        if (inventory == null) {
            return;
        }
        List<Integer> configSlots = config.getIntegerList("border.slots");
        if (!configSlots.isEmpty()) {
            protectedSlots.addAll(configSlots);
        } else {
            List<Integer> defaultSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
            protectedSlots.addAll(defaultSlots);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                protectedSlots.add(i);
            }
        }
    }

    private void updateProtectedSlots() {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        loadProtectedSlots(config);
    }

    private void addBorders() {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        if (!config.getBoolean("border.enabled", true)) {
            return;
        }
        String materialName = config.getString("border.material", "WHITE_STAINED_GLASS_PANE");
        Material borderMaterial = VersionUtils.getMaterialSafely(materialName, "STAINED_GLASS_PANE");
        ItemStack testItem = new ItemStack(borderMaterial);
        if (testItem.getType().name().contains("GLASS_PANE")) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "' in PreviewGUI.yml, using WHITE_STAINED_GLASS_PANE");
            String errorMessage = config.getString("messages.invalid-material", "&cInvalid material %material% for %button%!");
            errorMessage = errorMessage.replace("%material%", materialName).replace("%button%", "border");
            PlaceholderContext errorContext = PlaceholderContext.create().player(player);
            player.sendMessage(Placeholders.apply(errorMessage, errorContext));
            borderMaterial = VersionUtils.getWhiteGlassPaneMaterial();
        }
        boolean enchantmentGlow = config.getBoolean("border.enchantment-glow", false);
        List<Integer> borderSlots = config.getIntegerList("border.slots");
        if (borderSlots.isEmpty()) {
            borderSlots = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53);
        }
        ItemStack borderItem = new ItemStack(borderMaterial);
        ItemMeta borderMeta = borderItem.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            if (enchantmentGlow) {
                borderMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                borderMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            borderItem.setItemMeta(borderMeta);
        }
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < 54) {
                inventory.setItem(slot, borderItem);
            } else {
                plugin.getLogger().warning("Invalid slot " + slot + " in PreviewGUI.yml border configuration (must be 0-53)");
            }
        }
    }

    /**
     * Adds the main bounty information item to the GUI // note: Displays bounty details like money, XP, and items
     */
    private void addBountyInfoItem() {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        int slot = config.getInt("bounty-info.slot", 49);
        if (slot < 0 || slot > 53) {
            plugin.getLogger().warning("Invalid bounty-info.slot " + slot + " in PreviewGUI.yml, using default 49");
            slot = 49;
        }
        String materialName = config.getString("bounty-info.material", "PAPER");
        Material material = VersionUtils.getMaterialSafely(materialName, "PAPER");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.getLogger().warning("Failed to get ItemMeta for bounty-info item, using default item");
            return;
        }
        Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(targetUUID)
                .bountyCount(bounty != null ? bounty.getSponsors().size() : 0)
                .moneyValue(bounty != null ? bounty.getCurrentMoney() : 0.0)
                .expValue(bounty != null ? bounty.getCurrentXP() : 0)
                .timeValue(bounty != null ? bounty.getFormattedCurrentDuration() : "Permanent")
                .itemCount(bounty != null ? bounty.getCurrentItems().size() : 0)
                .itemValue(bounty != null ? bounty.getCurrentItemValue() : 0.0)
                .taxRate(plugin.getConfig().getDouble("bounty-place-tax-rate", 0.0))
                .moneyLine("&7Money: &a$" + (bounty != null ? String.format("%.2f", bounty.getCurrentMoney()) : "0.00"))
                .experienceLine("&7Experience: &e" + (bounty != null ? (bounty.getCurrentXP() == 0 ? "0 XP Levels" : bounty.getCurrentXP() + " XP Level" + (bounty.getCurrentXP() > 1 ? "s" : "")) : "0 XP Levels"));
        String name = config.getString("bounty-info.name", "&6%bountiesplus_target% Bounty");
        if (name == null || name.isEmpty()) {
            plugin.getLogger().warning("Invalid or empty bounty-info.name, using default");
            name = "&6%bountiesplus_target% Bounty";
        }
        String processedName = Placeholders.apply(name, context);
        meta.setDisplayName(processedName);
        plugin.getLogger().info("Applied placeholder name for bounty-info: raw=" + name + ", processed=" + processedName);
        List<String> lore = config.getStringList("bounty-info.lore");
        if (lore.isEmpty()) {
            plugin.getLogger().warning("Empty bounty-info.lore, using default");
            lore = Arrays.asList(
                    "&7Top Sponsors: %bountiesplus_top3_sponsors_numbered%",
                    "",
                    "&7Original Money: &a$%bountiesplus_original_money%",
                    "&7Current Money: &a$%bountiesplus_money_value%",
                    "&7Money Increase: &e%bountiesplus_price_increase_percent%%",
                    "",
                    "&7Original Items: &b%bountiesplus_original_item_count% ($%bountiesplus_original_item_value%)",
                    "&7Current Items: &b%bountiesplus_item_count% ($%bountiesplus_item_value%)",
                    "&7Item Increase: &e%bountiesplus_item_increase_percent%%",
                    "",
                    "&7Original XP: &e%bountiesplus_original_xp% Levels",
                    "&7Current XP: &e%bountiesplus_exp_value%",
                    "&7XP Increase: &e%bountiesplus_xplevel_increase_percent%%",
                    "",
                    "&7Original Duration: &9%bountiesplus_original_duration%",
                    "&7Current Duration: &9%bountiesplus_time_value%",
                    "&7Duration Increase: &e%bountiesplus_bountyduration_increase_percent%%",
                    "",
                    "&7Original Pool: &6$%bountiesplus_original_pool%",
                    "&7Current Pool: &6$%bountiesplus_total_pool%",
                    "&7Pool Increase: &e%bountiesplus_pool_increase_percent%%"
            );
        }
        List<String> processedLore = Placeholders.apply(lore, context);
        meta.setLore(processedLore);
        plugin.getLogger().info("Applied placeholder lore for bounty-info: raw=" + lore + ", processed=" + processedLore);
        boolean enchantmentGlow = config.getBoolean("bounty-info.enchantment-glow", false);
        if (enchantmentGlow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (VersionUtils.isPost19()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        protectedSlots.add(slot);
    }

    /**
     * Adds custom items to the GUI from PreviewGUI.yml // note: Populates custom decorative or interactive items with placeholders
     */
    private void addCustomItems() {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        ConfigurationSection customItemsSection = config.getConfigurationSection("custom-items");
        if (customItemsSection == null) {
            plugin.getLogger().info("No custom-items section found in PreviewGUI.yml");
            return;
        }
        Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(targetUUID)
                .bountyCount(bounty != null ? bounty.getSponsors().size() : 0)
                .moneyValue(bounty != null ? bounty.getCurrentMoney() : 0.0)
                .expValue(bounty != null ? bounty.getCurrentXP() : 0)
                .timeValue(bounty != null ? bounty.getFormattedCurrentDuration() : "Permanent")
                .itemCount(bounty != null ? bounty.getCurrentItems().size() : 0)
                .itemValue(bounty != null ? bounty.getCurrentItemValue() : 0.0);
        for (String key : customItemsSection.getKeys(false)) {
            String path = "custom-items." + key;
            String materialName = config.getString(path + ".material", "STONE");
            int slot = config.getInt(path + ".slot", -1);
            if (slot < 0 || slot >= 54) {
                plugin.getLogger().warning("Invalid slot " + slot + " for custom item " + key + " in PreviewGUI.yml");
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("slot", String.valueOf(slot));
                placeholders.put("item", key);
                MessageUtils.sendFormattedMessage(player, "invalid-custom-slot");
                continue;
            }
            if (protectedSlots.contains(slot)) {
                plugin.getLogger().warning("Slot " + slot + " for custom item " + key + " is already occupied");
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("slot", String.valueOf(slot));
                placeholders.put("item", key);
                MessageUtils.sendFormattedMessage(player, "invalid-custom-slot");
                continue;
            }
            Material material = VersionUtils.getMaterialSafely(materialName, "STONE");
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                plugin.getLogger().warning("Failed to get ItemMeta for custom item " + key);
                continue;
            }
            String name = config.getString(path + ".name", "&6Custom Item");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Placeholders.apply(name, context)));
            List<String> lore = config.getStringList(path + ".lore");
            if (!lore.isEmpty()) {
                List<String> processedLore = Placeholders.apply(lore, context).stream()
                        .map(line -> line.replace("%item_value%", String.format("%.2f", plugin.getItemValueCalculator().calculateItemValue(item))))
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(processedLore);
            }
            boolean enchantmentGlow = config.getBoolean(path + ".enchantment-glow", false);
            if (enchantmentGlow && VersionUtils.supportsGlowingEffect()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            protectedSlots.add(slot);
            plugin.getLogger().info("Added custom item " + key + " to slot " + slot);
        }
    }


    /**
     * Adds bounty items to the GUI // note: Populates available slots with items contributed by sponsors to the bounty
     */
    private void addBountyItems() {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        List<Integer> contentSlots = config.getIntegerList("bounty-items.content-slots");
        if (contentSlots.isEmpty()) {
            contentSlots = new ArrayList<>();
            for (int row = 1; row <= 4; row++) {
                for (int col = 1; col <= 7; col++) {
                    int slot = (row * 9) + col;
                    contentSlots.add(slot);
                }
            }
            plugin.getLogger().info("Using default content slots for bounty items: " + contentSlots);
        } else {
            contentSlots = contentSlots.stream()
                    .filter(slot -> slot >= 0 && slot < 54 && !protectedSlots.contains(slot))
                    .collect(Collectors.toList());
            if (contentSlots.isEmpty()) {
                plugin.getLogger().warning("No valid content slots defined in PreviewGUI.yml for bounty items, using default");
                for (int row = 1; row <= 4; row++) {
                    for (int col = 1; col <= 7; col++) {
                        int slot = (row * 9) + col;
                        if (!protectedSlots.contains(slot)) {
                            contentSlots.add(slot);
                        }
                    }
                }
            }
        }
        int itemsPerPage = contentSlots.size();
        int startIndex = currentPage * itemsPerPage;
        Bounty bounty = plugin.getBountyManager().getBounty(targetUUID);
        List<Bounty.BountyItem> items = (bounty != null && !bounty.getCurrentItems().isEmpty()) ?
                bounty.getSortedItems() : new ArrayList<>();
        plugin.getLogger().info("Adding bounty items: Total items=" + items.size() +
                ", Page=" + (currentPage + 1) + ", StartIndex=" + startIndex +
                ", ItemsPerPage=" + itemsPerPage + ", AvailableSlots=" + contentSlots.size());
        for (int slot : contentSlots) {
            inventory.setItem(slot, null);
        }
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        PlaceholderContext baseContext = PlaceholderContext.create()
                .player(player)
                .target(targetUUID);
        for (int i = startIndex; i < endIndex; i++) {
            Bounty.BountyItem bountyItem = items.get(i);
            ItemStack item = bountyItem.getItem().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                plugin.getLogger().warning("Failed to get ItemMeta for bounty item at index " + i +
                        ", Material=" + item.getType().name());
                continue;
            }
            String contributorName = bountyItem.isAnonymous() ? "&k|||||||" :
                    Bukkit.getOfflinePlayer(bountyItem.getContributor()).getName();
            if (contributorName == null) {
                contributorName = "Unknown";
                plugin.getLogger().warning("Contributor name is null for UUID: " +
                        bountyItem.getContributor());
            }
            double itemValue = plugin.getItemValueCalculator().calculateItemValue(item);
            List<String> lore = config.getStringList("bounty-items.lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7Contributor: %contributor%",
                        "&7Value: &a$%item_value%",
                        "&7Click to view details"
                );
                plugin.getLogger().info("Using default lore for bounty items");
            }
            List<String> processedLore = new ArrayList<>();
            for (String line : lore) {
                String processedLine = Placeholders.apply(line, baseContext)
                        .replace("%contributor%", contributorName)
                        .replace("%item_value%", String.format("%.2f", itemValue));
                processedLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(processedLore);
            boolean enchantmentGlow = config.getBoolean("bounty-items.enchantment-glow", false);
            if (enchantmentGlow && VersionUtils.supportsGlowingEffect()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
            int slotIndex = i - startIndex;
            if (slotIndex < contentSlots.size()) {
                int slot = contentSlots.get(slotIndex);
                inventory.setItem(slot, item);
                protectedSlots.add(slot);
                plugin.getLogger().info("Placed bounty item (Material=" + item.getType().name() +
                        ", Contributor=" + contributorName + ") in slot " + slot);
            } else {
                plugin.getLogger().warning("Slot index " + slotIndex +
                        " exceeds available content slots for item at index " + i);
            }
        }
        if (items.size() > itemsPerPage) {
            addPaginationButtons(itemsPerPage, items.size());
        } else {
            int prevSlot = config.getInt("pagination.previous.slot", 45);
            int nextSlot = config.getInt("pagination.next.slot", 53);
            inventory.setItem(prevSlot, null);
            inventory.setItem(nextSlot, null);
            protectedSlots.remove(prevSlot);
            protectedSlots.remove(nextSlot);
        }
    }

    private void addPaginationButtons(int itemsPerPage, int totalItems) {
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
        FileConfiguration config = plugin.getPreviewGUIConfig();
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                String name = config.getString("pagination.previous.name", "&ePrevious Page");
                prevMeta.setDisplayName(Placeholders.apply(name, null));
                List<String> lore = config.getStringList("pagination.previous.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Page %current_page% of %total_pages%",
                            "&7Click to go to previous page"
                    );
                }
                List<String> processedLore = new ArrayList<>();
                for (String line : lore) {
                    line = line.replace("%current_page%", String.valueOf(currentPage))
                            .replace("%total_pages%", String.valueOf(totalPages));
                    processedLore.add(Placeholders.apply(line, null));
                }
                prevMeta.setLore(processedLore);
                boolean glow = config.getBoolean("pagination.previous.enchantment-glow", false);
                if (glow) {
                    prevMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                    prevMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                prevButton.setItemMeta(prevMeta);
            }
            int slot = config.getInt("pagination.previous.slot", 45);
            inventory.setItem(slot, prevButton);
            protectedSlots.add(slot);
        }
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                String name = config.getString("pagination.next.name", "&eNext Page");
                nextMeta.setDisplayName(Placeholders.apply(name, null));
                List<String> lore = config.getStringList("pagination.next.lore");
                if (lore.isEmpty()) {
                    lore = Arrays.asList(
                            "&7Page %current_page% of %total_pages%",
                            "&7Click to go to next page"
                    );
                }
                List<String> processedLore = new ArrayList<>();
                for (String line : lore) {
                    line = line.replace("%current_page%", String.valueOf(currentPage + 2))
                            .replace("%total_pages%", String.valueOf(totalPages));
                    processedLore.add(Placeholders.apply(line, null));
                }
                nextMeta.setLore(processedLore);
                boolean glow = config.getBoolean("pagination.next.enchantment-glow", false);
                if (glow) {
                    nextMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                    nextMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                nextButton.setItemMeta(nextMeta);
            }
            int slot = config.getInt("pagination.next.slot", 53);
            inventory.setItem(slot, nextButton);
            protectedSlots.add(slot);
        }
    }

    /**
     * Handles inventory click events for the GUI // note: Processes clicks on pagination buttons and custom items
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        Player clickingPlayer = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();
        if (protectedSlots.contains(slot)) {
            event.setCancelled(true);
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;
            FileConfiguration config = plugin.getPreviewGUIConfig();
            if (slot == config.getInt("pagination.previous.slot", 45) && clickedItem.getType() == Material.ARROW) {
                if (currentPage > 0) {
                    currentPage--;
                    refreshGUI();
                }
            } else if (slot == config.getInt("pagination.next.slot", 53) && clickedItem.getType() == Material.ARROW) {
                int totalItems = plugin.getBountyManager().getBounty(targetUUID).getCurrentItems().size();
                int itemsPerPage = 28;
                int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshGUI();
                }
            } else {
                ConfigurationSection customItemsSection = config.getConfigurationSection("custom-items");
                if (customItemsSection != null) {
                    for (String key : customItemsSection.getKeys(false)) {
                        int customSlot = config.getInt("custom-items." + key + ".slot", -1);
                        if (slot == customSlot) {
                            handleCustomItemClick(clickingPlayer, key);
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles clicks on custom items // note: Processes actions for custom items like closing the GUI
     */
    private void handleCustomItemClick(Player player, String itemKey) {
        FileConfiguration config = plugin.getPreviewGUIConfig();
        String path = "custom-items." + itemKey;
        String action = config.getString(path + ".action", "none");
        switch (action.toLowerCase()) {
            case "close":
                player.closeInventory();
                break;
            default:
                plugin.getLogger().info("No action defined for custom item " + itemKey);
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;
        boolean hasProtectedSlots = false;
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize() && protectedSlots.contains(slot)) {
                hasProtectedSlots = true;
                break;
            }
        }
        if (hasProtectedSlots) {
            event.setCancelled(true);
            FileConfiguration config = plugin.getPreviewGUIConfig();
            String errorMessage = config.getString("messages.cannot-place-items", "&cYou cannot place items in this GUI!");
            PlaceholderContext context = PlaceholderContext.create().player(player);
            player.sendMessage(Placeholders.apply(errorMessage, context));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        if (!event.getPlayer().equals(player)) return;
        plugin.getLogger().info("Inventory closed by " + player.getName());
        cleanup();
    }

    private void cleanup() {
        HandlerList.unregisterAll(this);
    }
}