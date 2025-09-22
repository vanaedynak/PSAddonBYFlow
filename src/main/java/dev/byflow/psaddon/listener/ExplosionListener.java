package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Objects;

public final class ExplosionListener implements Listener {
    private static final NamespacedKey CUSTOM_TNT_ID = Objects.requireNonNull(NamespacedKey.fromString("psaddon:custom_tnt_id"));
    private static final NamespacedKey CUSTOM_TNT_FLAGS = Objects.requireNonNull(NamespacedKey.fromString("psaddon:custom_tnt_flags"));
    private static final int FLAG_AFFECTS_REGIONS = 0x1;

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
        if (!(source instanceof TNTPrimed primed)) {
            return false;
        }
        PersistentDataContainer container = primed.getPersistentDataContainer();
        String rawId = container.get(CUSTOM_TNT_ID, PersistentDataType.STRING);
        if (rawId == null) {
            return false;
        }
        String normalizedId = rawId.toLowerCase(Locale.ROOT);
        if (!settings.allowedCustomTnt().isEmpty() && !settings.allowedCustomTnt().contains(normalizedId)) {
            return false;
        }
        Integer flags = container.get(CUSTOM_TNT_FLAGS, PersistentDataType.INTEGER);
        return flags != null && (flags & FLAG_AFFECTS_REGIONS) != 0;
    }

    private void protectBlock(EntityExplodeEvent event, Block block) {
        event.blockList().removeIf(candidate -> candidate.getLocation().equals(block.getLocation()));
    }
}
