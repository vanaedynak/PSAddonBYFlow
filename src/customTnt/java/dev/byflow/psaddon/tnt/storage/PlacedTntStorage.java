package dev.byflow.psaddon.tnt.storage;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PlacedTntStorage {
    private static final String ROOT = "placed";

    private final JavaPlugin plugin;
    private final File storageFile;
    private final YamlConfiguration configuration = new YamlConfiguration();
    private final Map<String, String> placements = new HashMap<>();

    public PlacedTntStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "placed.yml");
    }

    public void load() {
        placements.clear();
        if (!storageFile.exists()) {
            return;
        }
        try {
            configuration.load(storageFile);
            ConfigurationSection root = configuration.getConfigurationSection(ROOT);
            if (root == null) {
                return;
            }
            for (String key : root.getKeys(false)) {
                String type = root.getString(key);
                if (type != null) {
                    placements.put(key, type);
                }
            }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            plugin.getLogger().severe("Failed to load placed TNT storage: " + ex.getMessage());
        }
    }

    public void save() {
        configuration.set(ROOT, null);
        if (placements.isEmpty()) {
            try {
                configuration.save(storageFile);
            } catch (IOException ignored) {
            }
            return;
        }
        ConfigurationSection root = configuration.createSection(ROOT);
        placements.forEach(root::set);
        try {
            configuration.save(storageFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save placed TNT storage: " + ex.getMessage());
        }
    }

    public void cleanupUnloaded() {
        if (placements.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, String>> iterator = placements.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            Location location = parseLocation(entry.getKey());
            if (location == null) {
                iterator.remove();
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() != org.bukkit.Material.TNT) {
                iterator.remove();
            }
        }
    }

    public void register(Block block, String typeId) {
        placements.put(toKey(block.getLocation()), Objects.requireNonNull(typeId, "typeId"));
    }

    public Optional<String> get(Block block) {
        return Optional.ofNullable(placements.get(toKey(block.getLocation())));
    }

    public void remove(Block block) {
        placements.remove(toKey(block.getLocation()));
    }

    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(placements);
    }

    private static String toKey(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        return world.getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private Location parseLocation(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        World world = plugin.getServer().getWorld(java.util.UUID.fromString(parts[0]));
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
