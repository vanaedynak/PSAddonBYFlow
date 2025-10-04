package dev.byflow.psaddon;

import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.config.AddonSettings;
import dev.byflow.psaddon.hologram.HologramManager;
import dev.byflow.psaddon.listener.ExplosionListener;
import dev.byflow.psaddon.listener.ProtectionStonesEventBridge;
import dev.byflow.psaddon.listener.WitherProtectionListener;
import dev.byflow.psaddon.tnt.RegionTntManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
public final class PSAddonPlugin extends JavaPlugin {
    private ProtectionStonesHook protectionStonesHook;
    private RegionHealthManager regionHealthManager;
    private HologramManager hologramManager;
    private AddonSettings addonSettings;
    private RegionTntManager regionTntManager;

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

        this.regionHealthManager = new RegionHealthManager(this, protectionStonesHook);
        this.regionHealthManager.load();

        this.hologramManager = new HologramManager(this);
        this.hologramManager.restoreAll(regionHealthManager, protectionStonesHook);

        this.regionTntManager = new RegionTntManager(this);

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
        pluginManager.registerEvents(regionTntManager, this);
        pluginManager.registerEvents(new ExplosionListener(this), this);
        pluginManager.registerEvents(new WitherProtectionListener(this), this);

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
        cfg.addDefault("default.hologram.lines", List.of(
                "&cЖизни привата: &f{lives}&7/&f{max}",
                "&7Владелец: &f{owner}"
        ));
        cfg.addDefault("prevent-stacking", true);
        cfg.addDefault("messages.stack-block-denied", "&cНельзя ставить приват вплотную к другому приватному блоку!");
        cfg.options().copyDefaults(true);
        saveConfig();
        this.addonSettings = new AddonSettings(cfg);
    }

    public RegionTntManager getRegionTntManager() {
        return regionTntManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("regiontnt")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Команда доступна только игрокам.", NamedTextColor.RED));
            return true;
        }

        if (!sender.hasPermission("psaddon.regiontnt")) {
            sender.sendMessage(Component.text("Недостаточно прав.", NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length > 0) {
            try {
                amount = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Неверное количество: " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        ItemStack item = regionTntManager.createItem(amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));

        sender.sendMessage(Component.text("Вы получили регионный динамит (" + amount + ")", NamedTextColor.GREEN));
        return true;
    }
}
