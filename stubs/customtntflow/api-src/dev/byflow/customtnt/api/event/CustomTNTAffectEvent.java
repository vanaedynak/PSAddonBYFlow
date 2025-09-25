package dev.byflow.customtnt.api.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.byflow.customtnt.api.RegionTNTType;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CustomTNTAffectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RegionTNTType type;
    private final TNTPrimed tnt;
    private final Set<Block> blocks = new HashSet<>();
    private boolean cancelled;

    public CustomTNTAffectEvent(RegionTNTType type, TNTPrimed tnt) {
        this.type = type;
        this.tnt = tnt;
    }

    public RegionTNTType getType() {
        return type;
    }

    public TNTPrimed getTnt() {
        return tnt;
    }

    public Set<Block> getBlocks() {
        return blocks;
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
