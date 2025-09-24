package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public final class ExplosionListener implements Listener {
    private final PSAddonPlugin plugin;

    public ExplosionListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity source = event.getEntity();

        plugin.getProtectionStonesHook().findRegion(event.getLocation()).ifPresent(region -> {
            AddonSettings.BlockSettings settings = plugin.getAddonSettings().resolve(region.getProtectBlock());
            if (!isSourceAllowed(settings, source)) {
                protectBlock(event, region.getProtectBlock());
                return;
            }

            int remaining = plugin.damageRegion(region, settings, settings.damagePerExplosion());
            if (remaining > 0) {
                protectBlock(event, region.getProtectBlock());
            }
        });
    }

    private boolean isSourceAllowed(AddonSettings.BlockSettings settings, Entity source) {
        if (!settings.tntOnly()) {
            return true;
        }
        return source instanceof TNTPrimed;
    }

    private void protectBlock(EntityExplodeEvent event, Block block) {
        event.blockList().removeIf(candidate -> candidate.getLocation().equals(block.getLocation()));
    }
}
