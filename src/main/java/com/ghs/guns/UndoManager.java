package com.ghs.guns;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshots blocks around gun explosions so they can be put back with /guns undo.
 * One snapshot can cover several explosions (the nuke is made of many), and each
 * explosion's area is captured right before it goes off so the work is spread out.
 * Snapshots only live in memory, so they're lost on restart.
 */
public final class UndoManager {

    public record Snapshot(String shooterName, long time, List<BlockState> blocks) {}

    /** A snapshot that is still collecting blocks (explosions still going off). */
    public static final class Pending {
        private final Map<Long, BlockState> before = new HashMap<>();
        private final List<Location> centers = new ArrayList<>();
        private final String shooterName;
        private int maxRadius;

        private Pending(String shooterName) {
            this.shooterName = shooterName;
        }
    }

    private final GHSGuns plugin;
    private final Deque<Snapshot> history = new ArrayDeque<>();

    public UndoManager(GHSGuns plugin) {
        this.plugin = plugin;
    }

    public Pending begin(String shooterName) {
        return new Pending(shooterName);
    }

    /** Record every non-air block in a sphere before it can get blown up. Blocks already recorded are kept as-is. */
    public void capture(Pending pending, Location center, int radius) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), cy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + radius);
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    int dy = y - cy;
                    if (x * x + dy * dy + z * z > r2) continue;
                    int bx = cx + x, bz = cz + z;
                    long key = (((long) bx & 0x3FFFFFF) << 38) | (((long) bz & 0x3FFFFFF) << 12) | (y & 0xFFF);
                    if (pending.before.containsKey(key)) continue;
                    Block b = world.getBlockAt(bx, y, bz);
                    if (b.getType().isAir()) continue;
                    pending.before.put(key, b.getState());
                }
            }
        }
        pending.centers.add(center.clone());
        pending.maxRadius = Math.max(pending.maxRadius, radius);
    }

    /** Keep only the blocks that actually changed and add them to the undo history. */
    public void finish(Pending pending) {
        List<BlockState> changed = new ArrayList<>();
        for (BlockState state : pending.before.values()) {
            Block now = state.getBlock();
            if (now.getType() != state.getType() || !now.getBlockData().equals(state.getBlockData())) {
                changed.add(state);
            }
        }
        if (changed.isEmpty()) return;
        // Remove item drops from the blast so undo doesn't duplicate blocks or chest contents
        for (Location c : pending.centers) {
            for (Item item : c.getWorld().getNearbyEntitiesByType(Item.class, c, pending.maxRadius + 4)) {
                if (item.getTicksLived() <= 200) item.remove();
            }
        }
        history.push(new Snapshot(pending.shooterName, System.currentTimeMillis(), changed));
        int max = Math.max(1, plugin.getConfig().getInt("undo.max-history", 50));
        while (history.size() > max) history.removeLast();
    }

    /** Undo the most recent explosions. Returns how many were undone. */
    public int undo(int count) {
        int done = 0;
        while (done < count && !history.isEmpty()) {
            restore(history.pop());
            done++;
        }
        return done;
    }

    /** Undo all stored explosions caused by one player. */
    public int undoPlayer(String name) {
        List<Snapshot> matches = new ArrayList<>();
        var it = history.iterator();
        while (it.hasNext()) {
            Snapshot s = it.next();
            if (s.shooterName().equalsIgnoreCase(name)) {
                matches.add(s);
                it.remove();
            }
        }
        matches.forEach(this::restore); // newest first
        return matches.size();
    }

    public int size() {
        return history.size();
    }

    private void restore(Snapshot snapshot) {
        for (BlockState state : snapshot.blocks()) {
            if (state.getBlock().getType() == Material.FIRE) state.getBlock().setType(Material.AIR, false);
        }
        // Solid blocks first, then everything else (torches, doors etc. need support)
        for (BlockState state : snapshot.blocks()) {
            if (state.getType().isSolid()) state.update(true, false);
        }
        for (BlockState state : snapshot.blocks()) {
            if (!state.getType().isSolid()) state.update(true, false);
        }
    }
}
