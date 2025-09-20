package dev.byflow.psaddon;

import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.hologram.HologramManager;
import dev.byflow.psaddon.listener.ExplosionListener;
import dev.byflow.psaddon.listener.ProtectionStonesEventBridge;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PSAddonPlugin extends JavaPlugin {
    private ProtectionStonesHook protectionStonesHook;
    private RegionHealthManager regionHealthManager;
    private HologramManager hologramManager;
    private AddonSettings addonSettings;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtectionStones") == null) {
            getLogger().severe("ProtectionStones plugin is not loaded. Disabling addon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
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

        this.regionHealthManager = new RegionHealthManager(this);
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

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ExplosionListener(this), this);

        ProtectionStonesEventBridge bridge = new ProtectionStonesEventBridge(this, protectionStonesHook);
        bridge.register(pluginManager);
    }

    private void reloadConfiguration() {
        FileConfiguration cfg = getConfig();
        cfg.addDefault("default.lives", 3);
        cfg.addDefault("default.damage-per-explosion", 1);
        cfg.addDefault("default.tnt-only", true);
        cfg.addDefault("default.hologram.enabled", true);
        cfg.addDefault("default.hologram.offset-y", 1.8);
        cfg.addDefault("default.hologram.text", "&cЖизни привата: &f{lives}&7/&f{max}");
        cfg.addDefault("prevent-stacking", true);
        cfg.addDefault("messages.stack-block-denied", "&cНельзя ставить приват вплотную к другому приватному блоку!");
        cfg.options().copyDefaults(true);
        saveConfig();
        this.addonSettings = new AddonSettings(cfg);
    }
}
