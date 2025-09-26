package dev.byflow.psaddon.tnt;

import de.tr7zw.changeme.nbtapi.NBT;
import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class CustomTntResolver {
    private final PSAddonPlugin plugin;
    private final AddonSettings settings;
    private final NamespacedKey typeKey;
    private final NamespacedKey traitsKey;
    private final boolean nbtApiAvailable;

    public CustomTntResolver(PSAddonPlugin plugin, AddonSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.typeKey = parseKey(settings.getCustomTntTypeKey());
        this.traitsKey = parseKey(settings.getCustomTntTraitsKey());
        this.nbtApiAvailable = detectNbtApi();
    }

    public Optional<Match> resolve(TNTPrimed primed) {
        if (!settings.hasCustomTntIntegration() || typeKey == null) {
            return Optional.empty();
        }
        String typeId = readPersistentString(primed.getPersistentDataContainer(), typeKey);
        if (typeId == null) {
            typeId = readNbtString(primed, settings.getCustomTntTypeKey());
        }
        if (typeId == null || typeId.isBlank()) {
            return Optional.empty();
        }
        typeId = typeId.trim();

        List<AddonSettings.CustomTntSettings> candidates = settings.findCustomTntSettings(typeId);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<String, String> traits = Collections.emptyMap();
        if (traitsKey != null) {
            String rawTraits = readPersistentString(primed.getPersistentDataContainer(), traitsKey);
            if (rawTraits == null) {
                rawTraits = readNbtString(primed, settings.getCustomTntTraitsKey());
            }
            traits = parseTraits(rawTraits);
        }

        for (AddonSettings.CustomTntSettings candidate : candidates) {
            if (candidate.matches(typeId, traits)) {
                return Optional.of(new Match(candidate, typeId, traits));
            }
        }
        return Optional.empty();
    }

    private boolean detectNbtApi() {
        try {
            Class.forName("de.tr7zw.changeme.nbtapi.NBT");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private NamespacedKey parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] split = raw.split(":", 2);
        if (split.length != 2) {
            plugin.getLogger().warning("Invalid custom TNT key: " + raw);
            return null;
        }
        try {
            return new NamespacedKey(split[0], split[1]);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse namespaced key " + raw, ex);
            return null;
        }
    }

    private String readPersistentString(PersistentDataContainer container, NamespacedKey key) {
        if (container == null || key == null) {
            return null;
        }
        try {
            return container.get(key, PersistentDataType.STRING);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINEST, "Failed to read persistent data key " + key, throwable);
            return null;
        }
    }

    private String readNbtString(TNTPrimed primed, String path) {
        if (!nbtApiAvailable || path == null || path.isBlank()) {
            return null;
        }
        try {
            return NBT.get(primed, nbt -> nbt.getString(path));
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.FINEST, "Failed to read NBT key " + path, throwable);
            return null;
        }
    }

    private Map<String, String> parseTraits(String raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyMap();
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        List<String> entries = splitTopLevel(trimmed);
        Map<String, String> result = new HashMap<>();
        for (String entry : entries) {
            if (entry.isBlank()) {
                continue;
            }
            int colon = findTopLevelColon(entry);
            if (colon < 0) {
                continue;
            }
            String key = stripQuotes(entry.substring(0, colon).trim());
            String value = stripQuotes(entry.substring(colon + 1).trim());
            if (!key.isEmpty()) {
                result.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
        if (result.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
    }

    private List<String> splitTopLevel(String source) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        int depth = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inQuotes) {
                if (c == quoteChar && !isEscaped(source, i)) {
                    inQuotes = false;
                }
                current.append(c);
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
                current.append(c);
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private int findTopLevelColon(String source) {
        boolean inQuotes = false;
        char quoteChar = 0;
        int depth = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inQuotes) {
                if (c == quoteChar && !isEscaped(source, i)) {
                    inQuotes = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ':' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEscaped(String source, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0; i--) {
            if (source.charAt(i) == '\\') {
                backslashes++;
            } else {
                break;
            }
        }
        return (backslashes % 2) != 0;
    }

    private String stripQuotes(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            String unescaped = value.substring(1, value.length() - 1);
            return unescaped.replace("\\\"", "\"").replace("\\'", "'");
        }
        return value;
    }

    public record Match(AddonSettings.CustomTntSettings settings, String typeId, Map<String, String> traits) {
    }
}
