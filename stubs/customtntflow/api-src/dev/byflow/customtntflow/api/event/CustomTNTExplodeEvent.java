package dev.byflow.customtntflow.api.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import dev.byflow.customtntflow.api.RegionTNTType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CustomTNTExplodeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final RegionTNTType type;
    private final TNTPrimed tnt;
    private final Set<Block> blocks = new HashSet<>();
    private final Player owner;

    public CustomTNTExplodeEvent(RegionTNTType type, TNTPrimed tnt, Player owner) {
        this.type = type;
        this.tnt = tnt;
        this.owner = owner;
    }

    public RegionTNTType getType() {
        return type;
    }

    public TNTPrimed getTnt() {
        return tnt;
    }

    public Player getOwner() {
        return owner;
    }

    public Set<Block> getBlocks() {
        return Collections.unmodifiableSet(blocks);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
