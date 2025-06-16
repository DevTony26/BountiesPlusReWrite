package tony26.bountiesPlus.utils;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import tony26.bountiesPlus.BountiesPlus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages debug message logging for BountiesPlus // note: Centralizes immediate and buffered debug logging with periodic output
 */
public class DebugManager {
    private final BountiesPlus plugin;
    private final boolean debugEnabled;
    private final ConcurrentMap<String, AtomicLong> debugLogCounts;
    private BukkitTask debugLoggingTask;

    /**
     * Initializes the DebugManager with plugin instance // note: Sets up debug logging based on config settings
     */
    public DebugManager(BountiesPlus plugin) {
        this.plugin = plugin;
        this.debugEnabled = plugin.getConfig().getBoolean("debug-enabled", false);
        this.debugLogCounts = new ConcurrentHashMap<>();
        this.debugLoggingTask = null;
        if (debugEnabled) {
            startDebugLoggingTask();
            plugin.getLogger().info("DebugManager initialized with debug logging enabled");
        } else {
            plugin.getLogger().info("DebugManager initialized with debug logging disabled");
        }
    }

    /**
     * Logs an immediate debug message if debug is enabled // note: Outputs message directly to console without buffering
     */
    public void logDebug(String message) {
        if (debugEnabled) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    /**
     * Logs a warning message if debug is enabled // note: Outputs warning directly to console without buffering
     */
    public void logWarning(String message) {
        if (debugEnabled) {
            plugin.getLogger().warning("[DEBUG] " + message);
        }
    }

    /**
     * Buffers a debug message for periodic output // note: Increments count for message to be summarized every 30 seconds
     */
    public void bufferDebug(String message) {
        if (debugEnabled) {
            debugLogCounts.computeIfAbsent(message, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /**
     * Starts a task to periodically log buffered debug messages // note: Summarizes buffered debug logs every 30 seconds
     */
    private void startDebugLoggingTask() {
        if (debugLoggingTask != null) {
            debugLoggingTask.cancel();
        }
        debugLoggingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (debugLogCounts.isEmpty()) {
                return;
            }
            StringBuilder summary = new StringBuilder("Debug summary (past 30 seconds):\n");
            debugLogCounts.forEach((message, count) -> {
                summary.append(String.format("- %s: %d times\n", message, count.get()));
            });
            plugin.getLogger().info(summary.toString());
            debugLogCounts.clear();
        }, 600L, 600L); // 30 seconds (600 ticks)
    }

    /**
     * Stops the periodic debug logging task // note: Cancels the task and clears buffered logs
     */
    public void stopDebugLoggingTask() {
        if (debugLoggingTask != null) {
            debugLoggingTask.cancel();
            debugLoggingTask = null;
            debugLogCounts.clear();
        }
    }
}