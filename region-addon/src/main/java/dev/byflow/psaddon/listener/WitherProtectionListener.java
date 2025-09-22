package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public final class WitherProtectionListener implements Listener {
    private final PSAddonPlugin plugin;

    public WitherProtectionListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Wither) && !(entity instanceof WitherSkull)) {
            return;
        }

        Block block = event.getBlock();
        plugin.getProtectionStonesHook().findRegion(block.getLocation()).ifPresent(region -> {
            if (!region.getProtectBlock().getLocation().equals(block.getLocation())) {
                return;
            }

            AddonSettings.BlockSettings settings = plugin.getAddonSettings().resolve(region.getProtectBlock());
            if (settings.tntOnly()) {
                event.setCancelled(true);
                return;
            }

            int remaining = plugin.damageRegion(region, settings, settings.damagePerExplosion());
            if (remaining > 0) {
                event.setCancelled(true);
            }
        });
    }
}
