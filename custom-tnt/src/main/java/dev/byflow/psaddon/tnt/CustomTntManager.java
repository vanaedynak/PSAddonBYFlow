package dev.byflow.psaddon.tnt;

import dev.byflow.psaddon.tnt.config.CustomTntType;
import dev.byflow.psaddon.tnt.config.TntConfiguration;
import dev.byflow.psaddon.tnt.storage.PlacedTntStorage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CustomTntManager {
    public static final NamespacedKey TNT_ID_KEY = Objects.requireNonNull(NamespacedKey.fromString("psaddon:custom_tnt_id"));
    private static final NamespacedKey TNT_FLAGS_KEY = Objects.requireNonNull(NamespacedKey.fromString("psaddon:custom_tnt_flags"));
    private static final int FLAG_AFFECTS_REGIONS = 0x1;

    private final Map<String, CustomTntType> types;
    private final PlacedTntStorage storage;

    public CustomTntManager(TntConfiguration configuration, PlacedTntStorage storage) {
        this.types = configuration.types();
        this.storage = storage;
    }

    public Optional<CustomTntType> getType(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public Collection<CustomTntType> getTypes() {
        return types.values();
    }

    public Optional<CustomTntType> fromItem(ItemStack itemStack) {
        if (itemStack == null) {
            return Optional.empty();
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String typeId = container.get(TNT_ID_KEY, PersistentDataType.STRING);
        if (typeId == null) {
            return Optional.empty();
        }
        return getType(typeId);
    }

    public ItemStack createItem(CustomTntType type, int amount) {
        ItemStack itemStack = new ItemStack(type.material(), Math.max(1, amount));
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            if (type.displayName() != null && !type.displayName().isBlank()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.displayName()));
            }
            if (!type.lore().isEmpty()) {
                meta.setLore(type.lore().stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .toList());
            }
            if (type.customModelData() != null) {
                meta.setCustomModelData(type.customModelData());
            }
            meta.getPersistentDataContainer().set(TNT_ID_KEY, PersistentDataType.STRING, type.id());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public void consumeItem(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
    }

    public void registerBlockPlacement(Block block, CustomTntType type) {
        storage.register(block, type.id());
    }

    public void unregisterBlock(Block block) {
        storage.remove(block);
    }

    public Optional<CustomTntType> findPlaced(Block block) {
        return storage.get(block).flatMap(this::getType);
    }

    public TNTPrimed spawnPrimed(Location location, CustomTntType type, Entity source) {
        Location spawn = location.toCenterLocation();
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location has no world for spawning TNT");
        }
        return location.getWorld().spawn(spawn, TNTPrimed.class, primed -> {
            primed.setFuseTicks(type.fuseTicks());
            primed.setYield(type.explosionPower());
            if (source != null) {
                primed.setSource(source);
            }
            PersistentDataContainer container = primed.getPersistentDataContainer();
            container.set(TNT_ID_KEY, PersistentDataType.STRING, type.id());
            int flags = 0;
            if (type.affectsRegions()) {
                flags |= FLAG_AFFECTS_REGIONS;
            }
            container.set(TNT_FLAGS_KEY, PersistentDataType.INTEGER, flags);
        });
    }

    public Optional<CustomTntType> fromEntity(Entity entity) {
        if (!(entity instanceof TNTPrimed primed)) {
            return Optional.empty();
        }
        PersistentDataContainer container = primed.getPersistentDataContainer();
        String typeId = container.get(TNT_ID_KEY, PersistentDataType.STRING);
        if (typeId == null) {
            return Optional.empty();
        }
        return getType(typeId);
    }

    public boolean entityAffectsRegions(Entity entity) {
        if (!(entity instanceof TNTPrimed primed)) {
            return false;
        }
        PersistentDataContainer container = primed.getPersistentDataContainer();
        Integer flags = container.get(TNT_FLAGS_KEY, PersistentDataType.INTEGER);
        return flags != null && (flags & FLAG_AFFECTS_REGIONS) != 0;
    }

    public PlacedTntStorage getStorage() {
        return storage;
    }
}
