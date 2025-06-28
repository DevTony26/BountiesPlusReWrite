
package tony26.bountiesPlus.wrappers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.cryptomorin.xseries.XMaterial;

public interface VersionWrapper {

    /**
     * Creates a player head with the given player's skin
     */
    ItemStack createPlayerHead(String playerName);

    /**
     * Sends a title and subtitle to the player
     */
    void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);

    /**
     * Spawns particles at the player's location
     */
    void spawnParticles(Player player, String particleType, int count);

    /**
     * Gets the player's UUID as a string
     */
    String getPlayerUUID(Player player);

    /**
     * Gets version-safe material for cross-version compatibility
     */
    default Material getMaterial(String materialName) {
        try {
            XMaterial xMaterial = XMaterial.valueOf(materialName.toUpperCase());
            return xMaterial.parseMaterial();
        } catch (Exception e) {
            // Fallback to direct material lookup
            try {
                return Material.valueOf(materialName.toUpperCase());
            } catch (Exception ex) {
                return Material.STONE; // Ultimate fallback
            }
        }
    }

    /**
     * Applies a glow effect to an item if enabled
     * // note: Adds enchantment glow to the item for visual effect, version-safe for 1.8.8 and later
     */
    void applyGlow(ItemStack item, boolean glowEnabled);

    /**
     * Creates a player head ItemStack using XMaterial
     */
    default ItemStack createPlayerHeadItem() {
        XMaterial playerHead = XMaterial.PLAYER_HEAD;
        return playerHead.parseItem();
    }

    /**
     * Checks if a material exists in this version
     */
    default boolean materialExists(String materialName) {
        try {
            XMaterial.valueOf(materialName.toUpperCase());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the success sound for this version
     */
    String getSuccessSound();

    /**
     * Gets the error sound for this version
     */
    String getErrorSound();
}