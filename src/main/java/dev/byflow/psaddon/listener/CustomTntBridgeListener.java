package dev.byflow.psaddon.listener;

import dev.byflow.customtntflow.api.MutableBlockBehavior;
import dev.byflow.customtntflow.api.RegionTNTAPI;
import dev.byflow.customtntflow.api.event.CustomTNTPreAffectEvent;
import dev.byflow.customtntflow.api.event.RegionTNTDetonateEvent;
import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.config.AddonSettings.CustomTntSettings;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class CustomTntBridgeListener implements Listener {
    private final PSAddonPlugin plugin;

    public CustomTntBridgeListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreAffect(CustomTNTPreAffectEvent event) {
        if (resolveSettings(event.getTnt()).isEmpty()) {
            return;
        }
        MutableBlockBehavior behavior = event.getBehavior();
        behavior.setBreakBlocks(false);
        behavior.setDropBlocks(false);
        behavior.setApiOnly(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDetonate(RegionTNTDetonateEvent event) {
        Optional<CustomTntSettings> maybeSettings = resolveSettings(event.getTnt());
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

    private Optional<CustomTntSettings> resolveSettings(TNTPrimed primed) {
        String typeId = resolveTypeId(primed);
        if (typeId == null) {
            return Optional.empty();
        }
        return plugin.getAddonSettings().resolveCustomTnt(typeId);
    }

    private String resolveTypeId(TNTPrimed primed) {
        if (primed == null) {
            return null;
        }
        RegionTNTAPI api = plugin.getRegionTntApi();
        if (api == null) {
            return null;
        }
        try {
            Object type = api.getType(primed);
            if (type == null) {
                return null;
            }
            try {
                Method method = type.getClass().getMethod("getId");
                Object value = method.invoke(type);
                return value != null ? value.toString() : null;
            } catch (ReflectiveOperationException reflectionError) {
                plugin.getLogger().log(Level.WARNING, "Failed to access CustomTNTFlow type identifier", reflectionError);
                return null;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "Failed to resolve CustomTNTFlow type", throwable);
            return null;
        }
    }
}
