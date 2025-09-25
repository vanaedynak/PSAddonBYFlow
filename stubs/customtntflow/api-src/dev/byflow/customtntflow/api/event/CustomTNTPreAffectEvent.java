package dev.byflow.customtntflow.api.event;

import dev.byflow.customtntflow.api.MutableBlockBehavior;
import dev.byflow.customtntflow.api.RegionTNTType;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CustomTNTPreAffectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RegionTNTType type;
    private final TNTPrimed tnt;
    private final MutableBlockBehavior behavior;
    private boolean cancelled;

    public CustomTNTPreAffectEvent(RegionTNTType type, TNTPrimed tnt) {
        this.type = type;
        this.tnt = tnt;
        this.behavior = new MutableBlockBehavior();
    }

    public RegionTNTType getType() {
        return type;
    }

    public TNTPrimed getTnt() {
        return tnt;
    }

    public MutableBlockBehavior getBehavior() {
        return behavior;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
