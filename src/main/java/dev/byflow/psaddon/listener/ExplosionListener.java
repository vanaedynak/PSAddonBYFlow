package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.hologram.HologramManager;
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
            if (settings.tntOnly() && !(source instanceof TNTPrimed)) {
                return;
            }

            int maxLives = settings.lives();
            int remaining = plugin.getRegionHealthManager().damageRegion(region, settings.damagePerExplosion(), maxLives);
            if (remaining > 0) {
                HologramManager hologramManager = plugin.getHologramManager();
                hologramManager.update(region, settings, remaining, maxLives);
                event.blockList().clear();
                event.setCancelled(true);
            } else {
                boolean deleted = plugin.getProtectionStonesHook().deleteRegion(region);
                plugin.getRegionHealthManager().removeRegion(region);
                plugin.getHologramManager().remove(region);
                if (!deleted) {
                    plugin.getLogger().warning("Failed to remove region " + region.getStorageKey() + " after durability reached zero.");
                }
            }
        });
    }
}
