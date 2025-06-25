package tony26.bountiesPlus.utils;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import tony26.bountiesPlus.BountiesPlus;
import tony26.bountiesPlus.BountyCreationChatListener;
import tony26.bountiesPlus.GUIs.BountyCancel;
import tony26.bountiesPlus.GUIs.BountyGUI;
import tony26.bountiesPlus.GUIs.TopGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages registration and unregistration of event listeners
 * // note: Centralizes listener management for plugin scalability and cleanup
 */
public class EventManager {
    private final BountiesPlus plugin;
    private final List<Listener> registeredListeners = new ArrayList<>();

    public EventManager(BountiesPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a listener with the plugin
     * // note: Adds listener to Bukkit and tracks it for cleanup
     */
    public void register(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
        plugin.getDebugManager().logDebug("Registered listener: " + listener.getClass().getSimpleName());
    }

    /**
     * Unregisters a specific listener
     * // note: Removes listener from Bukkit and tracking list
     */
    public void unregister(Listener listener) {
        HandlerList.unregisterAll(listener);
        registeredListeners.remove(listener);
        plugin.getDebugManager().logDebug("Unregistered listener: " + listener.getClass().getSimpleName());
    }

    /**
     * Unregisters all listeners
     * // note: Clears all registered listeners during plugin disable
     */
    public void unregisterAll() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();
        plugin.getDebugManager().logDebug("Unregistered all listeners.");
    }

    /**
     * Registers global listeners for the plugin
     * // note: Initializes core listeners during plugin startup
     */
    public void registerGlobalListeners() {
        register(new PlayerDeathListener(plugin, this));
        register(new BountyCreationChatListener(plugin, this));
        register(new TopGUI(plugin, this));
        register(new BountyGUI(plugin, this, null));
        register(new BountyCancel(plugin, this));
    }
}