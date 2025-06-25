
package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.*;
import tony26.bountiesPlus.utils.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BountyCancel implements Listener {

    private final BountiesPlus plugin;
    private FileConfiguration config;
    private String guiTitle;
    private int guiSize;
    private int itemsPerPage;
    private static final Map<String, String> itemFailures = new ConcurrentHashMap<>();

    /**
     * Constructs the BountyCancel GUI
     * // note: Initializes bounty cancellation GUI and registers listeners
     */
    public BountyCancel(BountiesPlus plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.config = plugin.getConfig(); // Use main config instead
        loadConfiguration();
        eventManager.register(this); // Use EventManager
    }

    /**
     * Loads or creates BountyCancelGUI.yml configuration
     * // note: Initializes GUI settings for the bounty cancellation display
     */
    private void loadConfiguration() {
        File configFile = new File(plugin.getDataFolder(), "GUIs/BountyCancelGUI.yml");
        if (!configFile.exists()) {
            try {
                plugin.saveResource("GUIs/BountyCancelGUI.yml", false);
                if (plugin.getDebugManager() != null) {
                    plugin.getDebugManager().logDebug("[DEBUG - BountyCancelGUI] Created default BountyCancelGUI.yml");
                } else {
                    plugin.getLogger().info("[DEBUG - BountyCancelGUI] Created default BountyCancelGUI.yml");
                }
            } catch (IllegalArgumentException e) {
                if (plugin.getDebugManager() != null) {
                    plugin.getDebugManager().logWarning("[DEBUG - BountyCancelGUI] Failed to save default BountyCancelGUI.yml: " + e.getMessage());
                } else {
                    plugin.getLogger().warning("[DEBUG - BountyCancelGUI] Failed to save default BountyCancelGUI.yml: " + e.getMessage());
                }
            }
        }
        config = plugin.getBountyCancelGUIConfig();
        // Verify configuration integrity
        if (config.getConfigurationSection("bounty-item") == null) {
            if (plugin.getDebugManager() != null) {
                plugin.getDebugManager().logWarning("[DEBUG - BountyCancelGUI] BountyCancelGUI.yml is empty or invalid, reloading default");
            } else {
                plugin.getLogger().warning("[DEBUG - BountyCancelGUI] BountyCancelGUI.yml is empty or invalid, reloading default");
            }
            try {
                configFile.delete();
                plugin.saveResource("GUIs/BountyCancelGUI.yml", false);
                config = YamlConfiguration.loadConfiguration(configFile);
            } catch (IllegalArgumentException e) {
                if (plugin.getDebugManager() != null) {
                    plugin.getDebugManager().logWarning("[DEBUG - BountyCancelGUI] Failed to reload default BountyCancelGUI.yml: " + e.getMessage());
                } else {
                    plugin.getLogger().warning("[DEBUG - BountyCancelGUI] Failed to reload default BountyCancelGUI.yml: " + e.getMessage());
                }
            }
        }
        this.guiTitle = config.getString("gui.title", "&4Cancel Bounty");
        this.guiSize = config.getInt("gui.size", 54);
        this.itemsPerPage = config.getInt("gui.items-per-page", 36);
    }

    /**
     * Handles the /bounty cancel command
     * // note: Opens the cancel GUI if the player has active bounties
     */
    public static void handleCancelCommand(Player player, BountiesPlus plugin) {
        UUID playerUUID = player.getUniqueId();
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
        List<Map.Entry<UUID, Integer>> playerBounties = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Integer>> targetEntry : allBounties.entrySet()) {
            UUID targetUUID = targetEntry.getKey();
            Map<UUID, Integer> setters = targetEntry.getValue();
            if (setters.containsKey(playerUUID)) {
                playerBounties.add(new AbstractMap.SimpleEntry<>(targetUUID, setters.get(playerUUID)));
            }
        }

        if (playerBounties.isEmpty()) {
            MessageUtils.sendFormattedMessage(player, "no-bounties-to-cancel");
            return;
        }

        BountyCancel bountyCancel = new BountyCancel(plugin, plugin.getEventManager());
        bountyCancel.openCancelGUI(player, 0);
    }

    /**
     * Opens the cancel GUI for a player
     * // note: Displays player’s active bounties with pagination
     */
    private void openCancelGUI(Player player, int page) {
        UUID playerUUID = player.getUniqueId();
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
        List<Map.Entry<UUID, Integer>> playerBounties = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Integer>> targetEntry : allBounties.entrySet()) {
            UUID targetUUID = targetEntry.getKey();
            Map<UUID, Integer> setters = targetEntry.getValue();
            if (setters.containsKey(playerUUID)) {
                playerBounties.add(new AbstractMap.SimpleEntry<>(targetUUID, setters.get(playerUUID)));
            }
        }

        int totalPages = (int) Math.ceil((double) playerBounties.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        String title = ChatColor.translateAlternateColorCodes('&', guiTitle + " (Page " + (page + 1) + "/" + totalPages + ")");
        Inventory gui = Bukkit.createInventory(player, guiSize, title); // Set player as holder
        addBorder(gui);
        addBountyItems(gui, playerBounties, page, player);
        addNavigationButtons(gui, page, totalPages, playerBounties.size());
        addInfoButton(gui, playerBounties, page, totalPages, player);
        player.openInventory(gui);
    }

    /**
     * Adds border items to the GUI
     * // note: Populates border slots with gray stained glass panes
     */
    private void addBorder(Inventory gui) {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getBountyCancelGUIConfig();
        String materialName = config.getString("border.material", "GRAY_STAINED_GLASS_PANE");
        ItemStack borderItem = VersionUtils.getXMaterialItemStack(materialName);
        String failureReason = null;
        if (borderItem.getType() == Material.STONE && !materialName.equalsIgnoreCase("GRAY_STAINED_GLASS_PANE")) {
            debugManager.logWarning("[DEBUG - BountyCancelGUI] Invalid border material '" + materialName + "' in BountyCancelGUI.yml, using GRAY_STAINED_GLASS_PANE");
            failureReason = "Invalid material '" + materialName + "'";
            borderItem = VersionUtils.getXMaterialItemStack("GRAY_STAINED_GLASS_PANE");
        }

        ItemMeta meta = borderItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            borderItem.setItemMeta(meta);
        } else {
            debugManager.logWarning("[DEBUG - BountyCancelGUI] Failed to get ItemMeta for border item");
            failureReason = "Failed to get ItemMeta";
        }

        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        int totalItems = borderSlots.length;
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        for (int slot : borderSlots) {
            if (slot < gui.getSize()) {
                if (failureReason == null) {
                    gui.setItem(slot, borderItem.clone());
                    successfulItems++;
                } else {
                    failures.add("border-slot-" + slot + " Reason: " + failureReason);
                }
            } else {
                debugManager.logWarning("[DEBUG - BountyCancelGUI] Invalid slot " + slot + " for border in BountyCancel GUI");
                failures.add("border-slot-" + slot + " Reason: Invalid slot " + slot);
            }
        }

        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - BountyCancelGUI] All border items created");
        } else {
            String failureMessage = "[DEBUG - BountyCancelGUI] " + successfulItems + "/" + totalItems + " border items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("BountyCancel_border_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds bounty items to the GUI
     * // note: Populates bounty skulls for cancellation
     */
    private void addBountyItems(Inventory gui, List<Map.Entry<UUID, Integer>> playerBounties, int page, Player player) {
        DebugManager debugManager = plugin.getDebugManager();
        itemFailures.clear(); // Clear previous failures

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, playerBounties.size());
        int totalItems = endIndex - startIndex;
        int successfulItems = 0;
        List<String> failures = new ArrayList<>();

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, Integer> bountyEntry = playerBounties.get(i);
            UUID targetUUID = bountyEntry.getKey();
            int amount = bountyEntry.getValue();
            ItemStack bountyItem = createBountyItem(player, targetUUID, amount);
            if (bountyItem != null && bountyItem.getType() != Material.AIR) {
                gui.setItem(slot, bountyItem);
                successfulItems++;
            } else {
                String failure = itemFailures.get("bounty-item-" + targetUUID);
                failures.add("bounty-item-" + targetUUID + " Reason: " + (failure != null ? failure : "Failed to create item"));
            }
            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 45) break;
        }

        // Log consolidated debug message
        if (successfulItems == totalItems) {
            debugManager.logDebug("[DEBUG - BountyCancelGUI] All bounty items created");
        } else {
            String failureMessage = "[DEBUG - BountyCancelGUI] " + successfulItems + "/" + totalItems + " bounty items created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("BountyCancel_bounty_items_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds navigation buttons to the GUI
     * // note: Places Previous and Next buttons for pagination
     */
    private void addNavigationButtons(Inventory gui, int page, int totalPages, int totalBounties) {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getBountyCancelGUIConfig();
        int totalButtons = (page > 0 ? 1 : 0) + (page < totalPages - 1 ? 1 : 0);
        int successfulButtons = 0;
        List<String> failures = new ArrayList<>();

        if (page > 0) {
            ItemStack prevButton = VersionUtils.getXMaterialItemStack("ARROW");
            String failureReason = null;
            ItemMeta meta = prevButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aPrevious Page");
                meta.setLore(Arrays.asList("§7Go to page §e" + page, "", "§aClick to navigate"));
                prevButton.setItemMeta(meta);
            } else {
                debugManager.logWarning("[DEBUG - BountyCancelGUI] Failed to get ItemMeta for previous button");
                failureReason = "Failed to get ItemMeta";
            }
            if (failureReason == null) {
                gui.setItem(48, prevButton);
                successfulButtons++;
            } else {
                failures.add("previous Reason: " + failureReason);
            }
        }

        if (page < totalPages - 1) {
            ItemStack nextButton = VersionUtils.getXMaterialItemStack("ARROW");
            String failureReason = null;
            ItemMeta meta = nextButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aNext Page");
                meta.setLore(Arrays.asList("§7Go to page §e" + (page + 2), "", "§aClick to navigate"));
                nextButton.setItemMeta(meta);
            } else {
                debugManager.logWarning("[DEBUG - BountyCancelGUI] Failed to get ItemMeta for next button");
                failureReason = "Failed to get ItemMeta";
            }
            if (failureReason == null) {
                gui.setItem(50, nextButton);
                successfulButtons++;
            } else {
                failures.add("next Reason: " + failureReason);
            }
        }

        if (totalButtons == 0) {
            debugManager.logDebug("[DEBUG - BountyCancelGUI] No navigation buttons to create");
        } else if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG - BountyCancelGUI] All navigation buttons created");
        } else {
            String failureMessage = "[DEBUG - BountyCancelGUI] " + successfulButtons + "/" + totalButtons + " navigation buttons created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("BountyCancel_navigation_" + System.currentTimeMillis(), failureMessage);
        }
    }

    /**
     * Adds the info button to the GUI
     * // note: Displays cancellation information including total bounties and refund details
     */
    private void addInfoButton(Inventory gui, List<Map.Entry<UUID, Integer>> playerBounties, int page, int totalPages, Player player) {
        DebugManager debugManager = plugin.getDebugManager();
        FileConfiguration config = plugin.getBountyCancelGUIConfig();
        int totalButtons = 1;
        int successfulButtons = 0;
        List<String> failures = new ArrayList<>();

        String materialName = config.getString("navigation.info-button.material", "BOOK");
        ItemStack infoButton = VersionUtils.getXMaterialItemStack(materialName);
        String failureReason = null;
        if (infoButton.getType() == Material.STONE && !materialName.equalsIgnoreCase("BOOK")) {
            debugManager.logWarning("[DEBUG - BountyCancelGUI] Invalid material '" + materialName + "' for info button, using BOOK");
            failureReason = "Invalid material '" + materialName + "'";
            infoButton = VersionUtils.getXMaterialItemStack("BOOK");
        }

        ItemMeta meta = infoButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Bounty Cancellation Info");
            double totalAmount = playerBounties.stream().mapToDouble(Map.Entry::getValue).sum();
            boolean taxEnabled = plugin.getConfig().getBoolean("bounty-cancel-tax-enabled", true);
            double taxRate = plugin.getConfig().getDouble("bounty-cancel-tax-rate", 10.0);
            double totalRefund = taxEnabled ? totalAmount * (1 - taxRate / 100.0) : totalAmount;
            List<String> lore = Arrays.asList(
                    "§7Total Bounties: §e" + playerBounties.size(),
                    "§7Page: §e" + (page + 1) + "§7/§e" + totalPages,
                    "",
                    "§eTax Rate: §c" + String.format("%.1f", taxRate) + "%",
                    "§eTotal Refund: §a$" + String.format("%.2f", totalRefund)
            );
            meta.setLore(lore);
            infoButton.setItemMeta(meta);
        } else {
            debugManager.logWarning("[DEBUG - BountyCancelGUI] Failed to get ItemMeta for info button");
            failureReason = "Failed to get ItemMeta";
        }

        if (failureReason == null) {
            gui.setItem(49, infoButton);
            successfulButtons++;
        } else {
            failures.add("info-button Reason: " + failureReason);
        }

        if (successfulButtons == totalButtons) {
            debugManager.logDebug("[DEBUG - BountyCancelGUI] All buttons created");
        } else {
            String failureMessage = "[DEBUG - BountyCancelGUI] " + successfulButtons + "/" + totalButtons + " buttons created";
            if (!failures.isEmpty()) {
                failureMessage += ", failed to create: " + String.join(", ", failures);
            }
            debugManager.bufferFailure("BountyCancel_info_button_" + System.currentTimeMillis(), failureMessage);
        }
    }

    private ItemStack createNavigationButton(String buttonType, int page, int totalPages) {
        ItemStack button = new ItemStack(Material.ARROW);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            if (buttonType.equals("previous")) {
                meta.setDisplayName("§aPrevious Page");
                meta.setLore(Arrays.asList("§7Go to page §e" + page, "", "§aClick to navigate"));
            } else if (buttonType.equals("next")) {
                meta.setDisplayName("§aNext Page");
                meta.setLore(Arrays.asList("§7Go to page §e" + (page + 2), "", "§aClick to navigate"));
            }
            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Creates a bounty item for the cancel GUI
     * // note: Generates a player skull representing a bounty to cancel
     */
    private ItemStack createBountyItem(Player player, UUID targetUUID, int amount) {
        DebugManager debugManager = plugin.getDebugManager();
        String failureReason = null;

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
        boolean taxEnabled = plugin.getConfig().getBoolean("bounty-cancel-tax-enabled", true);
        double taxRate = plugin.getConfig().getDouble("bounty-cancel-tax-rate", 10.0);
        double refund = amount;
        double taxAmount = 0;
        if (taxEnabled) {
            taxAmount = plugin.getTaxManager().calculateTax(amount, null);
            refund = amount - taxAmount;
        }
        String setTime = getFormattedSetTime();

        ItemStack item = SkullUtils.createVersionAwarePlayerHead(targetPlayer);
        if (!VersionUtils.isPlayerHead(item)) {
            debugManager.logWarning("[DEBUG - BountyCancelGUI] Failed to create PLAYER_HEAD for " + targetName);
            failureReason = "Failed to create PLAYER_HEAD";
            item = new ItemStack(Material.STONE);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debugManager.logWarning("[DEBUG - BountyCancelGUI] Failed to get ItemMeta for bounty item for " + targetName);
            failureReason = "Failed to get ItemMeta";
            return item;
        }

        FileConfiguration config = plugin.getConfig();
        PlaceholderContext context = PlaceholderContext.create()
                .player(player)
                .target(targetUUID)
                .bountyAmount((double) amount)
                .refundAmount(refund)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .setTime(setTime);

        String displayName = Placeholders.apply(config.getString("bounty-cancel.name", "&cCancel Bounty on &e%target%"), context);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

        List<String> loreLines = config.getStringList("bounty-cancel.lore");
        if (loreLines.isEmpty()) {
            loreLines = Arrays.asList(
                    "&7Target: &f%target%",
                    "&7Amount: &a$%bounty_amount%",
                    "&7Set Time: &e%set_time%",
                    "",
                    "&eClick to cancel this bounty",
                    "&cRefund: &a$%refund_amount%",
                    "&7Tax: &c$%tax_amount%"
            );
        }
        List<String> lore = Placeholders.apply(loreLines, context);
        meta.setLore(lore);

        item.setItemMeta(meta);

        if (failureReason != null) {
            itemFailures.put("bounty-item-" + targetUUID, failureReason);
        }

        return item;
    }

    private String getFormattedSetTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
        return sdf.format(new Date());
    }

    /**
     * Handles inventory click events for the cancel GUI
     * // note: Processes clicks on bounty skulls and navigation buttons
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof Player) || !inventory.getHolder().equals(player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(ChatColor.translateAlternateColorCodes('&', guiTitle))) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null) return;

        if (slot == 48 && clickedItem.getType() == Material.ARROW) {
            handleNavigationClick(player, -1, inventory);
        } else if (slot == 50 && clickedItem.getType() == Material.ARROW) {
            handleNavigationClick(player, 1, inventory);
        } else if (VersionUtils.isPlayerHead(clickedItem)) {
            handleBountyCancelClick(player, clickedItem, inventory);
        }
        player.updateInventory();
    }

    private void handleBountyCancelClick(Player player, ItemStack item, Inventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            MessageUtils.sendFormattedMessage(player, "cancel-error");
            return;
        }

        List<String> lore = meta.getLore();
        String targetName = null;
        int amount = 0;

        for (String line : lore) {
            String stripped = line.replaceAll("§[0-9a-fk-or]", "");
            if (stripped.startsWith("Target: ")) {
                targetName = stripped.substring(8);
            } else if (stripped.startsWith("Amount: $")) {
                try {
                    amount = Integer.parseInt(stripped.substring(9));
                } catch (NumberFormatException e) {
                    // Continue to next line
                }
            }
        }

        if (targetName == null || amount == 0) {
            MessageUtils.sendFormattedMessage(player, "cancel-error");
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
        cancelBounty(player, player.getUniqueId(), targetPlayer.getUniqueId(), amount);
        player.closeInventory();
    }

    private void handleNavigationClick(Player player, int direction, Inventory inventory) {
        int currentPage = getCurrentPage(inventory);
        openCancelGUI(player, currentPage + direction);
    }

    /**
     * Cancels a bounty and processes the refund
     */
    private void cancelBounty(Player player, UUID setterUUID, UUID targetUUID, int amount) {
        plugin.getBountyManager().removeBounty(setterUUID, targetUUID);

        boolean taxEnabled = plugin.getConfig().getBoolean("bounty-cancel-tax-enabled", true);
        double taxRate = plugin.getConfig().getDouble("bounty-cancel-tax-rate", 10.0);
        double refund = amount;
        double taxAmount = 0;

        if (taxEnabled) {
            taxAmount = plugin.getTaxManager().calculateTax(amount, null);
            refund = amount - taxAmount;
        }

        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, refund);
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("refund", String.format("%.2f", refund));
        placeholders.put("tax_rate", String.format("%.1f", taxRate));
        placeholders.put("tax_amount", String.format("%.2f", taxAmount));


        MessageUtils.sendFormattedMessage(player, "bounty-cancelled");

        if (taxEnabled && taxAmount > 0) {
            MessageUtils.sendFormattedMessage(player, "bounty-cancel-tax");
        }
    }

    /**
     * Gets the current page from the inventory
     * // note: Extracts the current page number from the inventory title
     */
    private int getCurrentPage(Inventory inventory) {
        String title = inventory.getHolder() instanceof Player ? ChatColor.translateAlternateColorCodes('&', guiTitle) + " (Page " + (getCurrentPage(inventory) + 1) + "/" + getTotalPages(inventory) + ")" : "";
        try {
            String pageInfo = title.substring(title.indexOf("(Page ") + 6);
            String currentPageStr = pageInfo.substring(0, pageInfo.indexOf("/"));
            return Integer.parseInt(currentPageStr.trim()) - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Gets the total pages for the inventory
     * // note: Calculates total pages based on player bounties
     */
    private int getTotalPages(Inventory inventory) {
        Player player = inventory.getHolder() instanceof Player ? (Player) inventory.getHolder() : null;
        if (player == null) return 1;
        UUID playerUUID = player.getUniqueId();
        Map<UUID, Map<UUID, Integer>> allBounties = plugin.getBountyManager().listAllBounties();
        List<Map.Entry<UUID, Integer>> playerBounties = new ArrayList<>();
        for (Map.Entry<UUID, Map<UUID, Integer>> targetEntry : allBounties.entrySet()) {
            if (targetEntry.getValue().containsKey(playerUUID)) {
                playerBounties.add(new AbstractMap.SimpleEntry<>(targetEntry.getKey(), targetEntry.getValue().get(playerUUID)));
            }
        }
        return (int) Math.ceil((double) playerBounties.size() / itemsPerPage);
    }
}