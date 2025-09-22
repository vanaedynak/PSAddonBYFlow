package dev.byflow.psaddon.tnt.config;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class BlockDamageRule {
    public enum Mode {
        ALL,
        NONE,
        ALLOW_LIST,
        DENY_LIST
    }

    private final Mode mode;
    private final Set<Material> blockSet;
    private final Set<Material> forceBreak;

    public BlockDamageRule(Mode mode, Set<Material> blockSet, Set<Material> forceBreak) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.blockSet = normalize(blockSet);
        this.forceBreak = normalize(forceBreak);
    }

    private static Set<Material> normalize(Set<Material> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(input));
    }

    public Mode mode() {
        return mode;
    }

    public Set<Material> blockSet() {
        return blockSet;
    }

    public Set<Material> forceBreak() {
        return forceBreak;
    }

    public boolean canDamage(Material material) {
        return switch (mode) {
            case ALL -> true;
            case NONE -> false;
            case ALLOW_LIST -> blockSet.contains(material);
            case DENY_LIST -> !blockSet.contains(material);
        };
    }

    public boolean shouldForceBreak(Material material) {
        return forceBreak.contains(material);
    }
}
