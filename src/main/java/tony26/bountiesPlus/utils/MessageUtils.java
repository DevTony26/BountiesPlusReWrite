package tony26.bountiesPlus.utils;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import tony26.bountiesPlus.BountiesPlus;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for sending formatted messages to players
 * // note: Manages message retrieval, formatting, and sending with color codes and PlaceholderAPI support
 */
public class MessageUtils {
    private static FileConfiguration messagesConfig;
    private static BountiesPlus plugin;
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");

    /**
     * Initializes the message utility with the plugin instance
     * // note: Sets up the plugin reference and reloads messages.yml
     */
    public static void initialize(BountiesPlus pluginInstance) {
        plugin = pluginInstance;
        reloadMessages();
    }

    /**
     * Reloads and caches messages from messages.yml
     * // note: Updates the cached messagesConfig for performance
     */
    public static void reloadMessages() {
        messagesConfig = plugin.getMessagesConfig();
    }

    /**
     * Sends a formatted message to a player, handling single strings or lists with PlaceholderAPI
     * // note: Retrieves message from messages.yml, formats with color codes and PlaceholderAPI placeholders, and sends as BaseComponent[]
     *
     * @param player     The player to send the message to
     * @param messageKey The key in messages.yml to retrieve the message from
     */
    public static void sendFormattedMessage(Player player, String messageKey) {
        if (player == null || messageKey == null) {
            if (plugin.getLogger() != null) {
                plugin.getLogger().warning("Invalid sendFormattedMessage call: player or messageKey is null");
            }
            return;
        }

        Object messageObj = messagesConfig.get(messageKey);

        // Handle missing or invalid message path
        if (messageObj == null) {
            player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
            return;
        }

        List<String> messages;
        // Check if it's a single string or a list
        if (messageObj instanceof String) {
            String message = messagesConfig.getString(messageKey);
            if (message == null || message.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
                return;
            }
            messages = Collections.singletonList(message);
        } else if (messageObj instanceof List) {
            messages = messagesConfig.getStringList(messageKey);
            if (messages.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
                return;
            }
        } else {
            player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
            return;
        }

        // Format and send each message line
        for (String message : messages) {
            String formatted = formatMessage(message, player);
            BaseComponent[] components = TextComponent.fromLegacyText(formatted);
            player.spigot().sendMessage(components);
        }
    }

    /**
     * Sends a formatted message to a player with context, handling single strings or lists with PlaceholderAPI
     * // note: Retrieves message from messages.yml, formats with color codes and PlaceholderAPI placeholders using context, and sends as BaseComponent[]
     *
     * @param player     The player to send the message to
     * @param messageKey The key in messages.yml to retrieve the message from
     * @param context    The PlaceholderContext for placeholder resolution
     */
    public static void sendFormattedMessage(Player player, String messageKey, PlaceholderContext context) {
        if (player == null || messageKey == null) {
            if (plugin.getLogger() != null) {
                plugin.getLogger().warning("Invalid sendFormattedMessage call: player or messageKey is null");
            }
            return;
        }

        Object messageObj = messagesConfig.get(messageKey);

        // Handle missing or invalid message path
        if (messageObj == null) {
            player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
            return;
        }

        List<String> messages;
        // Check if it's a single string or a list
        if (messageObj instanceof String) {
            String message = messagesConfig.getString(messageKey);
            if (message == null || message.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
                return;
            }
            messages = Collections.singletonList(message);
        } else if (messageObj instanceof List) {
            messages = messagesConfig.getStringList(messageKey);
            if (messages.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
                return;
            }
        } else {
            player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
            return;
        }

        // Format and send each message line
        for (String message : messages) {
            String formatted = formatMessage(message, player, context);
            BaseComponent[] components = TextComponent.fromLegacyText(formatted);
            player.spigot().sendMessage(components);
        }
    }

    /**
     * Checks if the console likely supports ANSI colors
     * // note: Determines if ANSI escape codes should be used based on environment
     */
    public static boolean isAnsiSupported() {
        String os = System.getProperty("os.name").toLowerCase();
        // Assume ANSI support on Linux/macOS or if running in a modern Windows terminal
        return !os.contains("win") || System.getenv("TERM") != null || System.getenv("WT_SESSION") != null;
    }

    /**
     * Formats a single message string with color codes and PlaceholderAPI placeholders
     * // note: Applies color codes and PlaceholderAPI placeholders for the given player
     *
     * @param message The raw message string
     * @param player  The player for PlaceholderAPI context
     * @return The fully formatted string
     */
    private static String formatMessage(String message, Player player) {
        if (message == null) return "";

        // Translate color codes
        message = ChatColor.translateAlternateColorCodes('&', message);

        // Apply PlaceholderAPI if installed
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && player != null) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }

        return message;
    }

    /**
     * Formats a single message string with color codes and PlaceholderAPI placeholders using context
     * // note: Applies color codes and PlaceholderAPI placeholders for the given player and context
     *
     * @param message The raw message string
     * @param player  The player for PlaceholderAPI context
     * @param context The PlaceholderContext for placeholder resolution
     * @return The fully formatted string
     */
    private static String formatMessage(String message, Player player, PlaceholderContext context) {
        if (message == null) return "";

        // Translate color codes
        message = ChatColor.translateAlternateColorCodes('&', message);

        // Apply PlaceholderAPI with context if installed
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && player != null) {
            if (context != null) {
                Placeholders.apply(message, context); // Sets context in Placeholders.contextMap
                message = PlaceholderAPI.setPlaceholders(player, message);
            } else {
                message = PlaceholderAPI.setPlaceholders(player, message);
            }
        }

        return message;
    }
}