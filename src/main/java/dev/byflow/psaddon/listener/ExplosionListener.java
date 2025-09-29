package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.tnt.CustomTntResolver;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExplosionListener implements Listener {
    private final PSAddonPlugin plugin;

    public ExplosionListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity source = event.getEntity();

        if (source instanceof TNTPrimed primed) {
            CustomTntResolver.Match customMatch = plugin.resolveCustomTnt(primed);
            if (customMatch != null) {
                handleCustomTnt(event, customMatch);
                return;
            }

            handleStandardExplosion(event, source, true);
            return;
        }

        handleStandardExplosion(event, source, false);
    }

    private void handleStandardExplosion(EntityExplodeEvent event, Entity source, boolean primedTntWithoutMatch) {
        plugin.getProtectionStonesHook().findRegion(event.getLocation()).ifPresent(region -> {
            AddonSettings.BlockSettings settings = plugin.getAddonSettings().resolve(region.getProtectBlock());
            if (!isSourceAllowed(settings, source, primedTntWithoutMatch)) {
                protectBlock(event, region.getProtectBlock());
                return;
            }

            int remaining = plugin.damageRegion(region, settings, settings.damagePerExplosion());
            if (remaining > 0) {
                protectBlock(event, region.getProtectBlock());
            }
        });
    }

    private void handleCustomTnt(EntityExplodeEvent event, CustomTntResolver.Match match) {
        AddonSettings.CustomTntSettings customSettings = match.settings();
        Map<RegionHandle, AddonSettings.BlockSettings> impacted = new LinkedHashMap<>();

        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            plugin.getProtectionStonesHook().findRegion(block.getLocation()).ifPresentOrElse(region -> {
                impacted.putIfAbsent(region, plugin.getAddonSettings().resolve(region.getProtectBlock()));
            }, () -> {
                if (customSettings.onlyRegionBlocks()) {
                    iterator.remove();
                }
            });
        }

        plugin.getProtectionStonesHook().findRegion(event.getLocation())
                .ifPresent(region -> impacted.putIfAbsent(region,
                        plugin.getAddonSettings().resolve(region.getProtectBlock())));

        if (impacted.isEmpty()) {
            if (customSettings.cancelWhenEmpty()) {
                event.setCancelled(true);
            }
            return;
        }

        for (Map.Entry<RegionHandle, AddonSettings.BlockSettings> entry : impacted.entrySet()) {
            RegionHandle region = entry.getKey();
            AddonSettings.BlockSettings blockSettings = entry.getValue();
            int damage = customSettings.resolveDamage(blockSettings);
            int remaining = plugin.damageRegion(region, blockSettings, damage);
            if (remaining > 0) {
                protectBlock(event, region.getProtectBlock());
            }
        }
    }

    private boolean isSourceAllowed(AddonSettings.BlockSettings settings, Entity source,
                                    boolean primedTntWithoutMatch) {
        if (!settings.tntOnly()) {
            return true;
        }
        if (primedTntWithoutMatch) {
            return false;
        }
        return source instanceof TNTPrimed;
    }

    private void protectBlock(EntityExplodeEvent event, Block block) {
        event.blockList().removeIf(candidate -> candidate.getLocation().equals(block.getLocation()));
    }
}
