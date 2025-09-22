package dev.byflow.psaddon.tnt.config;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CustomTntType {
    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final Integer customModelData;
    private final boolean autoIgniteWhenPlaced;
    private final boolean dropWhenBroken;
    private final boolean igniteInWater;
    private final int fuseTicks;
    private final float explosionPower;
    private final boolean affectsRegions;
    private final boolean onlyDamagesRegions;
    private final BlockDamageRule blockDamageRule;

    public CustomTntType(
            String id,
            Material material,
            String displayName,
            List<String> lore,
            Integer customModelData,
            boolean autoIgniteWhenPlaced,
            boolean dropWhenBroken,
            boolean igniteInWater,
            int fuseTicks,
            float explosionPower,
            boolean affectsRegions,
            boolean onlyDamagesRegions,
            BlockDamageRule blockDamageRule
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.material = Objects.requireNonNull(material, "material");
        this.displayName = displayName;
        this.lore = lore == null ? Collections.emptyList() : List.copyOf(lore);
        this.customModelData = customModelData;
        this.autoIgniteWhenPlaced = autoIgniteWhenPlaced;
        this.dropWhenBroken = dropWhenBroken;
        this.igniteInWater = igniteInWater;
        this.fuseTicks = Math.max(1, fuseTicks);
        this.explosionPower = Math.max(0.1f, explosionPower);
        this.affectsRegions = affectsRegions;
        this.onlyDamagesRegions = onlyDamagesRegions;
        this.blockDamageRule = Objects.requireNonNull(blockDamageRule, "blockDamageRule");
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public boolean autoIgniteWhenPlaced() {
        return autoIgniteWhenPlaced;
    }

    public boolean dropWhenBroken() {
        return dropWhenBroken;
    }

    public boolean igniteInWater() {
        return igniteInWater;
    }

    public int fuseTicks() {
        return fuseTicks;
    }

    public float explosionPower() {
        return explosionPower;
    }

    public boolean affectsRegions() {
        return affectsRegions;
    }

    public boolean onlyDamagesRegions() {
        return onlyDamagesRegions;
    }

    public BlockDamageRule blockDamageRule() {
        return blockDamageRule;
    }
}
