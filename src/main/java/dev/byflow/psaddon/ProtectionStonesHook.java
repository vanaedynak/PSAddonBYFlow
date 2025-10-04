package dev.byflow.psaddon;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionStonesHook {
    private final Class<?> psRegionClass;
    private final Method fromLocationMethod;
    private final Method getIdMethod;
    private final Method getWorldMethod;
    private final Method getProtectBlockMethod;
    private final Method deleteRegionMethod;
    private final Class<?> psCreateEventClass;
    private final Class<?> psRemoveEventClass;
    private final Method createEventGetRegionMethod;
    private final Method removeEventGetRegionMethod;
    private final Method eventGetPlayerMethod;
    private final Method eventSetCancelledMethod;
    private final Method getRegionsByIdMethod;
    private final Method getOwnerUuidMethod;
    private final Method getOwnerNameMethod;
    private final Method getOwnersMethod;
    private final Method getWorldGuardRegionMethod;
    private final Map<Class<?>, Method> minimumPointCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> maximumPointCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, VectorAccessors> vectorAccessorCache = new ConcurrentHashMap<>();

    public ProtectionStonesHook() throws ReflectiveOperationException {
        this.psRegionClass = Class.forName("dev.espi.protectionstones.PSRegion");
        this.fromLocationMethod = psRegionClass.getMethod("fromLocationGroup", Location.class);
        this.getIdMethod = psRegionClass.getMethod("getId");
        this.getWorldMethod = psRegionClass.getMethod("getWorld");
        this.getProtectBlockMethod = psRegionClass.getMethod("getProtectBlock");
        this.deleteRegionMethod = psRegionClass.getMethod("deleteRegion", boolean.class);

        this.getOwnerUuidMethod = resolveOwnerUuidMethod(psRegionClass);
        this.getOwnerNameMethod = resolveOwnerNameMethod(psRegionClass);
        this.getOwnersMethod = resolveOwnersMethod(psRegionClass);

        this.psCreateEventClass = Class.forName("dev.espi.protectionstones.event.PSCreateEvent");
        this.psRemoveEventClass = Class.forName("dev.espi.protectionstones.event.PSRemoveEvent");
        this.createEventGetRegionMethod = resolveEventRegionAccessor(psCreateEventClass);
        this.removeEventGetRegionMethod = resolveEventRegionAccessor(psRemoveEventClass);
        Method playerMethod;
        Method cancelMethod;
        try {
            playerMethod = psCreateEventClass.getMethod("getPlayer");
        } catch (NoSuchMethodException ex) {
            playerMethod = null;
        }
        try {
            cancelMethod = psCreateEventClass.getMethod("setCancelled", boolean.class);
        } catch (NoSuchMethodException ex) {
            cancelMethod = null;
        }
        this.eventGetPlayerMethod = playerMethod;
        this.eventSetCancelledMethod = cancelMethod;

        Class<?> psMainClass = Class.forName("dev.espi.protectionstones.ProtectionStones");
        this.getRegionsByIdMethod = psMainClass.getMethod("getPSRegions", World.class, String.class);
        this.getWorldGuardRegionMethod = resolveRegionMethod();
    }

    public Optional<RegionHandle> findRegion(Location location) {
        try {
            Object region = fromLocationMethod.invoke(null, location);
            if (region == null) {
                return Optional.empty();
            }
            return Optional.of(new RegionHandle(this, region));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().severe("Failed to query ProtectionStones region: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public RegionHandle wrap(Object region) {
        if (region == null) {
            return null;
        }
        return new RegionHandle(this, region);
    }

    public Optional<RegionHandle> findRegion(World world, String regionId) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> regions = (List<Object>) getRegionsByIdMethod.invoke(null, world, regionId);
            if (regions == null || regions.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RegionHandle(this, regions.get(0)));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().severe("Failed to lookup region by id: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<RegionHandle> findRegionByKey(String key) {
        String[] parts = key.split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            UUID worldId = UUID.fromString(parts[0]);
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                return Optional.empty();
            }
            return findRegion(world, parts[1]);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean deleteRegion(RegionHandle region) {
        try {
            Object result = deleteRegionMethod.invoke(region.getHandle(), true);
            return result instanceof Boolean && (Boolean) result;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().severe("Failed to delete ProtectionStones region: " + ex.getMessage());
            return false;
        }
    }

    public String getRegionId(Object region) throws InvocationTargetException, IllegalAccessException {
        return (String) getIdMethod.invoke(region);
    }

    public World getRegionWorld(Object region) throws InvocationTargetException, IllegalAccessException {
        return (World) getWorldMethod.invoke(region);
    }

    public Block getProtectBlock(Object region) throws InvocationTargetException, IllegalAccessException {
        return (Block) getProtectBlockMethod.invoke(region);
    }

    public Optional<OwnerInfo> getOwnerInfo(Object region) {
        OwnerDetails details = new OwnerDetails();
        if (getOwnerUuidMethod != null) {
            try {
                Object value = getOwnerUuidMethod.invoke(region);
                details.accept(value);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (getOwnerNameMethod != null) {
            try {
                Object value = getOwnerNameMethod.invoke(region);
                details.accept(value);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (details.isEmpty() && getOwnersMethod != null) {
            try {
                Object value = getOwnersMethod.invoke(region);
                details.accept(value);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (details.isEmpty()) {
            populateOwnersFromWorldGuard(region, details);
        }
        UUID uuid = details.uuid;
        String rawName = details.rawName;
        if (uuid == null && rawName == null) {
            return Optional.empty();
        }
        String displayName = rawName;
        if ((displayName == null || displayName.isBlank()) && uuid != null) {
            displayName = Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName())
                    .orElse(uuid.toString());
        }
        return Optional.of(new OwnerInfo(uuid, rawName, displayName));
    }

    private void populateOwnersFromWorldGuard(Object region, OwnerDetails details) {
        if (getWorldGuardRegionMethod == null) {
            return;
        }
        try {
            Object wgRegion = getWorldGuardRegionMethod.invoke(region);
            if (wgRegion == null) {
                return;
            }
            Method ownersAccessor = findMethod(wgRegion.getClass(), "getOwners", "owners");
            if (ownersAccessor == null) {
                return;
            }
            Object ownersDomain = ownersAccessor.invoke(wgRegion);
            if (ownersDomain == null) {
                return;
            }
            Method uniqueIdsAccessor = findMethod(ownersDomain.getClass(),
                    "getUniqueIds", "getUniqueId", "uniqueIds");
            if (uniqueIdsAccessor != null) {
                try {
                    Object uniqueIds = uniqueIdsAccessor.invoke(ownersDomain);
                    details.accept(uniqueIds);
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
            if (!details.isEmpty()) {
                return;
            }
            Method playerNamesAccessor = findMethod(ownersDomain.getClass(),
                    "getPlayers", "getPlayerNames", "getNames", "players");
            if (playerNamesAccessor != null) {
                try {
                    Object players = playerNamesAccessor.invoke(ownersDomain);
                    details.accept(players);
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().warning("Failed to resolve WorldGuard owners: " + ex.getMessage());
        }
    }

    public Optional<RegionBounds> getRegionBounds(Object region) {
        if (getWorldGuardRegionMethod == null || region == null) {
            return Optional.empty();
        }
        try {
            Object wgRegion = getWorldGuardRegionMethod.invoke(region);
            if (wgRegion == null) {
                return Optional.empty();
            }
            Object minimum = invokeCachedNoArg(wgRegion, minimumPointCache,
                    "getMinimumPoint", "getMinimum", "getMinPoint");
            Object maximum = invokeCachedNoArg(wgRegion, maximumPointCache,
                    "getMaximumPoint", "getMaximum", "getMaxPoint");
            if (minimum == null || maximum == null) {
                return Optional.empty();
            }
            Coordinates min = extractCoordinates(minimum);
            Coordinates max = extractCoordinates(maximum);
            if (min == null || max == null) {
                return Optional.empty();
            }
            return Optional.of(new RegionBounds(min.x(), min.y(), min.z(), max.x(), max.y(), max.z()));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().warning("Failed to read ProtectionStones region bounds: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public Object getCreateEventRegion(Object event) {
        return extractRegionFromEvent(event, psCreateEventClass, createEventGetRegionMethod);
    }

    public void cancelCreateEvent(Object event) {
        if (eventSetCancelledMethod == null || !psCreateEventClass.isInstance(event)) {
            return;
        }
        try {
            eventSetCancelledMethod.invoke(event, true);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().warning("Failed to cancel PSCreateEvent: " + ex.getMessage());
        }
    }

    public void sendCreateEventMessage(Object event, String message) {
        if (eventGetPlayerMethod == null || !psCreateEventClass.isInstance(event)) {
            return;
        }
        try {
            Object result = eventGetPlayerMethod.invoke(event);
            if (result instanceof Player player) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().warning("Failed to message PSCreateEvent player: " + ex.getMessage());
        }
    }

    private Method resolveRegionMethod() {
        Method method = findMethod(psRegionClass, "getWGRegion", "getRegion", "getProtectedRegion");
        if (method == null) {
            Bukkit.getLogger().warning("Unable to locate WorldGuard region accessor on PSRegion; stacking checks will be limited.");
        }
        return method;
    }

    private Method findMethod(Class<?> type, String... candidates) {
        for (String name : candidates) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method resolveEventRegionAccessor(Class<?> eventClass) {
        try {
            return eventClass.getMethod("getRegion");
        } catch (NoSuchMethodException ex) {
            Bukkit.getLogger().warning("Unable to locate getRegion() on event class " + eventClass.getName());
            return null;
        }
    }

    private Object invokeCachedNoArg(Object target, Map<Class<?>, Method> cache, String... candidates)
            throws InvocationTargetException, IllegalAccessException {
        Method method = cache.computeIfAbsent(target.getClass(), clazz -> findMethod(clazz, candidates));
        if (method == null) {
            return null;
        }
        return method.invoke(target);
    }

    private Coordinates extractCoordinates(Object vector) throws InvocationTargetException, IllegalAccessException {
        if (vector == null) {
            return null;
        }
        VectorAccessors accessors = vectorAccessorCache.computeIfAbsent(vector.getClass(), this::resolveVectorAccessors);
        if (accessors == null) {
            return null;
        }
        Number x = (Number) accessors.xMethod().invoke(vector);
        Number y = (Number) accessors.yMethod().invoke(vector);
        Number z = (Number) accessors.zMethod().invoke(vector);
        return new Coordinates(x.intValue(), y.intValue(), z.intValue());
    }

    private VectorAccessors resolveVectorAccessors(Class<?> type) {
        Method x = findMethod(type, "getBlockX", "getX", "getMinX");
        Method y = findMethod(type, "getBlockY", "getY", "getMinY");
        Method z = findMethod(type, "getBlockZ", "getZ", "getMinZ");
        if (x == null || y == null || z == null) {
            Bukkit.getLogger().warning("Unable to resolve coordinate accessors for vector type " + type.getName());
            return null;
        }
        return new VectorAccessors(x, y, z);
    }

    private record Coordinates(int x, int y, int z) {
    }

    private record VectorAccessors(Method xMethod, Method yMethod, Method zMethod) {
    }

    public record RegionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public RegionBounds {
            if (minX > maxX) {
                int tmp = minX;
                minX = maxX;
                maxX = tmp;
            }
            if (minY > maxY) {
                int tmp = minY;
                minY = maxY;
                maxY = tmp;
            }
            if (minZ > maxZ) {
                int tmp = minZ;
                minZ = maxZ;
                maxZ = tmp;
            }
        }

        public boolean intersects(RegionBounds other) {
            return this.maxX >= other.minX && this.minX <= other.maxX
                    && this.maxY >= other.minY && this.minY <= other.maxY
                    && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
        }
    }

    public Object getRemoveEventRegion(Object event) {
        return extractRegionFromEvent(event, psRemoveEventClass, removeEventGetRegionMethod);
    }

    private Object extractRegionFromEvent(Object event, Class<?> expectedClass, Method accessor) {
        if (!expectedClass.isInstance(event) || accessor == null) {
            return null;
        }
        try {
            return accessor.invoke(event);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Bukkit.getLogger().severe("Failed to read ProtectionStones event region: " + ex.getMessage());
            return null;
        }
    }

    public Class<?> getPsCreateEventClass() {
        return psCreateEventClass;
    }

    public Class<?> getPsRemoveEventClass() {
        return psRemoveEventClass;
    }

    private Method resolveOwnerUuidMethod(Class<?> regionClass) {
        for (String methodName : new String[]{"getOwnerUUID", "getOwner", "getOwnerId"}) {
            try {
                Method method = regionClass.getMethod(methodName);
                if (UUID.class.isAssignableFrom(method.getReturnType())
                        || String.class.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method resolveOwnerNameMethod(Class<?> regionClass) {
        for (String methodName : new String[]{"getOwnerName", "getOwnerDisplayName"}) {
            try {
                Method method = regionClass.getMethod(methodName);
                if (String.class.isAssignableFrom(method.getReturnType())) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method resolveOwnersMethod(Class<?> regionClass) {
        return findMethod(regionClass, "getOwners", "getOwnerUUIDs", "getOwnerIds", "getOwnersUUID");
    }

    private static final class OwnerDetails {
        private UUID uuid;
        private String rawName;

        private void accept(Object value) {
            if (value == null) {
                return;
            }
            if (value instanceof Optional<?> optional) {
                optional.ifPresent(this::accept);
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    accept(element);
                    if (hasCompleteInfo()) {
                        break;
                    }
                }
                return;
            }
            if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    accept(java.lang.reflect.Array.get(value, i));
                    if (hasCompleteInfo()) {
                        break;
                    }
                }
                return;
            }
            if (value instanceof UUID u) {
                if (uuid == null) {
                    uuid = u;
                }
                return;
            }
            if (value instanceof String text) {
                if (text.isEmpty()) {
                    return;
                }
                if (uuid == null) {
                    try {
                        uuid = UUID.fromString(text);
                        return;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (rawName == null) {
                    rawName = text;
                }
                return;
            }
            if (value instanceof OfflinePlayer offlinePlayer) {
                if (uuid == null) {
                    uuid = offlinePlayer.getUniqueId();
                }
                if (rawName == null) {
                    String name = offlinePlayer.getName();
                    if (name != null && !name.isBlank()) {
                        rawName = name;
                    }
                }
                return;
            }
            UUID reflectedUuid = tryInvokeUuid(value);
            if (uuid == null && reflectedUuid != null) {
                uuid = reflectedUuid;
            }
            if (rawName == null) {
                String name = tryInvokeName(value);
                if (name != null && !name.isBlank()) {
                    rawName = name;
                    return;
                }
            }
            if (rawName == null && uuid == null) {
                rawName = value.toString();
            }
        }

        private boolean isEmpty() {
            return uuid == null && (rawName == null || rawName.isBlank());
        }

        private boolean hasCompleteInfo() {
            return uuid != null && rawName != null && !rawName.isBlank();
        }

        private UUID tryInvokeUuid(Object value) {
            for (String methodName : new String[]{"getUniqueId", "getUniqueID", "getUuid", "getUUID", "uniqueId", "uuid"}) {
                try {
                    Method method = value.getClass().getMethod(methodName);
                    Object result = method.invoke(value);
                    if (result instanceof UUID u) {
                        return u;
                    }
                    if (result instanceof String text && !text.isEmpty()) {
                        try {
                            return UUID.fromString(text);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
            return null;
        }

        private String tryInvokeName(Object value) {
            for (String methodName : new String[]{"getName", "name", "getPlayerName", "getOwnerName"}) {
                try {
                    Method method = value.getClass().getMethod(methodName);
                    Object result = method.invoke(value);
                    if (result instanceof String text && !text.isBlank()) {
                        return text;
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (IllegalAccessException | InvocationTargetException ignored) {
                }
            }
            return null;
        }
    }

    public record OwnerInfo(UUID uuid, String rawName, String displayName) {
    }
}
