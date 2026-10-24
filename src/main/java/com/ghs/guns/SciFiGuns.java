package com.ghs.guns;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Raygun, laser, railgun, tesla gun, plasma cannon, homing missiles, gravity gun, black hole gun, bubbles and confetti. */
public final class SciFiGuns {

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final Nukes nukes;
    /** Gravity gun: holder -> held entity. */
    private final Map<UUID, Entity> held = new HashMap<>();

    public SciFiGuns(GHSGuns plugin, SafeDamage safe, Nukes nukes) {
        this.plugin = plugin;
        this.safe = safe;
        this.nukes = nukes;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGravity, 1L, 1L);
    }

    public void fire(Player shooter, GunType type, Location eye, Vector dir, GunStats stats) {
        Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(0, -0.15, 0);
        switch (type) {
            case RAYGUN -> raygun(shooter, muzzle, dir, stats);
            case LASER_RIFLE -> laser(shooter, muzzle, dir, stats);
            case RAILGUN -> railgun(shooter, stats);
            case TESLA_GUN -> tesla(shooter, muzzle, dir, stats);
            case PLASMA_CANNON -> plasma(shooter, muzzle, dir, stats);
            case HOMING_LAUNCHER -> homing(shooter, muzzle, dir, stats);
            case BLACK_HOLE_GUN -> blackHoleShot(shooter, muzzle, dir, stats);
            case BUBBLE_GUN -> bubble(shooter, muzzle, dir, stats);
            case CONFETTI_CANNON -> confetti(shooter, muzzle, dir, stats);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void hurt(Player shooter, LivingEntity target, double damage) {
        if (damage <= 0) return;
        target.setNoDamageTicks(0);
        safe.gunDamage(() -> target.damage(damage, shooter));
    }

    private void push(LivingEntity e, Vector v) {
        if (e instanceof Player p) safe.launch(p, v); else e.setVelocity(v);
    }

    /** How far until the beam hits a wall. */
    private double wallDistance(Location from, Vector dir, double range) {
        RayTraceResult hit = from.getWorld().rayTraceBlocks(from, dir, range, FluidCollisionMode.NEVER, true);
        return hit == null ? range : hit.getHitPosition().distance(from.toVector());
    }

    private LivingEntity firstTarget(Player shooter, Location from, Vector dir, double range, double size) {
        RayTraceResult hit = from.getWorld().rayTraceEntities(from, dir, range, size,
                e -> e != shooter && e instanceof LivingEntity le && !le.isDead());
        return hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    /** Every living entity along a line, nearest first. */
    private List<LivingEntity> along(Player shooter, Location start, Vector dir, double length, double radius) {
        Vector d = dir.clone().normalize();
        Location end = start.clone().add(d.clone().multiply(length));
        BoundingBox box = BoundingBox.of(start, end).expand(radius + 1);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : start.getWorld().getNearbyEntities(box)) {
            if (e == shooter || !(e instanceof LivingEntity le) || le.isDead()) continue;
            Vector rel = le.getBoundingBox().getCenter().subtract(start.toVector());
            double t = rel.dot(d);
            if (t < 0 || t > length) continue;
            double perp = rel.clone().subtract(d.clone().multiply(t)).length();
            if (perp <= radius + le.getWidth() / 2 + 0.3) out.add(le);
        }
        out.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(start)));
        return out;
    }

    // ------------------------------------------------------------------ raygun

    private void raygun(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        double len = wallDistance(muzzle, dir, stats.range());
        LivingEntity target = firstTarget(shooter, muzzle, dir, len, 0.3);
        double dist = target == null ? len : Math.min(len, target.getLocation().add(0, target.getHeight() / 2, 0).distance(muzzle));
        Location end = muzzle.clone().add(dir.clone().multiply(dist));
        double phase = ThreadLocalRandom.current().nextDouble(Math.PI * 2);

        FX.line(muzzle, end, 0.2, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(140, 255, 90, 0.9f)));
        FX.helix(muzzle, dir, dist, 0.28, 0.7, 0.12, phase, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(60, 255, 60, 0.7f)));
        FX.helix(muzzle, dir, dist, 0.28, 0.7, 0.12, phase + Math.PI, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(40, 230, 255, 0.7f)));
        FX.line(muzzle, end, 1.0, p -> world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0.01));
        FX.ring(muzzle, dir, 0.35, 14, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(180, 255, 120, 1f)));
        world.playSound(muzzle, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 2f);
        world.playSound(muzzle, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 2f);

        FX.sphere(end, 0.7, 40, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(120, 255, 80, 1.1f)));
        FX.burst(end, Particle.ELECTRIC_SPARK, 20, 0.25);
        world.spawnParticle(Particle.GLOW, end, 10, 0.3, 0.3, 0.3, 0.05);
        if (target == null) return;
        hurt(shooter, target, stats.damage());
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
        world.playSound(end, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 2f);
        // Disintegration shimmer
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 10 || !target.isValid()) {
                    cancel();
                    return;
                }
                Location c = target.getLocation().add(0, target.getHeight() / 2, 0);
                FX.sphere(c, 1.2 - t * 0.09, 25, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(100, 255, 100, 0.8f)));
                world.spawnParticle(Particle.HAPPY_VILLAGER, c, 2, 0.3, 0.5, 0.3, 0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ laser rifle (pierces)

    private void laser(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        double len = wallDistance(muzzle, dir, stats.range());
        Location end = muzzle.clone().add(dir.clone().multiply(len));
        FX.line(muzzle, end, 0.15, p -> {
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 30, 30, 1.3f));
            world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 220, 220, 0.5f));
        });
        world.spawnParticle(Particle.LAVA, end, 2, 0.1, 0.1, 0.1, 0);
        world.spawnParticle(Particle.FLAME, end, 6, 0.1, 0.1, 0.1, 0.04);
        world.spawnParticle(Particle.SMOKE, end, 4, 0.1, 0.1, 0.1, 0.02);
        FX.burst(end, Particle.ELECTRIC_SPARK, 10, 0.2);
        world.playSound(muzzle, Sound.ENTITY_GUARDIAN_ATTACK, 0.5f, 2f);
        world.playSound(muzzle, Sound.BLOCK_BEACON_DEACTIVATE, 0.3f, 2f);
        for (LivingEntity le : along(shooter, muzzle, dir, len, 0.2)) {
            hurt(shooter, le, stats.damage());
            if (le instanceof Player p) safe.markBurning(p, 60);
            le.setFireTicks(Math.max(le.getFireTicks(), 30));
            world.spawnParticle(Particle.FLAME, le.getLocation().add(0, 1, 0), 10, 0.2, 0.4, 0.2, 0.03);
        }
    }

    // ------------------------------------------------------------------ railgun (charges, pierces walls and everything)

    private void railgun(Player shooter, GunStats stats) {
        World world = shooter.getWorld();
        shooter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 14, 2, false, false, false));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!shooter.isOnline() || shooter.isDead() || plugin.gunType(shooter.getInventory().getItemInMainHand()) != GunType.RAILGUN) {
                    cancel();
                    return;
                }
                Location eye = shooter.getEyeLocation();
                Vector dir = eye.getDirection();
                Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(0, -0.15, 0);
                if (t++ < 12) {
                    FX.implode(muzzle, Particle.ELECTRIC_SPARK, 1.8 - t * 0.1, 10, 0.25);
                    FX.ring(muzzle, dir, 0.6 - t * 0.04, 16, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(80, 220, 255, 0.8f)));
                    if (t % 3 == 0) world.playSound(muzzle, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.8f + t * 0.1f);
                    return;
                }
                cancel();
                double range = stats.range();
                Location end = muzzle.clone().add(dir.clone().multiply(range));
                FX.line(muzzle, end, 0.25, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 255, 255, 1.2f)));
                FX.helix(muzzle, dir, range, 0.45, 0.4, 0.2, 0, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(60, 200, 255, 0.9f)));
                FX.helix(muzzle, dir, range, 0.45, 0.4, 0.4, Math.PI, p -> world.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0, 0, 0, 0));
                for (double d = 3; d < range; d += 3) {
                    Location c = muzzle.clone().add(dir.clone().multiply(d));
                    FX.ring(c, dir, 0.7, 12, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(150, 230, 255, 1f)));
                    if (d % 9 < 3) world.spawnParticle(Particle.SONIC_BOOM, c, 1);
                }
                world.playSound(muzzle, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.4f);
                world.playSound(muzzle, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.8f);
                shooter.setVelocity(shooter.getVelocity().add(dir.clone().multiply(-0.6)));
                for (LivingEntity le : along(shooter, muzzle, dir, range, 0.4)) {
                    hurt(shooter, le, stats.damage());
                    push(le, dir.clone().multiply(2.2).setY(0.6));
                    world.spawnParticle(Particle.FLASH, le.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(150, 230, 255));
                    FX.burst(le.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 30, 0.4);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ tesla gun (chain lightning)

    private void tesla(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        double len = wallDistance(muzzle, dir, stats.range());
        LivingEntity first = firstTarget(shooter, muzzle, dir, len, 0.9);
        world.playSound(muzzle, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
        world.playSound(muzzle, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
        if (first == null) {
            Location end = muzzle.clone().add(dir.clone().multiply(Math.min(len, 12)));
            FX.zap(muzzle, end, p -> world.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0, 0, 0, 0));
            FX.zap(muzzle, end, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(120, 200, 255, 0.7f)));
            return;
        }
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        LivingEntity last = first;
        while (chain.size() < 6) {
            LivingEntity next = null;
            double best = 64;
            for (Entity e : world.getNearbyEntities(last.getLocation(), 8, 8, 8)) {
                if (e == shooter || !(e instanceof LivingEntity le) || le.isDead() || chain.contains(le)) continue;
                double d = le.getLocation().distanceSquared(last.getLocation());
                if (d < best) {
                    best = d;
                    next = le;
                }
            }
            if (next == null) break;
            chain.add(next);
            last = next;
        }
        Location from = muzzle;
        double damage = stats.damage();
        for (LivingEntity le : chain) {
            Location to = le.getLocation().add(0, le.getHeight() / 2, 0);
            FX.zap(from, to, p -> world.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0, 0, 0, 0));
            FX.zap(from, to, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(150, 210, 255, 0.8f)));
            FX.line(from, to, 1.5, p -> world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0.01));
            world.spawnParticle(Particle.FLASH, to, 1, 0, 0, 0, 0, Color.fromRGB(150, 210, 255));
            FX.burst(to, Particle.ELECTRIC_SPARK, 25, 0.3);
            hurt(shooter, le, damage);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3, false, false));
            world.playSound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 2f);
            damage *= 0.8;
            from = to;
        }
    }

    // ------------------------------------------------------------------ plasma cannon

    private void plasma(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        world.playSound(muzzle, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.5f);
        world.playSound(muzzle, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        new Bolt(shooter, muzzle, dir.clone().multiply(stats.projectileSpeed()), 60, 0.8,
                (at, vel, tick) -> {
                    FX.sphere(at, 0.45, 14, p -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0));
                    world.spawnParticle(Particle.DUST, at, 6, 0.2, 0.2, 0.2, 0, FX.dust(90, 220, 255, 1.6f));
                    world.spawnParticle(Particle.DUST, at, 4, 0.25, 0.25, 0.25, 0, FX.dust(200, 80, 255, 1.4f));
                    FX.ring(at, vel, 0.7, 10, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(140, 160, 255, 0.8f)));
                    world.spawnParticle(Particle.END_ROD, at, 1, 0.05, 0.05, 0.05, 0.01);
                    if (tick % 5 == 0) world.playSound(at, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2f);
                },
                (at, target) -> {
                    UndoManager.Pending undo = nukes.startUndo(shooter);
                    nukes.boom(shooter, at, stats.explosionPower(), undo);
                    nukes.finishLater(undo, 3);
                    world.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.fromRGB(120, 200, 255));
                    FX.burst(at, Particle.SOUL_FIRE_FLAME, 120, 0.4);
                    FX.burst(at, Particle.END_ROD, 40, 0.3);
                    world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 1f);
                    world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1.4f);
                    new BukkitRunnable() {
                        int t = 0;

                        @Override
                        public void run() {
                            if (t++ > 6) {
                                cancel();
                                return;
                            }
                            Color c = t % 2 == 0 ? Color.fromRGB(80, 210, 255) : Color.fromRGB(190, 90, 255);
                            FX.sphere(at, t * 0.8, 60, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(c, 1.6f)));
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }).start(plugin);
    }

    // ------------------------------------------------------------------ homing missiles

    private void homing(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        double speed = stats.projectileSpeed();
        LivingEntity[] lock = {firstTarget(shooter, shooter.getEyeLocation(), dir, 80, 2.5)};
        if (lock[0] != null) {
            shooter.sendActionBar(net.kyori.adventure.text.Component.text("LOCKED ON: " + lock[0].getName(), net.kyori.adventure.text.format.NamedTextColor.RED));
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
        world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.7f);
        new Bolt(shooter, muzzle, dir.clone().multiply(speed), 160, 0.8,
                (at, vel, tick) -> {
                    world.spawnParticle(Particle.FLAME, at, 4, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.SMOKE, at, 3, 0.08, 0.08, 0.08, 0.01);
                    world.spawnParticle(Particle.LARGE_SMOKE, at.clone().subtract(vel.clone().normalize().multiply(0.6)), 1, 0.05, 0.05, 0.05, 0.01);
                    if (tick % 4 < 2) world.spawnParticle(Particle.DUST, at, 2, 0.05, 0.05, 0.05, 0, FX.dust(255, 0, 0, 1.4f));
                    if (tick % 6 == 0) world.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
                },
                (at, target) -> nukes.rpgExplosion(shooter, at, stats.explosionPower()))
                .steer(bolt -> {
                    LivingEntity t = lock[0];
                    if (t == null || !t.isValid() || t.isDead()) {
                        t = null;
                        double best = 30 * 30;
                        for (Entity e : world.getNearbyEntities(bolt.position(), 30, 30, 30)) {
                            if (e == shooter || !(e instanceof LivingEntity le) || le.isDead()) continue;
                            double d = le.getLocation().distanceSquared(bolt.position());
                            if (d < best) {
                                best = d;
                                t = le;
                            }
                        }
                        lock[0] = t;
                    }
                    Vector v = bolt.velocity();
                    if (t == null) return v;
                    Vector want = t.getLocation().add(0, t.getHeight() / 2, 0).toVector().subtract(bolt.position().toVector()).normalize();
                    return v.normalize().multiply(0.8).add(want.multiply(0.35)).normalize().multiply(speed);
                }).start(plugin);
    }

    // ------------------------------------------------------------------ black hole gun

    private void blackHoleShot(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        world.playSound(muzzle, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 2f);
        world.playSound(muzzle, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        new Bolt(shooter, muzzle, dir.clone().multiply(stats.projectileSpeed()), 60, 0.7,
                (at, vel, tick) -> {
                    FX.sphere(at, 0.3, 10, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(10, 0, 20, 1.6f)));
                    FX.ring(at, new Vector(0.3, 1, 0.2), 0.6, 12, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(180, 80, 255, 0.9f)));
                    world.spawnParticle(Particle.REVERSE_PORTAL, at, 6, 0.1, 0.1, 0.1, 0.05);
                },
                (at, target) -> nukes.blackHole(shooter, at, false)).start(plugin);
    }

    // ------------------------------------------------------------------ bubble gun

    private void bubble(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        world.playSound(muzzle, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1.5f, 1.5f);
        new Bolt(shooter, muzzle, dir.clone().multiply(stats.projectileSpeed()), 50, 0.7,
                (at, vel, tick) -> {
                    FX.sphere(at, 0.35, 16, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(170, 230, 255, 0.7f)));
                    world.spawnParticle(Particle.DUST, at.clone().add(0.12, 0.12, 0), 1, 0, 0, 0, 0, FX.dust(255, 255, 255, 0.6f));
                    world.spawnParticle(Particle.BUBBLE_POP, at, 1, 0.1, 0.1, 0.1, 0);
                },
                (at, target) -> {
                    world.playSound(at, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1.5f, 1f);
                    world.spawnParticle(Particle.BUBBLE_POP, at, 15, 0.3, 0.3, 0.3, 0.05);
                    world.spawnParticle(Particle.SPLASH, at, 15, 0.3, 0.3, 0.3, 0.1);
                    if (target != null) trapInBubble(target);
                }).start(plugin);
    }

    private void trapInBubble(LivingEntity target) {
        World world = target.getWorld();
        if (target instanceof Player p) safe.launch(p, new Vector(0, 0.2, 0));
        world.playSound(target.getLocation(), Sound.ITEM_BUCKET_FILL, 1f, 1.5f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!target.isValid()) {
                    cancel();
                    return;
                }
                Location c = target.getLocation().add(0, target.getHeight() / 2, 0);
                double radius = Math.max(target.getWidth(), target.getHeight()) / 2 + 0.6;
                if (t++ >= 80) {
                    cancel();
                    world.spawnParticle(Particle.BUBBLE_POP, c, 40, radius / 2, radius / 2, radius / 2, 0.1);
                    world.spawnParticle(Particle.SPLASH, c, 40, radius / 2, radius / 2, radius / 2, 0.2);
                    world.playSound(c, Sound.ENTITY_PLAYER_SPLASH, 1f, 1.6f);
                    return;
                }
                target.setVelocity(new Vector(Math.sin(t * 0.1) * 0.05, 0.06, Math.cos(t * 0.1) * 0.05));
                target.setFallDistance(0);
                FX.sphere(c, radius, 40, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(170, 230, 255, 0.7f)));
                world.spawnParticle(Particle.DUST, c.clone().add(radius * 0.4, radius * 0.5, 0), 2, 0.05, 0.05, 0.05, 0, FX.dust(255, 255, 255, 1f));
                if (t % 10 == 0) world.spawnParticle(Particle.BUBBLE_POP, c, 3, radius / 2, radius / 2, radius / 2, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ confetti cannon

    private void confetti(Player shooter, Location muzzle, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 120; i++) {
            Vector d = dir.clone().add(new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(0.2)).normalize();
            Location p = muzzle.clone().add(d.multiply(r.nextDouble(1, stats.range())));
            world.spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0, FX.dust(FX.RAINBOW[r.nextInt(FX.RAINBOW.length)], 1.2f));
        }
        for (int i = 0; i < 30; i++) {
            Vector d = dir.clone().add(new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(0.15)).normalize();
            world.spawnParticle(Particle.FIREWORK, muzzle, 0, d.getX(), d.getY(), d.getZ(), 0.6);
        }
        nukes.firework(muzzle.clone().add(dir.clone().multiply(Math.min(6, stats.range()))),
                FireworkEffect.builder().with(FireworkEffect.Type.BURST).withColor(FX.RAINBOW).withFade(Color.WHITE).flicker(true).build());
        world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1f);
        world.playSound(muzzle, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        for (Entity e : world.getNearbyEntities(muzzle, stats.range(), stats.range(), stats.range())) {
            if (e == shooter || !(e instanceof LivingEntity le)) continue;
            Vector to = le.getLocation().toVector().subtract(muzzle.toVector());
            if (to.length() > stats.range() || to.clone().normalize().dot(dir) < 0.8) continue;
            push(le, dir.clone().multiply(1.2).setY(0.4));
        }
    }

    // ------------------------------------------------------------------ gravity gun

    /** Right-click: grab what you're looking at, right-click again to throw it. */
    public void gravity(Player player) {
        World world = player.getWorld();
        Entity current = held.remove(player.getUniqueId());
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        if (current != null && current.isValid()) {
            Vector v = dir.clone().multiply(3.2);
            if (current instanceof Player p) safe.launch(p, v); else current.setVelocity(v);
            FX.burst(current.getLocation().add(0, 0.5, 0), Particle.END_ROD, 40, 0.4);
            FX.ring(eye.clone().add(dir), dir, 0.6, 16, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(200, 120, 255, 1.2f)));
            world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.8f);
            world.playSound(eye, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.5f);
            return;
        }
        RayTraceResult hit = world.rayTraceEntities(eye, dir, 25, 0.6,
                e -> e != player && !e.isDead() && !held.containsValue(e) && !(e instanceof org.bukkit.entity.ExperienceOrb));
        double wall = wallDistance(eye, dir, 25);
        if (hit == null || hit.getHitEntity() == null || hit.getHitPosition().distance(eye.toVector()) > wall) {
            world.playSound(eye, Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.6f);
            return;
        }
        Entity target = hit.getHitEntity();
        held.put(player.getUniqueId(), target);
        if (target instanceof Player p) safe.launch(p, p.getVelocity());
        world.playSound(eye, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 2f);
        world.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
    }

    private void tickGravity() {
        Iterator<Map.Entry<UUID, Entity>> it = held.entrySet().iterator();
        int tick = Bukkit.getCurrentTick();
        while (it.hasNext()) {
            Map.Entry<UUID, Entity> entry = it.next();
            Player holder = Bukkit.getPlayer(entry.getKey());
            Entity e = entry.getValue();
            if (holder == null || holder.isDead() || !e.isValid() || e.getWorld() != holder.getWorld()
                    || plugin.gunType(holder.getInventory().getItemInMainHand()) != GunType.GRAVITY_GUN
                    || e.getLocation().distanceSquared(holder.getLocation()) > 40 * 40) {
                it.remove();
                continue;
            }
            World world = holder.getWorld();
            Location eye = holder.getEyeLocation();
            Location hold = eye.clone().add(eye.getDirection().multiply(3.5 + e.getWidth()));
            Location center = e.getLocation().add(0, e.getHeight() / 2, 0);
            Vector v = hold.toVector().subtract(center.toVector()).multiply(0.45);
            if (v.length() > 1.6) v.normalize().multiply(1.6);
            e.setVelocity(v);
            e.setFallDistance(0);
            Location gun = eye.clone().add(eye.getDirection().multiply(0.9)).add(0, -0.2, 0);
            FX.line(gun, center, 0.35, p -> world.spawnParticle(Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0, FX.dust(190, 110, 255, 0.6f)));
            double radius = Math.max(e.getWidth(), e.getHeight()) / 2 + 0.4;
            FX.ring(center, new Vector(Math.sin(tick * 0.15), 1, Math.cos(tick * 0.15)), radius, 18,
                    p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(150, 200, 255, 0.8f)));
            world.spawnParticle(Particle.REVERSE_PORTAL, center, 3, radius / 2, radius / 2, radius / 2, 0.02);
            if (tick % 10 == 0) world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 2f);
        }
    }
}
