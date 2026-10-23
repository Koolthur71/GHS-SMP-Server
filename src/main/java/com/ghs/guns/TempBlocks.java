package com.ghs.guns;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Blocks placed by trolls (cages, webs). They only ever go into empty air,
 * can't be farmed for drops, and turn back into air after a while or when the server stops.
 */
public final class TempBlocks implements Listener {

    private final GHSGuns plugin;
    private final Map<Location, Material> placed = new HashMap<>();

    public TempBlocks(GHSGuns plugin) {
        this.plugin = plugin;
    }

    /** Place a block at an air position for the given time. Returns false if the spot wasn't empty. */
    public boolean place(Block block, Material type, long ticks) {
        if (!block.getType().isAir()) return false;
        Location key = block.getLocation();
        block.setType(type, false);
        placed.put(key, type);
        Bukkit.getScheduler().runTaskLater(plugin, () -> revert(key), ticks);
        return true;
    }

    private void revert(Location key) {
        Material type = placed.remove(key);
        if (type == null) return;
        Block block = key.getBlock();
        if (block.getType() == type) block.setType(Material.AIR, false);
    }

    public void revertAll() {
        for (Location key : Map.copyOf(placed).keySet()) revert(key);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (placed.remove(event.getBlock().getLocation()) != null) event.setDropItems(false);
    }
}
