package dev.byflow.psaddon.tnt;

import dev.byflow.psaddon.tnt.command.CustomTntCommand;
import dev.byflow.psaddon.tnt.config.TntConfiguration;
import dev.byflow.psaddon.tnt.listener.CustomTntBlockListener;
import dev.byflow.psaddon.tnt.listener.CustomTntExplosionListener;
import dev.byflow.psaddon.tnt.storage.PlacedTntStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomTntPlugin extends JavaPlugin {
    private CustomTntManager manager;
    private PlacedTntStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.storage = new PlacedTntStorage(this);
        storage.load();
        reloadManager();
        storage.cleanupUnloaded();
        storage.save();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CustomTntBlockListener(this, manager), this);
        pluginManager.registerEvents(new CustomTntExplosionListener(manager), this);

        PluginCommand command = getCommand("customtnt");
        if (command != null) {
            CustomTntCommand executor = new CustomTntCommand(manager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command customtnt is not defined in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.save();
        }
    }

    public void reloadManager() {
        reloadConfig();
        TntConfiguration configuration = new TntConfiguration(getConfig(), getLogger());
        this.manager = new CustomTntManager(configuration, storage);
        if (manager.getTypes().isEmpty()) {
            getLogger().warning("Custom TNT configuration is empty. No special explosives will be available.");
        }
    }

    public CustomTntManager getManager() {
        return manager;
    }

    public PlacedTntStorage getStorage() {
        return storage;
    }
}
