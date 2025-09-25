package dev.byflow.customtntflow.api;

import java.util.Optional;

import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;

public final class RegionTNTAPI {
    private static final RegionTNTAPI INSTANCE = new RegionTNTAPI();

    private RegionTNTAPI() {
    }

    public static RegionTNTAPI get() {
        return INSTANCE;
    }

    public boolean isCustom(TNTPrimed tnt) {
        return false;
    }

    public RegionTNTType getType(TNTPrimed tnt) {
        return null;
    }

    public Optional<RegionTNTType> findType(ItemStack itemStack) {
        return Optional.empty();
    }
}
