package dev.byflow.psaddon;

import dev.byflow.customtntflow.api.RegionTNTAPI;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.hologram.HologramManager;
import dev.byflow.psaddon.listener.ExplosionListener;
import dev.byflow.psaddon.listener.CustomTntBridgeListener;
import dev.byflow.psaddon.listener.ProtectionStonesEventBridge;
import dev.byflow.psaddon.listener.WitherProtectionListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
public final class PSAddonPlugin extends JavaPlugin {
    private ProtectionStonesHook protectionStonesHook;
    private RegionHealthManager regionHealthManager;
    private HologramManager hologramManager;
    private AddonSettings addonSettings;
    private RegionTNTAPI regionTntApi;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtectionStones") == null) {
            getLogger().severe("ProtectionStones plugin is not loaded. Disabling addon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("CustomTNTFlow") != null) {
            try {
                this.regionTntApi = RegionTNTAPI.get();
            } catch (Throwable throwable) {
                getLogger().warning("Failed to access CustomTNTFlow API: " + throwable.getMessage());
            }
        } else {
            getLogger().warning("CustomTNTFlow plugin is not loaded. Custom TNT integration disabled.");
        }

        saveDefaultConfig();
        reloadConfiguration();

        try {
            this.protectionStonesHook = new ProtectionStonesHook();
        } catch (ReflectiveOperationException ex) {
            getLogger().severe("Failed to initialize ProtectionStones hook: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.regionHealthManager = new RegionHealthManager(this, protectionStonesHook);
        this.regionHealthManager.load();

        this.hologramManager = new HologramManager(this);
        this.hologramManager.restoreAll(regionHealthManager, protectionStonesHook);

        registerListeners();
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.removeAll();
        }
        if (regionHealthManager != null) {
            regionHealthManager.save();
        }
    }

    public ProtectionStonesHook getProtectionStonesHook() {
        return protectionStonesHook;
    }

    public RegionHealthManager getRegionHealthManager() {
        return regionHealthManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public AddonSettings getAddonSettings() {
        return addonSettings;
    }

    public RegionTNTAPI getRegionTntApi() {
        return regionTntApi;
    }

    public boolean isCustomTnt(Entity entity) {
        if (!(entity instanceof TNTPrimed primed) || regionTntApi == null) {
            return false;
        }
        try {
            return regionTntApi.isCustom(primed);
        } catch (Throwable throwable) {
            getLogger().warning("Failed to query CustomTNTFlow for TNT entity: " + throwable.getMessage());
            return false;
        }
    }

    public int damageRegion(RegionHandle region, AddonSettings.BlockSettings settings, int amount) {
        int maxLives = settings.lives();
        int remaining = regionHealthManager.damageRegion(region, amount, maxLives);
        if (remaining > 0) {
            hologramManager.update(region, settings, remaining, maxLives);
            return remaining;
        }

        boolean deleted = protectionStonesHook.deleteRegion(region);
        regionHealthManager.removeRegion(region);
        hologramManager.remove(region);
        if (!deleted) {
            getLogger().warning("Failed to remove region " + region.getStorageKey() + " after durability reached zero.");
        }
        return 0;
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ExplosionListener(this), this);
        pluginManager.registerEvents(new WitherProtectionListener(this), this);

        ProtectionStonesEventBridge bridge = new ProtectionStonesEventBridge(this, protectionStonesHook);
        bridge.register(pluginManager);

        if (regionTntApi != null && addonSettings.hasCustomTntIntegration()) {
            pluginManager.registerEvents(new CustomTntBridgeListener(this), this);
        }
    }

    private void reloadConfiguration() {
        FileConfiguration cfg = getConfig();
        cfg.addDefault("default.lives", 3);
        cfg.addDefault("default.damage-per-explosion", 1);
        cfg.addDefault("default.tnt-only", true);
        cfg.addDefault("default.hologram.enabled", true);
        cfg.addDefault("default.hologram.offset-y", 1.8);
        cfg.addDefault("default.hologram.lines", List.of(
                "&cЖизни привата: &f{lives}&7/&f{max}",
                "&7Владелец: &f{owner}"
        ));
        cfg.addDefault("prevent-stacking", true);
        cfg.addDefault("messages.stack-block-denied", "&cНельзя ставить приват вплотную к другому приватному блоку!");
        cfg.addDefault("custom-tnt.enabled", true);
        cfg.addDefault("custom-tnt.types.region_breaker.damage-override", 1);
        cfg.addDefault("custom-tnt.types.region_breaker.only-region-blocks", true);
        cfg.addDefault("custom-tnt.types.region_breaker.cancel-when-empty", true);
        cfg.options().copyDefaults(true);
        saveConfig();
        this.addonSettings = new AddonSettings(cfg);
    }
}
