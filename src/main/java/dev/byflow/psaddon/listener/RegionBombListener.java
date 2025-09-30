package dev.byflow.psaddon.listener;

import dev.byflow.psaddon.PSAddonPlugin;
import dev.byflow.psaddon.tnt.RegionBombManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class RegionBombListener implements Listener {
    private final PSAddonPlugin plugin;

    public RegionBombListener(PSAddonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        RegionBombManager manager = plugin.getRegionBombManager();
        if (manager == null || !manager.isEnabled()) {
            return;
        }
        ItemStack item = event.getItemInHand();
        if (!manager.isRegionBombItem(item)) {
            return;
        }

        event.setCancelled(true);
        event.getBlockPlaced().setType(Material.AIR);

        Location spawnLocation = event.getBlockPlaced().getLocation().add(0.5, 0, 0.5);
        TNTPrimed primed = spawnLocation.getWorld().spawn(spawnLocation, TNTPrimed.class, spawned -> {
            spawned.setFuseTicks(manager.getFuseTicks());
            spawned.setSource(event.getPlayer());
        });
        manager.mark(primed);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            consumeItem(event);
        }
    }

    private void consumeItem(BlockPlaceEvent event) {
        EquipmentSlot hand = event.getHand();
        ItemStack original = event.getItemInHand();
        if (original == null) {
            return;
        }
        ItemStack updated = original.clone();
        int amount = Math.max(0, updated.getAmount() - 1);
        if (amount <= 0) {
            event.getPlayer().getInventory().setItem(hand, null);
        } else {
            updated.setAmount(amount);
            event.getPlayer().getInventory().setItem(hand, updated);
        }
        event.getPlayer().updateInventory();
    }
}
