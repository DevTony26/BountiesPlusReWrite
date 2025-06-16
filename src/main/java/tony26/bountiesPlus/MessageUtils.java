// file: src/main/java/tony26/bountiesPlus/utils/MessageUtils.java
package tony26.bountiesPlus.utils;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import tony26.bountiesPlus.BountiesPlus;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.List;

public class MessageUtils {
    private static FileConfiguration messagesConfig;
    private static BountiesPlus plugin;

    /**
     * Initializes the message utility with the plugin instance.
     * // note: Sets up the plugin reference and reloads messages.yml
     */
    public static void initialize(BountiesPlus pluginInstance) {
        plugin = pluginInstance;
        reloadMessages();
    }

    /**
     * Reloads and caches messages from messages.yml.
     * // note: Updates the cached messagesConfig for performance
     */
    public static void reloadMessages() {
        messagesConfig = plugin.getMessagesConfig();
    }

    /**
     * Sends a formatted message to a player, handling single strings or lists with PlaceholderAPI.
     * // note: Retrieves message from messages.yml, formats with color codes and PlaceholderAPI placeholders, and sends as BaseComponent[]
     *
     * @param player     The player to send the message to.
     * @param messageKey The key in messages.yml to retrieve the message from.
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
     * Formats a single message string with color codes and PlaceholderAPI placeholders.
     * // note: Applies color codes and PlaceholderAPI placeholders for the given player
     *
     * @param message The raw message string.
     * @param player  The player for PlaceholderAPI context.
     * @return The fully formatted string.
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
}