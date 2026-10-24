package com.ghs.guns;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Blocks placed by trolls and tools (cages, webs, snow, sky walker glass). They only ever go into
 * empty air (or water/lava when allowed), can't be farmed for drops, and are put back exactly
 * as they were after a while or when the server stops.
 */
public final class TempBlocks implements Listener {

    private record Placed(Material type, BlockData original) {}

    private final GHSGuns plugin;
    private final Map<Location, Placed> placed = new HashMap<>();

    public TempBlocks(GHSGuns plugin) {
        this.plugin = plugin;
    }

    /** Place a block at an air position for the given time. Returns false if the spot wasn't empty. */
    public boolean place(Block block, Material type, long ticks) {
        return place(block, type, ticks, false);
    }

    /** Like {@link #place(Block, Material, long)} but can also cover water and lava. */
    public boolean place(Block block, Material type, long ticks, boolean overLiquid) {
        Location key = block.getLocation();
        Placed existing = placed.get(key);
        if (existing != null && block.getType() == existing.type()) {
            // Already ours: keep it around longer. A new record makes the older timer a no-op.
            Placed renewed = new Placed(existing.type(), existing.original());
            placed.put(key, renewed);
            Bukkit.getScheduler().runTaskLater(plugin, () -> revert(key, renewed), ticks);
            return true;
        }
        Material current = block.getType();
        boolean ok = current.isAir() || (overLiquid && (current == Material.WATER || current == Material.LAVA));
        if (!ok) return false;
        Placed p = new Placed(type, block.getBlockData());
        block.setType(type, false);
        placed.put(key, p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> revert(key, p), ticks);
        return true;
    }

    private void revert(Location key, Placed expected) {
        // A later place() call may have extended it; only the newest timer may revert
        if (placed.get(key) != expected) return;
        placed.remove(key);
        Block block = key.getBlock();
        if (block.getType() == expected.type()) block.setBlockData(expected.original(), false);
    }

    public void revertAll() {
        for (Map.Entry<Location, Placed> e : Map.copyOf(placed).entrySet()) revert(e.getKey(), e.getValue());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (placed.remove(event.getBlock().getLocation()) != null) event.setDropItems(false);
    }
}
