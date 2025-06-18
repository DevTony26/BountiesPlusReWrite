package tony26.bountiesPlus.Items;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.utils.VersionUtils;
import java.util.*;

public class UAV implements Listener {

    private final BountiesPlus plugin;
    private final Map<UUID, BukkitTask> activeUAVs = new HashMap<>();
    private final Map<UUID, Set<UUID>> glowingTargets = new HashMap<>();

    private int maxUses;
    private double searchRadius;
    private int effectDuration;
    private String uavName;
    private List<String> uavLore;
    private String noTargetMessage;
    private String uavStartMessage;
    private String uavExpiredMessage;
    private String itemIdentifier;
    private String alreadyActiveMessage;
    private String versionNotSupportedMessage;
    private String targetFoundMessage;
    private String effectExpiredMessage;

    public UAV(BountiesPlus plugin) {
        this.plugin = plugin;
        loadConfiguration();

        if (VersionUtils.supportsGlowingEffect()) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        } else {
            plugin.getLogger().info("UAV disabled - requires Minecraft 1.9+ for glow effect");
        }
    }

    private void loadConfiguration() {
        FileConfiguration itemsConfig = plugin.getItemsConfig();

        this.maxUses = itemsConfig.getInt("uav.max-uses", 3);
        this.searchRadius = itemsConfig.getDouble("uav.search-radius", 100.0);
        this.effectDuration = itemsConfig.getInt("uav.effect-duration", 30);
        this.uavName = itemsConfig.getString("uav.item-name", "&6&lUAV Scanner");
        this.uavLore = itemsConfig.getStringList("uav.item-lore");
        this.noTargetMessage = itemsConfig.getString("uav.messages.no-target", "&cNo bounty targets found within range!");
        this.uavStartMessage = itemsConfig.getString("uav.messages.uav-start", "&aUAV deployed! Bounty targets are now glowing.");
        this.uavExpiredMessage = itemsConfig.getString("uav.messages.uav-expired", "&cYour UAV has run out of uses and was removed!");
        this.alreadyActiveMessage = itemsConfig.getString("uav.messages.already-active", "&cUAV is already active!");
        this.versionNotSupportedMessage = itemsConfig.getString("uav.messages.version-not-supported", "&cUAV requires Minecraft 1.9 or higher!");
        this.targetFoundMessage = itemsConfig.getString("uav.messages.target-found", "&aBounty target detected: &e%target%");
        this.effectExpiredMessage = itemsConfig.getString("uav.messages.effect-expired", "&eUAV effect has expired.");
        this.itemIdentifier = itemsConfig.getString("uav.item-identifier", "UAV_SCANNER_ITEM");

        if (uavLore.isEmpty()) {
            uavLore = new ArrayList<>();
            uavLore.add("&7Right-click to deploy UAV scanner");
            uavLore.add("&7within a " + (int)searchRadius + " block radius");
            uavLore.add("&c&lUses: &f%uses%&c/&f%max_uses%");
            uavLore.add("");
            uavLore.add("&eBounty targets will glow for");
            uavLore.add("&e" + effectDuration + " seconds through walls");
        }
    }

    /**
     * Creates a new UAV item with the specified number of uses // note: Creates a compass item with NBT tags for type and uses
     */
    public ItemStack createUAVItem(int uses) {
        if (!VersionUtils.supportsGlowingEffect()) return null;

        ItemStack uav = new ItemStack(Material.COMPASS);
        ItemMeta meta = uav.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', uavName));
            List<String> lore = new ArrayList<>();
            for (String line : uavLore) {
                String processedLine = line.replace("%uses%", String.valueOf(uses))
                        .replace("%max_uses%", String.valueOf(maxUses))
                        .replace("%radius%", String.valueOf((int)searchRadius))
                        .replace("%duration%", String.valueOf(effectDuration));
                lore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);
            uav.setItemMeta(meta);
            uav = VersionUtils.setNBTString(uav, "item_type", "uav");
            uav = VersionUtils.setNBTInteger(uav, "uses", uses);
        }

        return uav;
    }

    /**
     * Checks if an item is a UAV item // note: Verifies item type using NBT tag
     */
    public boolean isUAVItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || item.getItemMeta() == null) {
            return false;
        }
        return "uav".equals(VersionUtils.getNBTString(item, "item_type"));
    }

    /**
     * Gets the remaining uses from a UAV item // note: Retrieves uses from NBT tag
     */
    private int getUsesFromItem(ItemStack item) {
        if (!isUAVItem(item)) return 0;
        Integer uses = VersionUtils.getNBTInteger(item, "uses");
        return uses != null ? uses : 0;
    }

    /**
     * Updates the UAV item with new uses count // note: Creates a new item with updated uses in NBT and lore
     */
    private ItemStack updateUAVUses(ItemStack item, int newUses) {
        if (!isUAVItem(item)) return item;
        return createUAVItem(newUses);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isUAVItem(item)) {
            return;
        }

        event.setCancelled(true);

        if (!VersionUtils.supportsGlowingEffect()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', versionNotSupportedMessage));
            return;
        }

        if (activeUAVs.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyActiveMessage));
            return;
        }

        int currentUses = getUsesFromItem(item);
        if (currentUses <= 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', uavExpiredMessage));
            player.getInventory().remove(item);
            return;
        }

        List<Player> bountyTargets = findBountyTargets(player);
        if (bountyTargets.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noTargetMessage));
            return;
        }

        startUAV(player, bountyTargets);

        int newUses = currentUses - 1;
        if (newUses <= 0) {
            player.getInventory().remove(item);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', uavExpiredMessage));
        } else {
            ItemStack updatedItem = updateUAVUses(item, newUses);
            int slot = player.getInventory().getHeldItemSlot();
            player.getInventory().setItem(slot, updatedItem);
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', uavStartMessage));
    }

    /**
     * Finds players with bounties within the search radius
     * // note: Returns a list of valid bounty targets, respecting jammers
     */
    private List<Player> findBountyTargets(Player scanner) {
        List<Player> targets = new ArrayList<>();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(scanner)) continue;

            if (!plugin.getBountyManager().hasBounty(target.getUniqueId())) continue;

            if (!target.getWorld().equals(scanner.getWorld())) continue;

            if (scanner.getLocation().distance(target.getLocation()) > searchRadius) continue;

            // Check if target has an active jammer
            if (plugin.getJammer() != null && plugin.getJammer().isBlocked(target)) {
                scanner.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getJammer().getJammerBlockedMessage()));
                continue;
            }

            targets.add(target);
        }

        return targets;
    }

    private void startUAV(Player scanner, List<Player> targets) {
        UUID scannerUUID = scanner.getUniqueId();
        Set<UUID> targetUUIDs = new HashSet<>();

        for (Player target : targets) {
            targetUUIDs.add(target.getUniqueId());
            applyGlowEffect(target);
            String message = targetFoundMessage.replace("%target%", target.getName());
            scanner.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        glowingTargets.put(scannerUUID, targetUUIDs);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                endUAV(scannerUUID);
            }
        }.runTaskLater(plugin, effectDuration * 20L);

        activeUAVs.put(scannerUUID, task);
    }

    private void applyGlowEffect(Player target) {
        try {
            PotionEffectType glowType = PotionEffectType.getByName("GLOWING");
            if (glowType != null) {
                target.addPotionEffect(new PotionEffect(glowType, effectDuration * 20, 0, false, false));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply glow effect: " + e.getMessage());
        }
    }

    private void endUAV(UUID scannerUUID) {
        Set<UUID> targets = glowingTargets.remove(scannerUUID);
        if (targets != null) {
            for (UUID targetUUID : targets) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null) {
                    PotionEffectType glowType = PotionEffectType.getByName("GLOWING");
                    if (glowType != null) {
                        target.removePotionEffect(glowType);
                    }
                }
            }
        }

        BukkitTask task = activeUAVs.remove(scannerUUID);
        if (task != null) {
            task.cancel();
        }

        Player scanner = Bukkit.getPlayer(scannerUUID);
        if (scanner != null) {
            scanner.sendMessage(ChatColor.translateAlternateColorCodes('&', effectExpiredMessage));
        }
    }

    public void cleanup() {
        for (BukkitTask task : activeUAVs.values()) {
            task.cancel();
        }
        activeUAVs.clear();

        for (Set<UUID> targets : glowingTargets.values()) {
            for (UUID targetUUID : targets) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null) {
                    PotionEffectType glowType = PotionEffectType.getByName("GLOWING");
                    if (glowType != null) {
                        target.removePotionEffect(glowType);
                    }
                }
            }
        }
        glowingTargets.clear();
    }

    public boolean hasActiveUAV(Player player) {
        return activeUAVs.containsKey(player.getUniqueId());
    }

    public void stopUAV(Player player) {
        endUAV(player.getUniqueId());
    }

    public int getMaxUses() { return maxUses; }
    public double getSearchRadius() { return searchRadius; }
    public int getEffectDuration() { return effectDuration; }
}