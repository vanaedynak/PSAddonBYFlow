package dev.byflow.psaddon;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ProtectionStonesHook {
    private final Class<?> psRegionClass;
    private final Method fromLocationMethod;
    private final Method getIdMethod;
    private final Method getWorldMethod;
    private final Method getProtectBlockMethod;
    private final Method deleteRegionMethod;
    private final Class<?> psCreateEventClass;
    private final Class<?> psRemoveEventClass;
    private final Method eventGetRegionMethod;
    private final Method eventGetPlayerMethod;
    private final Method eventSetCancelledMethod;
    private final Method getRegionsByIdMethod;
    private final Method getOwnerUuidMethod;
    private final Method getOwnerNameMethod;

    public ProtectionStonesHook() throws ReflectiveOperationException {
        this.psRegionClass = Class.forName("dev.espi.protectionstones.PSRegion");
        this.fromLocationMethod = psRegionClass.getMethod("fromLocationGroup", Location.class);
        this.getIdMethod = psRegionClass.getMethod("getId");
        this.getWorldMethod = psRegionClass.getMethod("getWorld");
        this.getProtectBlockMethod = psRegionClass.getMethod("getProtectBlock");
        this.deleteRegionMethod = psRegionClass.getMethod("deleteRegion", boolean.class);

        this.getOwnerUuidMethod = resolveOwnerUuidMethod(psRegionClass);
        this.getOwnerNameMethod = resolveOwnerNameMethod(psRegionClass);

        this.psCreateEventClass = Class.forName("dev.espi.protectionstones.event.PSCreateEvent");
        this.psRemoveEventClass = Class.forName("dev.espi.protectionstones.event.PSRemoveEvent");
        this.eventGetRegionMethod = psCreateEventClass.getMethod("getRegion");
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
        UUID uuid = null;
        String rawName = null;
        if (getOwnerUuidMethod != null) {
            try {
                Object value = getOwnerUuidMethod.invoke(region);
                if (value instanceof UUID u) {
                    uuid = u;
                } else if (value instanceof String text && !text.isEmpty()) {
                    try {
                        uuid = UUID.fromString(text);
                    } catch (IllegalArgumentException ignored) {
                        rawName = text;
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        if (getOwnerNameMethod != null) {
            try {
                Object value = getOwnerNameMethod.invoke(region);
                if (value != null) {
                    rawName = value.toString();
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
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

    public Object getCreateEventRegion(Object event) {
        return extractRegionFromEvent(event, psCreateEventClass);
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

    public Object getRemoveEventRegion(Object event) {
        return extractRegionFromEvent(event, psRemoveEventClass);
    }

    private Object extractRegionFromEvent(Object event, Class<?> expectedClass) {
        if (!expectedClass.isInstance(event)) {
            return null;
        }
        try {
            return eventGetRegionMethod.invoke(event);
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

    public record OwnerInfo(UUID uuid, String rawName, String displayName) {
    }
}
