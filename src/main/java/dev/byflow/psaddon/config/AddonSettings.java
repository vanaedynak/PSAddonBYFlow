package dev.byflow.psaddon.config;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class AddonSettings {
    private final BlockSettings defaultSettings;
    private final Map<Material, BlockSettings> overrides;
    private final boolean preventStacking;

    public AddonSettings(FileConfiguration configuration) {
        ConfigurationSection defaultSection = configuration.getConfigurationSection("default");
        if (defaultSection == null) {
            defaultSection = configuration.createSection("default");
        }
        this.defaultSettings = BlockSettings.from(defaultSection, null);

        this.overrides = new EnumMap<>(Material.class);
        ConfigurationSection blocksSection = configuration.getConfigurationSection("blocks");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    continue;
                }
                ConfigurationSection overrideSection = blocksSection.getConfigurationSection(key);
                if (overrideSection == null) {
                    continue;
                }
                BlockSettings settings = BlockSettings.from(overrideSection, defaultSettings);
                overrides.put(material, settings);
            }
        }

        this.preventStacking = configuration.getBoolean("prevent-stacking", true);
    }

    public BlockSettings getDefaultSettings() {
        return defaultSettings;
    }

    public BlockSettings resolve(Block block) {
        if (block == null) {
            return defaultSettings;
        }
        return overrides.getOrDefault(block.getType(), defaultSettings);
    }

    public boolean isPreventStacking() {
        return preventStacking;
    }

    public Optional<BlockSettings> getOverride(Material material) {
        return Optional.ofNullable(overrides.get(material));
    }

    public record BlockSettings(
            int lives,
            int damagePerExplosion,
            boolean tntOnly,
            boolean hologramEnabled,
            double hologramOffset,
            String hologramText
    ) {
        private static final String DEFAULT_HOLOGRAM_TEXT = "&cЖизни привата: &f{lives}&7/&f{max}";

        private BlockSettings {
            lives = Math.max(1, lives);
            damagePerExplosion = Math.max(1, damagePerExplosion);
            hologramText = hologramText == null ? DEFAULT_HOLOGRAM_TEXT : hologramText;
        }

        static BlockSettings from(ConfigurationSection section, BlockSettings fallback) {
            int lives = section.getInt("lives", fallback != null ? fallback.lives : 3);
            int damage = section.getInt("damage-per-explosion", fallback != null ? fallback.damagePerExplosion : 1);
            boolean tntOnly = section.getBoolean("tnt-only", fallback != null && fallback.tntOnly);

            boolean hologramEnabled = fallback != null ? fallback.hologramEnabled : true;
            double hologramOffset = fallback != null ? fallback.hologramOffset : 1.8;
            String hologramText = fallback != null ? fallback.hologramText : DEFAULT_HOLOGRAM_TEXT;

            if (section.isConfigurationSection("hologram")) {
                ConfigurationSection hologram = section.getConfigurationSection("hologram");
                if (hologram != null) {
                    hologramEnabled = hologram.getBoolean("enabled", hologramEnabled);
                    hologramOffset = hologram.getDouble("offset-y", hologramOffset);
                    hologramText = hologram.getString("text", hologramText);
                }
            } else {
                hologramEnabled = section.getBoolean("hologram.enabled", hologramEnabled);
                hologramOffset = section.getDouble("hologram.offset-y", hologramOffset);
                hologramText = section.getString("hologram.text", hologramText);
            }

            return new BlockSettings(lives, damage, tntOnly, hologramEnabled, hologramOffset, hologramText);
        }
    }
}
