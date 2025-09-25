package dev.byflow.psaddon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

public final class RegionHandle {
    private final ProtectionStonesHook hook;
    private final Object handle;

    RegionHandle(ProtectionStonesHook hook, Object handle) {
        this.hook = hook;
        this.handle = handle;
    }

    public Object getHandle() {
        return handle;
    }

    public String getId() {
        try {
            return hook.getRegionId(handle);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to read region id", ex);
        }
    }

    public World getWorld() {
        try {
            return hook.getRegionWorld(handle);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to read region world", ex);
        }
    }

    public Block getProtectBlock() {
        try {
            return hook.getProtectBlock(handle);
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to access protection block", ex);
        }
    }

    public Location getHologramLocation(double offsetY) {
        Location base = getProtectBlock().getLocation().add(0.5, offsetY, 0.5);
        return base;
    }

    public Optional<ProtectionStonesHook.RegionBounds> getBounds() {
        return hook.getRegionBounds(handle);
    }

    public Optional<ProtectionStonesHook.OwnerInfo> getOwnerInfo() {
        return hook.getOwnerInfo(handle);
    }

    public String getStorageKey() {
        return getWorld().getUID() + ":" + getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionHandle that = (RegionHandle) o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getWorld(), that.getWorld());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getWorld());
    }

    @Override
    public String toString() {
        return "RegionHandle{" + getStorageKey() + "}";
    }
}
