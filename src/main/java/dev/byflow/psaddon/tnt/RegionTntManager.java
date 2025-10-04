package dev.byflow.psaddon.tnt;

import dev.byflow.psaddon.PSAddonPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public final class RegionTntManager implements Listener {
    private final PSAddonPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey entityKey;
    private final Set<BlockKey> trackedBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RegionTntManager(PSAddonPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "region_tnt_item");
        this.entityKey = new NamespacedKey(plugin, "region_tnt_entity");
    }

    public ItemStack createItem(int amount) {
        ItemStack stack = new ItemStack(Material.TNT, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Регионный динамит", NamedTextColor.RED));
            meta.lore(List.of(
                    Component.text("Наносит урон привату", NamedTextColor.GRAY),
                    Component.text("Создан PSAddon", NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isRegionTnt(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TNT) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(itemKey, PersistentDataType.BYTE);
    }

    public boolean isRegionTnt(TNTPrimed primed) {
        if (primed == null) {
            return false;
        }
        PersistentDataContainer container = primed.getPersistentDataContainer();
        return container.has(entityKey, PersistentDataType.BYTE);
    }

    private void markPrimed(TNTPrimed primed) {
        if (primed == null) {
            return;
        }
        primed.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isRegionTnt(item)) {
            return;
        }
        trackedBlocks.add(BlockKey.from(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        removeTracking(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        removeTracking(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            removeTracking(iterator.next());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            removeTracking(iterator.next());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            removeTracking(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            removeTracking(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        Block block = event.getBlock();
        if (block == null) {
            return;
        }
        BlockKey key = BlockKey.from(block);
        if (!trackedBlocks.remove(key)) {
            return;
        }
        markPrimed(event.getPrimedTNT());
    }

    private void removeTracking(Block block) {
        if (block == null) {
            return;
        }
        trackedBlocks.remove(BlockKey.from(block));
    }

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey from(Block block) {
            return new BlockKey(
                    block.getWorld().getUID().toString(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
        }
    }
}
