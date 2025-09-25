package dev.byflow.psaddon.listener;

import dev.byflow.customtnt.api.RegionTNTType;
import dev.byflow.customtnt.api.event.CustomTNTPreAffectEvent;
import dev.byflow.customtnt.api.event.RegionTNTDetonateEvent;
import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.config.AddonSettings.CustomTntSettings;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CustomTntBridgeListener implements Listener {
    private final PSAddonPlugin plugin;

    public CustomTntBridgeListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreAffect(CustomTNTPreAffectEvent event) {
        if (!shouldHandle(event.getType())) {
            return;
        }
        CustomTNTPreAffectEvent.MutableBlockBehavior behavior = event.getBehavior();
        behavior.setBreakBlocks(false);
        behavior.setDropBlocks(false);
        behavior.setApiOnly(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDetonate(RegionTNTDetonateEvent event) {
        Optional<CustomTntSettings> maybeSettings = resolveSettings(event.getType());
        if (maybeSettings.isEmpty()) {
            return;
        }
        CustomTntSettings settings = maybeSettings.get();

        Map<RegionHandle, AddonSettings.BlockSettings> impactedRegions = new LinkedHashMap<>();
        Iterator<Block> iterator = event.getAffectedBlocks().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Optional<RegionHandle> region = plugin.getProtectionStonesHook().findRegion(block.getLocation());
            if (region.isPresent()) {
                RegionHandle handle = region.get();
                impactedRegions.putIfAbsent(handle, plugin.getAddonSettings().resolve(handle.getProtectBlock()));
            } else if (settings.onlyRegionBlocks()) {
                iterator.remove();
            }
        }

        if (impactedRegions.isEmpty()) {
            if (settings.cancelWhenEmpty()) {
                event.setCancelled(true);
            }
            return;
        }

        for (Map.Entry<RegionHandle, AddonSettings.BlockSettings> entry : impactedRegions.entrySet()) {
            RegionHandle region = entry.getKey();
            AddonSettings.BlockSettings blockSettings = entry.getValue();
            int damage = settings.resolveDamage(blockSettings);
            plugin.damageRegion(region, blockSettings, damage);
        }
    }

    private boolean shouldHandle(RegionTNTType type) {
        return resolveSettings(type).isPresent();
    }

    private Optional<CustomTntSettings> resolveSettings(RegionTNTType type) {
        if (type == null) {
            return Optional.empty();
        }
        return plugin.getAddonSettings().resolveCustomTnt(type.getId());
    }
}
