package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.RegionHealthManager;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.tnt.RegionBombManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ExplosionListener implements Listener {
    private final PSAddonPlugin plugin;

    public ExplosionListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity source = event.getEntity();

        if (source instanceof TNTPrimed primed) {
            RegionBombManager bombManager = plugin.getRegionBombManager();
            if (bombManager != null && bombManager.isEnabled() && bombManager.isRegionBomb(primed)) {
                handleRegionBomb(event, bombManager);
                return;
            }

            handleStandardExplosion(event);
            return;
        }

        handleStandardExplosion(event);
    }

    private void handleRegionBomb(EntityExplodeEvent event, RegionBombManager manager) {
        event.blockList().clear();
        event.setYield(0F);

        double radius = manager.getRadius();
        if (radius <= 0) {
            return;
        }

        List<RegionHandle> impacted = new ArrayList<>(plugin.getRegionHealthManager()
                .findRegionsWithinRadius(event.getLocation(), radius));
        plugin.getProtectionStonesHook().findRegion(event.getLocation()).ifPresent(region -> {
            if (!impacted.contains(region)) {
                impacted.add(region);
            }
        });
        if (impacted.isEmpty()) {
            return;
        }

        for (RegionHandle region : impacted) {
            AddonSettings.BlockSettings blockSettings = plugin.getAddonSettings().resolve(region.getProtectBlock());
            int damage = manager.getDamage();
            int remaining = plugin.damageRegion(region, blockSettings, damage);
            if (remaining > 0) {
                protectBlock(event, region.getProtectBlock());
            }
        }
    }

    private void handleStandardExplosion(EntityExplodeEvent event) {
        RegionHealthManager healthManager = plugin.getRegionHealthManager();
        if (healthManager == null) {
            return;
        }

        event.blockList().removeIf(block -> isProtectBlock(block, healthManager));
    }

    private boolean isProtectBlock(Block block, RegionHealthManager healthManager) {
        if (block == null) {
            return false;
        }

        Location location = block.getLocation();
        String blockKey = RegionHealthManager.toBlockKey(location);

        Optional<String> cachedRegionKey = healthManager.getRegionKeyByBlock(blockKey);
        if (cachedRegionKey.isPresent()) {
            Optional<RegionHandle> regionByKey = cachedRegionKey
                    .flatMap(key -> plugin.getProtectionStonesHook().findRegionByKey(key));
            if (regionByKey.isPresent()) {
                return matchesProtectBlock(regionByKey.get(), location);
            }
        }

        return plugin.getProtectionStonesHook()
                .findRegion(location)
                .map(region -> matchesProtectBlock(region, location))
                .orElse(false);
    }

    private boolean matchesProtectBlock(RegionHandle region, Location location) {
        Block protectBlock = region.getProtectBlock();
        return protectBlock != null && protectBlock.getLocation().equals(location);
    }

    private void protectBlock(EntityExplodeEvent event, Block block) {
        event.blockList().removeIf(candidate -> candidate.getLocation().equals(block.getLocation()));
    }
}
