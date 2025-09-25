package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.ProtectionStonesHook;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.RegionHealthManager;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

public final class ProtectionStonesEventBridge implements Listener {
    private final PSAddonPlugin plugin;
    private final ProtectionStonesHook hook;

    public ProtectionStonesEventBridge(PSAddonPlugin plugin, ProtectionStonesHook hook) {
        this.plugin = plugin;
        this.hook = hook;
    }

    public void register(PluginManager manager) {
        EventExecutor createExecutor = (listener, event) -> handleCreate(event);
        EventExecutor removeExecutor = (listener, event) -> handleRemove(event);

        @SuppressWarnings("unchecked")
        Class<? extends Event> createClass = (Class<? extends Event>) hook.getPsCreateEventClass();
        @SuppressWarnings("unchecked")
        Class<? extends Event> removeClass = (Class<? extends Event>) hook.getPsRemoveEventClass();

        manager.registerEvent(createClass, this, EventPriority.HIGHEST, createExecutor, plugin, false);
        manager.registerEvent(removeClass, this, EventPriority.MONITOR, removeExecutor, plugin, false);
    }

    private void handleCreate(Event event) {
        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        Object regionObject = hook.getCreateEventRegion(event);
        if (regionObject == null) {
            return;
        }
        RegionHandle region = hook.wrap(regionObject);
        Block protectBlock = region.getProtectBlock();
        AddonSettings.BlockSettings settings = plugin.getAddonSettings().resolve(protectBlock);
        RegionHealthManager.RegistrationResult result = plugin.getRegionHealthManager().registerRegion(
                region,
                settings.lives(),
                protectBlock,
                plugin.getAddonSettings().isPreventStacking()
        );
        if (!result.accepted()) {
            hook.cancelCreateEvent(event);
            String message = plugin.getConfig().getString("messages.stack-block-denied",
                    "&cНельзя ставить приват вплотную к другому приватному блоку!");
            if (message != null && !message.isEmpty()) {
                hook.sendCreateEventMessage(event, message);
            }
            String blockKey = protectBlock != null
                    ? RegionHealthManager.toBlockKey(protectBlock.getLocation())
                    : region.getStorageKey();
            plugin.getLogger().info("Prevented overlapping ProtectionStones region at " + blockKey +
                    ", existing region key " + result.conflictingRegionKey());
            return;
        }

        int lives = result.lives() != null ? result.lives() : settings.lives();
        plugin.getHologramManager().update(region, settings, lives, settings.lives());
    }

    private void handleRemove(Event event) {
        Object regionObject = hook.getRemoveEventRegion(event);
        if (regionObject == null) {
            return;
        }
        RegionHandle region = hook.wrap(regionObject);
        plugin.getRegionHealthManager().removeRegion(region);
        plugin.getHologramManager().remove(region);
    }
}
