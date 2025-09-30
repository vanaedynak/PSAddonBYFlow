package dev.byflow.psaddon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RegionHealthManager {
    private static final String REGIONS_ROOT = "regions";

    private final JavaPlugin plugin;
    private final ProtectionStonesHook hook;
    private final Map<String, RegionRecord> regionRecords = new HashMap<>();
    private final Map<String, String> blockIndex = new HashMap<>();
    private final File storageFile;
    private final YamlConfiguration storageConfig = new YamlConfiguration();

    public RegionHealthManager(JavaPlugin plugin, ProtectionStonesHook hook) {
        this.plugin = plugin;
        this.hook = hook;
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        this.storageFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public static String toBlockKey(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return toBlockKey(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static String toBlockKey(World world, int x, int y, int z) {
        return world.getUID() + ":" + x + ":" + y + ":" + z;
    }

    public void load() {
        regionRecords.clear();
        blockIndex.clear();
        if (!storageFile.exists()) {
            return;
        }
        try {
            storageConfig.load(storageFile);
            ConfigurationSection regions = storageConfig.getConfigurationSection(REGIONS_ROOT);
            if (regions == null) {
                return;
            }
            for (String key : regions.getKeys(false)) {
                String path = REGIONS_ROOT + "." + key;
                if (storageConfig.isInt(path)) {
                    int lives = storageConfig.getInt(path);
                    RegionRecord record = new RegionRecord(lives, null);
                    regionRecords.put(key, record);
                } else {
                    ConfigurationSection section = storageConfig.getConfigurationSection(path);
                    if (section == null) {
                        continue;
                    }
                    int lives = section.getInt("lives", 3);
                    String block = section.getString("block", null);
                    RegionRecord record = new RegionRecord(lives, block);
                    regionRecords.put(key, record);
                    if (block != null) {
                        blockIndex.put(block, key);
                    }
                }
            }
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Unable to load data.yml: " + ex.getMessage());
        }
    }

    public void save() {
        storageConfig.set(REGIONS_ROOT, null);
        regionRecords.forEach((key, record) -> {
            String path = REGIONS_ROOT + "." + key;
            storageConfig.set(path + ".lives", record.lives());
            storageConfig.set(path + ".block", record.blockKey());
        });
        try {
            storageConfig.save(storageFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Unable to save data.yml: " + ex.getMessage());
        }
    }

    public RegistrationResult registerRegion(RegionHandle region, int defaultLives, Block block, boolean preventStacking) {
        String blockKey = block != null ? toBlockKey(block.getLocation()) : null;
        String storageKey = region.getStorageKey();
        ProtectionStonesHook.RegionBounds bounds = preventStacking
                ? region.getBounds().orElse(null)
                : null;
        if (preventStacking && blockKey != null) {
            String existingRegion = blockIndex.get(blockKey);
            if (existingRegion != null && !Objects.equals(existingRegion, storageKey)) {
                return RegistrationResult.conflict(existingRegion);
            }
            if (block != null) {
                String conflict = findAdjacentConflict(block, storageKey);
                if (conflict != null) {
                    return RegistrationResult.conflict(conflict);
                }
            }
        }
        if (preventStacking && bounds != null) {
            String overlapping = findBoundsConflict(storageKey, bounds);
            if (overlapping != null) {
                return RegistrationResult.conflict(overlapping);
            }
        }

        RegionRecord record = regionRecords.computeIfAbsent(storageKey, key -> new RegionRecord(defaultLives, blockKey));
        if (blockKey != null && !Objects.equals(blockKey, record.blockKey())) {
            record = record.withBlock(blockKey);
            regionRecords.put(storageKey, record);
        }
        if (blockKey != null) {
            blockIndex.put(blockKey, storageKey);
        }
        save();
        return RegistrationResult.accepted(record.lives());
    }

    public void ensureBlockIndex(RegionHandle region, Block block) {
        String storageKey = region.getStorageKey();
        RegionRecord record = regionRecords.get(storageKey);
        if (record == null) {
            return;
        }
        if (block == null) {
            return;
        }
        String blockKey = toBlockKey(block.getLocation());
        if (!Objects.equals(blockKey, record.blockKey())) {
            regionRecords.put(storageKey, record.withBlock(blockKey));
            blockIndex.put(blockKey, storageKey);
            save();
        }
    }

    public int getLives(RegionHandle region, int defaultLives) {
        RegionRecord record = regionRecords.get(region.getStorageKey());
        if (record == null) {
            return defaultLives;
        }
        return record.lives();
    }

    public int damageRegion(RegionHandle region, int amount, int defaultLives) {
        String storageKey = region.getStorageKey();
        RegionRecord record = regionRecords.computeIfAbsent(storageKey, key -> new RegionRecord(defaultLives, null));
        int next = Math.max(0, record.lives() - amount);
        RegionRecord updated = record.withLives(next);
        regionRecords.put(storageKey, updated);
        if (updated.blockKey() != null) {
            blockIndex.put(updated.blockKey(), storageKey);
        }
        save();
        return next;
    }

    public void resetRegion(RegionHandle region, int lives, String blockKey) {
        String storageKey = region.getStorageKey();
        RegionRecord record = new RegionRecord(lives, blockKey);
        regionRecords.put(storageKey, record);
        if (blockKey != null) {
            blockIndex.put(blockKey, storageKey);
        }
        save();
    }

    public void removeRegion(RegionHandle region) {
        String storageKey = region.getStorageKey();
        RegionRecord record = regionRecords.remove(storageKey);
        if (record != null && record.blockKey() != null) {
            blockIndex.remove(record.blockKey());
        }
        save();
    }

    public Map<String, Integer> getRegionLivesView() {
        Map<String, Integer> result = new HashMap<>();
        regionRecords.forEach((key, value) -> result.put(key, value.lives()));
        return Map.copyOf(result);
    }

    public Optional<String> getRegionKeyByBlock(String blockKey) {
        return Optional.ofNullable(blockIndex.get(blockKey));
    }

    public List<RegionHandle> findRegionsWithinRadius(Location center, double radius) {
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        double maxDistanceSquared = Math.max(0D, radius) * Math.max(0D, radius);
        List<RegionHandle> result = new ArrayList<>();
        regionRecords.forEach((key, record) -> {
            Optional<RegionHandle> handleOptional = hook.findRegionByKey(key);
            if (handleOptional.isEmpty()) {
                return;
            }
            RegionHandle handle = handleOptional.get();
            Block block = handle.getProtectBlock();
            if (block == null || block.getWorld() == null) {
                return;
            }
            if (!Objects.equals(block.getWorld(), center.getWorld())) {
                return;
            }
            double dx = (block.getX() + 0.5) - center.getX();
            double dy = (block.getY() + 0.5) - center.getY();
            double dz = (block.getZ() + 0.5) - center.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= maxDistanceSquared) {
                result.add(handle);
            }
        });
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private String findAdjacentConflict(Block block, String storageKey) {
        World world = block.getWorld();
        int baseX = block.getX();
        int baseY = block.getY();
        int baseZ = block.getZ();
        int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };
        for (int[] offset : offsets) {
            String key = toBlockKey(world, baseX + offset[0], baseY + offset[1], baseZ + offset[2]);
            String regionKey = blockIndex.get(key);
            if (regionKey != null && !Objects.equals(regionKey, storageKey)) {
                return regionKey;
            }
        }
        return null;
    }

    private String findBoundsConflict(String storageKey, ProtectionStonesHook.RegionBounds bounds) {
        for (String existingKey : regionRecords.keySet()) {
            if (Objects.equals(existingKey, storageKey)) {
                continue;
            }
            Optional<RegionHandle> handle = hook.findRegionByKey(existingKey);
            if (handle.isEmpty()) {
                continue;
            }
            Optional<ProtectionStonesHook.RegionBounds> otherBounds = handle.get().getBounds();
            if (otherBounds.isEmpty()) {
                continue;
            }
            if (bounds.intersects(otherBounds.get())) {
                return existingKey;
            }
        }
        return null;
    }

    public record RegistrationResult(boolean accepted, Integer lives, String conflictingRegionKey) {
        static RegistrationResult accepted(int lives) {
            return new RegistrationResult(true, lives, null);
        }

        static RegistrationResult conflict(String otherRegionKey) {
            return new RegistrationResult(false, null, otherRegionKey);
        }
    }

    private record RegionRecord(int lives, String blockKey) {
        RegionRecord withLives(int lives) {
            return new RegionRecord(lives, blockKey);
        }

        RegionRecord withBlock(String blockKey) {
            return new RegionRecord(lives, blockKey);
        }
    }
}
