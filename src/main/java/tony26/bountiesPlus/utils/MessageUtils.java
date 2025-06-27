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
     * Sends a formatted message to a player using a message key
     * // note: Retrieves message from messages.yml, applies placeholders, and sends as BaseComponent
     */
    public static void sendFormattedMessage(Player player, String messageKey) {
        sendFormattedMessage(player, messageKey, null);
    }

    /**
     * Sends a formatted message with placeholders to a player
     * // note: Retrieves message from messages.yml, applies PlaceholderAPI and context, and sends as BaseComponent
     */
    public static void sendFormattedMessage(Player player, String messageKey, PlaceholderContext context) {
        if (messagesConfig == null) {
            BountiesPlus.getInstance().getLogger().warning("[DEBUG - MessageUtils] messagesConfig is null, cannot send message: " + messageKey);
            player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
            return;
        }

        if (messagesConfig.isList(messageKey)) {
            List<String> messages = messagesConfig.getStringList(messageKey);
            if (messages.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Message not found: " + messageKey);
                return;
            }
            for (String message : messages) {
                String formatted = ChatColor.translateAlternateColorCodes('&', message);
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    formatted = PlaceholderAPI.setPlaceholders(player, formatted);
                }
                BaseComponent[] components = TextComponent.fromLegacyText(formatted);
                player.spigot().sendMessage(components);
            }
        } else {
            String message = messagesConfig.getString(messageKey, "Message not found: " + messageKey);
            String formatted = ChatColor.translateAlternateColorCodes('&', Placeholders.apply(message, context));
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                formatted = PlaceholderAPI.setPlaceholders(player, formatted);
            }
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