package dev.byflow.psaddon.config;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
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
            List<String> hologramLines,
            List<String> allowedCustomTnt
    ) {
        private static final List<String> DEFAULT_HOLOGRAM_LINES =
                List.of("&cЖизни привата: &f{lives}&7/&f{max}");
        private static final List<String> DEFAULT_ALLOWED_TNT =
                List.of("region_breaker");

        public BlockSettings {
            lives = Math.max(1, lives);
            damagePerExplosion = Math.max(1, damagePerExplosion);
            hologramLines = hologramLines == null || hologramLines.isEmpty()
                    ? DEFAULT_HOLOGRAM_LINES
                    : List.copyOf(hologramLines);
            allowedCustomTnt = normalizeTntList(allowedCustomTnt);
        }

        static BlockSettings from(ConfigurationSection section, BlockSettings fallback) {
            int lives = section.getInt("lives", fallback != null ? fallback.lives : 3);
            int damage = section.getInt("damage-per-explosion", fallback != null ? fallback.damagePerExplosion : 1);
            boolean tntOnly = section.getBoolean("tnt-only", fallback != null && fallback.tntOnly);

            boolean hologramEnabled = fallback != null ? fallback.hologramEnabled : true;
            double hologramOffset = fallback != null ? fallback.hologramOffset : 1.8;
            List<String> hologramLines = fallback != null ? fallback.hologramLines : DEFAULT_HOLOGRAM_LINES;
            List<String> allowedCustomTnt = fallback != null ? fallback.allowedCustomTnt : DEFAULT_ALLOWED_TNT;

            if (section.isConfigurationSection("hologram")) {
                ConfigurationSection hologram = section.getConfigurationSection("hologram");
                if (hologram != null) {
                    hologramEnabled = hologram.getBoolean("enabled", hologramEnabled);
                    hologramOffset = hologram.getDouble("offset-y", hologramOffset);
                    hologramLines = readHologramLines(hologram, hologramLines);
                }
            } else {
                hologramEnabled = section.getBoolean("hologram.enabled", hologramEnabled);
                hologramOffset = section.getDouble("hologram.offset-y", hologramOffset);
                hologramLines = readLegacyText(section.get("hologram.text"), hologramLines);
            }

            if (section.contains("allowed-custom-tnt")) {
                allowedCustomTnt = readAllowedTnt(section.get("allowed-custom-tnt"), allowedCustomTnt);
            }

            return new BlockSettings(lives, damage, tntOnly, hologramEnabled, hologramOffset, hologramLines, allowedCustomTnt);
        }

        private static List<String> readHologramLines(ConfigurationSection section, List<String> fallback) {
            if (section.isList("lines")) {
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) section.getList("lines");
                if (lines != null) {
                    return sanitizeLines(lines);
                }
            }
            Object value = section.get("text");
            return readLegacyText(value, fallback);
        }

        private static List<String> readLegacyText(Object value, List<String> fallback) {
            if (value instanceof List<?> rawList) {
                List<String> lines = new ArrayList<>();
                for (Object entry : rawList) {
                    if (entry != null) {
                        lines.add(entry.toString());
                    }
                }
                if (!lines.isEmpty()) {
                    return sanitizeLines(lines);
                }
            }
            if (value instanceof String text) {
                return parseMultilineString(text);
            }
            return fallback;
        }

        private static List<String> parseMultilineString(String text) {
            String prepared = text.replace("\\n", "\n");
            String[] split = prepared.split("\n");
            List<String> result = new ArrayList<>(split.length);
            Collections.addAll(result, split);
            return sanitizeLines(result);
        }

        private static List<String> sanitizeLines(List<String> lines) {
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String sanitized = line.replace("\r", "");
                result.add(sanitized);
            }
            if (result.isEmpty()) {
                return DEFAULT_HOLOGRAM_LINES;
            }
            return List.copyOf(result);
        }

        private static List<String> readAllowedTnt(Object raw, List<String> fallback) {
            if (raw instanceof List<?> list) {
                List<String> collected = new ArrayList<>();
                for (Object entry : list) {
                    if (entry != null) {
                        collected.add(entry.toString());
                    }
                }
                if (collected.isEmpty()) {
                    return List.of();
                }
                return normalizeTntList(collected);
            }
            if (raw instanceof String single) {
                return normalizeTntList(List.of(single));
            }
            return fallback;
        }

        private static List<String> normalizeTntList(List<String> values) {
            if (values == null) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
            if (normalized.isEmpty()) {
                return List.of();
            }
            return List.copyOf(normalized);
        }
    }
}
