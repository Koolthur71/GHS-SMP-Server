package com.ghs.guns;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.function.Function;

/**
 * A projectile that is only particles: moves a point each tick, checks for blocks and
 * entities in small sub-steps, draws itself, and calls back when it hits something.
 */
final class Bolt extends BukkitRunnable {

    interface Draw {
        void draw(Location at, Vector velocity, int tick);
    }

    interface Hit {
        /** target is null when a block (or the range limit) was hit. */
        void hit(Location at, LivingEntity target);
    }

    private final Player shooter;
    private final Location pos;
    private Vector velocity;
    private final int maxTicks;
    private final double hitRadius;
    private final Draw draw;
    private final Hit hit;
    private Function<Bolt, Vector> steer;
    private int tick;

    Bolt(Player shooter, Location start, Vector velocity, int maxTicks, double hitRadius, Draw draw, Hit hit) {
        this.shooter = shooter;
        this.pos = start.clone();
        this.velocity = velocity.clone();
        this.maxTicks = maxTicks;
        this.hitRadius = hitRadius;
        this.draw = draw;
        this.hit = hit;
    }

    /** Called every tick to change the velocity (homing). */
    Bolt steer(Function<Bolt, Vector> steer) {
        this.steer = steer;
        return this;
    }

    Location position() {
        return pos.clone();
    }

    Vector velocity() {
        return velocity.clone();
    }

    Player shooter() {
        return shooter;
    }

    Bolt start(Plugin plugin) {
        runTaskTimer(plugin, 0L, 1L);
        return this;
    }

    @Override
    public void run() {
        if (tick++ >= maxTicks) {
            cancel();
            hit.hit(pos, null);
            return;
        }
        if (steer != null) velocity = steer.apply(this);
        World world = pos.getWorld();
        double len = velocity.length();
        int steps = Math.max(1, (int) Math.ceil(len / 0.4));
        Vector stepVec = velocity.clone().multiply(1.0 / steps);
        for (int i = 0; i < steps; i++) {
            pos.add(stepVec);
            Block block = pos.getBlock();
            if (!block.isPassable() && block.getBoundingBox().contains(pos.toVector())) {
                cancel();
                hit.hit(pos.clone().subtract(stepVec), null);
                return;
            }
            for (Entity e : world.getNearbyEntities(pos, hitRadius, hitRadius, hitRadius)) {
                if (e == shooter || !(e instanceof LivingEntity le) || le.isDead()) continue;
                cancel();
                hit.hit(pos.clone(), le);
                return;
            }
        }
        draw.draw(pos.clone(), velocity.clone(), tick);
    }
}
