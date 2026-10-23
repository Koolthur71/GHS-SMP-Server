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
import java.util.List;
import java.util.UUID;

/**
 * Snapshots blocks around gun explosions so they can be put back with /guns undo.
 * Snapshots only live in memory, so they're lost on restart.
 */
public final class UndoManager {

    public record Snapshot(UUID shooter, String shooterName, long time, List<BlockState> blocks) {}

    /** A snapshot that has been taken but not finished (explosion still in progress). */
    public static final class Pending {
        private final List<BlockState> before;
        private final Location center;
        private final int radius;
        private final UUID shooter;
        private final String shooterName;

        private Pending(List<BlockState> before, Location center, int radius, UUID shooter, String shooterName) {
            this.before = before;
            this.center = center;
            this.radius = radius;
            this.shooter = shooter;
            this.shooterName = shooterName;
        }
    }

    private final GHSGuns plugin;
    private final Deque<Snapshot> history = new ArrayDeque<>();

    public UndoManager(GHSGuns plugin) {
        this.plugin = plugin;
    }

    /** Record every non-air block in a sphere before it can get blown up. */
    public Pending begin(Location center, int radius, UUID shooter, String shooterName) {
        World world = center.getWorld();
        List<BlockState> states = new ArrayList<>();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), cy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + radius);
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    int dy = y - cy;
                    if (x * x + dy * dy + z * z > r2) continue;
                    Block b = world.getBlockAt(cx + x, y, cz + z);
                    if (b.getType().isAir()) continue;
                    states.add(b.getState());
                }
            }
        }
        return new Pending(states, center.clone(), radius, shooter, shooterName);
    }

    /** Keep only the blocks that actually changed and add them to the undo history. */
    public void finish(Pending pending) {
        List<BlockState> changed = new ArrayList<>();
        for (BlockState state : pending.before) {
            Block now = state.getBlock();
            if (now.getType() != state.getType() || !now.getBlockData().equals(state.getBlockData())) {
                changed.add(state);
            }
        }
        // Remove item drops from the blast so undo doesn't duplicate blocks or chest contents
        if (!changed.isEmpty()) {
            double r = pending.radius + 4;
            for (Item item : pending.center.getWorld().getNearbyEntitiesByType(Item.class, pending.center, r)) {
                if (item.getTicksLived() <= 120) item.remove();
            }
        }
        if (changed.isEmpty()) return;
        history.push(new Snapshot(pending.shooter, pending.shooterName, System.currentTimeMillis(), changed));
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
        int done = 0;
        var it = history.iterator();
        List<Snapshot> matches = new ArrayList<>();
        while (it.hasNext()) {
            Snapshot s = it.next();
            if (s.shooterName().equalsIgnoreCase(name)) {
                matches.add(s);
                it.remove();
            }
        }
        for (Snapshot s : matches) { // newest first
            restore(s);
            done++;
        }
        return done;
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
