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
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    private static final Pattern LEGACY_NAME_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_.+");
    private static final String HOLOGRAM_PREFIX = "psaddon_";

    private final PSAddonPlugin plugin;
    private final Renderer renderer;

    public HologramManager(PSAddonPlugin plugin) {
        this.plugin = plugin;
        this.renderer = detectRenderer(plugin);
    }

    public void restoreAll(RegionHealthManager healthManager, ProtectionStonesHook hook) {
        renderer.purgeOrphans();

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

        void purgeOrphans();
    }

    private static final class VanillaHologramRenderer implements Renderer {
        private final PSAddonPlugin plugin;
        private final Map<String, UUID> holograms = new HashMap<>();
        private final NamespacedKey storageKey;

        private VanillaHologramRenderer(PSAddonPlugin plugin) {
            this.plugin = plugin;
            this.storageKey = new NamespacedKey(plugin, "hologram");
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
            tag(display, region.getStorageKey());
        }

        @Override
        public void remove(RegionHandle region) {
            UUID id = holograms.remove(region.getStorageKey());
            if (id == null) {
                removeTagged(region.getStorageKey());
                return;
            }
            Entity entity = Bukkit.getEntity(id);
            if (entity == null) {
                entity = region.getWorld().getEntity(id);
            }
            if (entity == null) {
                for (World world : plugin.getServer().getWorlds()) {
                    entity = world.getEntity(id);
                    if (entity != null) {
                        break;
                    }
                }
            }
            if (entity != null) {
                entity.remove();
                return;
            }

            removeTagged(region.getStorageKey());
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
            purgeOrphans();
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
            tag(display, region.getStorageKey());
            return display;
        }

        @Override
        public void purgeOrphans() {
            for (World world : plugin.getServer().getWorlds()) {
                for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                    PersistentDataContainer container = display.getPersistentDataContainer();
                    if (container.has(storageKey, PersistentDataType.STRING)) {
                        display.remove();
                    }
                }
            }
            holograms.clear();
        }

        private void tag(TextDisplay display, String value) {
            display.getPersistentDataContainer().set(storageKey, PersistentDataType.STRING, value);
        }

        private void removeTagged(String storageKeyValue) {
            for (World world : plugin.getServer().getWorlds()) {
                for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                    String value = display.getPersistentDataContainer().get(this.storageKey, PersistentDataType.STRING);
                    if (storageKeyValue.equals(value)) {
                        display.remove();
                    }
                }
            }
        }
    }

    private static final class DecentHologramRenderer implements Renderer {
        private final Class<?> dhapiClass;
        private final Class<?> hologramClass;
        private final Method createWithLines;
        private final Method createSimple;
        private final Method removeMethod;
        private final Method getMethod;
        private final Method setLinesMethod;
        private final Method getAllMethod;
        private final Method getNameMethod;
        private final Map<String, String> hologramNames = new HashMap<>();

        private DecentHologramRenderer() throws ReflectiveOperationException {
            this.dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            this.hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram");
            this.createWithLines = resolveMethod("createHologram", String.class, Location.class, boolean.class, List.class);
            this.createSimple = resolveMethod("createHologram", String.class, Location.class, boolean.class);
            this.removeMethod = dhapiClass.getMethod("removeHologram", String.class);
            this.getMethod = dhapiClass.getMethod("getHologram", String.class);
            this.setLinesMethod = resolveMethod("setHologramLines", hologramClass, List.class);
            this.getAllMethod = resolveMethod("getHolograms");
            this.getNameMethod = resolveHologramMethod("getName");
        }

        @Override
        public void update(RegionHandle region, Location location, List<String> lines) {
            String storageKey = region.getStorageKey();
            String name = sanitizeName(storageKey);
            hologramNames.put(storageKey, name);
            removeByName(name);
            removeByName(legacyName(storageKey));
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
            String storageKey = region.getStorageKey();
            String name = hologramNames.remove(storageKey);
            if (name == null) {
                name = sanitizeName(storageKey);
            }
            removeByName(name);
            removeByName(legacyName(storageKey));
        }

        @Override
        public void removeAll() {
            for (String name : new ArrayList<>(hologramNames.values())) {
                removeByName(name);
            }
            hologramNames.clear();
            purgeOrphans();
        }

        @Override
        public void purgeOrphans() {
            if (getAllMethod == null || getNameMethod == null) {
                return;
            }
            try {
                Object result = getAllMethod.invoke(null);
                if (result instanceof Iterable<?> iterable) {
                    for (Object hologram : iterable) {
                        String name = (String) getNameMethod.invoke(hologram);
                        if (name != null && isAddonName(name)) {
                            removeByName(name);
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }

        private Method resolveMethod(String name, Class<?>... types) {
            try {
                return dhapiClass.getMethod(name, types);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }

        private String sanitizeName(String storageKey) {
            return HOLOGRAM_PREFIX + storageKey.replace(':', '_').replace('/', '_');
        }

        private boolean isAddonName(String name) {
            return name.startsWith(HOLOGRAM_PREFIX) || LEGACY_NAME_PATTERN.matcher(name).matches();
        }

        private String legacyName(String storageKey) {
            return storageKey.replace(':', '_').replace('/', '_');
        }

        private Method resolveHologramMethod(String name, Class<?>... types) {
            try {
                return hologramClass.getMethod(name, types);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }

        private void removeByName(String name) {
            if (name == null || name.isEmpty()) {
                return;
            }
            try {
                if (getMethod != null) {
                    try {
                        getMethod.invoke(null, name);
                    } catch (IllegalAccessException | InvocationTargetException ignored) {
                        // ignore lookup failures and attempt removal anyway
                    }
                }
                removeMethod.invoke(null, name);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
    }
}
