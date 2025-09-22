package dev.byflow.psaddon.tnt.listener;

import dev.byflow.psaddon.tnt.CustomTntManager;
import dev.byflow.psaddon.tnt.config.BlockDamageRule;
import dev.byflow.psaddon.tnt.config.CustomTntType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Collection;
import java.util.Optional;

public final class CustomTntExplosionListener implements Listener {
    private final CustomTntManager manager;

    public CustomTntExplosionListener(CustomTntManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        Optional<CustomTntType> typeOptional = manager.fromEntity(event.getEntity());
        if (typeOptional.isEmpty()) {
            return;
        }
        CustomTntType type = typeOptional.get();

        if (!type.igniteInWater() && event.getLocation().getBlock().isLiquid()) {
            event.setCancelled(true);
            return;
        }

        if (type.onlyDamagesRegions()) {
            event.blockList().clear();
            event.setYield(0f);
        } else {
            applyBlockRule(event.blockList(), type.blockDamageRule());
        }

        applyForcedBreak(event.getLocation(), type);
    }

    private void applyBlockRule(Collection<Block> blocks, BlockDamageRule rule) {
        switch (rule.mode()) {
            case ALL -> {
            }
            case NONE -> blocks.clear();
            default -> blocks.removeIf(block -> !rule.canDamage(block.getType()));
        }
    }

    private void applyForcedBreak(Location origin, CustomTntType type) {
        BlockDamageRule rule = type.blockDamageRule();
        if (rule.forceBreak().isEmpty()) {
            return;
        }
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        int radius = Math.max(1, Math.round(type.explosionPower())) + 1;
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();
        double maxDistanceSq = Math.pow(type.explosionPower() + 1.5D, 2);
        Location center = origin.toCenterLocation();
        for (int x = originX - radius; x <= originX + radius; x++) {
            for (int y = originY - radius; y <= originY + radius; y++) {
                for (int z = originZ - radius; z <= originZ + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    if (!rule.forceBreak().contains(material)) {
                        continue;
                    }
                    Location blockCenter = block.getLocation().toCenterLocation();
                    if (blockCenter.distanceSquared(center) > maxDistanceSq) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                }
            }
        }
    }
}
