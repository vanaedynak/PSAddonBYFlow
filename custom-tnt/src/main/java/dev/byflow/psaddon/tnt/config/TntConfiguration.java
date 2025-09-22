package dev.byflow.psaddon.tnt.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class TntConfiguration {
    private final Map<String, CustomTntType> types;

    public TntConfiguration(FileConfiguration configuration, Logger logger) {
        ConfigurationSection root = configuration.getConfigurationSection("tnts");
        if (root == null) {
            this.types = Collections.emptyMap();
            return;
        }

        Map<String, CustomTntType> loaded = new HashMap<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            CustomTntType type = readType(key, section, logger);
            if (type != null) {
                loaded.put(key, type);
            }
        }
        this.types = Collections.unmodifiableMap(loaded);
    }

    private CustomTntType readType(String id, ConfigurationSection section, Logger logger) {
        String materialName = section.getString("material", "TNT");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            logger.warning(() -> "Unknown material '" + materialName + "' for custom TNT " + id + ". Skipping.");
            return null;
        }

        String displayName = section.getString("display-name", "&c" + id);
        List<String> lore = readStringList(section, "lore");
        Integer customModelData = section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null;
        boolean autoIgnite = section.getBoolean("auto-ignite-when-placed", false);
        boolean dropWhenBroken = section.getBoolean("drop-when-broken", true);
        boolean igniteInWater = section.getBoolean("ignite-in-water", true);
        int fuseTicks = section.getInt("fuse-ticks", 80);
        double power = section.getDouble("explosion-power", 4.0D);
        boolean affectsRegions = section.getBoolean("affects-regions", false);
        boolean onlyRegions = section.getBoolean("only-damage-regions", false);

        BlockDamageRule blockDamage = readBlockDamage(section.getConfigurationSection("block-damage"), logger);
        if (blockDamage == null) {
            blockDamage = new BlockDamageRule(BlockDamageRule.Mode.NONE, Collections.emptySet(), Collections.emptySet());
        }

        return new CustomTntType(
                id,
                material,
                displayName,
                lore,
                customModelData,
                autoIgnite,
                dropWhenBroken,
                igniteInWater,
                fuseTicks,
                (float) power,
                affectsRegions,
                onlyRegions,
                blockDamage
        );
    }

    private BlockDamageRule readBlockDamage(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return new BlockDamageRule(BlockDamageRule.Mode.NONE, Collections.emptySet(), Collections.emptySet());
        }
        String modeName = section.getString("mode", "NONE");
        BlockDamageRule.Mode mode;
        try {
            mode = BlockDamageRule.Mode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            logger.warning(() -> "Unknown block-damage mode '" + modeName + "'. Using NONE.");
            mode = BlockDamageRule.Mode.NONE;
        }

        Set<Material> blockSet = readMaterialSet(section, "blocks", logger);
        Set<Material> forceBreak = readMaterialSet(section, "force-break", logger);
        return new BlockDamageRule(mode, blockSet, forceBreak);
    }

    private Set<Material> readMaterialSet(ConfigurationSection section, String path, Logger logger) {
        List<String> values = readStringList(section, path);
        if (values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String value : values) {
            Material material = Material.matchMaterial(value);
            if (material != null) {
                result.add(material);
            } else {
                logger.warning(() -> "Unknown material '" + value + "' in block list " + path + ".");
            }
        }
        return result;
    }

    private List<String> readStringList(ConfigurationSection section, String path) {
        if (section == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        if (section.isList(path)) {
            for (Object entry : section.getList(path)) {
                if (entry != null) {
                    values.add(entry.toString());
                }
            }
            return values;
        }
        String value = section.getString(path);
        if (value != null) {
            values.add(value);
        }
        return values;
    }

    public Map<String, CustomTntType> types() {
        return types;
    }
}
