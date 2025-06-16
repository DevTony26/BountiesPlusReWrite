package tony26.bountiesPlus.wrappers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.SkullUtils;
import tony26.bountiesPlus.utils.VersionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ModernVersionWrapper implements VersionWrapper {

    /**
     * Creates a player head with the specified player's skin
     */
    @Override
    public ItemStack createPlayerHead(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            BountiesPlus.getInstance().getLogger().warning("Cannot create player head: Invalid player name"); // Logs invalid name
            return VersionUtils.getXMaterialItemStack("SKELETON_SKULL"); // Returns skeleton skull as fallback
        }
        ItemStack head = VersionUtils.getXMaterialItemStack("PLAYER_HEAD"); // Creates base player head
        if (head == null || head.getType() == Material.STONE) {
            BountiesPlus.getInstance().getLogger().warning("Failed to create skull item for " + playerName); // Logs creation failure
            return VersionUtils.getXMaterialItemStack("SKELETON_SKULL"); // Returns skeleton skull as fallback
        }
        ItemMeta rawMeta = head.getItemMeta();
        if (!(rawMeta instanceof SkullMeta)) {
            BountiesPlus.getInstance().getLogger().warning("Failed to get SkullMeta for " + playerName); // Logs meta failure
            return head; // Returns head without skin
        }
        SkullMeta skullMeta = (SkullMeta) rawMeta;
        try {
            skullMeta.setOwner(playerName); // Sets skull owner for skin application
            head.setItemMeta(skullMeta); // Applies meta with skin
            if (!hasValidOwner(skullMeta, playerName)) {
                BountiesPlus.getInstance().getLogger().warning("Player head for " + playerName + " created without valid skin, trying reflection"); // Logs skin validation failure
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName); // Gets OfflinePlayer for reflection
                injectGameProfileViaReflection(skullMeta, target); // Attempts reflection fallback
                head.setItemMeta(skullMeta); // Re-applies meta
            }
        } catch (Exception e) {
            BountiesPlus.getInstance().getLogger().warning("Failed to set skull owner for " + playerName + ": " + e.getMessage()); // Logs error
            return VersionUtils.getXMaterialItemStack("SKELETON_SKULL"); // Returns skeleton skull as fallback
        }
        BountiesPlus.getInstance().getLogger().info("Successfully created player head for " + playerName + " (Modern)"); // Logs successful creation
        return head; // Returns configured head with skin
    }

    /**
     * Checks if the skull has a valid owner (for skin verification)
     */
    private boolean hasValidOwner(SkullMeta skullMeta, String expectedOwner) {
        try {
            String owner = skullMeta.getOwner(); // Gets owner name
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
     * Injects the OfflinePlayer's internal GameProfile (with skin) into skullMeta via reflection
     */
    private void injectGameProfileViaReflection(SkullMeta skullMeta, OfflinePlayer target) {
        try {
            Object profile = null; // GameProfile object
            try {
                Field profileField = target.getClass().getDeclaredField("profile"); // Accesses GameProfile field
                profileField.setAccessible(true);
                profile = profileField.get(target); // Gets profile
                BountiesPlus.getInstance().getLogger().info("Got GameProfile from OfflinePlayer for " + target.getName()); // Logs success
            } catch (Exception e) {
                BountiesPlus.getInstance().getLogger().warning("Could not get GameProfile from OfflinePlayer for " + target.getName() + ": " + e.getMessage()); // Logs failure
            }
            if (profile == null) {
                BountiesPlus.getInstance().getLogger().warning("No GameProfile available for " + target.getName() + ", skipping reflection injection"); // Logs no profile
                return;
            }
            try {
                Method setter = skullMeta.getClass().getDeclaredMethod("setProfile", profile.getClass()); // Attempts setProfile method
                setter.setAccessible(true);
                setter.invoke(skullMeta, profile); // Sets profile
                BountiesPlus.getInstance().getLogger().info("Injected GameProfile using setProfile for " + target.getName()); // Logs success
            } catch (NoSuchMethodException nsme) {
                try {
                    Field profileField = skullMeta.getClass().getDeclaredField("profile"); // Attempts direct field access
                    profileField.setAccessible(true);
                    profileField.set(skullMeta, profile); // Sets profile
                    BountiesPlus.getInstance().getLogger().info("Injected GameProfile using field access for " + target.getName()); // Logs success
                } catch (Exception e) {
                    BountiesPlus.getInstance().getLogger().warning("Failed to inject GameProfile for " + target.getName() + ": " + e.getMessage()); // Logs failure
                }
            }
        } catch (Exception t) {
            BountiesPlus.getInstance().getLogger().warning("Reflection injection failed for " + target.getName() + ": " + t.getMessage()); // Logs general failure
        }
    }

    @Override
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        VersionUtils.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    @Override
    public void spawnParticles(Player player, String particleType, int count) {
        VersionUtils.spawnParticle(player, particleType, player.getLocation().add(0, 1, 0), count, 0.5, 0.5, 0.5, 0.1);
    }

    @Override
    public String getPlayerUUID(Player player) {
        return player.getUniqueId().toString();
    }

    @Override
    public boolean materialExists(String materialName) {
        try {
            org.bukkit.Material.valueOf(materialName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets version-safe material for modern versions
     */
    @Override
    public Material getMaterial(String materialName) {
        // Use XMaterial for version-safe material resolution // Ensures cross-version compatibility
        Material material = VersionUtils.getMaterialXM(materialName); // Resolves material via XMaterial
        if (material == Material.STONE && !materialName.equalsIgnoreCase("STONE")) {
            BountiesPlus.getInstance().getLogger().warning("Invalid material '" + materialName + "' in modern version, using STONE"); // Logs warning
        }
        return material; // Returns resolved material
    }

    @Override
    public String getSuccessSound() {
        return "ENTITY.EXPERIENCE.ORB.PICKUP"; // Modern sound name
    }

    @Override
    public String getErrorSound() {
        return "entity.villager.no"; // Modern sound name
    }
}