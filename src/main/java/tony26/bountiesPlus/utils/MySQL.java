package tony26.bountiesPlus.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import tony26.bountiesPlus.BountiesPlus;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages MySQL connections and operations for bounty and stats storage
 * // note: Handles database initialization, queries, and data migration
 */
public class MySQL {
    private final BountiesPlus plugin;
    private Connection connection;

    public MySQL(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if MySQL is enabled
     * // note: Verifies if MySQL is configured and connected
     */
    public boolean isEnabled() {
        return connection != null && plugin.getConfig().getBoolean("mysql.enabled", false);
    }

    /**
     * Gets the MySQL connection
     * // note: Returns the active database connection
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Initializes the MySQL database connection
     * // note: Sets up connection pool for async database operations
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("mysql.enabled")) {
            plugin.getLogger().info("MySQL is disabled in config.yml, using YAML storage.");
            connection = null;
            return;
        }

        String host = config.getString("mysql.host", "localhost");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "bountiesplus");
        String username = config.getString("mysql.username", "root");
        String password = config.getString("mysql.password", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";

        try {
            connection = DriverManager.getConnection(url, username, password);
            plugin.getLogger().info("Successfully connected to MySQL database!");
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to MySQL database: " + e.getMessage());
            connection = null;
        }
    }

    /**
     * Creates necessary database tables
     * // note: Initializes bounties and player_stats tables if they don't exist
     */
    private void createTables() {
        if (connection == null) return;

        String createBountiesTable = "CREATE TABLE IF NOT EXISTS bounties (" +
                "target_uuid VARCHAR(36) NOT NULL, " +
                "setter_uuid VARCHAR(36) NOT NULL, " +
                "amount INT NOT NULL, " +
                "set_time BIGINT NOT NULL, " +
                "expire_time BIGINT, " +
                "PRIMARY KEY (target_uuid, setter_uuid))";

        String createStatsTable = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "player_uuid VARCHAR(36) PRIMARY KEY, " +
                "claimed INT DEFAULT 0, " +
                "survived INT DEFAULT 0, " +
                "money_earned DOUBLE DEFAULT 0.0, " +
                "xp_earned INT DEFAULT 0, " +
                "total_value_earned DOUBLE DEFAULT 0.0)";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createBountiesTable);
            stmt.executeUpdate(createStatsTable);
            plugin.getLogger().info("Database tables created or verified successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
        }
    }

    /**
     * Migrates stats data from StatStorage.yml to MySQL
     * // note: Transfers player stats to player_stats table and sets stats-data-migrated flag
     */
    public void migrateStatsData() {
        if (connection == null || plugin.getConfig().getBoolean("stats-data-migrated", false)) {
            return;
        }

        FileConfiguration statsConfig = plugin.getStatsConfig();
        ConfigurationSection playersSection = statsConfig.getConfigurationSection("players");
        if (playersSection == null) {
            plugin.getConfig().set("stats-data-migrated", true);
            plugin.saveConfig();
            return;
        }

        String insertQuery = "INSERT INTO player_stats (player_uuid, claimed, survived, money_earned, xp_earned, total_value_earned) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "claimed = VALUES(claimed), survived = VALUES(survived), money_earned = VALUES(money_earned), " +
                "xp_earned = VALUES(xp_earned), total_value_earned = VALUES(total_value_earned)";

        try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    int claimed = statsConfig.getInt("players." + uuidString + ".claimed", 0);
                    int survived = statsConfig.getInt("players." + uuidString + ".survived", 0);
                    double moneyEarned = statsConfig.getDouble("players." + uuidString + ".money_earned", 0.0);
                    int xpEarned = statsConfig.getInt("players." + uuidString + ".xp_earned", 0);
                    double totalValueEarned = statsConfig.getDouble("players." + uuidString + ".total_value_earned", 0.0);

                    pstmt.setString(1, uuid.toString());
                    pstmt.setInt(2, claimed);
                    pstmt.setInt(3, survived);
                    pstmt.setDouble(4, moneyEarned);
                    pstmt.setInt(5, xpEarned);
                    pstmt.setDouble(6, totalValueEarned);
                    pstmt.addBatch();
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in StatStorage.yml: " + uuidString);
                }
            }
            pstmt.executeBatch();
            plugin.getConfig().set("stats-data-migrated", true);
            plugin.saveConfig();
            plugin.getLogger().info("Successfully migrated stats data from StatStorage.yml to MySQL.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to migrate stats data: " + e.getMessage());
        }
    }

    /**
     * Migrates bounties from YAML to MySQL
     * // note: Transfers BountyStorage.yml data to MySQL
     */
    public CompletableFuture<Void> migrateData() {
        return CompletableFuture.runAsync(() -> {
            if (!isEnabled() || plugin.getConfig().getBoolean("data-migrated", false)) {
                return;
            }
            FileConfiguration config = plugin.getBountiesConfig();
            ConfigurationSection bountiesSection = config.getConfigurationSection("bounties");
            if (bountiesSection == null) {
                plugin.getConfig().set("data-migrated", true);
                plugin.saveConfig();
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO bounties (target_uuid, setter_uuid, amount, set_time, expire_time) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                for (String targetUUIDStr : bountiesSection.getKeys(false)) {
                    UUID targetUUID;
                    try {
                        targetUUID = UUID.fromString(targetUUIDStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in BountyStorage.yml: " + targetUUIDStr);
                        continue;
                    }
                    ConfigurationSection targetSection = bountiesSection.getConfigurationSection(targetUUIDStr);
                    for (String setterUUIDStr : targetSection.getKeys(false)) {
                        UUID setterUUID;
                        try {
                            setterUUID = UUID.fromString(setterUUIDStr);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid setter UUID in BountyStorage.yml: " + setterUUIDStr);
                            continue;
                        }
                        int amount = config.getInt("bounties." + targetUUIDStr + "." + setterUUIDStr + ".amount", 0);
                        long setTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".set_time", System.currentTimeMillis());
                        long expireTime = config.getLong("bounties." + targetUUIDStr + "." + setterUUIDStr + ".expire_time", -1);
                        pstmt.setString(1, targetUUID.toString());
                        pstmt.setString(2, setterUUID.toString());
                        pstmt.setInt(3, amount);
                        pstmt.setLong(4, setTime);
                        pstmt.setLong(5, expireTime);
                        pstmt.addBatch();
                    }
                }
                pstmt.executeBatch();
                plugin.getConfig().set("data-migrated", true);
                plugin.saveConfig();
                plugin.getLogger().info("Migrated bounties from BountyStorage.yml to MySQL.");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to migrate bounties: " + e.getMessage());
            }
        });
    }

    /**
     * Gets a player's claimed bounties count
     * // note: Retrieves claimed stat from player_stats table asynchronously
     */
    public CompletableFuture<Integer> getClaimed(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.supplyAsync(() -> plugin.getStatsConfig().getInt("players." + playerUUID + ".claimed", 0));
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT claimed FROM player_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("claimed");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch claimed for " + playerUUID + ": " + e.getMessage());
            }
            return 0;
        }, plugin.getExecutorService());
    }

    /**
     * Sets a player's claimed bounties count
     * // note: Updates claimed stat in player_stats table asynchronously
     */
    public CompletableFuture<Void> setClaimed(UUID playerUUID, int claimed) {
        if (!isEnabled()) {
            return CompletableFuture.runAsync(() -> {
                FileConfiguration statsConfig = plugin.getStatsConfig();
                statsConfig.set("players." + playerUUID + ".claimed", claimed);
                plugin.saveStatsConfig();
            });
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO player_stats (player_uuid, claimed) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE claimed = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, claimed);
                pstmt.setInt(3, claimed);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set claimed for " + playerUUID + ": " + e.getMessage());
            }
        }, plugin.getExecutorService());
    }

    /**
     * Gets a player's survived bounties count
     * // note: Retrieves survived stat from player_stats table asynchronously
     */
    public CompletableFuture<Integer> getSurvived(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.supplyAsync(() -> plugin.getStatsConfig().getInt("players." + playerUUID + ".survived", 0));
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT survived FROM player_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("survived");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch survived for " + playerUUID + ": " + e.getMessage());
            }
            return 0;
        }, plugin.getExecutorService());
    }

    /**
     * Sets a player's survived bounties count
     * // note: Updates survived stat in player_stats table asynchronously
     */
    public CompletableFuture<Void> setSurvived(UUID playerUUID, int survived) {
        if (!isEnabled()) {
            return CompletableFuture.runAsync(() -> {
                FileConfiguration statsConfig = plugin.getStatsConfig();
                statsConfig.set("players." + playerUUID + ".survived", survived);
                plugin.saveStatsConfig();
            });
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO player_stats (player_uuid, survived) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE survived = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, survived);
                pstmt.setInt(3, survived);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set survived for " + playerUUID + ": " + e.getMessage());
            }
        }, plugin.getExecutorService());
    }

    /**
     * Gets a player's earned money from bounties
     * // note: Retrieves money_earned stat from player_stats table asynchronously
     */
    public CompletableFuture<Double> getMoneyEarned(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.supplyAsync(() -> plugin.getStatsConfig().getDouble("players." + playerUUID + ".money_earned", 0.0));
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT money_earned FROM player_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getDouble("money_earned");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch money_earned for " + playerUUID + ": " + e.getMessage());
            }
            return 0.0;
        }, plugin.getExecutorService());
    }

    /**
     * Sets a player's earned money from bounties
     * // note: Updates money_earned stat in player_stats table asynchronously
     */
    public CompletableFuture<Void> setMoneyEarned(UUID playerUUID, double moneyEarned) {
        if (!isEnabled()) {
            return CompletableFuture.runAsync(() -> {
                FileConfiguration statsConfig = plugin.getStatsConfig();
                statsConfig.set("players." + playerUUID + ".money_earned", moneyEarned);
                plugin.saveStatsConfig();
            });
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO player_stats (player_uuid, money_earned) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE money_earned = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setDouble(2, moneyEarned);
                pstmt.setDouble(3, moneyEarned);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set money_earned for " + playerUUID + ": " + e.getMessage());
            }
        }, plugin.getExecutorService());
    }

    /**
     * Gets a player's earned XP from bounties
     * // note: Retrieves xp_earned stat from player_stats table asynchronously
     */
    public CompletableFuture<Integer> getXPEarned(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.supplyAsync(() -> plugin.getStatsConfig().getInt("players." + playerUUID + ".xp_earned", 0));
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT xp_earned FROM player_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("xp_earned");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch xp_earned for " + playerUUID + ": " + e.getMessage());
            }
            return 0;
        }, plugin.getExecutorService());
    }

    /**
     * Sets a player's earned XP from bounties
     * // note: Updates xp_earned stat in player_stats table asynchronously
     */
    public CompletableFuture<Void> setXPEarned(UUID playerUUID, int xpEarned) {
        if (!isEnabled()) {
            return CompletableFuture.runAsync(() -> {
                FileConfiguration statsConfig = plugin.getStatsConfig();
                statsConfig.set("players." + playerUUID + ".xp_earned", xpEarned);
                plugin.saveStatsConfig();
            });
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO player_stats (player_uuid, xp_earned) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE xp_earned = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setInt(2, xpEarned);
                pstmt.setInt(3, xpEarned);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set xp_earned for " + playerUUID + ": " + e.getMessage());
            }
        }, plugin.getExecutorService());
    }

    /**
     * Gets a player's total value earned from bounties
     * // note: Retrieves total_value_earned stat from player_stats table asynchronously
     */
    public CompletableFuture<Double> getTotalValueEarned(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.supplyAsync(() -> plugin.getStatsConfig().getDouble("players." + playerUUID + ".total_value_earned", 0.0));
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT total_value_earned FROM player_stats WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getDouble("total_value_earned");
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch total_value_earned for " + playerUUID + ": " + e.getMessage());
            }
            return 0.0;
        }, plugin.getExecutorService());
    }

    /**
     * Sets a player's total value earned from bounties
     * // note: Updates total_value_earned stat in player_stats table asynchronously
     */
    public CompletableFuture<Void> setTotalValueEarned(UUID playerUUID, double totalValueEarned) {
        if (!isEnabled()) {
            return CompletableFuture.runAsync(() -> {
                FileConfiguration statsConfig = plugin.getStatsConfig();
                statsConfig.set("players." + playerUUID + ".total_value_earned", totalValueEarned);
                plugin.saveStatsConfig();
            });
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO player_stats (player_uuid, total_value_earned) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE total_value_earned = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setDouble(2, totalValueEarned);
                pstmt.setDouble(3, totalValueEarned);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set total_value_earned for " + playerUUID + ": " + e.getMessage());
            }
        }, plugin.getExecutorService());
    }

    /**
     * Loads bounties from MySQL
     * // note: Retrieves all bounties from the database
     */
    public CompletableFuture<Map<UUID, Map<UUID, Integer>>> loadBountiesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Map<UUID, Integer>> bounties = new HashMap<>();
            if (!isEnabled()) {
                return bounties;
            }
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM bounties")) {
                while (rs.next()) {
                    UUID targetUUID = UUID.fromString(rs.getString("target_uuid"));
                    UUID setterUUID = UUID.fromString(rs.getString("setter_uuid"));
                    int amount = rs.getInt("amount");
                    bounties.computeIfAbsent(targetUUID, k -> new HashMap<>()).put(setterUUID, amount);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load bounties: " + e.getMessage());
            }
            return bounties;
        });
    }

    /**
     * Sets a bounty in MySQL
     * // note: Inserts or updates a bounty record
     */
    public CompletableFuture<Void> setBountyAsync(UUID setter, UUID target, double amount, long expireTime) {
        return CompletableFuture.runAsync(() -> {
            if (!isEnabled()) {
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "INSERT INTO bounties (target_uuid, setter_uuid, amount, set_time, expire_time) " +
                            "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                            "amount = ?, set_time = ?, expire_time = ?")) {
                long setTime = System.currentTimeMillis();
                pstmt.setString(1, target.toString());
                pstmt.setString(2, setter.toString());
                pstmt.setInt(3, (int) amount);
                pstmt.setLong(4, setTime);
                pstmt.setLong(5, expireTime);
                pstmt.setInt(6, (int) amount);
                pstmt.setLong(7, setTime);
                pstmt.setLong(8, expireTime);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set bounty: " + e.getMessage());
            }
        });
    }

    /**
     * Removes a specific bounty from MySQL
     * // note: Deletes a bounty record for a setter and target
     */
    public CompletableFuture<Void> removeBountyAsync(UUID setter, UUID target) {
        return CompletableFuture.runAsync(() -> {
            if (!isEnabled()) {
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM bounties WHERE target_uuid = ? AND setter_uuid = ?")) {
                pstmt.setString(1, target.toString());
                pstmt.setString(2, setter.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to remove bounty: " + e.getMessage());
            }
        });
    }

    /**
     * Clears all bounties for a target in MySQL
     * // note: Deletes all bounty records for a target
     */
    public CompletableFuture<Void> clearBountiesAsync(UUID target) {
        return CompletableFuture.runAsync(() -> {
            if (!isEnabled()) {
                return;
            }
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "DELETE FROM bounties WHERE target_uuid = ?")) {
                pstmt.setString(1, target.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to clear bounties: " + e.getMessage());
            }
        });
    }

    /**
     * Closes the MySQL connection
     * // note: Safely closes the database connection on plugin disable
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("MySQL connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to close MySQL connection: " + e.getMessage());
            }
            connection = null;
        }
    }
}