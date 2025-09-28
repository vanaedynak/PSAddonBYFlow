package dev.byflow.psaddon.tnt;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.config.AddonSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CustomTntResolver {
    private final PSAddonPlugin plugin;
    private final AddonSettings settings;
    private final NamespacedKey typeKey;
    private final NamespacedKey traitsKey;
    private final NbtApiBridge nbtApi;

    public CustomTntResolver(PSAddonPlugin plugin, AddonSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.typeKey = parseKey(settings.getCustomTntTypeKey());
        this.traitsKey = parseKey(settings.getCustomTntTraitsKey());
        this.nbtApi = NbtApiBridge.create(plugin);
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

        MarkerValueCache markerCache = new MarkerValueCache(primed);

        for (AddonSettings.CustomTntSettings candidate : candidates) {
            if (candidate.matches(typeId, traits, markerCache)) {
                return Optional.of(new Match(candidate, typeId, traits));
            }
        }
        return Optional.empty();
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
        if (nbtApi == null || path == null || path.isBlank()) {
            return null;
        }
        return nbtApi.readString(primed, path);
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

    private String readMarkerValue(TNTPrimed primed, String key) {
        if (nbtApi == null || key == null || key.isBlank()) {
            return null;
        }
        return nbtApi.readMarkerValue(primed, key);
    }

    private final class MarkerValueCache implements AddonSettings.CustomTntSettings.MarkerValueProvider {
        private final TNTPrimed primed;
        private final Map<String, String> cache = new HashMap<>();

        private MarkerValueCache(TNTPrimed primed) {
            this.primed = primed;
        }

        @Override
        public String get(String key) {
            if (key == null) {
                return null;
            }
            return cache.computeIfAbsent(key, ignored -> readMarkerValue(primed, key));
        }
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

    private static final class NbtApiBridge {
        private final Logger logger;
        private final Method nbtGetMethod;
        private final Method readableGetStringMethod;
        private final Class<?> compoundClass;
        private final Method compoundHasTagMethod;
        private final Method compoundGetTypeMethod;
        private final Method compoundGetStringMethod;
        private final Method compoundGetIntegerMethod;
        private final Method compoundGetShortMethod;
        private final Method compoundGetLongMethod;
        private final Method compoundGetFloatMethod;
        private final Method compoundGetDoubleMethod;
        private final Method compoundGetByteMethod;
        private final Method compoundGetBooleanMethod;
        private final Method compoundGetByteArrayMethod;
        private final Method compoundGetIntArrayMethod;
        private final Method compoundGetLongArrayMethod;

        private NbtApiBridge(Logger logger) throws ClassNotFoundException, NoSuchMethodException {
            this.logger = logger;

            Class<?> nbtClass = Class.forName("de.tr7zw.changeme.nbtapi.NBT");
            Class<?> readableClass = Class.forName("de.tr7zw.changeme.nbtapi.iface.ReadableNBT");
            this.compoundClass = Class.forName("de.tr7zw.changeme.nbtapi.NBTCompound");

            this.nbtGetMethod = nbtClass.getMethod("get", org.bukkit.entity.Entity.class, Function.class);
            this.readableGetStringMethod = readableClass.getMethod("getString", String.class);
            this.compoundHasTagMethod = compoundClass.getMethod("hasTag", String.class);
            this.compoundGetTypeMethod = compoundClass.getMethod("getType", String.class);
            this.compoundGetStringMethod = compoundClass.getMethod("getString", String.class);
            this.compoundGetIntegerMethod = compoundClass.getMethod("getInteger", String.class);
            this.compoundGetShortMethod = compoundClass.getMethod("getShort", String.class);
            this.compoundGetLongMethod = compoundClass.getMethod("getLong", String.class);
            this.compoundGetFloatMethod = compoundClass.getMethod("getFloat", String.class);
            this.compoundGetDoubleMethod = compoundClass.getMethod("getDouble", String.class);
            this.compoundGetByteMethod = compoundClass.getMethod("getByte", String.class);
            this.compoundGetBooleanMethod = findOptionalMethod(compoundClass, "getBoolean", String.class);
            this.compoundGetByteArrayMethod = compoundClass.getMethod("getByteArray", String.class);
            this.compoundGetIntArrayMethod = compoundClass.getMethod("getIntArray", String.class);
            this.compoundGetLongArrayMethod = compoundClass.getMethod("getLongArray", String.class);
        }

        private static Method findOptionalMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
            try {
                return owner.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        static NbtApiBridge create(PSAddonPlugin plugin) {
            try {
                return new NbtApiBridge(plugin.getLogger());
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.FINER, "NBT-API not present; skipping advanced custom TNT lookups.");
                return null;
            }
        }

        private String readString(TNTPrimed primed, String path) {
            return call(primed, nbt -> invokeString(readableGetStringMethod, nbt, path));
        }

        private String readMarkerValue(TNTPrimed primed, String key) {
            return call(primed, nbt -> compoundClass.isInstance(nbt) ? readMarkerFromCompound(nbt, key) : null);
        }

        private String call(TNTPrimed primed, Function<Object, String> extractor) {
            try {
                Function<Object, Object> function = new Function<>() {
                    @Override
                    public Object apply(Object argument) {
                        try {
                            return extractor.apply(argument);
                        } catch (RuntimeException ex) {
                            logger.log(Level.FINEST, "Failed to process NBT payload", ex);
                            return null;
                        }
                    }
                };
                Object result = nbtGetMethod.invoke(null, primed, function);
                return result instanceof String string ? string : (result != null ? result.toString() : null);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                logger.log(Level.FINEST, "Failed to access NBT data", ex);
                return null;
            }
        }

        private String readMarkerFromCompound(Object compound, String key) {
            if (compound == null || key == null || !hasTag(compound, key)) {
                return null;
            }
            Object type = invoke(compoundGetTypeMethod, compound, key);
            if (type == null) {
                return safeGetString(compound, key);
            }
            String typeName = type instanceof Enum<?> enumType ? enumType.name() : type.toString();
            return switch (typeName) {
                case "NBTTagString" -> safeGetString(compound, key);
                case "NBTTagInt" -> numberToString(invoke(compoundGetIntegerMethod, compound, key));
                case "NBTTagShort" -> numberToString(invoke(compoundGetShortMethod, compound, key));
                case "NBTTagLong" -> numberToString(invoke(compoundGetLongMethod, compound, key));
                case "NBTTagFloat" -> numberToString(invoke(compoundGetFloatMethod, compound, key));
                case "NBTTagDouble" -> numberToString(invoke(compoundGetDoubleMethod, compound, key));
                case "NBTTagByte" -> readByte(compound, key);
                case "NBTTagByteArray" -> arrayToString(invoke(compoundGetByteArrayMethod, compound, key));
                case "NBTTagIntArray" -> arrayToString(invoke(compoundGetIntArrayMethod, compound, key));
                case "NBTTagLongArray" -> arrayToString(invoke(compoundGetLongArrayMethod, compound, key));
                default -> null;
            };
        }

        private boolean hasTag(Object compound, String key) {
            Object result = invoke(compoundHasTagMethod, compound, key);
            return result instanceof Boolean bool && bool;
        }

        private String readByte(Object compound, String key) {
            if (compoundGetBooleanMethod != null) {
                Object bool = invoke(compoundGetBooleanMethod, compound, key);
                if (bool instanceof Boolean) {
                    return Boolean.toString((Boolean) bool);
                }
            }
            Object value = invoke(compoundGetByteMethod, compound, key);
            if (value instanceof Byte byteValue) {
                return Byte.toString(byteValue);
            }
            return null;
        }

        private String safeGetString(Object compound, String key) {
            Object value = invoke(compoundGetStringMethod, compound, key);
            return value != null ? value.toString() : null;
        }

        private String numberToString(Object value) {
            return value instanceof Number number ? number.toString() : null;
        }

        private String arrayToString(Object value) {
            if (value instanceof byte[] bytes) {
                return Arrays.toString(bytes);
            }
            if (value instanceof int[] ints) {
                return Arrays.toString(ints);
            }
            if (value instanceof long[] longs) {
                return Arrays.toString(longs);
            }
            return null;
        }

        private String invokeString(Method method, Object target, String argument) {
            Object value = invoke(method, target, argument);
            return value != null ? value.toString() : null;
        }

        private Object invoke(Method method, Object target, String argument) {
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(target, argument);
            } catch (IllegalAccessException | InvocationTargetException ex) {
                logger.log(Level.FINEST, "Failed to invoke NBT method " + method.getName(), ex);
                return null;
            }
        }
    }
}
