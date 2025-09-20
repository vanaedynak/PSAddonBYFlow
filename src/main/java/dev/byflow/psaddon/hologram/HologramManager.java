package dev.byflow.psaddon.hologram;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.ProtectionStonesHook;
import dev.byflow.psaddon.RegionHandle;
import dev.byflow.psaddon.RegionHealthManager;
import dev.byflow.psaddon.config.AddonSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HologramManager {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?:&)?#([0-9a-f]{6})");
    private static final Pattern ANGLED_HEX_PATTERN = Pattern.compile("(?i)<#([0-9a-f]{6})>");

    private final PSAddonPlugin plugin;
    private final Renderer renderer;

    public HologramManager(PSAddonPlugin plugin) {
        this.plugin = plugin;
        this.renderer = detectRenderer(plugin);
    }

    public void restoreAll(RegionHealthManager healthManager, ProtectionStonesHook hook) {
        healthManager.getRegionLivesView().forEach((key, lives) ->
                hook.findRegionByKey(key).ifPresent(region -> {
                    AddonSettings.BlockSettings settings = plugin.getAddonSettings().resolve(region.getProtectBlock());
                    if (!settings.hologramEnabled()) {
                        remove(region);
                        return;
                    }
                    Block block = region.getProtectBlock();
                    healthManager.ensureBlockIndex(region, block);
                    update(region, settings, lives, settings.lives());
                })
        );
    }

    public void update(RegionHandle region, AddonSettings.BlockSettings settings, int lives, int maxLives) {
        if (!settings.hologramEnabled()) {
            return;
        }
        Location location = region.getHologramLocation(settings.hologramOffset());
        List<String> lines = formatLines(region, settings, lives, maxLives);
        renderer.update(region, location, lines);
    }

    public void remove(RegionHandle region) {
        renderer.remove(region);
    }

    public void removeAll() {
        renderer.removeAll();
    }

    private Renderer detectRenderer(PSAddonPlugin plugin) {
        Plugin dh = Bukkit.getPluginManager().getPlugin("DecentHolograms");
        if (dh != null && dh.isEnabled()) {
            try {
                return new DecentHologramRenderer();
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().warning("Failed to enable DecentHolograms integration: " + ex.getMessage());
            }
        }
        return new VanillaHologramRenderer(plugin);
    }

    private List<String> formatLines(RegionHandle region, AddonSettings.BlockSettings settings, int lives, int maxLives) {
        List<String> result = new ArrayList<>();
        Map<String, String> placeholders = buildPlaceholders(region, lives, maxLives);
        for (String template : settings.hologramLines()) {
            if (template == null) {
                continue;
            }
            String line = template;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace(entry.getKey(), entry.getValue());
            }
            line = translateHexColors(line);
            result.add(line);
        }
        return result;
    }

    private Map<String, String> buildPlaceholders(RegionHandle region, int lives, int maxLives) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{lives}", Integer.toString(lives));
        placeholders.put("{max}", Integer.toString(maxLives));
        placeholders.put("{region}", region.getId());
        placeholders.put("{world}", region.getWorld().getName());

        Block block = region.getProtectBlock();
        placeholders.put("{x}", Integer.toString(block.getX()));
        placeholders.put("{y}", Integer.toString(block.getY()));
        placeholders.put("{z}", Integer.toString(block.getZ()));
        placeholders.put("{material}", block.getType().name());

        Optional<ProtectionStonesHook.OwnerInfo> ownerInfo = region.getOwnerInfo();
        String ownerName = ownerInfo.flatMap(info -> Optional.ofNullable(info.displayName())).orElse("Неизвестно");
        String ownerUuid = ownerInfo.flatMap(info -> Optional.ofNullable(info.uuid()))
                .map(UUID::toString)
                .orElse("-");
        placeholders.put("{owner}", ownerName);
        placeholders.put("{owner_name}", ownerName);
        placeholders.put("{owner_display}", ownerName);
        placeholders.put("{owner_uuid}", ownerUuid);

        return placeholders;
    }

    private static String translateHexColors(String input) {
        String processed = replaceHexPattern(input, ANGLED_HEX_PATTERN);
        return replaceHexPattern(processed, HEX_PATTERN);
    }

    private static String replaceHexPattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase(Locale.ROOT);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private interface Renderer {
        void update(RegionHandle region, Location location, List<String> lines);

        void remove(RegionHandle region);

        void removeAll();
    }

    private static final class VanillaHologramRenderer implements Renderer {
        private final PSAddonPlugin plugin;
        private final Map<String, UUID> holograms = new HashMap<>();

        private VanillaHologramRenderer(PSAddonPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void update(RegionHandle region, Location location, List<String> lines) {
            TextDisplay display = obtain(region, location);
            List<Component> components = new ArrayList<>(lines.size());
            for (String line : lines) {
                components.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            }
            Component text = components.isEmpty()
                    ? Component.empty()
                    : Component.join(JoinConfiguration.separator(Component.newline()), components);
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setPersistent(true);
            display.setShadowed(false);
        }

        @Override
        public void remove(RegionHandle region) {
            UUID id = holograms.remove(region.getStorageKey());
            if (id == null) {
                return;
            }
            Entity entity = region.getWorld().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }

        @Override
        public void removeAll() {
            holograms.forEach((key, uuid) -> {
                for (World world : plugin.getServer().getWorlds()) {
                    Entity entity = world.getEntity(uuid);
                    if (entity != null) {
                        entity.remove();
                    }
                }
            });
            holograms.clear();
        }

        private TextDisplay obtain(RegionHandle region, Location location) {
            UUID id = holograms.get(region.getStorageKey());
            if (id != null) {
                Entity entity = region.getWorld().getEntity(id);
                if (entity instanceof TextDisplay existing) {
                    existing.teleport(location);
                    return existing;
                }
            }
            TextDisplay display = region.getWorld().spawn(location, TextDisplay.class, spawned -> {
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setBillboard(Display.Billboard.CENTER);
            });
            holograms.put(region.getStorageKey(), display.getUniqueId());
            return display;
        }
    }

    private static final class DecentHologramRenderer implements Renderer {
        private final Class<?> dhapiClass;
        private final Method createWithLines;
        private final Method createSimple;
        private final Method removeMethod;
        private final Method getMethod;
        private final Method setLinesMethod;
        private final Map<String, String> hologramNames = new HashMap<>();

        private DecentHologramRenderer() throws ReflectiveOperationException {
            this.dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            this.createWithLines = resolveMethod("createHologram", String.class, Location.class, boolean.class, List.class);
            this.createSimple = resolveMethod("createHologram", String.class, Location.class, boolean.class);
            this.removeMethod = dhapiClass.getMethod("removeHologram", String.class);
            this.getMethod = dhapiClass.getMethod("getHologram", String.class);
            this.setLinesMethod = resolveMethod("setHologramLines",
                    Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram"), List.class);
        }

        @Override
        public void update(RegionHandle region, Location location, List<String> lines) {
            String name = hologramNames.computeIfAbsent(region.getStorageKey(), key -> sanitizeName(key));
            remove(region);
            List<String> colored = new ArrayList<>(lines.size());
            for (String line : lines) {
                colored.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            try {
                if (createWithLines != null) {
                    createWithLines.invoke(null, name, location, true, colored);
                } else if (createSimple != null) {
                    Object hologram = createSimple.invoke(null, name, location, true);
                    if (hologram != null && setLinesMethod != null) {
                        setLinesMethod.invoke(null, hologram, colored);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to update DecentHolograms hologram", ex);
            }
        }

        @Override
        public void remove(RegionHandle region) {
            String name = hologramNames.remove(region.getStorageKey());
            if (name == null) {
                return;
            }
            try {
                Object hologram = getMethod.invoke(null, name);
                if (hologram != null) {
                    removeMethod.invoke(null, name);
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // ignore, hologram will be re-created on next update
            }
        }

        @Override
        public void removeAll() {
            for (String name : hologramNames.values()) {
                try {
                    removeMethod.invoke(null, name);
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
            hologramNames.clear();
        }

        private Method resolveMethod(String name, Class<?>... types) {
            try {
                return dhapiClass.getMethod(name, types);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }

        private String sanitizeName(String storageKey) {
            return storageKey.replace(':', '_').replace('/', '_');
        }
    }
}
