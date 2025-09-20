package dev.byflow.psaddon.hologram;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.ProtectionStonesHook;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.RegionHealthManager;
import dev.byflow.psaddon.config.AddonSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HologramManager {
    private final PSAddonPlugin plugin;
    private final Map<String, UUID> holograms = new HashMap<>();

    public HologramManager(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    public void restoreAll(RegionHealthManager healthManager, ProtectionStonesHook hook) {
        healthManager.getRegionLivesView().forEach((key, lives) ->
                hook.findRegionByKey(key).ifPresent(region -> {
                    AddonSettings.BlockSettings settings = plugin.getAddonSettings().resolve(region.getProtectBlock());
                    if (!settings.hologramEnabled()) {
                        remove(region);
                        return;
                    }
                    String blockKey = RegionHealthManager.toBlockKey(region.getProtectBlock().getLocation());
                    healthManager.ensureBlockIndex(region, blockKey);
                    update(region, settings, lives, settings.lives());
                })
        );
    }

    public void update(RegionHandle region, AddonSettings.BlockSettings settings, int lives, int maxLives) {
        if (!settings.hologramEnabled()) {
            return;
        }
        double offset = settings.hologramOffset();
        String pattern = settings.hologramText();
        Component text = LegacyComponentSerializer.legacyAmpersand().deserialize(
                pattern.replace("{lives}", Integer.toString(lives))
                        .replace("{max}", Integer.toString(maxLives))
        );

        TextDisplay display = obtain(region, offset);
        display.text(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        display.setPersistent(true);
        display.setShadowed(false);
    }

    public void remove(RegionHandle region) {
        UUID id = holograms.remove(region.getStorageKey());
        if (id == null) {
            return;
        }
        World world = region.getWorld();
        Entity entity = world.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    public void removeAll() {
        holograms.forEach((key, uuid) -> {
            for (World world : plugin.getServer().getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
        });
        holograms.clear();
    }

    private TextDisplay obtain(RegionHandle region, double offset) {
        UUID id = holograms.get(region.getStorageKey());
        if (id != null) {
            Entity entity = region.getWorld().getEntity(id);
            if (entity instanceof TextDisplay existing) {
                existing.teleport(region.getHologramLocation(offset));
                return existing;
            }
        }
        TextDisplay display = region.getWorld().spawn(region.getHologramLocation(offset), TextDisplay.class, spawned -> {
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setBillboard(Display.Billboard.CENTER);
        });
        holograms.put(region.getStorageKey(), display.getUniqueId());
        return display;
    }
}
