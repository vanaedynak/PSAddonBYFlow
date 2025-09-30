package dev.byflow.psaddon.tnt;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.TNTPrimed;

import java.util.ArrayList;
import java.util.List;

public final class RegionBombManager {
    private final PSAddonPlugin plugin;
    private final NamespacedKey markerKey;
    private AddonSettings.RegionBombSettings settings;

    public RegionBombManager(PSAddonPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "region_bomb");
        this.settings = AddonSettings.RegionBombSettings.disabled();
    }

    public void reload(AddonSettings addonSettings) {
        this.settings = addonSettings.getRegionBombSettings();
    }

    public boolean isEnabled() {
        return settings != null && settings.enabled();
    }

    public double getRadius() {
        return settings.radius();
    }

    public int getFuseTicks() {
        return settings.fuseTicks();
    }

    public int getDamage() {
        return settings.damage();
    }

    public ItemStack createItem(int amount) {
        if (!isEnabled()) {
            throw new IllegalStateException("Region bomb is disabled in configuration");
        }
        ItemStack stack = new ItemStack(org.bukkit.Material.TNT, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String displayName = colorize(settings.displayName());
            if (!displayName.isBlank()) {
                meta.setDisplayName(displayName);
            }
            if (!settings.lore().isEmpty()) {
                meta.setLore(colorize(settings.lore()));
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(markerKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isRegionBombItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte flag = container.get(markerKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public void mark(TNTPrimed primed) {
        if (primed == null) {
            return;
        }
        primed.setYield(0F);
        primed.setIsIncendiary(false);
        String name = colorize(settings.displayName());
        if (!name.isBlank()) {
            primed.setCustomName(name);
            primed.setCustomNameVisible(true);
        }
        primed.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isRegionBomb(TNTPrimed primed) {
        if (primed == null) {
            return false;
        }
        PersistentDataContainer container = primed.getPersistentDataContainer();
        Byte flag = container.get(markerKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    private String colorize(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private List<String> colorize(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }
}
