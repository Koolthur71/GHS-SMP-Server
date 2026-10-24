package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Every explosion in the plugin: RPGs, all the nuke types, black holes and their effects. */
public final class Nukes implements Listener {

    /** How big a nuke is. */
    record Profile(String title, int rings, double ringSpacing, float ringPower, float corePower, double shockwave,
                   int debris, double cloudHeight, double cloudRadius, int countdownTicks) {}

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final TrollListener trolls;
    private final TempBlocks tempBlocks;
    private final NamespacedKey fireworkKey;

    public Nukes(GHSGuns plugin, SafeDamage safe, TrollListener trolls, TempBlocks tempBlocks) {
        this.plugin = plugin;
        this.safe = safe;
        this.trolls = trolls;
        this.tempBlocks = tempBlocks;
        this.fireworkKey = new NamespacedKey(plugin, "safe_firework");
    }

    // ------------------------------------------------------------------ basics

    UndoManager.Pending startUndo(Player shooter) {
        if (!plugin.getConfig().getBoolean("explosions-break-blocks", true)) return null;
        return plugin.undo().begin(shooter == null ? "unknown" : shooter.getName());
    }

    /** One explosion: snapshot the area for undo, make it non-lethal, then blow it up. */
    void boom(Player shooter, Location loc, float power, UndoManager.Pending undo) {
        World world = loc.getWorld();
        if (undo != null) plugin.undo().capture(undo, loc, (int) Math.ceil(power * 1.8) + 1);
        safe.addBlastZone(loc, power * 2.5 + 2);
        boolean breakBlocks = plugin.getConfig().getBoolean("explosions-break-blocks", true);
        boolean fire = plugin.getConfig().getBoolean("explosions-set-fire", false);
        world.createExplosion(loc, power, fire, breakBlocks, shooter);
    }

    /** An explosion that pushes things around but never breaks blocks. */
    void softBoom(Player shooter, Location loc, float power) {
        safe.addBlastZone(loc, power * 2.5 + 2);
        loc.getWorld().createExplosion(loc, power, false, false, shooter);
    }

    void finishLater(UndoManager.Pending pending, long delay) {
        if (pending == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.undo().finish(pending), delay);
    }

    void launchNearby(Location loc, double radius, double strength) {
        if (!plugin.getConfig().getBoolean("troll.launch-players", true)) return;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            Vector away = p.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(0, 1, 0);
            double falloff = 1 - Math.min(1, away.length() / radius);
            if (falloff <= 0) continue;
            Vector v = away.normalize().multiply(strength * falloff).setY(0.6 + strength * falloff);
            safe.launch(p, p.getVelocity().add(v));
        }
    }

    /** A real firework explosion that can't hurt anyone. */
    void firework(Location at, FireworkEffect effect) {
        Firework fw = at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(effect);
            f.setFireworkMeta(meta);
            f.getPersistentDataContainer().set(fireworkKey, PersistentDataType.BYTE, (byte) 1);
        });
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 1L);
    }

    FireworkEffect randomFirework() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        return FireworkEffect.builder()
                .with(types[r.nextInt(types.length)])
                .withColor(FX.RAINBOW[r.nextInt(FX.RAINBOW.length)], FX.RAINBOW[r.nextInt(FX.RAINBOW.length)])
                .withFade(Color.WHITE)
                .flicker(r.nextBoolean())
                .trail(r.nextBoolean())
                .build();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(fireworkKey)) {
            event.setCancelled(true);
        }
    }

    private void titleNearby(Location loc, double radius, Component title, Component subtitle) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) > radius * radius) continue;
            p.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(1000))));
        }
    }

    private void soundNearby(Location loc, double radius, Sound sound, float volume, float pitch) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < radius * radius) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private void broadcastNuke(Player shooter, String what) {
        if (!plugin.getConfig().getBoolean("troll.nuke-broadcast", true)) return;
        String who = shooter == null ? "Someone" : shooter.getName();
        Bukkit.broadcast(Component.text("☢ " + who + " dropped a " + what + "! ☢", NamedTextColor.DARK_RED, TextDecoration.BOLD));
    }

    // ------------------------------------------------------------------ dispatch

    void detonate(Player shooter, GunType type, Location loc) {
        switch (type) {
            case NUKE -> nuke(shooter, loc, profile("nuke", "NUKE", 3, 10, 7, plugin.stats(GunType.NUKE).explosionPower(), 80, 60, 55, 22, 80));
            case TSAR_BOMBA -> nuke(shooter, loc, profile("tsar-bomba", "TSAR BOMBA", 4, 12, 7.5f, plugin.stats(GunType.TSAR_BOMBA).explosionPower(), 150, 120, 85, 35, 140));
            case CLUSTER_NUKE -> cluster(shooter, loc);
            case CRYO_NUKE -> cryo(shooter, loc);
            case CHICKEN_NUKE -> chickenNuke(shooter, loc);
            case PARTY_NUKE -> partyNuke(shooter, loc);
            case BLACK_HOLE_BOMB -> blackHole(shooter, loc, true);
            default -> rpgExplosion(shooter, loc, plugin.stats(type).explosionPower());
        }
    }

    private Profile profile(String key, String title, int rings, double spacing, float ringPower, float core,
                            double shockwave, int debris, double cloudHeight, double cloudRadius, int countdown) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection(key);
        if (c == null) return new Profile(title, rings, spacing, ringPower, core, shockwave, debris, cloudHeight, cloudRadius, countdown);
        return new Profile(title, c.getInt("rings", rings), c.getDouble("ring-spacing", spacing),
                (float) c.getDouble("ring-power", ringPower), core, c.getDouble("shockwave-radius", shockwave),
                c.getInt("debris", debris), c.getDouble("cloud-height", cloudHeight), c.getDouble("cloud-radius", cloudRadius), countdown);
    }

    /** Particle trail behind a flying bomb. */
    void trail(GunType type, Location loc, int tick) {
        World world = loc.getWorld();
        switch (type) {
            case NUKE, TSAR_BOMBA -> {
                world.spawnParticle(Particle.FLAME, loc, 8, 0.15, 0.15, 0.15, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 5, 0.2, 0.2, 0.2, 0.01);
                FX.dustAt(loc, FX.dust(80, 255, 80, 1.8f), 4, 0.25);
                if (type == GunType.TSAR_BOMBA) FX.ring(loc, 0.8, 10, p -> FX.dustAt(p, FX.dust(255, 40, 40, 1.2f), 1, 0));
            }
            case CLUSTER_NUKE -> {
                world.spawnParticle(Particle.FLAME, loc, 5, 0.1, 0.1, 0.1, 0.03);
                FX.helix(loc, new Vector(0, 1, 0), 0.01, 0.6, 1, 1, tick * 0.6, p -> FX.dustAt(p, FX.dust(255, 120, 0, 1.4f), 2, 0.05));
            }
            case CRYO_NUKE -> {
                world.spawnParticle(Particle.SNOWFLAKE, loc, 8, 0.2, 0.2, 0.2, 0.02);
                FX.dustAt(loc, FX.dust(140, 220, 255, 1.6f), 5, 0.25);
                world.spawnParticle(Particle.END_ROD, loc, 1, 0.1, 0.1, 0.1, 0.01);
            }
            case CHICKEN_NUKE -> {
                world.spawnParticle(Particle.ITEM, loc, 4, 0.2, 0.2, 0.2, 0.05, new ItemStack(Material.FEATHER));
                world.spawnParticle(Particle.CLOUD, loc, 2, 0.1, 0.1, 0.1, 0.01);
                if (tick % 8 == 0) world.playSound(loc, Sound.ENTITY_CHICKEN_AMBIENT, 1f, 1.5f);
            }
            case PARTY_NUKE -> {
                FX.dustAt(loc, FX.dust(FX.RAINBOW[tick % FX.RAINBOW.length], 1.8f), 6, 0.25);
                world.spawnParticle(Particle.FIREWORK, loc, 3, 0.1, 0.1, 0.1, 0.05);
                world.spawnParticle(Particle.NOTE, loc, 1, 0.3, 0.3, 0.3, Math.random());
            }
            case BLACK_HOLE_BOMB -> {
                FX.dustAt(loc, FX.dust(20, 0, 30, 2f), 6, 0.2);
                world.spawnParticle(Particle.REVERSE_PORTAL, loc, 10, 0.2, 0.2, 0.2, 0.05);
                FX.ring(loc, new Vector(0.3, 1, 0.2), 0.9, 12, p -> FX.dustAt(p, FX.dust(170, 60, 255, 1f), 1, 0));
            }
            case GRENADE_LAUNCHER -> {
                world.spawnParticle(Particle.SMOKE, loc, 2, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.SMALL_FLAME, loc, 1, 0.03, 0.03, 0.03, 0.01);
            }
            default -> {
                world.spawnParticle(Particle.FLAME, loc, 4, 0.06, 0.06, 0.06, 0.01);
                world.spawnParticle(Particle.SMOKE, loc, 3, 0.08, 0.08, 0.08, 0.01);
                world.spawnParticle(Particle.FIREWORK, loc, 1, 0, 0, 0, 0.02);
                world.spawnParticle(Particle.LAVA, loc, 1, 0, 0, 0, 0);
            }
        }
        if (type == GunType.RPG && tick % 6 == 0) world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4f, 1.6f);
    }

    // ------------------------------------------------------------------ RPG / grenade

    void rpgExplosion(Player shooter, Location loc, float power) {
        World world = loc.getWorld();
        UndoManager.Pending undo = startUndo(shooter);
        boom(shooter, loc, power, undo);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        FX.burst(loc, Particle.FLAME, (int) (25 * power), 0.25 + power * 0.04);
        FX.burst(loc, Particle.LARGE_SMOKE, (int) (12 * power), 0.15);
        world.spawnParticle(Particle.LAVA, loc, (int) (5 * power), power / 4, power / 4, power / 4, 0);
        FX.ring(loc.clone().add(0, 0.3, 0), power * 1.2, (int) (power * 12), p -> world.spawnParticle(Particle.CLOUD, p, 1, 0.1, 0.1, 0.1, 0.05));
        FX.sphere(loc, power * 0.6, (int) (power * 15), p -> FX.dustAt(p, FX.dust(255, 140 + ThreadLocalRandom.current().nextInt(80), 20, 2f), 1, 0.1));
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3f, 0.6f);
        launchNearby(loc, power * 1.5, 1.2);
        finishLater(undo, 3);
    }

    // ------------------------------------------------------------------ classic nuke / tsar bomba

    void nuke(Player shooter, Location loc, Profile profile) {
        World world = loc.getWorld();
        broadcastNuke(shooter, profile.title());
        soundNearby(loc, 300, Sound.EVENT_RAID_HORN, 4f, profile.rings() > 3 ? 0.7f : 1f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= profile.countdownTicks()) {
                    cancel();
                    detonateNuke(shooter, loc, profile);
                    return;
                }
                double charge = t / (double) profile.countdownTicks();
                world.spawnParticle(Particle.DUST, loc, 30, 0.8, 0.8, 0.8, 0, FX.dust(80, 255, 80, 2.5f));
                world.spawnParticle(Particle.END_ROD, loc, 4, 0.5, 0.5, 0.5, 0.06);
                // Energy sucked into the core faster and faster
                if (t % 2 == 0) FX.implode(loc, Particle.ELECTRIC_SPARK, 4 + charge * 4, 20, 0.3);
                FX.ring(loc, 1.5 + Math.sin(t * 0.3) * 0.5, 24, p -> FX.dustAt(p, FX.dust(255, 255, 80, 1.2f), 1, 0));
                int interval = charge < 0.5 ? 10 : charge < 0.8 ? 5 : 2;
                if (t % interval == 0) world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 4f, 0.8f + (float) charge);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void detonateNuke(Player shooter, Location loc, Profile p) {
        World world = loc.getWorld();
        double totalRadius = p.rings() * p.ringSpacing() + p.ringPower() * 2;
        UndoManager.Pending undo = startUndo(shooter);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        boom(shooter, loc, p.corePower(), undo);

        // Rings of explosions rolling outwards, one blast per tick to spread the lag
        int delay = 2;
        for (int ring = 1; ring <= p.rings(); ring++) {
            double radius = ring * p.ringSpacing();
            int count = 4 + ring * 4;
            double offset = r.nextDouble(Math.PI);
            for (int i = 0; i < count; i++) {
                double a = offset + Math.PI * 2 * i / count;
                Location at = loc.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                at.setY(world.getHighestBlockYAt(at) + r.nextDouble(-1, 1.5));
                if (Math.abs(at.getY() - loc.getY()) > radius) at.setY(loc.getY()); // don't jump to mountaintops/void
                float power = p.ringPower() * (float) (1 - 0.12 * (ring - 1));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    boom(shooter, at, power, undo);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                    FX.burst(at, Particle.FLAME, 40, 0.3);
                    world.spawnParticle(Particle.LAVA, at, 8, 1.5, 1, 1.5, 0);
                }, delay++);
            }
            delay += 2;
        }

        // Flash, sound and shake for everyone in range
        double hearing = totalRadius * 8;
        for (Player pl : world.getPlayers()) {
            double d = pl.getLocation().distance(loc);
            if (d > hearing) continue;
            pl.playSound(pl.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.4f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.5f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.4f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
            if (d < hearing / 2) {
                pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                pl.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false));
                pl.showTitle(Title.title(
                        Component.text("☢", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text(p.title(), NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(1000))));
            }
        }
        world.spawnParticle(Particle.FLASH, loc, 10, 3, 3, 3, 0, Color.WHITE);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 25, 6, 4, 6, 0);
        FX.burst(loc, Particle.FLAME, 400, 0.9);
        FX.burst(loc, Particle.SOUL_FIRE_FLAME, 150, 0.7);
        world.spawnParticle(Particle.LAVA, loc, 150, 8, 4, 8, 0);
        launchNearby(loc, totalRadius * 1.5, 3.0);
        fireball(loc, totalRadius * 0.45);
        throwDebris(loc, p.debris());
        shockwave(loc, p.shockwave());
        mushroomCloud(loc, p.cloudHeight(), p.cloudRadius());
        radiation(loc, totalRadius);

        finishLater(undo, delay + 40); // after the last ring has gone off and debris has landed
    }

    /** A glowing ball of fire that swells out from ground zero. */
    private void fireball(Location loc, double maxRadius) {
        World world = loc.getWorld();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 25) {
                    cancel();
                    return;
                }
                double radius = maxRadius * Math.min(1, t / 15.0);
                int points = (int) Math.min(400, radius * radius * 3);
                Color c = t < 8 ? Color.fromRGB(255, 255, 200) : t < 16 ? Color.fromRGB(255, 160, 30) : Color.fromRGB(200, 60, 20);
                FX.sphere(loc.clone().add(0, radius * 0.4, 0), radius, points, pt -> world.spawnParticle(Particle.DUST, pt, 1, 0.3, 0.3, 0.3, 0, FX.dust(c, 3f)));
                if (t % 3 == 0) FX.sphere(loc, radius * 0.7, points / 3, pt -> world.spawnParticle(Particle.FLAME, pt, 1, 0.2, 0.2, 0.2, 0.02));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Fake blocks flying out of the crater. They vanish when they land so they don't grief or break undo. */
    void throwDebris(Location loc, int amount) {
        World world = loc.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        BlockData[] looks = {
                Material.DIRT.createBlockData(), Material.STONE.createBlockData(), Material.COBBLESTONE.createBlockData(),
                Material.MAGMA_BLOCK.createBlockData(), Material.NETHERRACK.createBlockData(), Material.GRAVEL.createBlockData()
        };
        for (int i = 0; i < amount; i++) {
            FallingBlock fb = spawnDebris(loc.clone().add(0, 2, 0), looks[r.nextInt(looks.length)]);
            fb.setVelocity(new Vector(r.nextDouble(-1.6, 1.6), r.nextDouble(1.0, 2.6), r.nextDouble(-1.6, 1.6)));
        }
    }

    FallingBlock spawnDebris(Location at, BlockData look) {
        return at.getWorld().spawn(at, FallingBlock.class, b -> {
            b.setBlockData(look);
            b.setDropItem(false);
            b.setHurtEntities(false);
            b.setCancelDrop(true);
            b.getPersistentDataContainer().set(plugin.projectileKey(), PersistentDataType.STRING, "debris");
        });
    }

    @EventHandler
    public void onDebrisLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (!fb.getPersistentDataContainer().has(plugin.projectileKey())) return;
        event.setCancelled(true);
        fb.remove();
        Location at = fb.getLocation();
        at.getWorld().spawnParticle(Particle.BLOCK, at, 15, 0.3, 0.3, 0.3, fb.getBlockData());
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 3, 0.2, 0.2, 0.2, 0.02);
    }

    void shockwave(Location loc, double maxRadius) {
        World world = loc.getWorld();
        new BukkitRunnable() {
            double radius = 4;

            @Override
            public void run() {
                if (radius > maxRadius) {
                    cancel();
                    return;
                }
                int points = (int) Math.min(300, radius * 4);
                FX.ring(loc.clone().add(0, 0.5, 0), radius, points, pt -> {
                    world.spawnParticle(Particle.CLOUD, pt, 1, 0.2, 0.3, 0.2, 0.02);
                    world.spawnParticle(Particle.DUST, pt.clone().add(0, 0.6, 0), 1, 0.2, 0.2, 0.2, 0, FX.dust(255, 200, 150, 2f));
                });
                FX.ring(loc.clone().add(0, 0.5, 0), radius * 0.92, points / 4, pt -> world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pt, 1, 0.3, 0.3, 0.3, 0.01));
                radius += 3;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    void mushroomCloud(Location loc, double maxHeight, double maxCapRadius) {
        World world = loc.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 150) {
                    cancel();
                    return;
                }
                double height = Math.min(t * 0.8, maxHeight);
                double stemWidth = 2.5 + Math.min(t * 0.03, maxCapRadius * 0.15);
                for (double y = 0; y < height; y += 2) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, y, 0), 3, stemWidth, 0.6, stemWidth, 0.01);
                    if (t < 60 && y < height * 0.4) world.spawnParticle(Particle.FLAME, loc.clone().add(0, y, 0), 2, stemWidth, 0.5, stemWidth, 0.02);
                }
                // Rolling ring around the stem
                FX.ring(loc.clone().add(0, height * 0.35, 0), stemWidth + 2, 20, pt -> world.spawnParticle(Particle.LARGE_SMOKE, pt, 1, 0.4, 0.4, 0.4, 0.01));
                Location cap = loc.clone().add(0, height, 0);
                double capRadius = 5 + Math.min(t * 0.25, maxCapRadius);
                for (int i = 0; i < 50; i++) {
                    double a = r.nextDouble(Math.PI * 2);
                    double rr = capRadius * Math.sqrt(r.nextDouble());
                    Location c = cap.clone().add(Math.cos(a) * rr, r.nextDouble(-3, 5) * (1 - rr / capRadius * 0.6), Math.sin(a) * rr);
                    world.spawnParticle(Particle.LARGE_SMOKE, c, 1, 0.8, 0.8, 0.8, 0.01);
                    if (t < 80) {
                        Color hot = Color.fromRGB(255, 120 + r.nextInt(100), 20);
                        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, c, 1, 1, 1, 1, 0, FX.fade(hot, Color.fromRGB(70, 70, 70), 3.5f));
                    }
                }
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, cap, 10, capRadius / 2, 2, capRadius / 2, 0.02);
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Lingering green glow. Poison can't kill in vanilla, so it's safe. */
    private void radiation(Location loc, double radius) {
        World world = loc.getWorld();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 30) { // ~15 seconds
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, loc, 120, radius / 2, 3, radius / 2, 0, FX.dust(90, 255, 60, 1.4f));
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc, 40, radius / 2, 3, radius / 2, 0);
                world.spawnParticle(Particle.GLOW, loc, 30, radius / 2, 3, radius / 2, 0);
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) > radius * radius) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
                    if (t % 4 == 0) p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 2f);
                }
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    // ------------------------------------------------------------------ cluster nuke

    private void cluster(Player shooter, Location loc) {
        World world = loc.getWorld();
        broadcastNuke(shooter, "CLUSTER NUKE");
        UndoManager.Pending undo = startUndo(shooter);
        float corePower = plugin.stats(GunType.CLUSTER_NUKE).explosionPower();
        boom(shooter, loc, corePower, undo);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 1, 1, 1, 0);
        FX.burst(loc, Particle.FLAME, 200, 0.6);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 4f, 0.5f);
        launchNearby(loc, corePower * 2, 1.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int bomblets = plugin.getConfig().getInt("cluster-nuke.bomblets", 14);
        float bombletPower = (float) plugin.getConfig().getDouble("cluster-nuke.bomblet-power", 4.0);
        for (int i = 0; i < bomblets; i++) {
            double a = Math.PI * 2 * i / bomblets + r.nextDouble(0.3);
            double speed = r.nextDouble(0.7, 1.5);
            Vector v = new Vector(Math.cos(a) * speed, r.nextDouble(0.9, 1.4), Math.sin(a) * speed);
            new Bolt(shooter, loc.clone().add(0, 1.5, 0), v, 120, 0.6,
                    (at, vel, tick) -> {
                        world.spawnParticle(Particle.FLAME, at, 3, 0.05, 0.05, 0.05, 0.02);
                        world.spawnParticle(Particle.SMOKE, at, 2, 0.05, 0.05, 0.05, 0.01);
                        FX.dustAt(at, FX.dust(255, 60, 20, 1.5f), 2, 0.08);
                        if (tick % 5 == 0) world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 2f);
                    },
                    (at, target) -> {
                        boom(shooter, at, bombletPower, undo);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                        FX.burst(at, Particle.FLAME, 50, 0.3);
                        FX.sphere(at, 2, 30, pt -> FX.dustAt(pt, FX.dust(255, 140, 0, 2f), 1, 0.1));
                    }).steer(b -> b.velocity().add(new Vector(0, -0.06, 0))).start(plugin);
        }
        finishLater(undo, 140);
    }

    // ------------------------------------------------------------------ cryo nuke

    private void cryo(Player shooter, Location loc) {
        World world = loc.getWorld();
        broadcastNuke(shooter, "CRYO NUKE");
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 50) {
                    cancel();
                    cryoDetonate(shooter, loc);
                    return;
                }
                world.spawnParticle(Particle.SNOWFLAKE, loc, 15, 0.8, 0.8, 0.8, 0.02);
                FX.implode(loc, Particle.END_ROD, 5, 10, 0.25);
                FX.ring(loc, new Vector(Math.sin(t * 0.2), 1, Math.cos(t * 0.2)), 2, 20, p -> FX.dustAt(p, FX.dust(160, 230, 255, 1.3f), 1, 0));
                if (t % 6 == 0) world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 3f, 0.6f + t / 50f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cryoDetonate(Player shooter, Location loc) {
        World world = loc.getWorld();
        UndoManager.Pending undo = startUndo(shooter);
        float power = plugin.stats(GunType.CRYO_NUKE).explosionPower();
        boom(shooter, loc, power, undo);
        world.spawnParticle(Particle.FLASH, loc, 5, 2, 2, 2, 0, Color.fromRGB(150, 220, 255));
        FX.burst(loc, Particle.SNOWFLAKE, 400, 1.2);
        FX.burst(loc, Particle.END_ROD, 150, 0.6);
        world.spawnParticle(Particle.ITEM_SNOWBALL, loc, 200, 4, 2, 4, 0.3);
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 4f, 0.5f);
        world.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 4f, 0.5f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.8f);
        soundNearby(loc, 150, Sound.BLOCK_POWDER_SNOW_BREAK, 3f, 0.5f);
        titleNearby(loc, 80, Component.text("❄", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("CRYO DETONATION", NamedTextColor.AQUA, TextDecoration.BOLD));
        double radius = 35;
        // Expanding ice shell
        new BukkitRunnable() {
            double r = 2;

            @Override
            public void run() {
                if (r > radius) {
                    cancel();
                    return;
                }
                FX.sphere(loc, r, (int) Math.min(350, r * r * 2), p -> world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.2, 0.2, 0.2, 0,
                        FX.fade(Color.fromRGB(220, 250, 255), Color.fromRGB(80, 160, 255), 2.5f)));
                FX.ring(loc.clone().add(0, 0.4, 0), r, (int) (r * 5), p -> world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0.1, 0.1, 0.1, 0.01));
                r += 2.5;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        // Freeze everything in range
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity le) trolls.freeze(le);
        }
        launchNearby(loc, power * 2, 1.2);
        // A blanket of snow (temporary, only on empty spots)
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int i = 0; i < 600; i++) {
            double a = rand.nextDouble(Math.PI * 2);
            double d = 25 * Math.sqrt(rand.nextDouble());
            Location at = loc.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Block top = world.getHighestBlockAt(at);
            if (Math.abs(top.getY() - loc.getY()) > 20 || !top.getType().isSolid()) continue;
            tempBlocks.place(top.getRelative(0, 1, 0), Material.SNOW, 600 + rand.nextInt(200));
        }
        // Snowstorm
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 60) { // 15 seconds
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 8, 0), 200, radius / 2, 6, radius / 2, 0.05);
                world.spawnParticle(Particle.WHITE_ASH, loc.clone().add(0, 6, 0), 200, radius / 2, 6, radius / 2, 0.02);
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) > radius * radius) continue;
                    p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 3, 0), 40, 4, 2, 4, 0.03);
                    if (t % 8 == 0) p.playSound(p.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.4f, 0.5f);
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
        finishLater(undo, 10);
    }

    // ------------------------------------------------------------------ chicken nuke

    private void chickenNuke(Player shooter, Location loc) {
        World world = loc.getWorld();
        broadcastNuke(shooter, "CHICKEN NUKE");
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 60) {
                    cancel();
                    chickenDetonate(loc);
                    return;
                }
                world.spawnParticle(Particle.ITEM, loc, 6, 0.6, 0.6, 0.6, 0.05, new ItemStack(Material.FEATHER));
                FX.ring(loc, 1.5, 16, p -> FX.dustAt(p, FX.dust(255, 255, 255, 1.4f), 1, 0));
                int interval = t < 30 ? 10 : t < 50 ? 5 : 2;
                if (t % interval == 0) world.playSound(loc, Sound.ENTITY_CHICKEN_AMBIENT, 4f, 0.8f + t / 60f);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void chickenDetonate(Location loc) {
        World world = loc.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 10, 3, 2, 3, 0);
        world.spawnParticle(Particle.FLASH, loc, 3, 1, 1, 1, 0, Color.WHITE);
        world.spawnParticle(Particle.ITEM, loc, 400, 4, 3, 4, 0.5, new ItemStack(Material.FEATHER));
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 1.2f);
        soundNearby(loc, 150, Sound.ENTITY_CHICKEN_HURT, 3f, 0.5f);
        titleNearby(loc, 80, Component.text("🐔 BAWK 🐔", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("you have been chicken nuked", NamedTextColor.GOLD));
        launchNearby(loc, 25, 2.2);
        List<Chicken> chickens = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Chicken c = world.spawn(loc.clone().add(0, 1, 0), Chicken.class, ch -> {
                ch.setPersistent(false);
                if (r.nextInt(3) == 0) ch.setBaby();
            });
            c.setVelocity(new Vector(r.nextDouble(-1.5, 1.5), r.nextDouble(0.8, 2), r.nextDouble(-1.5, 1.5)));
            chickens.add(c);
        }
        for (Chicken c : chickens) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!c.isValid()) return;
                Location l = c.getLocation();
                world.spawnParticle(Particle.POOF, l, 8, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticle(Particle.ITEM, l, 12, 0.2, 0.2, 0.2, 0.1, new ItemStack(Material.FEATHER));
                world.playSound(l, Sound.ENTITY_CHICKEN_DEATH, 1f, 1.5f);
                c.remove();
            }, 160L + r.nextInt(120));
        }
        // Feather mushroom cloud
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 60) {
                    cancel();
                    return;
                }
                double height = Math.min(t * 0.9, 30);
                for (double y = 0; y < height; y += 2) {
                    world.spawnParticle(Particle.CLOUD, loc.clone().add(0, y, 0), 2, 1.5, 0.5, 1.5, 0.01);
                    world.spawnParticle(Particle.ITEM, loc.clone().add(0, y, 0), 1, 1.5, 0.5, 1.5, 0.01, new ItemStack(Material.FEATHER));
                }
                double cap = 4 + Math.min(t * 0.25, 12);
                world.spawnParticle(Particle.ITEM, loc.clone().add(0, height, 0), 40, cap / 2, 2, cap / 2, 0.02, new ItemStack(Material.FEATHER));
                world.spawnParticle(Particle.DUST, loc.clone().add(0, height, 0), 40, cap / 2, 2, cap / 2, 0, FX.dust(255, 255, 230, 3f));
                world.spawnParticle(Particle.ITEM, loc.clone().add(0, height, 0), 8, cap / 2, 2, cap / 2, 0.02, new ItemStack(Material.EGG));
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    // ------------------------------------------------------------------ party nuke

    private void partyNuke(Player shooter, Location loc) {
        World world = loc.getWorld();
        broadcastNuke(shooter, "PARTY NUKE");
        float[] melody = {1.0f, 1.26f, 1.5f, 2.0f, 1.5f, 1.26f, 1.0f, 0.75f};
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= 60) {
                    cancel();
                    partyDetonate(loc);
                    return;
                }
                FX.ring(loc, 1.2 + (t % 10) * 0.1, 20, p -> FX.dustAt(p, FX.dust(FX.RAINBOW[(t / 3) % FX.RAINBOW.length], 1.5f), 1, 0));
                world.spawnParticle(Particle.NOTE, loc.clone().add(0, 1, 0), 2, 0.6, 0.6, 0.6, Math.random());
                if (t % 5 == 0) world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 3f, melody[(t / 5) % melody.length]);
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void partyDetonate(Location loc) {
        World world = loc.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        world.spawnParticle(Particle.FLASH, loc, 4, 1, 1, 1, 0, FX.RAINBOW[r.nextInt(FX.RAINBOW.length)]);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 6, 2, 2, 2, 0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 1.5f);
        soundNearby(loc, 150, Sound.ENTITY_PLAYER_LEVELUP, 2f, 0.8f);
        soundNearby(loc, 150, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        titleNearby(loc, 100, Component.text("🎉 PARTY NUKE 🎉", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("everybody dance now", NamedTextColor.YELLOW));
        launchNearby(loc, 20, 1.8);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 60 * 60) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
            }
        }
        float[] beat = {0.7f, 0.9f, 1.05f, 1.2f, 1.4f, 1.2f, 1.05f, 0.9f};
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 200) { // 10 seconds of fireworks
                    cancel();
                    return;
                }
                if (t % 4 == 0) {
                    double a = r.nextDouble(Math.PI * 2);
                    double d = r.nextDouble(25);
                    firework(loc.clone().add(Math.cos(a) * d, r.nextDouble(8, 30), Math.sin(a) * d), randomFirework());
                }
                if (t % 10 == 0) {
                    Color c = FX.RAINBOW[(t / 10) % FX.RAINBOW.length];
                    double shell = 4 + (t % 40) * 0.6;
                    FX.sphere(loc.clone().add(0, 4, 0), shell, 200, p -> FX.dustAt(p, FX.dust(c, 2.5f), 1, 0.1));
                }
                if (t % 5 == 0) {
                    world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 3f, 1f);
                    world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 3f, beat[(t / 5) % beat.length]);
                    for (Player p : loc.getNearbyPlayers(40)) Effects.confetti(p.getLocation().add(0, 2.5, 0), 6);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ black hole

    void blackHole(Player shooter, Location center, boolean big) {
        World world = center.getWorld();
        int duration = big ? 220 : 100;
        double pull = big ? 28 : 10;
        double scale = big ? 1.6 : 0.8;
        UndoManager.Pending undo = big ? startUndo(shooter) : null;
        if (big) broadcastNuke(shooter, "BLACK HOLE");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Set<UUID> grabbed = new HashSet<>();
        List<FallingBlock> debris = new ArrayList<>();
        world.playSound(center, Sound.BLOCK_PORTAL_TRAVEL, big ? 2f : 0.6f, 0.5f);

        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= duration) {
                    cancel();
                    debris.forEach(Entity::remove);
                    collapse(shooter, center, big, pull, undo);
                    return;
                }
                // Event horizon and accretion disk
                FX.sphere(center, scale, 40, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(15, 0, 25, 2.2f)));
                double[] radii = {2.2 * scale, 3.2 * scale, 4.4 * scale};
                Color[] colors = {Color.fromRGB(255, 240, 200), Color.fromRGB(255, 150, 40), Color.fromRGB(170, 60, 255)};
                Vector tilt = new Vector(0.25, 1, 0.15);
                for (int k = 0; k < radii.length; k++) {
                    int idx = k;
                    double phase = t * (0.25 - k * 0.05);
                    Vector[] b = FX.basis(tilt);
                    int points = 28 + k * 8;
                    for (int i = 0; i < points; i++) {
                        double a = phase + Math.PI * 2 * i / points;
                        Location p = center.clone().add(b[0].clone().multiply(Math.cos(a) * radii[idx])).add(b[1].clone().multiply(Math.sin(a) * radii[idx]));
                        world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, FX.dust(colors[idx], 1.3f));
                    }
                }
                // Stuff spiralling in
                FX.implode(center, Particle.END_ROD, pull * 0.5, 12, 0.35);
                FX.implode(center, Particle.REVERSE_PORTAL, pull * 0.3, 20, 0.6);
                if (t % 3 == 0) FX.implode(center, Particle.ELECTRIC_SPARK, scale * 6, 10, 0.4);
                if (t % 20 == 0) {
                    world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, big ? 4f : 2f, 0.5f);
                    world.playSound(center, Sound.BLOCK_PORTAL_AMBIENT, big ? 3f : 1.5f, 0.5f);
                }
                // Pull entities in
                for (Entity e : world.getNearbyEntities(center, pull, pull, pull)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    Vector to = center.toVector().subtract(le.getLocation().add(0, 1, 0).toVector());
                    double dist = to.length();
                    if (dist > pull || dist < 0.5) continue;
                    double strength = Math.min(1.2, (big ? 3 : 1.5) / Math.max(1.5, dist));
                    Vector swirl = new Vector(-to.getZ(), 0, to.getX()).normalize().multiply(strength * 0.4);
                    Vector v = to.normalize().multiply(strength).add(swirl);
                    if (le instanceof Player p && grabbed.add(p.getUniqueId())) safe.launch(p, v); else le.setVelocity(v);
                }
                if (big) {
                    // Rip up blocks (via explosions, so claims and undo still work) and fling fake debris in
                    if (t % 20 == 0) {
                        boom(shooter, center, 2.5f + t / 50f, undo);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
                    }
                    if (t % 2 == 0) {
                        double a = r.nextDouble(Math.PI * 2);
                        double d = r.nextDouble(6, pull * 0.8);
                        Location from = center.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                        Block ground = world.getHighestBlockAt(from);
                        if (!ground.getType().isAir() && Math.abs(ground.getY() - center.getY()) < 20) {
                            FallingBlock fb = spawnDebris(ground.getLocation().add(0.5, 1.2, 0.5), ground.getBlockData());
                            fb.setGravity(false);
                            debris.add(fb);
                        }
                    }
                    Iterator<FallingBlock> it = debris.iterator();
                    while (it.hasNext()) {
                        FallingBlock fb = it.next();
                        if (!fb.isValid()) {
                            it.remove();
                            continue;
                        }
                        Vector to = center.toVector().subtract(fb.getLocation().toVector());
                        if (to.length() < 1.5 * scale) {
                            world.spawnParticle(Particle.BLOCK, fb.getLocation(), 12, 0.3, 0.3, 0.3, fb.getBlockData());
                            fb.remove();
                            it.remove();
                            continue;
                        }
                        Vector swirl = new Vector(-to.getZ(), 0, to.getX()).normalize().multiply(0.35);
                        fb.setVelocity(to.normalize().multiply(0.7).add(swirl));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void collapse(Player shooter, Location center, boolean big, double pull, UndoManager.Pending undo) {
        World world = center.getWorld();
        world.spawnParticle(Particle.FLASH, center, 4, 0.5, 0.5, 0.5, 0, Color.fromRGB(200, 120, 255));
        world.spawnParticle(Particle.SONIC_BOOM, center, big ? 12 : 3, 1.5, 1.5, 1.5, 0);
        FX.burst(center, Particle.END_ROD, big ? 300 : 80, big ? 1.2 : 0.6);
        FX.burst(center, Particle.REVERSE_PORTAL, big ? 300 : 100, 1.5);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, big ? 4f : 2f, 0.5f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, big ? 4f : 1.5f, 0.6f);
        if (big) {
            boom(shooter, center, 7f, undo);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 10, 3, 3, 3, 0);
            titleNearby(center, 100, Component.text("◉", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                    Component.text("SINGULARITY COLLAPSE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            finishLater(undo, 20);
        }
        launchNearby(center, pull, big ? 2.5 : 1.2);
    }
}
