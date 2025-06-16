
package tony26.bountiesPlus.GUIs;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.SkullUtils;
import tony26.bountiesPlus.TaxManager;
import tony26.bountiesPlus.utils.MessageUtils;
import tony26.bountiesPlus.utils.Placeholders;
import tony26.bountiesPlus.utils.PlaceholderContext;
import tony26.bountiesPlus.utils.VersionUtils;
import tony26.bountiesPlus.TaxManager;
import java.text.SimpleDateFormat;
import java.util.*;

public class BountyCancel implements Listener {

    private final BountiesPlus plugin;
    private FileConfiguration config;
    private String guiTitle;
    private int guiSize;
    private int itemsPerPage;

    public BountyCancel(BountiesPlus plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig(); // Use main config instead
        loadConfiguration();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfiguration() {
        this.guiTitle = "&4Cancel Bounty";
        this.guiSize = 54;
        this.itemsPerPage = 36;
    }

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
            Map<String, String> placeholders = new HashMap<>();
            MessageUtils.sendFormattedMessage(player, "no-bounties-to-cancel");
            return;
        }

        new BountyCancel(plugin).openCancelGUI(player, 0);
    }

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
        String title = guiTitle + " (Page " + (page + 1) + "/" + totalPages + ")";
        Inventory gui = Bukkit.createInventory(null, guiSize, title);
        addBorder(gui);
        addBountyItems(gui, playerBounties, page, player);
        addNavigationButtons(gui, page, totalPages, playerBounties.size());
        addInfoButton(gui, playerBounties, page, totalPages, player);
        player.openInventory(gui);
    }

    private void addBorder(Inventory gui) {
        VersionUtils.MaterialData materialData = new VersionUtils.MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 7);
        Material borderMaterial = materialData.getMaterial();
        ItemStack borderItem = new ItemStack(borderMaterial, 1, (short) 7); // Gray glass pane
        ItemMeta meta = borderItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            borderItem.setItemMeta(meta);
        }

        // Border slots
        int[] borderSlots = {0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,49,50,51,52,53};
        for (int slot : borderSlots) {
            if (slot < gui.getSize()) {
                gui.setItem(slot, borderItem);
            }
        }
    }

    private void addBountyItems(Inventory gui, List<Map.Entry<UUID, Integer>> playerBounties, int page, Player player) {
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, playerBounties.size());
        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<UUID, Integer> bountyEntry = playerBounties.get(i);
            UUID targetUUID = bountyEntry.getKey();
            int amount = bountyEntry.getValue();
            ItemStack bountyItem = createBountyItem(player, targetUUID, amount);
            gui.setItem(slot, bountyItem);
            slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 45) break;
        }
    }

    private void addNavigationButtons(Inventory gui, int page, int totalPages, int totalBounties) {
        if (page > 0) {
            ItemStack prevButton = createNavigationButton("previous", page, totalPages);
            gui.setItem(48, prevButton);
        }
        if (page < totalPages - 1) {
            ItemStack nextButton = createNavigationButton("next", page, totalPages);
            gui.setItem(50, nextButton);
        }
    }

    private void addInfoButton(Inventory gui, List<Map.Entry<UUID, Integer>> playerBounties, int page, int totalPages, Player player) {
        double totalAmount = playerBounties.stream().mapToDouble(entry -> entry.getValue()).sum();
        boolean taxEnabled = plugin.getConfig().getBoolean("bounty-cancel-tax-enabled", true);
        double taxRate = plugin.getConfig().getDouble("bounty-cancel-tax-rate", 10.0);
        double totalRefund = taxEnabled ? totalAmount * (1 - taxRate / 100.0) : totalAmount;
        ItemStack infoButton = new ItemStack(Material.BOOK);
        ItemMeta meta = infoButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Bounty Cancellation Info");
            List<String> lore = Arrays.asList(
                    "§7Total Bounties: §e" + playerBounties.size(),
                    "§7Page: §e" + (page + 1) + "§7/§e" + totalPages,
                    "",
                    "§eTax Rate: §c" + String.format("%.1f", taxRate) + "%",
                    "§eTotal Refund: §a$" + String.format("%.2f", totalRefund)
            );
            meta.setLore(lore);
            infoButton.setItemMeta(meta);
        }

        gui.setItem(49, infoButton);
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
     */
    private ItemStack createBountyItem(Player player, UUID targetUUID, int amount) {
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
            plugin.getLogger().warning("Failed to create PLAYER_HEAD for " + targetName);
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
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
        }
        return item;
    }

    private String getFormattedSetTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
        return sdf.format(new Date());
    }

    /**
     * Handles inventory click events for the cancel GUI // note: Processes clicks on bounty skulls and navigation buttons
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (!title.contains("Cancel Bounty")) return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (clickedItem == null) return;

        if (slot == 48 && clickedItem.getType() == Material.ARROW) {
            handleNavigationClick(player, -1, event.getInventory());
        } else if (slot == 50 && clickedItem.getType() == Material.ARROW) {
            handleNavigationClick(player, 1, event.getInventory());
        } else if (VersionUtils.isPlayerHead(clickedItem)) {
            handleBountyCancelClick(player, clickedItem, event.getInventory());
        }
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

    private int getCurrentPage(Inventory inventory) {
        String title = inventory.getTitle();
        try {
            String pageInfo = title.substring(title.indexOf("(Page ") + 6);
            String currentPageStr = pageInfo.substring(0, pageInfo.indexOf("/"));
            return Integer.parseInt(currentPageStr) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
}