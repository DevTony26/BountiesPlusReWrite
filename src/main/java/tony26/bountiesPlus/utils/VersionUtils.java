package tony26.bountiesPlus.utils;

import com.cryptomorin.xseries.XMaterial;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import tony26.bountiesPlus.BountiesPlus;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class VersionUtils {

    private static final String version = Bukkit.getBukkitVersion();
    private static final int[] versionNumbers = parseVersion();
    private static final Map<String, MaterialData> MATERIAL_MAPPINGS = new HashMap<>();

    static {
        initializeMaterialMappings();
    }

    // Helper class to store material and damage value
    public static class MaterialData {
        private final Material material;
        private final short data;

        public MaterialData(Material material, short data) {
            this.material = material;
            this.data = data;
        }

        public MaterialData(Material material) {
            this(material, (short) 0);
        }

        public Material getMaterial() {
            return material;
        }

        public short getData() {
            return data;
        }
    }

    /**
     * Checks if the server supports PersistentDataContainer for NBT tags // note: Verifies if Minecraft version is 1.14 or higher
     */
    public static boolean supportsPersistentDataContainer() {
        try {
            Class.forName("org.bukkit.persistence.PersistentDataContainer");
            return isServerVersionAtLeast(1, 14);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Sets a double NBT tag on an item // note: Adds a double NBT tag using Item-NBT-API for cross-version compatibility
     */
    public static ItemStack setNBTDouble(ItemStack item, String key, double value) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemStack newItem = item.clone();
        NBTItem nbtItem = new NBTItem(newItem);
        nbtItem.setDouble("bountiesplus_" + key, value);
        return nbtItem.getItem();
    }

    /**
     * Gets a double NBT tag from an item // note: Retrieves a double value using Item-NBT-API for cross-version compatibility
     */
    public static Double getNBTDouble(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        NBTItem nbtItem = new NBTItem(item);
        return nbtItem.hasKey("bountiesplus_" + key) ? nbtItem.getDouble("bountiesplus_" + key) : null;
    }

    /**
     * Sets a string NBT tag on an item // note: Adds a string NBT tag using Item-NBT-API for cross-version compatibility
     */
    public static ItemStack setNBTString(ItemStack item, String key, String value) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemStack newItem = item.clone();
        NBTItem nbtItem = new NBTItem(newItem);
        nbtItem.setString("bountiesplus_" + key, value);
        return nbtItem.getItem();
    }

    /**
     * Gets a string NBT tag from an item // note: Retrieves a string value using Item-NBT-API for cross-version compatibility
     */
    public static String getNBTString(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        NBTItem nbtItem = new NBTItem(item);
        return nbtItem.hasKey("bountiesplus_" + key) ? nbtItem.getString("bountiesplus_" + key) : null;
    }

    /**
     * Sets an integer NBT tag on an item // note: Adds an integer NBT tag using Item-NBT-API for cross-version compatibility
     */
    public static ItemStack setNBTInteger(ItemStack item, String key, int value) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }
        ItemStack newItem = item.clone();
        NBTItem nbtItem = new NBTItem(newItem);
        nbtItem.setInteger("bountiesplus_" + key, value);
        return nbtItem.getItem();
    }

    /**
     * Gets an integer NBT tag from an item // note: Retrieves an integer value using Item-NBT-API for cross-version compatibility
     */
    public static Integer getNBTInteger(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        NBTItem nbtItem = new NBTItem(item);
        return nbtItem.hasKey("bountiesplus_" + key) ? nbtItem.getInteger("bountiesplus_" + key) : null;
    }

    /**
     * Checks if an item has a specific NBT tag // note: Verifies if the item has a given NBT tag using Item-NBT-API
     */
    public static boolean hasNBTTag(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        NBTItem nbtItem = new NBTItem(item);
        return nbtItem.hasKey("bountiesplus_" + key);
    }

    /**
     * Creates a version-safe ItemStack using XMaterial for cross-version compatibility
     */
    public static ItemStack getXMaterialItemStack(String materialName, int amount) {
        BountiesPlus plugin = BountiesPlus.getInstance();
        plugin.getLogger().info("Resolving material: " + materialName + ", isLegacy=" + isLegacy());
        ItemStack item;
        if (materialName.equalsIgnoreCase("PLAYER_HEAD") && !isLegacy()) {
            // Log runtime API version
            plugin.getLogger().info("Bukkit API version: " + Bukkit.getVersion() + ", BukkitVersion: " + Bukkit.getBukkitVersion());
            // Attempt 1: Material.getMaterial("PLAYER_HEAD")
            try {
                Material playerHeadMaterial = Material.getMaterial("PLAYER_HEAD");
                if (playerHeadMaterial != null) {
                    item = new ItemStack(playerHeadMaterial, amount);
                    plugin.getLogger().info("Created ItemStack via Material.getMaterial(PLAYER_HEAD): " + item.getType().name() + ", amount: " + amount);
                    if (item.getType().name().equals("PLAYER_HEAD")) {
                        return item;
                    }
                    plugin.getLogger().warning("Material.getMaterial(PLAYER_HEAD) created incorrect ItemStack material: expected PLAYER_HEAD, got " + item.getType().name());
                } else {
                    plugin.getLogger().warning("Material.getMaterial(PLAYER_HEAD) returned null");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create PLAYER_HEAD via Material.getMaterial(PLAYER_HEAD): " + e.getMessage() + ", trying reflection");
            }
            // Attempt 2: Reflection to access Material.PLAYER_HEAD
            try {
                Material playerHeadMaterial = (Material) Material.class.getField("PLAYER_HEAD").get(null);
                plugin.getLogger().info("Reflection found Material: " + playerHeadMaterial.name());
                item = new ItemStack(playerHeadMaterial, amount);
                plugin.getLogger().info("Created ItemStack with material: " + item.getType().name() + ", amount: " + amount);
                if (item.getType().name().equals("PLAYER_HEAD")) {
                    return item;
                }
                plugin.getLogger().warning("Reflection created incorrect ItemStack material: expected PLAYER_HEAD, got " + item.getType().name());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                plugin.getLogger().warning("Failed to access PLAYER_HEAD via reflection: " + e.getMessage() + ", trying Material.valueOf");
            }
            // Attempt 3: Direct enum access via Material.valueOf
            try {
                Material playerHeadMaterial = Material.valueOf("PLAYER_HEAD");
                item = new ItemStack(playerHeadMaterial, amount);
                plugin.getLogger().info("Created ItemStack via Material.valueOf: " + item.getType().name() + ", amount: " + amount);
                if (item.getType().name().equals("PLAYER_HEAD")) {
                    return item;
                }
                plugin.getLogger().warning("Material.valueOf created incorrect ItemStack material: expected PLAYER_HEAD, got " + item.getType().name());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Failed to create PLAYER_HEAD via Material.valueOf: " + e.getMessage() + ", falling back to XMaterial");
            }
            // Attempt 4: XMaterial fallback (last resort for modern versions)
            XMaterial xMaterial = XMaterial.matchXMaterial("PLAYER_HEAD").orElse(XMaterial.STONE);
            item = xMaterial.parseItem();
            if (item == null) {
                plugin.getLogger().warning("XMaterial failed to create PLAYER_HEAD, using STONE");
                item = new ItemStack(Material.STONE, amount);
            } else {
                item.setAmount(amount);
                plugin.getLogger().info("XMaterial created ItemStack with material: " + item.getType().name() + ", amount: " + amount);
            }
        } else {
            XMaterial xMaterial = XMaterial.matchXMaterial(materialName.toUpperCase()).orElse(XMaterial.STONE);
            item = xMaterial.parseItem();
            if (item == null) {
                plugin.getLogger().warning("XMaterial failed to create item for '" + materialName + "', using STONE");
                item = new ItemStack(Material.STONE, amount);
            } else {
                item.setAmount(amount);
            }
            if (isLegacy() && materialName.equalsIgnoreCase("PLAYER_HEAD")) {
                item.setType(Material.SKULL_ITEM);
                item.setDurability((short) 3);
                plugin.getLogger().info("Forced PLAYER_HEAD to SKULL_ITEM:3 for legacy version");
            } else if (isLegacy() && xMaterial.getData() != 0) {
                item.setDurability(xMaterial.getData());
            }
        }
        String debugInfo = "Resolved material for " + materialName + ": " + item.getType().name();
        if (isLegacy()) {
            debugInfo += ":" + item.getDurability();
            if (materialName.equalsIgnoreCase("PLAYER_HEAD") && (item.getType() != Material.SKULL_ITEM || item.getDurability() != 3)) {
                plugin.getLogger().warning("Incorrect skull material for PLAYER_HEAD in legacy mode: expected SKULL_ITEM:3, got " + item.getType().name() + ":" + item.getDurability());
            }
        } else if (materialName.equalsIgnoreCase("PLAYER_HEAD") && !item.getType().name().equals("PLAYER_HEAD")) {
            plugin.getLogger().warning("Incorrect skull material for PLAYER_HEAD in modern mode: expected PLAYER_HEAD, got " + item.getType().name());
        }
        plugin.getLogger().info(debugInfo);
        return item;
    }

    /**
     * Checks if an ItemStack is a glass pane
     */
    public static boolean isGlassPane(ItemStack item) {
        if (item == null || item.getType() == null) return false;
        String typeName = item.getType().name();
        if (isLegacy()) {
            return typeName.equals("STAINED_GLASS_PANE") || typeName.equals("THIN_GLASS");
        } else {
            return typeName.endsWith("_STAINED_GLASS_PANE") || typeName.equals("GLASS_PANE");
        }
    }

    /**
     * Creates a version-safe ItemStack with amount of 1
     */
    public static ItemStack getXMaterialItemStack(String materialName) {
        return getXMaterialItemStack(materialName, 1); // Calls main method with amount 1
    }

    /**
     * Checks if the server version is at least the specified version
     */
    public static boolean isServerVersionAtLeast(int major, int minor) {
        return versionNumbers[0] > major || (versionNumbers[0] == major && versionNumbers[1] >= minor); // Checks server version
    }

    /**
     * Checks if the server supports glowing effect
     */
    public static boolean supportsGlowingEffect() {
        return isPost19(); // Returns true for 1.9+
    }

    /**
     * Parses the server version into major, minor, and patch numbers
     */
    private static int[] parseVersion() {
        try {
            String cleanVersion = version.split("-")[0];
            BountiesPlus.getInstance().getLogger().info("Parsing server version: raw=" + version + ", clean=" + cleanVersion);
            String[] parts = cleanVersion.split("\\.");
            int[] nums = new int[3];
            if (parts.length < 1) {
                throw new IllegalArgumentException("Invalid version format: " + cleanVersion);
            }
            nums[0] = Integer.parseInt(parts[0]);
            nums[1] = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9]", "")) : 0;
            nums[2] = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9]", "")) : 0;
            String parsedVersion = nums[0] + "." + nums[1] + "." + nums[2];
            boolean isLegacy = nums[0] == 1 && nums[1] < 13;
            BountiesPlus.getInstance().getLogger().info("Parsed server version: " + parsedVersion + " (Raw: " + version + ", Major: " + nums[0] + ", Minor: " + nums[1] + ", Patch: " + nums[2] + ", Legacy: " + isLegacy + ")");
            return nums;
        } catch (Exception e) {
            BountiesPlus.getInstance().getLogger().warning("Failed to parse server version '" + version + "': " + e.getMessage() + ", defaulting to 1.8.8");
            return new int[]{1, 8, 8};
        }
    }

    /**
     * Returns true if the server is running a version older than 1.13 (legacy)
     */
    public static boolean isLegacy() {
        return versionNumbers[0] == 1 && versionNumbers[1] < 13; // Checks for pre-1.13
    }

    /**
     * Returns true if the server is running 1.9 or higher
     */
    public static boolean isPost19() {
        return versionNumbers[0] > 1 || (versionNumbers[0] == 1 && versionNumbers[1] >= 9); // Checks for 1.9+
    }

    /**
     * Returns true if the server is running 1.11 or higher
     */
    public static boolean isPost111() {
        return versionNumbers[0] > 1 || (versionNumbers[0] == 1 && versionNumbers[1] >= 11); // Checks for 1.11+
    }

    /**
     * Returns true if the server is running 1.13 or higher
     */
    public static boolean isPost113() {
        return versionNumbers[0] > 1 || (versionNumbers[0] == 1 && versionNumbers[1] >= 13); // Checks for 1.13+
    }

    /**
     * Returns true if the server is running 1.16 or higher
     */
    public static boolean isPost116() {
        return versionNumbers[0] > 1 || (versionNumbers[0] == 1 && versionNumbers[1] >= 16); // Checks for 1.16+
    }

    /**
     * Returns true if the server is running 1.20 or higher
     */
    public static boolean isPost120() {
        return versionNumbers[0] > 1 || (versionNumbers[0] == 1 && versionNumbers[1] >= 20); // Checks for 1.20+
    }

    /**
     * Checks if an ItemStack is a player head
     */
    public static boolean isPlayerHead(ItemStack item) {
        if (item == null || item.getType() == null) return false;
        String typeName = item.getType().name();
        if (isLegacy()) {
            return typeName.equals("SKULL_ITEM") && (item.getDurability() == 3 || item.getDurability() == 0);
        } else {
            return typeName.equals("PLAYER_HEAD") || typeName.equals("SKULL_ITEM");
        }
    }

    /**
     * Gets version-specific player head material
     */
    public static Material getPlayerHeadMaterial() {
        return getMaterialSafely("PLAYER_HEAD", "SKULL_ITEM"); // Resolves player head material
    }

    /**
     * Gets version-specific glass pane material
     */
    public static Material getGlassPaneMaterial() {
        return getMaterialSafely("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"); // Resolves black glass pane
    }

    /**
     * Gets white glass pane material
     */
    public static Material getWhiteGlassPaneMaterial() {
        return getMaterialSafely("WHITE_STAINED_GLASS_PANE", "STAINED_GLASS_PANE"); // Resolves white glass pane
    }

    /**
     * Gets experience bottle material
     */
    public static Material getExperienceBottleMaterial() {
        return getMaterialSafely("EXPERIENCE_BOTTLE", "EXP_BOTTLE"); // Resolves experience bottle
    }

    /**
     * Gets red wool material
     */
    public static Material getRedWoolMaterial() {
        return getMaterialSafely("RED_WOOL", "WOOL"); // Resolves red wool
    }

    /**
     * Gets firework star material
     */
    public static Material getFireworkStarMaterial() {
        return getMaterialSafely("FIREWORK_STAR", "FIREWORK_CHARGE"); // Resolves firework star
    }

    /**
     * Gets firework rocket material
     */
    public static Material getFireworkRocketMaterial() {
        return getMaterialSafely("FIREWORK_ROCKET", "FIREWORK"); // Resolves firework rocket
    }

    /**
     * Gets lapis lazuli material
     */
    public static Material getLapisLazuliMaterial() {
        return getMaterialSafely("LAPIS_LAZULI", "INK_SACK"); // Resolves lapis lazuli
    }

    /**
     * Gets totem of undying material
     */
    public static Material getTotemOfUndyingMaterial() {
        return getMaterialSafely("TOTEM_OF_UNDYING", "GOLDEN_APPLE"); // Resolves totem
    }

    /**
     * Gets clock material (version-safe)
     */
    public static Material getClockMaterial() {
        return getMaterialSafely("CLOCK", "WATCH"); // Resolves clock
    }

    /**
     * Gets confirm button material (green concrete or equivalent)
     */
    public static Material getConfirmButtonMaterial() {
        return getMaterialSafely("GREEN_CONCRETE", "WOOL"); // Resolves green concrete
    }

    /**
     * Gets cancel button material (red concrete or equivalent)
     */
    public static Material getCancelButtonMaterial() {
        return getMaterialSafely("RED_CONCRETE", "WOOL"); // Resolves red concrete
    }

    /**
     * Gets experience button material
     */
    public static Material getExperienceButtonMaterial() {
        return getExperienceBottleMaterial(); // Uses experience bottle material
    }

    /**
     * Gets money button material
     */
    public static Material getMoneyButtonMaterial() {
        return Material.EMERALD; // Returns emerald (consistent across versions)
    }

    /**
     * Gets total value button material
     */
    public static Material getTotalValueButtonMaterial() {
        return Material.EMERALD; // Returns emerald
    }

    /**
     * Gets add items button material
     */
    public static Material getAddItemsButtonMaterial() {
        return Material.CHEST; // Returns chest (consistent across versions)
    }

    /**
     * Gets version-safe player head material using XMaterial
     */
    public static Material getPlayerHeadMaterialXM() {
        return XMaterial.PLAYER_HEAD.parseMaterial(); // Resolves player head via XMaterial
    }

    /**
     * Gets version-safe glass pane material using XMaterial
     */
    public static Material getGlassPaneMaterialXM() {
        return XMaterial.WHITE_STAINED_GLASS_PANE.parseMaterial(); // Resolves white glass pane via XMaterial
    }

    /**
     * Gets version-safe lapis lazuli material using XMaterial
     */
    public static Material getLapisLazuliMaterialXM() {
        return XMaterial.LAPIS_LAZULI.parseMaterial(); // Resolves lapis lazuli via XMaterial
    }

    /**
     * Gets version-safe totem of undying material using XMaterial
     */
    public static Material getTotemOfUndyingMaterialXM() {
        return XMaterial.TOTEM_OF_UNDYING.parseMaterial(); // Resolves totem via XMaterial
    }

    /**
     * Gets material using XMaterial with fallback
     */
    public static Material getMaterialXM(String materialName) {
        try {
            XMaterial xMaterial = XMaterial.valueOf(materialName.toUpperCase()); // Resolves material
            return xMaterial.parseMaterial(); // Returns parsed material
        } catch (Exception e) {
            return getMaterialSafely(materialName, "STONE"); // Falls back to safe method
        }
    }

    /**
     * Checks if an ItemStack is a player head using XMaterial
     */
    public static boolean isPlayerHeadXM(ItemStack item) {
        if (item == null) return false; // Checks for null item
        return XMaterial.PLAYER_HEAD.isSimilar(item); // Checks if item is player head
    }

    /**
     * Safely gets a material by name with multiple fallbacks
     */
    public static Material getMaterialSafely(String primaryName, String fallbackName) {
        // Try XMaterial first for modern and legacy compatibility // Prioritizes XMaterial
        XMaterial xMaterial = XMaterial.matchXMaterial(primaryName.toUpperCase()).orElse(null); // Attempts XMaterial lookup
        if (xMaterial != null) {
            Material material = xMaterial.parseMaterial(); // Parses material
            if (material != null) {
                return material; // Returns valid material
            }
        }
        // Fall back to direct lookup // Uses Bukkit API as secondary check
        try {
            return Material.valueOf(primaryName); // Attempts direct material lookup
        } catch (IllegalArgumentException e) {
            BountiesPlus.getInstance().getLogger().warning("Material '" + primaryName + "' not found, trying fallback: " + fallbackName); // Logs warning
            try {
                return Material.valueOf(fallbackName); // Attempts fallback material
            } catch (IllegalArgumentException e2) {
                BountiesPlus.getInstance().getLogger().warning("Fallback material '" + fallbackName + "' not found, using STONE"); // Logs final fallback
                return Material.STONE; // Returns STONE as last resort
            }
        }
    }

    /**
     * Sets the color of a firework star item based on configuration
     */
    public static void setFireworkStarColor(ItemStack item, FileConfiguration config, String colorPath) {
        if (item == null || item.getType() != getFireworkStarMaterial()) {
            return; // Checks for valid firework star
        }
        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();
        if (meta == null) return; // Checks for valid meta
        String colorName = config.getString(colorPath, "WHITE"); // Gets color from config
        Color color = getColorFromName(colorName); // Resolves color
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(color) // Sets color
                .with(FireworkEffect.Type.BALL) // Sets effect type
                .build();
        meta.setEffect(effect); // Applies effect
        item.setItemMeta(meta); // Updates item
    }

    /**
     * Gets a Color object from a color name string
     */
    private static Color getColorFromName(String colorName) {
        switch (colorName.toUpperCase()) {
            case "RED": return Color.RED; // Returns red
            case "BLUE": return Color.BLUE; // Returns blue
            case "GREEN": return Color.GREEN; // Returns green
            case "YELLOW": return Color.YELLOW; // Returns yellow
            case "ORANGE": return Color.ORANGE; // Returns orange
            case "PURPLE": return Color.PURPLE; // Returns purple
            case "PINK": return Color.FUCHSIA; // Returns pink
            case "LIME": return Color.LIME; // Returns lime
            case "AQUA": return Color.AQUA; // Returns aqua
            case "SILVER": return Color.SILVER; // Returns silver
            case "GRAY": return Color.GRAY; // Returns gray
            case "BLACK": return Color.BLACK; // Returns black
            case "MAROON": return Color.MAROON; // Returns maroon
            case "NAVY": return Color.NAVY; // Returns navy
            case "TEAL": return Color.TEAL; // Returns teal
            case "WHITE": default: return Color.WHITE; // Returns white
        }
    }

    /**
     * Sends a title to a player in a version-safe way
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (isPost111()) {
            try {
                Method sendTitleMethod = player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class); // Attempts modern title method
                sendTitleMethod.invoke(player, title, subtitle, fadeIn, stay, fadeOut); // Sends title
            } catch (Exception e) {
                try {
                    player.sendTitle(title, subtitle); // Falls back to simpler method
                } catch (Exception e2) {
                    if (title != null && !title.isEmpty()) {
                        player.sendMessage(title); // Sends title as message
                    }
                    if (subtitle != null && !subtitle.isEmpty()) {
                        player.sendMessage(subtitle); // Sends subtitle as message
                    }
                }
            }
        } else {
            if (title != null && !title.isEmpty()) {
                player.sendMessage(title); // Sends title as message for legacy
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                player.sendMessage(subtitle); // Sends subtitle as message
            }
        }
    }

    /**
     * Creates a boss bar in a version-safe way
     */
    public static Object createBossBar(String message) {
        if (!isPost19()) {
            return null; // Boss bars not supported pre-1.9
        }
        try {
            Class<?> bossBarClass = Class.forName("org.bukkit.boss.BossBar"); // Gets boss bar class
            Class<?> barColorClass = Class.forName("org.bukkit.boss.BarColor"); // Gets bar color class
            Class<?> barStyleClass = Class.forName("org.bukkit.boss.BarStyle"); // Gets bar style class
            Object purple = barColorClass.getField("PURPLE").get(null); // Gets purple color
            Object solid = barStyleClass.getField("SOLID").get(null); // Gets solid style
            Method createBossBar = Bukkit.class.getMethod("createBossBar", String.class, barColorClass, barStyleClass); // Gets create method
            return createBossBar.invoke(null, message, purple, solid); // Creates boss bar
        } catch (Exception e) {
            return null; // Returns null on failure
        }
    }

    /**
     * Spawns particles in a version-safe way
     */
    public static void spawnParticle(Player player, String particleName, Location location, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (!isPost19()) {
            return; // Particles not supported pre-1.9
        }
        try {
            Particle particle = Particle.valueOf(particleName); // Resolves particle
            try {
                Method spawnParticleMethod = player.getClass().getMethod("spawnParticle",
                        Particle.class, Location.class, int.class, double.class, double.class, double.class, double.class); // Attempts full method
                spawnParticleMethod.invoke(player, particle, location, count, offsetX, offsetY, offsetZ, speed); // Spawns particles
            } catch (Exception e) {
                try {
                    Method spawnParticleMethod = player.getClass().getMethod("spawnParticle",
                            Particle.class, Location.class, int.class); // Falls back to simpler method
                    spawnParticleMethod.invoke(player, particle, location, count); // Spawns particles
                } catch (Exception e2) {
                    // Ignores failure
                }
            }
        } catch (IllegalArgumentException e) {
            try {
                Particle fallbackParticle = Particle.valueOf("REDSTONE"); // Uses redstone as fallback
                Method spawnParticleMethod = player.getClass().getMethod("spawnParticle",
                        Particle.class, Location.class, int.class); // Attempts simpler method
                spawnParticleMethod.invoke(player, fallbackParticle, location, count); // Spawns fallback particles
            } catch (Exception ignored) {
                // Ignores failure
            }
        } catch (Exception e) {
            // Ignores other exceptions
        }
    }

    /**
     * Returns the server version as a comparable integer (e.g., 1.8.8 = 1088)
     */
    public static int getVersionNumber() {
        return versionNumbers[0] * 1000 + versionNumbers[1] * 10 + versionNumbers[2]; // Returns version number
    }

    /**
     * Returns the raw version string
     */
    public static String getVersionString() {
        return version; // Returns version string
    }

    /**
     * Gets appropriate sound name for success
     */
    public static String getSuccessSound() {
        return isLegacy() ? "LEVEL_UP" : "ENTITY_PLAYER_LEVELUP"; // Returns success sound
    }

    /**
     * Gets appropriate sound name for error
     */
    public static String getErrorSound() {
        return isLegacy() ? "VILLAGER_NO" : "ENTITY_VILLAGER_NO"; // Returns error sound
    }

    /**
     * Gets appropriate particle name for redstone
     */
    public static String getRedstoneParticleName() {
        return "REDSTONE"; // Returns redstone particle
    }

    private static void initializeMaterialMappings() {
        // Wool colors // Maps legacy wool colors
        if (isLegacy()) {
            MATERIAL_MAPPINGS.put("WHITE_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 0));
            MATERIAL_MAPPINGS.put("ORANGE_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 1));
            MATERIAL_MAPPINGS.put("MAGENTA_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 2));
            MATERIAL_MAPPINGS.put("LIGHT_BLUE_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 3));
            MATERIAL_MAPPINGS.put("YELLOW_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 4));
            MATERIAL_MAPPINGS.put("LIME_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 5));
            MATERIAL_MAPPINGS.put("PINK_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 6));
            MATERIAL_MAPPINGS.put("GRAY_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 7));
            MATERIAL_MAPPINGS.put("LIGHT_GRAY_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 8));
            MATERIAL_MAPPINGS.put("CYAN_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 9));
            MATERIAL_MAPPINGS.put("PURPLE_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 10));
            MATERIAL_MAPPINGS.put("BLUE_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 11));
            MATERIAL_MAPPINGS.put("BROWN_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 12));
            MATERIAL_MAPPINGS.put("GREEN_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 13));
            MATERIAL_MAPPINGS.put("RED_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 14));
            MATERIAL_MAPPINGS.put("BLACK_WOOL", new MaterialData(Material.valueOf("WOOL"), (short) 15));
        }

        // Stained glass // Maps legacy stained glass
        if (isLegacy()) {
            MATERIAL_MAPPINGS.put("WHITE_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 0));
            MATERIAL_MAPPINGS.put("ORANGE_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 1));
            MATERIAL_MAPPINGS.put("MAGENTA_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 2));
            MATERIAL_MAPPINGS.put("LIGHT_BLUE_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 3));
            MATERIAL_MAPPINGS.put("YELLOW_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 4));
            MATERIAL_MAPPINGS.put("LIME_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 5));
            MATERIAL_MAPPINGS.put("PINK_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 6));
            MATERIAL_MAPPINGS.put("GRAY_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 7));
            MATERIAL_MAPPINGS.put("LIGHT_GRAY_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 8));
            MATERIAL_MAPPINGS.put("CYAN_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 9));
            MATERIAL_MAPPINGS.put("PURPLE_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 10));
            MATERIAL_MAPPINGS.put("BLUE_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 11));
            MATERIAL_MAPPINGS.put("BROWN_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 12));
            MATERIAL_MAPPINGS.put("GREEN_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 13));
            MATERIAL_MAPPINGS.put("RED_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 14));
            MATERIAL_MAPPINGS.put("BLACK_STAINED_GLASS", new MaterialData(Material.valueOf("STAINED_GLASS"), (short) 15));
        }

        // Stained glass panes // Maps legacy stained glass panes
        if (isLegacy()) {
            MATERIAL_MAPPINGS.put("WHITE_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 0));
            MATERIAL_MAPPINGS.put("ORANGE_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 1));
            MATERIAL_MAPPINGS.put("MAGENTA_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 2));
            MATERIAL_MAPPINGS.put("LIGHT_BLUE_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 3));
            MATERIAL_MAPPINGS.put("YELLOW_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 4));
            MATERIAL_MAPPINGS.put("LIME_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 5));
            MATERIAL_MAPPINGS.put("PINK_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 6));
            MATERIAL_MAPPINGS.put("GRAY_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 7));
            MATERIAL_MAPPINGS.put("LIGHT_GRAY_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 8));
            MATERIAL_MAPPINGS.put("CYAN_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 9));
            MATERIAL_MAPPINGS.put("PURPLE_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 10));
            MATERIAL_MAPPINGS.put("BLUE_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 11));
            MATERIAL_MAPPINGS.put("BROWN_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 12));
            MATERIAL_MAPPINGS.put("GREEN_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 13));
            MATERIAL_MAPPINGS.put("RED_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 14));
            MATERIAL_MAPPINGS.put("BLACK_STAINED_GLASS_PANE", new MaterialData(Material.valueOf("STAINED_GLASS_PANE"), (short) 15));
        }

        // Concrete mappings // Maps legacy concrete to wool
        if (getVersionNumber() < 1120) {
            MATERIAL_MAPPINGS.put("WHITE_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 0));
            MATERIAL_MAPPINGS.put("ORANGE_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 1));
            MATERIAL_MAPPINGS.put("MAGENTA_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 2));
            MATERIAL_MAPPINGS.put("LIGHT_BLUE_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 3));
            MATERIAL_MAPPINGS.put("YELLOW_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 4));
            MATERIAL_MAPPINGS.put("LIME_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 5));
            MATERIAL_MAPPINGS.put("PINK_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 6));
            MATERIAL_MAPPINGS.put("GRAY_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 7));
            MATERIAL_MAPPINGS.put("LIGHT_GRAY_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 8));
            MATERIAL_MAPPINGS.put("CYAN_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 9));
            MATERIAL_MAPPINGS.put("PURPLE_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 10));
            MATERIAL_MAPPINGS.put("BLUE_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 11));
            MATERIAL_MAPPINGS.put("BROWN_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 12));
            MATERIAL_MAPPINGS.put("GREEN_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 13));
            MATERIAL_MAPPINGS.put("RED_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 14));
            MATERIAL_MAPPINGS.put("BLACK_CONCRETE", new MaterialData(Material.valueOf("WOOL"), (short) 15));
        }

        // Terracotta mappings // Maps legacy terracotta
        if (isLegacy()) {
            MATERIAL_MAPPINGS.put("TERRACOTTA", new MaterialData(Material.valueOf("HARD_CLAY")));
            MATERIAL_MAPPINGS.put("WHITE_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 0));
            MATERIAL_MAPPINGS.put("ORANGE_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 1));
            MATERIAL_MAPPINGS.put("MAGENTA_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 2));
            MATERIAL_MAPPINGS.put("LIGHT_BLUE_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 3));
            MATERIAL_MAPPINGS.put("YELLOW_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 4));
            MATERIAL_MAPPINGS.put("LIME_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 5));
            MATERIAL_MAPPINGS.put("PINK_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 6));
            MATERIAL_MAPPINGS.put("GRAY_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 7));
            MATERIAL_MAPPINGS.put("LIGHT_GRAY_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 8));
            MATERIAL_MAPPINGS.put("CYAN_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 9));
            MATERIAL_MAPPINGS.put("PURPLE_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 10));
            MATERIAL_MAPPINGS.put("BLUE_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 11));
            MATERIAL_MAPPINGS.put("BROWN_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 12));
            MATERIAL_MAPPINGS.put("GREEN_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 13));
            MATERIAL_MAPPINGS.put("RED_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 14));
            MATERIAL_MAPPINGS.put("BLACK_TERRACOTTA", new MaterialData(Material.valueOf("STAINED_CLAY"), (short) 15));
        }

        // Dye mappings // Maps legacy dyes
        if (getVersionNumber() < 1140) {
            if (isLegacy()) {
                MATERIAL_MAPPINGS.put("BLACK_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 0));
                MATERIAL_MAPPINGS.put("RED_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 1));
                MATERIAL_MAPPINGS.put("GREEN_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 2));
                MATERIAL_MAPPINGS.put("BROWN_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 3));
                MATERIAL_MAPPINGS.put("BLUE_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 4));
                MATERIAL_MAPPINGS.put("PURPLE_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 5));
                MATERIAL_MAPPINGS.put("CYAN_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 6));
                MATERIAL_MAPPINGS.put("LIGHT_GRAY_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 7));
                MATERIAL_MAPPINGS.put("GRAY_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 8));
                MATERIAL_MAPPINGS.put("PINK_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 9));
                MATERIAL_MAPPINGS.put("LIME_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 10));
                MATERIAL_MAPPINGS.put("YELLOW_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 11));
                MATERIAL_MAPPINGS.put("LIGHT_BLUE_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 12));
                MATERIAL_MAPPINGS.put("MAGENTA_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 13));
                MATERIAL_MAPPINGS.put("ORANGE_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 14));
                MATERIAL_MAPPINGS.put("WHITE_DYE", new MaterialData(Material.valueOf("INK_SACK"), (short) 15));
            } else {
                MATERIAL_MAPPINGS.put("BLACK_DYE", new MaterialData(Material.valueOf("DYE"), (short) 0));
                MATERIAL_MAPPINGS.put("RED_DYE", new MaterialData(Material.valueOf("DYE"), (short) 1));
                MATERIAL_MAPPINGS.put("GREEN_DYE", new MaterialData(Material.valueOf("DYE"), (short) 2));
                MATERIAL_MAPPINGS.put("BROWN_DYE", new MaterialData(Material.valueOf("DYE"), (short) 3));
                MATERIAL_MAPPINGS.put("BLUE_DYE", new MaterialData(Material.valueOf("DYE"), (short) 4));
                MATERIAL_MAPPINGS.put("PURPLE_DYE", new MaterialData(Material.valueOf("DYE"), (short) 5));
                MATERIAL_MAPPINGS.put("CYAN_DYE", new MaterialData(Material.valueOf("DYE"), (short) 6));
                MATERIAL_MAPPINGS.put("LIGHT_GRAY_DYE", new MaterialData(Material.valueOf("DYE"), (short) 7));
                MATERIAL_MAPPINGS.put("GRAY_DYE", new MaterialData(Material.valueOf("DYE"), (short) 8));
                MATERIAL_MAPPINGS.put("PINK_DYE", new MaterialData(Material.valueOf("DYE"), (short) 9));
                MATERIAL_MAPPINGS.put("LIME_DYE", new MaterialData(Material.valueOf("DYE"), (short) 10));
                MATERIAL_MAPPINGS.put("YELLOW_DYE", new MaterialData(Material.valueOf("DYE"), (short) 11));
                MATERIAL_MAPPINGS.put("LIGHT_BLUE_DYE", new MaterialData(Material.valueOf("DYE"), (short) 12));
                MATERIAL_MAPPINGS.put("MAGENTA_DYE", new MaterialData(Material.valueOf("DYE"), (short) 13));
                MATERIAL_MAPPINGS.put("ORANGE_DYE", new MaterialData(Material.valueOf("DYE"), (short) 14));
                MATERIAL_MAPPINGS.put("WHITE_DYE", new MaterialData(Material.valueOf("DYE"), (short) 15));
            }
        }

        // Other common material changes // Maps legacy skulls and experience bottle
        if (isLegacy()) {
            MATERIAL_MAPPINGS.put("PLAYER_HEAD", new MaterialData(Material.valueOf("SKULL_ITEM"), (short) 3));
            MATERIAL_MAPPINGS.put("SKELETON_SKULL", new MaterialData(Material.valueOf("SKULL_ITEM"), (short) 0));
            MATERIAL_MAPPINGS.put("WITHER_SKELETON_SKULL", new MaterialData(Material.valueOf("SKULL_ITEM"), (short) 1));
            MATERIAL_MAPPINGS.put("ZOMBIE_HEAD", new MaterialData(Material.valueOf("SKULL_ITEM"), (short) 2));
            MATERIAL_MAPPINGS.put("CREEPER_HEAD", new MaterialData(Material.valueOf("SKULL_ITEM"), (short) 4));
            MATERIAL_MAPPINGS.put("DRAGON_HEAD", new MaterialData(Material.valueOf("SKULL_ITEM"), (short) 5));
            MATERIAL_MAPPINGS.put("EXPERIENCE_BOTTLE", new MaterialData(Material.valueOf("EXP_BOTTLE")));
        }
    }

    /**
     * Creates an ItemStack with proper material and damage value for the current version
     */
    public static ItemStack createVersionSafeItemStack(String materialName, int amount) {
        MaterialData matData = getMaterialData(materialName); // Gets material data
        ItemStack item = new ItemStack(matData.getMaterial(), amount); // Creates item
        if (isLegacy() && matData.getData() != 0) {
            item.setDurability(matData.getData()); // Sets legacy data value
        }
        return item; // Returns item
    }

    /**
     * Creates an ItemStack with amount of 1
     */
    public static ItemStack createVersionSafeItemStack(String materialName) {
        return createVersionSafeItemStack(materialName, 1); // Calls main method with amount 1
    }

    /**
     * Gets material data (material + damage value) for any material name
     */
    public static MaterialData getMaterialData(String materialName) {
        String upperName = materialName.toUpperCase(); // Normalizes material name
        if (MATERIAL_MAPPINGS.containsKey(upperName)) {
            return MATERIAL_MAPPINGS.get(upperName); // Returns mapped material
        }
        try {
            Material material = Material.valueOf(upperName); // Attempts direct lookup
            return new MaterialData(material); // Returns material data
        } catch (IllegalArgumentException e) {
            return new MaterialData(Material.STONE); // Falls back to STONE
        }
    }
}