package tony26.bountiesPlus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages MySQL connections and operations for bounty storage
 * // note: Handles database initialization, queries, and data migration for bounties
 */
public class MySQL {
    private final BountiesPlus plugin;
    private Connection connection;
    private final Gson gson;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useMySQL;

    public MySQL(BountiesPlus plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.useMySQL = config.getBoolean("mysql.use-mysql", false);
        this.host = config.getString("mysql.host", "localhost");
        this.port = config.getInt("mysql.port", 3306);
        this.database = config.getString("mysql.database", "bountiesplus");
        this.username = config.getString("mysql.username", "root");
        this.password = config.getString("mysql.password", "");
        this.gson = new GsonBuilder().create();
        if (useMySQL) {
            initializeDatabase();
        }
    }

    /**
     * Initializes the MySQL connection and creates tables
     * // note: Establishes connection and sets up the bounties table
     */
    private void initializeDatabase() {
        try {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
            connection = DriverManager.getConnection(url, username, password);
            createTables();
            plugin.getLogger().info("MySQL connection established successfully.");
        } catch (SQLException e) {
            plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to connect to database: " + e.getMessage());
            connection = null;
        }
    }

    /**
     * Creates the necessary database tables
     * // note: Sets up the bounties table structure
     */
    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS bounties (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "target_uuid VARCHAR(36) NOT NULL," +
                    "setter_uuid VARCHAR(36) NOT NULL," +
                    "money DOUBLE NOT NULL," +
                    "xp INT NOT NULL," +
                    "duration_minutes INT NOT NULL," +
                    "is_anonymous BOOLEAN NOT NULL," +
                    "set_time BIGINT NOT NULL," +
                    "expire_time BIGINT NOT NULL," +
                    "multiplier DOUBLE NOT NULL," +
                    "items TEXT)";
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to create tables: " + e.getMessage());
        }
    }

    /**
     * Checks if MySQL is enabled and connected
     * // note: Verifies database availability
     */
    public boolean isConnected() {
        if (!useMySQL) return false;
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            plugin.getLogger().warning("[DEBUG] MySQL Error: Connection check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes the MySQL connection
     * // note: Cleans up database resources
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("MySQL connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to close connection: " + e.getMessage());
            }
        }
    }

    /**
     * Loads bounties from MySQL
     * // note: Retrieves all bounties from the database
     */
    public CompletableFuture<Map<UUID, Bounty>> loadBountiesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Bounty> bounties = new HashMap<>();
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot load bounties, no connection.");
                return bounties;
            }
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM bounties")) {
                while (rs.next()) {
                    UUID targetUUID = UUID.fromString(rs.getString("target_uuid"));
                    UUID setterUUID = UUID.fromString(rs.getString("setter_uuid"));
                    double money = rs.getDouble("money");
                    int xp = rs.getInt("xp");
                    int durationMinutes = rs.getInt("duration_minutes");
                    boolean isAnonymous = rs.getBoolean("is_anonymous");
                    long setTime = rs.getLong("set_time");
                    long expireTime = rs.getLong("expire_time");
                    double multiplier = rs.getDouble("multiplier");
                    String itemsJson = rs.getString("items");
                    List<ItemStack> items = new ArrayList<>();
                    if (itemsJson != null && !itemsJson.isEmpty()) {
                        String[] itemStrings = gson.fromJson(itemsJson, String[].class);
                        for (String itemStr : itemStrings) {
                            String[] parts = itemStr.split(":");
                            if (parts.length == 2) {
                                try {
                                    Material material = Material.valueOf(parts[0]);
                                    int amount = Integer.parseInt(parts[1]);
                                    items.add(new ItemStack(material, amount));
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("Invalid item in MySQL: " + itemStr);
                                }
                            }
                        }
                    }
                    Bounty bounty = bounties.computeIfAbsent(targetUUID, k -> new Bounty(plugin, targetUUID));
                    bounty.addContribution(setterUUID, money, xp, durationMinutes, items, isAnonymous, !bounties.containsKey(targetUUID));
                    Bounty.Sponsor sponsor = bounty.getSponsors().stream()
                            .filter(s -> s.getPlayerUUID().equals(setterUUID))
                            .findFirst()
                            .orElse(null);
                    if (sponsor != null) {
                        sponsor.setSetTime(setTime);
                        sponsor.setExpireTime(expireTime);
                        sponsor.setMultiplier(multiplier);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to load bounties: " + e.getMessage());
            }
            return bounties;
        });
    }

    /**
     * Migrates bounties from YAML to MySQL
     * // note: Transfers BountyStorage.yml data to MySQL
     */
    public CompletableFuture<Void> migrateBountiesAsync() {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot migrate bounties, no connection.");
                return;
            }
            FileConfiguration config = plugin.getBountiesConfig();
            if (!config.isConfigurationSection("bounties")) {
                plugin.getLogger().info("No bounties to migrate from BountyStorage.yml.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO bounties (target_uuid, setter_uuid, money, xp, duration_minutes, is_anonymous, set_time, expire_time, multiplier, items) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (String targetUUIDStr : config.getConfigurationSection("bounties").getKeys(false)) {
                    UUID targetUUID;
                    try {
                        targetUUID = UUID.fromString(targetUUIDStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in BountyStorage.yml: " + targetUUIDStr);
                        continue;
                    }
                    for (String setterUUIDStr : config.getConfigurationSection("bounties." + targetUUIDStr).getKeys(false)) {
                        UUID setterUUID;
                        try {
                            setterUUID = UUID.fromString(setterUUIDStr);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid setter UUID in BountyStorage.yml: " + setterUUIDStr);
                            continue;
                        }
                        double amount = config.getDouble("bounties." + targetUUIDStr + "." + setterUUIDStr + ".amount", 0.0);
                        int xp = config.getInt("bounties." + targetUUIDStr + "." + setterUUIDStr + ".xp", 0);
                        int durationMinutes = config.getInt("bounties." + targetUUIDStr + "." + setterUUIDStr + ".duration", 0);
                        boolean isAnonymous = config.getBoolean("anonymous-bounties." + targetUUIDStr + "." + setterUUIDStr, false);
                        long setTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".set_time", System.currentTimeMillis());
                        long expireTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".expire_time", -1);
                        double multiplier = config.getDouble("bounties." + targetUUIDStr + "." + setterUUIDStr + ".multiplier", 1.0);
                        List<String> itemStrings = config.getStringList("bounties." + targetUUIDStr + "." + setterUUIDStr + ".items");
                        String itemsJson = itemStrings.isEmpty() ? null : gson.toJson(itemStrings);
                        pstmt.setString(1, targetUUID.toString());
                        pstmt.setString(2, setterUUID.toString());
                        pstmt.setDouble(3, amount);
                        pstmt.setInt(4, xp);
                        pstmt.setInt(5, durationMinutes);
                        pstmt.setBoolean(6, isAnonymous);
                        pstmt.setLong(7, setTime);
                        pstmt.setLong(8, expireTime);
                        pstmt.setDouble(9, multiplier);
                        pstmt.setString(10, itemsJson);
                        pstmt.addBatch();
                    }
                }
                pstmt.executeBatch();
                plugin.getLogger().info("Migrated bounties from BountyStorage.yml to MySQL.");
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to migrate bounties: " + e.getMessage());
            }
        });
    }

    /**
     * Sets a bounty in MySQL
     * // note: Inserts or updates a bounty record
     */
    public CompletableFuture<Void> setBountyAsync(UUID setter, UUID target, double amount, long expireTime) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot set bounty, no connection.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO bounties (target_uuid, setter_uuid, money, xp, duration_minutes, is_anonymous, set_time, expire_time, multiplier, items) " +
                            "VALUES (?, ?, ?, 0, 0, ?, ?, ?, 1.0, NULL) ON DUPLICATE KEY UPDATE " +
                            "money = ?, set_time = ?, expire_time = ?, multiplier = 1.0")) {
                long setTime = System.currentTimeMillis();
                pstmt.setString(1, target.toString());
                pstmt.setString(2, setter.toString());
                pstmt.setDouble(3, amount);
                pstmt.setBoolean(4, false);
                pstmt.setLong(5, setTime);
                pstmt.setLong(6, expireTime);
                pstmt.setDouble(7, amount);
                pstmt.setLong(8, setTime);
                pstmt.setLong(9, expireTime);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to set bounty: " + e.getMessage());
            }
        });
    }

    /**
     * Adds an anonymous bounty in MySQL
     * // note: Inserts or updates an anonymous bounty record
     */
    public CompletableFuture<Void> addAnonymousBountyAsync(UUID target, UUID setter, double amount, int xp, int durationMinutes, List<ItemStack> items) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot add anonymous bounty, no connection.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO bounties (target_uuid, setter_uuid, money, xp, duration_minutes, is_anonymous, set_time, expire_time, multiplier, items) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1.0, ?) ON DUPLICATE KEY UPDATE " +
                            "money = ?, xp = ?, duration_minutes = ?, is_anonymous = ?, set_time = ?, expire_time = ?, items = ?")) {
                long setTime = System.currentTimeMillis();
                long expireTime = durationMinutes > 0 ?
                        setTime + (durationMinutes * 60 * 1000L) :
                        setTime + (plugin.getConfig().getInt("default-bounty-duration", 1440) * 60 * 1000L);
                List<String> itemStrings = items.stream()
                        .filter(item -> item != null && !item.getType().equals(Material.AIR))
                        .map(item -> item.getType().name() + ":" + item.getAmount())
                        .collect(Collectors.toList());
                String itemsJson = itemStrings.isEmpty() ? null : gson.toJson(itemStrings);
                pstmt.setString(1, target.toString());
                pstmt.setString(2, setter.toString());
                pstmt.setDouble(3, amount);
                pstmt.setInt(4, xp);
                pstmt.setInt(5, durationMinutes);
                pstmt.setBoolean(6, true);
                pstmt.setLong(7, setTime);
                pstmt.setLong(8, expireTime);
                pstmt.setString(9, itemsJson);
                pstmt.setDouble(10, amount);
                pstmt.setInt(11, xp);
                pstmt.setInt(12, durationMinutes);
                pstmt.setBoolean(13, true);
                pstmt.setLong(14, setTime);
                pstmt.setLong(15, expireTime);
                pstmt.setString(16, itemsJson);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to add anonymous bounty: " + e.getMessage());
            }
        });
    }

    /**
     * Removes a specific bounty from MySQL
     * // note: Deletes a bounty record for a setter and target
     */
    public CompletableFuture<Void> removeBountyAsync(UUID setter, UUID target) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot remove bounty, no connection.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM bounties WHERE target_uuid = ? AND setter_uuid = ?")) {
                pstmt.setString(1, target.toString());
                pstmt.setString(2, setter.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to remove bounty: " + e.getMessage());
            }
        });
    }

    /**
     * Clears all bounties for a target in MySQL
     * // note: Deletes all bounty records for a target
     */
    public CompletableFuture<Void> clearBountiesAsync(UUID target) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot clear bounties, no connection.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM bounties WHERE target_uuid = ?")) {
                pstmt.setString(1, target.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to clear bounties: " + e.getMessage());
            }
        });
    }

    /**
     * Applies a manual boost to a bounty in MySQL
     * // note: Updates multiplier and expire time for a bounty
     */
    public CompletableFuture<Void> applyManualBoostAsync(UUID targetUUID, double multiplier, long expireTime) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot apply manual boost, no connection.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE bounties SET multiplier = ?, expire_time = ? WHERE target_uuid = ?")) {
                pstmt.setDouble(1, multiplier);
                pstmt.setLong(2, expireTime);
                pstmt.setString(3, targetUUID.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to apply manual boost: " + e.getMessage());
            }
        });
    }

    /**
     * Removes a manual boost from a bounty in MySQL
     * // note: Resets multiplier and expire time for a bounty
     */
    public CompletableFuture<Void> removeManualBoostAsync(UUID targetUUID) {
        return CompletableFuture.runAsync(() -> {
            if (!isConnected()) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Cannot remove manual boost, no connection.");
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "UPDATE bounties SET multiplier = 1.0, expire_time = -1 WHERE target_uuid = ?")) {
                pstmt.setString(1, targetUUID.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DEBUG] MySQL Error: Failed to remove manual boost: " + e.getMessage());
            }
        });
    }

    /**
     * Reconnects to MySQL
     * // note: Closes existing connection and re-establishes it
     */
    public void reconnect() {
        closeConnection();
        initializeDatabase();
    }
}