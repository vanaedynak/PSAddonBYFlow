package org.bukkit.event;

public abstract class Event {
    public Event() {
    }

    public Event(boolean isAsync) {
    }

    public abstract HandlerList getHandlers();
}
