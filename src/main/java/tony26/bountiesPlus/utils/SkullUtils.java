
package tony26.bountiesPlus.utils;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.wrappers.VersionWrapper;
import tony26.bountiesPlus.wrappers.VersionWrapperFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

public class SkullUtils {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");

    public static ItemStack createCustomBountySkull(Player killed, Map<UUID, Integer> bounties, Player killer) {
        try {
            BountiesPlus plugin = BountiesPlus.getInstance();
            plugin.getLogger().info("Creating bounty skull for " + killed.getName() + " killed by " + killer.getName());

            // Use the new version-aware method to create the skull with proper skin
            ItemStack skull = createVersionAwarePlayerHead(killed);

            if (skull == null || skull.getType() == Material.AIR) {
                plugin.getLogger().warning("createVersionAwarePlayerHead returned null/air for " + killed.getName());
                return null;
            }

            plugin.getLogger().info("Successfully created skull with skin for " + killed.getName());

            if (skull.getItemMeta() instanceof SkullMeta) {
                SkullMeta meta = (SkullMeta) skull.getItemMeta();

                // Get configuration
                FileConfiguration config = plugin.getConfig();
                String skullName = config.getString("bounty-skull.name", "&c&l☠ &4Bounty Head of %target% &c&l☠");
                List<String> skullLore = config.getStringList("bounty-skull.lore");

                // Calculate total bounty
                int totalBounty = bounties.values().stream().mapToInt(Integer::intValue).sum();

                // Create setter list
                List<String> setterNames = new ArrayList<>();
                for (UUID setterUUID : bounties.keySet()) {
                    String setterName = "Unknown";
                    try {
                        setterName = plugin.getServer().getOfflinePlayer(setterUUID).getName();
                        if (setterName == null) setterName = "Unknown";
                    } catch (Exception e) {
                        // Keep "Unknown"
                    }
                    setterNames.add(setterName);
                }
                String setterList = String.join(", ", setterNames);

                // Get current time for death time
                String deathTime = dateFormat.format(new Date());

                // Apply placeholders to name
                String finalName = applyPlaceholders(skullName, killed.getName(), totalBounty,
                        bounties.size(), killer.getName(), deathTime, setterList);
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', finalName));

                // Apply placeholders to lore
                List<String> finalLore = new ArrayList<>();
                for (String line : skullLore) {
                    String processedLine = applyPlaceholders(line, killed.getName(), totalBounty,
                            bounties.size(), killer.getName(), deathTime, setterList);
                    finalLore.add(ChatColor.translateAlternateColorCodes('&', processedLine));
                }

                // Add bounty identification data
                addBountySkullData(meta, killed, bounties, killer);
                meta.setLore(finalLore);

                // Add enchantment glow if configured
                boolean shouldGlow = config.getBoolean("bounty-skull.enchantment-glow", true);
                if (shouldGlow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                skull.setItemMeta(meta);
                plugin.getLogger().info("Successfully configured bounty skull for " + killed.getName());
                return skull;
            } else {
                plugin.getLogger().warning("Skull meta is not SkullMeta for " + killed.getName());
            }
        } catch (Exception e) {
            BountiesPlus.getInstance().getLogger().warning("Failed to create custom bounty skull: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static String applyPlaceholders(String text, String playerName, int totalBounty,
                                            int bountyCount, String killerName, String deathTime, String setterList) {
        return text.replace("%target%", playerName)
                .replace("%player%", playerName)
                .replace("%total_bounty%", String.valueOf(totalBounty))
                .replace("%bounty_count%", String.valueOf(bountyCount))
                .replace("%killer%", killerName)
                .replace("%death_time%", deathTime)
                .replace("%setter_list%", setterList);
    }

    /**
     * Adds bounty-specific data to a skull meta
     */
    private static void addBountySkullData(SkullMeta meta, Player killed, Map<UUID, Integer> bounties, Player killer) {
        List<String> currentLore = meta.getLore();
        if (currentLore == null) {
            currentLore = new ArrayList<>();
        }

        // Add invisible identifier lines
        currentLore.add(ChatColor.DARK_GRAY + "" + ChatColor.MAGIC + "BOUNTY_SKULL");
        currentLore.add(ChatColor.DARK_GRAY + "" + ChatColor.MAGIC + "KILLED:" + killed.getUniqueId().toString());
        currentLore.add(ChatColor.DARK_GRAY + "" + ChatColor.MAGIC + "KILLER:" + killer.getUniqueId().toString());

        // Store total bounty value
        int totalBountyValue = bounties.values().stream().mapToInt(Integer::intValue).sum();
        currentLore.add(ChatColor.DARK_GRAY + "" + ChatColor.MAGIC + "VALUE:" + totalBountyValue);

        meta.setLore(currentLore);
    }

    /**
     * Creates a player head with the target player's skin using version-aware methods
     * // note: Creates a player head ItemStack with proper skin for the server version
     */
    public static ItemStack createVersionAwarePlayerHead(OfflinePlayer targetPlayer) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        String playerName = targetPlayer.getName();
        if (playerName == null || playerName.isEmpty()) {
            plugin.getLogger().warning("Cannot create player head: OfflinePlayer has no name (UUID: " + targetPlayer.getUniqueId() + ")");
            return createFallbackSkull(targetPlayer);
        }
        plugin.getLogger().info("Creating player head for " + playerName + ", isLegacy=" + VersionUtils.isLegacy());
        ItemStack head = VersionUtils.getXMaterialItemStack("PLAYER_HEAD");
        if (head == null || head.getType() == XMaterial.STONE.parseMaterial()) {
            plugin.getLogger().warning("Failed to create PLAYER_HEAD item for " + playerName + ": null or STONE, falling back to SKELETON_SKULL");
            return createFallbackSkull(targetPlayer);
        }
        plugin.getLogger().info("Initial head item for " + playerName + ": type=" + head.getType().name() + (VersionUtils.isLegacy() ? ", durability=" + head.getDurability() : ""));
        if (VersionUtils.isLegacy()) {
            if (head.getType() != XMaterial.PLAYER_HEAD.parseMaterial() || head.getDurability() != 3) {
                plugin.getLogger().warning("Invalid legacy skull for " + playerName + ": expected SKULL_ITEM:3, got " + head.getType().name() + ":" + head.getDurability() + ", falling back to SKELETON_SKULL");
                return createFallbackSkull(targetPlayer);
            }
        } else {
            if (!head.getType().name().equals("PLAYER_HEAD")) {
                plugin.getLogger().warning("Invalid modern skull for " + playerName + ": expected PLAYER_HEAD, got " + head.getType().name() + ", falling back to SKELETON_SKULL");
                return createFallbackSkull(targetPlayer);
            }
        }
        ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta)) {
            plugin.getLogger().warning("ItemMeta is not SkullMeta for " + playerName + ": type=" + head.getType().name() + ", metaType=" + (rawMeta != null ? rawMeta.getClass().getSimpleName() : "null") + ", falling back to SKELETON_SKULL");
            head.setItemMeta(rawMeta);
            return createFallbackSkull(targetPlayer);
        }
        SkullMeta skullMeta = (SkullMeta) rawMeta;
        try {
            if (!VersionUtils.isLegacy()) {
                try {
                    Method setOwningPlayer = SkullMeta.class.getMethod("setOwningPlayer", OfflinePlayer.class);
                    setOwningPlayer.invoke(skullMeta, targetPlayer);
                    head.setItemMeta(skullMeta);
                    plugin.getLogger().info("Set owner for " + playerName + " using setOwningPlayer");
                    if (isSkinApplied(skullMeta, playerName)) {
                        plugin.getLogger().info("Skin applied for " + playerName + " using setOwningPlayer");
                        return head;
                    }
                    plugin.getLogger().warning("setOwningPlayer failed to apply skin for " + playerName + ", falling back to setOwner");
                } catch (NoSuchMethodException e) {
                    plugin.getLogger().warning("setOwningPlayer not available for " + playerName + ", falling back to setOwner");
                }
            }
            skullMeta.setOwner(playerName);
            head.setItemMeta(skullMeta);
            plugin.getLogger().info("Set owner for " + playerName + " using setOwner");
            if (isSkinApplied(skullMeta, playerName)) {
                plugin.getLogger().info("Skin applied for " + playerName + " using setOwner");
                return head;
            }
            plugin.getLogger().warning("setOwner failed to apply skin for " + playerName + ", attempting VersionWrapper");
            VersionWrapper wrapper = VersionWrapperFactory.getWrapper();
            ItemStack skinnedHead = wrapper.createPlayerHead(playerName);
            if (skinnedHead != null && VersionUtils.isPlayerHead(skinnedHead)) {
                SkullMeta skinnedMeta = (SkullMeta) skinnedHead.getItemMeta();
                if (skinnedMeta != null && isSkinApplied(skinnedMeta, playerName)) {
                    plugin.getLogger().info("Skin applied for " + playerName + " using VersionWrapper");
                    return skinnedHead;
                }
                plugin.getLogger().warning("VersionWrapper head for " + playerName + " failed skin validation");
            }
            plugin.getLogger().warning("VersionWrapper failed for " + playerName + ", attempting reflection");
            injectGameProfileViaReflection(skullMeta, targetPlayer);
            head.setItemMeta(skullMeta);
            if (isSkinApplied(skullMeta, playerName)) {
                plugin.getLogger().info("Skin applied for " + playerName + " using reflection");
                return head;
            }
            plugin.getLogger().warning("Reflection failed to apply skin for " + playerName);
        } catch (Exception e) {
            plugin.getLogger().warning("Exception while creating head for " + playerName + ": " + e.getMessage());
        }
        plugin.getLogger().warning("All skin application attempts failed for " + playerName + ", using fallback skull");
        return createFallbackSkull(targetPlayer);
    }

    /**
     * Validates whether a skull has the correct skin applied
     */
    private static boolean isSkinApplied(SkullMeta skullMeta, String expectedOwner) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        try {
            String owner = skullMeta.getOwner();
            if (owner != null && owner.equalsIgnoreCase(expectedOwner)) {
                plugin.getLogger().info("Skull owner matches: " + expectedOwner);
                return true;
            }
            plugin.getLogger().info("Skull owner mismatch: expected " + expectedOwner + ", found " + (owner == null ? "null" : owner));
            if (!VersionUtils.isLegacy()) {
                try {
                    Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    Object profile = profileField.get(skullMeta);
                    if (profile != null) {
                        Method getNameMethod = profile.getClass().getMethod("getName");
                        String profileName = (String) getNameMethod.invoke(profile);
                        if (profileName != null && profileName.equalsIgnoreCase(expectedOwner)) {
                            plugin.getLogger().info("GameProfile name matches for " + expectedOwner);
                            return true;
                        }
                        plugin.getLogger().info("GameProfile name mismatch: expected " + expectedOwner + ", found " + (profileName == null ? "null" : profileName));
                    } else {
                        plugin.getLogger().info("No GameProfile set for skull of " + expectedOwner);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error checking GameProfile for " + expectedOwner + ": " + e.getMessage());
                }
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error validating skull owner for " + expectedOwner + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates a fallback skeleton skull with a placeholder-applied name
     */
    private static ItemStack createFallbackSkull(OfflinePlayer targetPlayer) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        ItemStack head = VersionUtils.getXMaterialItemStack("SKELETON_SKULL");
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            PlaceholderContext context = PlaceholderContext.create()
                    .target(targetPlayer.getUniqueId());
            String fallbackName = Placeholders.apply("&e%bountiesplus_target%", context);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', fallbackName));
            head.setItemMeta(meta);
        } else {
            plugin.getLogger().warning("Failed to set meta for fallback skull (UUID: " + targetPlayer.getUniqueId() + ")");
        }
        return head;
    }

    /**
     * Injects the OfflinePlayer's internal GameProfile (with skin) into skullMeta via reflection
     */
    private static void injectGameProfileViaReflection(SkullMeta skullMeta, OfflinePlayer target) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        try {
            Object profile = null;
            try {
                Field profileField = target.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profile = profileField.get(target);
                if (profile == null) {
                    plugin.getLogger().warning("GameProfile is null for " + targetName);
                    return;
                }
                plugin.getLogger().info("Retrieved GameProfile for " + targetName);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to retrieve GameProfile for " + targetName + ": " + e.getMessage());
                return;
            }
            try {
                Method setter = skullMeta.getClass().getDeclaredMethod("setProfile", profile.getClass());
                setter.setAccessible(true);
                setter.invoke(skullMeta, profile);
                plugin.getLogger().info("Injected GameProfile using setProfile for " + targetName);
            } catch (NoSuchMethodException nsme) {
                try {
                    Field profileField = skullMeta.getClass().getDeclaredField("profile");
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, profile);
                    plugin.getLogger().info("Injected GameProfile using field access for " + targetName);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to inject GameProfile via field for " + targetName + ": " + e.getMessage());
                }
            }
        } catch (Exception t) {
            plugin.getLogger().warning("Reflection injection failed for " + targetName + ": " + t.getMessage());
        }
    }

    /**
     * Checks if the skull has a valid owner (for skin verification)
     */
    private static boolean hasValidOwner(SkullMeta skullMeta, String expectedOwner) {
        try {
            String owner = skullMeta.getOwner(); // Gets owner name (1.8.8 compatible)
            if (owner != null && owner.equalsIgnoreCase(expectedOwner)) {
                return true; // Validates owner matches
            }
            BountiesPlus.getInstance().getLogger().info("Skull owner mismatch: expected " + expectedOwner + ", found " + owner); // Logs mismatch
            return false; // Returns false if owner doesn’t match
        } catch (Exception e) {
            BountiesPlus.getInstance().getLogger().warning("Error validating skull owner for " + expectedOwner + ": " + e.getMessage()); // Logs error
            return false; // Returns false on error
        }
    }

    /**
     * Checks if an item is a valid bounty skull
     * // note: Validates the skull has the BOUNTY_SKULL identifier and respects shop.allow-expired-skulls for expiration
     */
    public static boolean isValidBountySkull(ItemStack item) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        if (item == null || !VersionUtils.isPlayerHead(item)) {
            return false;
        }

        if (!(item.getItemMeta() instanceof SkullMeta)) {
            return false;
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        List<String> lore = meta.getLore();

        if (lore == null || lore.isEmpty()) {
            return false;
        }

        // Check for the bounty skull identifier
        boolean isBountySkull = false;
        for (String line : lore) {
            if (line.contains("BOUNTY_SKULL")) {
                isBountySkull = true;
                break;
            }
        }

        // Also check by display name pattern
        String displayName = meta.getDisplayName();
        if (!isBountySkull && displayName != null && displayName.contains("Bounty Head")) {
            isBountySkull = true;
        }

        if (!isBountySkull) {
            return false;
        }

        // Check expiration if allow-expired-skulls is false
        boolean allowExpiredSkulls = plugin.getConfig().getBoolean("shop.allow-expired-skulls", true);
        if (!allowExpiredSkulls) {
            long expireTime = VersionUtils.getNBTDouble(item, "expire_time").longValue();
            if (expireTime > 0 && System.currentTimeMillis() > expireTime) {
                plugin.getLogger().info("[DEBUG] Bounty skull for " + (meta.getOwner() != null ? meta.getOwner() : "unknown") + " is expired");
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the UUID of the killed player from a bounty skull
     */
    public static UUID getKilledPlayerUUID(ItemStack skull) {
        if (!isValidBountySkull(skull)) {
            return null;
        }

        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        List<String> lore = meta.getLore();

        if (lore == null) {
            return null;
        }

        for (String line : lore) {
            if (line.contains("KILLED:")) {
                try {
                    String uuidString = line.substring(line.indexOf("KILLED:") + 7);
                    // Remove color codes and magic characters
                    uuidString = ChatColor.stripColor(uuidString).replaceAll("[^a-fA-F0-9-]", "");
                    return UUID.fromString(uuidString);
                } catch (Exception e) {
                    // Invalid UUID format
                }
            }
        }

        return null;
    }

    /**
     * Gets the UUID of the killer from a bounty skull
     */
    public static UUID getKillerUUID(ItemStack skull) {
        if (!isValidBountySkull(skull)) {
            return null;
        }

        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        List<String> lore = meta.getLore();

        if (lore == null) {
            return null;
        }

        for (String line : lore) {
            if (line.contains("KILLER:")) {
                try {
                    String uuidString = line.substring(line.indexOf("KILLER:") + 7);
                    // Remove color codes and magic characters
                    uuidString = ChatColor.stripColor(uuidString).replaceAll("[^a-fA-F0-9-]", "");
                    return UUID.fromString(uuidString);
                } catch (Exception e) {
                    // Invalid UUID format
                }
            }
        }

        return null;
    }
}