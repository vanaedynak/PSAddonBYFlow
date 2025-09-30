package dev.byflow.psaddon.config;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class AddonSettings {
    private final BlockSettings defaultSettings;
    private final Map<Material, BlockSettings> overrides;
    private final boolean preventStacking;
    private final boolean customTntEnabled;
    private final String customTntTypeKey;
    private final String customTntTraitsKey;
    private final Map<String, List<CustomTntSettings>> customTntSettingsByType;
    private final List<CustomTntSettings> customTntWildcard;
    private final RegionBombSettings regionBombSettings;

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

        ConfigurationSection customTntSection = configuration.getConfigurationSection("custom-tnt");
        boolean tntEnabled = false;
        String typeKey = null;
        String traitsKey = null;
        Map<String, List<CustomTntSettings>> byType = new HashMap<>();
        List<CustomTntSettings> wildcard = new ArrayList<>();
        if (customTntSection != null) {
            tntEnabled = customTntSection.getBoolean("enabled", true);
            typeKey = sanitizeKey(customTntSection.getString("type-key", "customtntflow:tnt_type"));
            traitsKey = sanitizeKey(customTntSection.getString("traits-key", "customtntflow:traits"));
            ConfigurationSection typesSection = customTntSection.getConfigurationSection("types");
            if (typesSection != null) {
                for (String key : typesSection.getKeys(false)) {
                    ConfigurationSection typeSection = typesSection.getConfigurationSection(key);
                    if (typeSection == null) {
                        continue;
                    }
                    Integer override = typeSection.isInt("damage-override")
                            ? typeSection.getInt("damage-override")
                            : null;
                    if (override != null && override <= 0) {
                        override = null;
                    }
                    boolean onlyRegions = typeSection.getBoolean("only-region-blocks", true);
                    boolean cancelEmpty = typeSection.getBoolean("cancel-when-empty", true);
                    String matchType = sanitizeKey(typeSection.getString("type", key));
                    Map<String, String> traitMatchers = readTraitMatchers(typeSection.getConfigurationSection("traits"));
                    Map<String, String> markerMatchers = readMarkerMatchers(typeSection.getConfigurationSection("nbt-markers"));

                    CustomTntSettings settings = new CustomTntSettings(matchType, traitMatchers, markerMatchers, override, onlyRegions, cancelEmpty);
                    if (matchType == null || "*".equals(matchType)) {
                        wildcard.add(settings);
                    } else {
                        String normalized = matchType.toLowerCase(Locale.ROOT);
                        byType.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(settings);
                    }
                }
            }
        }
        this.customTntEnabled = tntEnabled && (!byType.isEmpty() || !wildcard.isEmpty());
        this.customTntTypeKey = typeKey;
        this.customTntTraitsKey = traitsKey;
        this.customTntSettingsByType = copySettings(byType);
        this.customTntWildcard = List.copyOf(wildcard);

        ConfigurationSection regionBombSection = configuration.getConfigurationSection("region-bomb");
        if (regionBombSection == null) {
            this.regionBombSettings = RegionBombSettings.disabled();
        } else {
            boolean enabled = regionBombSection.getBoolean("enabled", false);
            double radius = Math.max(0D, regionBombSection.getDouble("radius", 6D));
            int fuseTicks = Math.max(0, regionBombSection.getInt("fuse-ticks", 60));
            int damage = Math.max(1, regionBombSection.getInt("damage", 1));
            String displayName = regionBombSection.getString("display-name", "&cРегионный динамит");
            List<String> lore = readLore(regionBombSection.get("lore"));
            this.regionBombSettings = new RegionBombSettings(enabled, radius, fuseTicks, damage, displayName, lore);
        }
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

    public boolean hasCustomTntIntegration() {
        return customTntEnabled;
    }

    public String getCustomTntTypeKey() {
        return customTntTypeKey;
    }

    public String getCustomTntTraitsKey() {
        return customTntTraitsKey;
    }

    public List<CustomTntSettings> findCustomTntSettings(String typeId) {
        if (!customTntEnabled) {
            return List.of();
        }
        List<CustomTntSettings> direct = typeId != null
                ? customTntSettingsByType.get(typeId.toLowerCase(Locale.ROOT))
                : null;
        if ((direct == null || direct.isEmpty()) && customTntWildcard.isEmpty()) {
            return List.of();
        }
        if (direct == null || direct.isEmpty()) {
            return customTntWildcard;
        }
        if (customTntWildcard.isEmpty()) {
            return direct;
        }
        List<CustomTntSettings> result = new ArrayList<>(direct.size() + customTntWildcard.size());
        result.addAll(direct);
        result.addAll(customTntWildcard);
        return List.copyOf(result);
    }

    public RegionBombSettings getRegionBombSettings() {
        return regionBombSettings;
    }

    public record BlockSettings(
            int lives,
            int damagePerExplosion,
            boolean tntOnly,
            boolean hologramEnabled,
            double hologramOffset,
            List<String> hologramLines
    ) {
        private static final List<String> DEFAULT_HOLOGRAM_LINES =
                List.of("&cЖизни привата: &f{lives}&7/&f{max}");

        public BlockSettings {
            lives = Math.max(1, lives);
            damagePerExplosion = Math.max(1, damagePerExplosion);
            hologramLines = hologramLines == null || hologramLines.isEmpty()
                    ? DEFAULT_HOLOGRAM_LINES
                    : List.copyOf(hologramLines);
        }

        static BlockSettings from(ConfigurationSection section, BlockSettings fallback) {
            int lives = section.getInt("lives", fallback != null ? fallback.lives : 3);
            int damage = section.getInt("damage-per-explosion", fallback != null ? fallback.damagePerExplosion : 1);
            boolean tntOnly = section.getBoolean("tnt-only", fallback != null && fallback.tntOnly);

            boolean hologramEnabled = fallback != null ? fallback.hologramEnabled : true;
            double hologramOffset = fallback != null ? fallback.hologramOffset : 1.8;
            List<String> hologramLines = fallback != null ? fallback.hologramLines : DEFAULT_HOLOGRAM_LINES;

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

            return new BlockSettings(lives, damage, tntOnly, hologramEnabled, hologramOffset, hologramLines);
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

    }

    public record CustomTntSettings(
            String matchType,
            Map<String, String> traitMatchers,
            Map<String, String> markerMatchers,
            Integer damageOverride,
            boolean onlyRegionBlocks,
            boolean cancelWhenEmpty
    ) {
        public int resolveDamage(BlockSettings blockSettings) {
            if (damageOverride != null && damageOverride > 0) {
                return damageOverride;
            }
            return blockSettings.damagePerExplosion();
        }

        public boolean matches(String typeId, Map<String, String> traits, MarkerValueProvider markerProvider) {
            if (matchType != null && typeId != null && !matchType.equalsIgnoreCase(typeId)) {
                return false;
            }
            if (matchType != null && typeId == null) {
                return false;
            }
            if (traitMatchers.isEmpty()) {
                if (markerMatchers.isEmpty()) {
                    return true;
                }
            } else {
                if (traits == null || traits.isEmpty()) {
                    return false;
                }
                for (Map.Entry<String, String> entry : traitMatchers.entrySet()) {
                    String actual = traits.get(entry.getKey());
                    if (actual == null) {
                        return false;
                    }
                    if (!compareTrait(entry.getValue(), actual)) {
                        return false;
                    }
                }
            }

            if (markerMatchers.isEmpty()) {
                return true;
            }

            for (Map.Entry<String, String> entry : markerMatchers.entrySet()) {
                String actual = markerProvider != null ? markerProvider.get(entry.getKey()) : null;
                if (actual == null) {
                    return false;
                }
                if (!compareTrait(entry.getValue(), actual)) {
                    return false;
                }
            }
            return true;
        }

        public boolean requiresMarkers() {
            return !markerMatchers.isEmpty();
        }

        private boolean compareTrait(String expected, String actual) {
            if (expected == null) {
                return actual == null;
            }
            if (actual == null) {
                return false;
            }
            if (expected.equalsIgnoreCase(actual)) {
                return true;
            }
            String trimmedExpected = expected.trim();
            String trimmedActual = actual.trim();
            if (isBoolean(trimmedExpected) || isBoolean(trimmedActual)) {
                return Boolean.parseBoolean(trimmedExpected) == Boolean.parseBoolean(trimmedActual);
            }
            try {
                double expectedNumber = Double.parseDouble(trimmedExpected);
                double actualNumber = Double.parseDouble(trimmedActual);
                return Double.compare(expectedNumber, actualNumber) == 0;
            } catch (NumberFormatException ignored) {
                // ignore
            }
            return trimmedExpected.equalsIgnoreCase(trimmedActual);
        }

        private boolean isBoolean(String value) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        }

        @FunctionalInterface
        public interface MarkerValueProvider {
            String get(String key);
        }
    }

    private static Map<String, List<CustomTntSettings>> copySettings(Map<String, List<CustomTntSettings>> source) {
        Map<String, List<CustomTntSettings>> result = new HashMap<>();
        for (Map.Entry<String, List<CustomTntSettings>> entry : source.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> readTraitMatchers(ConfigurationSection traitsSection) {
        if (traitsSection == null) {
            return Map.of();
        }
        Map<String, String> traits = new HashMap<>();
        for (String key : traitsSection.getKeys(false)) {
            Object value = traitsSection.get(key);
            if (value == null) {
                continue;
            }
            traits.put(key.toLowerCase(Locale.ROOT), value.toString());
        }
        return traits.isEmpty() ? Map.of() : Map.copyOf(traits);
    }

    private static Map<String, String> readMarkerMatchers(ConfigurationSection markersSection) {
        if (markersSection == null) {
            return Map.of();
        }
        Map<String, String> markers = new HashMap<>();
        for (String key : markersSection.getKeys(false)) {
            Object value = markersSection.get(key);
            if (value == null) {
                continue;
            }
            markers.put(key, value.toString());
        }
        return markers.isEmpty() ? Map.of() : Map.copyOf(markers);
    }

    private static String sanitizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> readLore(Object rawLore) {
        if (rawLore instanceof List<?> rawList) {
            List<String> lore = new ArrayList<>();
            for (Object entry : rawList) {
                if (entry != null) {
                    lore.add(entry.toString().replace("\r", ""));
                }
            }
            return lore.isEmpty() ? List.of() : List.copyOf(lore);
        }
        if (rawLore instanceof String text) {
            String prepared = text.replace("\\n", "\n");
            String[] split = prepared.split("\n");
            List<String> lore = new ArrayList<>(split.length);
            Collections.addAll(lore, split);
            return lore.isEmpty() ? List.of() : List.copyOf(lore);
        }
        return List.of();
    }

    public record RegionBombSettings(
            boolean enabled,
            double radius,
            int fuseTicks,
            int damage,
            String displayName,
            List<String> lore
    ) {
        public RegionBombSettings {
            radius = Math.max(0D, radius);
            fuseTicks = Math.max(0, fuseTicks);
            damage = Math.max(1, damage);
            displayName = displayName == null ? "" : displayName;
            lore = lore == null ? List.of() : List.copyOf(lore);
        }

        public static RegionBombSettings disabled() {
            return new RegionBombSettings(false, 0D, 60, 1, "", List.of());
        }
    }
}
