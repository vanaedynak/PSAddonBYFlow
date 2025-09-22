package dev.byflow.psaddon.tnt.listener;

import dev.byflow.psaddon.tnt.CustomTntManager;
import dev.byflow.psaddon.tnt.CustomTntPlugin;
import dev.byflow.psaddon.tnt.config.CustomTntType;
import io.papermc.paper.event.block.TNTPrimeEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class CustomTntBlockListener implements Listener {
    private final CustomTntPlugin plugin;
    private final CustomTntManager manager;

    public CustomTntBlockListener(CustomTntPlugin plugin, CustomTntManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Optional<CustomTntType> typeOptional = manager.fromItem(event.getItemInHand());
        if (typeOptional.isEmpty()) {
            return;
        }
        CustomTntType type = typeOptional.get();
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();

        if (type.autoIgniteWhenPlaced()) {
            event.setCancelled(true);
            manager.consumeItem(player, hand);
            manager.spawnPrimed(event.getBlock().getLocation(), type, player);
            return;
        }

        manager.registerBlockPlacement(event.getBlockPlaced(), type);
        plugin.getStorage().save();
        if (event.getBlockPlaced().getType() != Material.TNT) {
            event.getBlockPlaced().setType(Material.TNT, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<CustomTntType> typeOptional = manager.findPlaced(block);
        if (typeOptional.isEmpty()) {
            return;
        }
        CustomTntType type = typeOptional.get();
        manager.unregisterBlock(block);
        plugin.getStorage().save();
        event.setDropItems(false);
        block.setType(Material.AIR, false);
        if (type.dropWhenBroken() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.0, 0.5), manager.createItem(type, 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        Optional<CustomTntType> typeOptional = manager.findPlaced(event.getBlock());
        if (typeOptional.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getBlock();
        CustomTntType type = typeOptional.get();
        manager.unregisterBlock(block);
        block.setType(Material.AIR, false);
        manager.spawnPrimed(block.getLocation(), type, event.getPrimingEntity());
        plugin.getStorage().save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Optional<CustomTntType> typeOptional = manager.findPlaced(event.getBlock());
        if (typeOptional.isEmpty()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        cleanupDestroyed(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        cleanupDestroyed(event.blockList());
    }

    private void cleanupDestroyed(Iterable<Block> blocks) {
        boolean changed = false;
        for (Block block : blocks) {
            if (manager.findPlaced(block).isPresent()) {
                manager.unregisterBlock(block);
                block.setType(Material.AIR, false);
                changed = true;
            }
        }
        if (changed) {
            plugin.getStorage().save();
        }
    }
}
