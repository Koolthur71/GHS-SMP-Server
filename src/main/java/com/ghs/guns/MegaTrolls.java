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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The fourth batch of trolls: meteors, UFOs, rockets, rainbows, piñatas, creeper parties and more. */
public final class MegaTrolls implements Listener {

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final Nukes nukes;
    private final MoreTrolls more;
    private final NamespacedKey partyCreeperKey;
    /** Piñata players -> tick their piñata time ends. */
    private final Map<UUID, Integer> pinatas = new HashMap<>();

    public MegaTrolls(GHSGuns plugin, SafeDamage safe, Nukes nukes, MoreTrolls more) {
        this.plugin = plugin;
        this.safe = safe;
        this.nukes = nukes;
        this.more = more;
        this.partyCreeperKey = new NamespacedKey(plugin, "party_creeper");
    }

    private void push(LivingEntity e, Vector v) {
        if (e instanceof Player p) safe.launch(p, v); else e.setVelocity(v);
    }

    private void title(LivingEntity target, Component title, Component sub) {
        if (target instanceof Player p) {
            p.showTitle(Title.title(title, sub, Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(500))));
        }
    }

    // ------------------------------------------------------------------ meteor shower

    public void meteors(LivingEntity target) {
        World world = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        title(target, Component.text("☄ INCOMING ☄", NamedTextColor.GOLD, TextDecoration.BOLD), Component.text("look up", NamedTextColor.GRAY));
        world.playSound(target.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 2f, 0.5f);
        for (int i = 0; i < 8; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid()) return;
                Location impact = target.getLocation().add(r.nextDouble(-5, 5), 0, r.nextDouble(-5, 5));
                Location start = impact.clone().add(r.nextDouble(-12, 12), 40, r.nextDouble(-12, 12));
                Vector v = impact.toVector().subtract(start.toVector()).normalize().multiply(1.6);
                new Bolt(null, start, v, 80, 0.8,
                        (at, vel, tick) -> {
                            FX.sphere(at, 0.8, 18, p -> world.spawnParticle(Particle.FLAME, p, 1, 0, 0, 0, 0));
                            world.spawnParticle(Particle.DUST, at, 6, 0.4, 0.4, 0.4, 0, FX.dust(255, 120, 20, 2.5f));
                            world.spawnParticle(Particle.LAVA, at, 2, 0.3, 0.3, 0.3, 0);
                            world.spawnParticle(Particle.LARGE_SMOKE, at.clone().subtract(vel), 4, 0.3, 0.3, 0.3, 0.01);
                            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().subtract(vel.clone().multiply(2)), 2, 0.2, 0.2, 0.2, 0.01);
                            if (tick % 4 == 0) world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
                        },
                        (at, hit) -> {
                            nukes.softBoom(null, at, 2.5f);
                            world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                            FX.burst(at, Particle.FLAME, 80, 0.4);
                            FX.burst(at, Particle.LAVA, 10, 0.2);
                            world.spawnParticle(Particle.BLOCK, at, 40, 1, 0.3, 1, Material.MAGMA_BLOCK.createBlockData());
                            FX.ring(at.clone().add(0, 0.3, 0), 3, 30, p -> world.spawnParticle(Particle.LARGE_SMOKE, p, 1, 0.2, 0.2, 0.2, 0.02));
                            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
                        }).start(plugin);
            }, i * 12L);
        }
    }

    // ------------------------------------------------------------------ rocket ride

    public void rocket(LivingEntity target) {
        World world = target.getWorld();
        push(target, new Vector(0, 3.2, 0));
        world.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!target.isValid()) {
                    cancel();
                    return;
                }
                Location l = target.getLocation();
                FX.helix(l.clone().subtract(0, 1.5, 0), new Vector(0, 1, 0), 1.5, 0.5, 1, 0.15, t * 0.8,
                        p -> world.spawnParticle(Particle.FIREWORK, p, 1, 0, 0, 0, 0.01));
                world.spawnParticle(Particle.FLAME, l, 6, 0.15, 0, 0.15, 0.05);
                world.spawnParticle(Particle.DUST, l, 4, 0.3, 0.3, 0.3, 0, FX.dust(FX.RAINBOW[t % FX.RAINBOW.length], 1.5f));
                if (t++ >= 20) {
                    cancel();
                    Location top = l.clone().add(0, 1, 0);
                    nukes.firework(top, nukes.randomFirework());
                    nukes.firework(top, nukes.randomFirework());
                    FX.sphere(top, 3, 120, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(FX.RAINBOW[ThreadLocalRandom.current().nextInt(FX.RAINBOW.length)], 1.6f)));
                    world.playSound(top, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3f, 1f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ rainbow trail

    public void rainbow(LivingEntity target) {
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 1.5f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 600 || !target.isValid()) { // 30 seconds
                    cancel();
                    return;
                }
                Location l = target.getLocation();
                Vector back = l.getDirection().setY(0);
                if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
                back.normalize().multiply(-0.4);
                for (int i = 0; i < FX.RAINBOW.length; i++) {
                    Location p = l.clone().add(back).add(0, 0.15 + i * 0.12, 0);
                    world.spawnParticle(Particle.DUST, p, 1, 0.05, 0, 0.05, 0, FX.dust(FX.RAINBOW[i], 1.1f));
                }
                if (t % 2 == 0) {
                    Location halo = l.clone().add(0, target.getHeight() + 0.4, 0);
                    FX.ring(halo, 0.45, 14, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0,
                            FX.dust(FX.RAINBOW[(t / 2) % FX.RAINBOW.length], 0.8f)));
                }
                if (t % 20 == 0) world.spawnParticle(Particle.END_ROD, l.clone().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ piñata

    public void pinata(LivingEntity target) {
        if (!(target instanceof Player p)) return;
        pinatas.put(p.getUniqueId(), Bukkit.getCurrentTick() + 600);
        title(p, Component.text("🎉 YOU'RE A PIÑATA 🎉", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("everyone, hit them!", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text(p.getName() + " is a piñata for 30 seconds! Hit them for candy!", NamedTextColor.LIGHT_PURPLE));
        new BukkitRunnable() {
            @Override
            public void run() {
                Integer until = pinatas.get(p.getUniqueId());
                if (until == null || Bukkit.getCurrentTick() > until || !p.isOnline()) {
                    pinatas.remove(p.getUniqueId());
                    cancel();
                    return;
                }
                Location hat = p.getLocation().add(0, p.getHeight() + 0.5, 0);
                FX.ring(hat, 0.4, 10, pt -> p.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, FX.dust(Effects.randomColor(), 1f)));
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPinataHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player p) || !pinatas.containsKey(p.getUniqueId())) return;
        World world = p.getWorld();
        Location at = p.getLocation().add(0, 1.2, 0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Material[] candy = {Material.COOKIE, Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.HONEY_BOTTLE,
                Material.PUMPKIN_PIE, Material.CAKE, Material.GOLDEN_CARROT};
        for (int i = 0; i < 6; i++) {
            Item item = world.dropItem(at, new ItemStack(candy[r.nextInt(candy.length)]), it -> {
                it.setCanPlayerPickup(false);
                it.setCanMobPickup(false);
            });
            item.setVelocity(new Vector(r.nextDouble(-0.4, 0.4), r.nextDouble(0.3, 0.6), r.nextDouble(-0.4, 0.4)));
            Bukkit.getScheduler().runTaskLater(plugin, item::remove, 80L);
        }
        Effects.confetti(at, 20);
        world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.4f);
        world.playSound(at, Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1.6f);
    }

    // ------------------------------------------------------------------ creeper party

    public void creeperParty(Location center) {
        World world = center.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        world.playSound(center, Sound.ENTITY_CREEPER_PRIMED, 2f, 1.5f);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Location spawn = center.clone().add(Math.cos(a) * 4, 0.5, Math.sin(a) * 4);
            Creeper c = world.spawn(spawn, Creeper.class, cr -> {
                cr.setPowered(r.nextBoolean());
                cr.setInvulnerable(true);
                cr.setPersistent(false);
                cr.customName(Component.text("party creeper", NamedTextColor.LIGHT_PURPLE));
                cr.getPersistentDataContainer().set(partyCreeperKey, PersistentDataType.BYTE, (byte) 1);
            });
            FX.burst(spawn.clone().add(0, 1, 0), Particle.END_ROD, 20, 0.2);
            world.spawnParticle(Particle.POOF, spawn, 10, 0.3, 0.5, 0.3, 0.05);
            // Pop on their own after a while if they never reach anyone
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (c.isValid()) popCreeper(c);
            }, 300L + r.nextInt(100));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreeperPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Creeper c) || !c.getPersistentDataContainer().has(partyCreeperKey)) return;
        event.setCancelled(true);
        popCreeper(c);
    }

    private void popCreeper(Creeper c) {
        Location at = c.getLocation().add(0, 1, 0);
        World world = at.getWorld();
        c.remove();
        nukes.firework(at, FireworkEffect.builder().with(FireworkEffect.Type.CREEPER)
                .withColor(Color.LIME, Color.GREEN).withFade(Color.WHITE).flicker(true).build());
        Effects.confetti(at, 40);
        world.spawnParticle(Particle.EXPLOSION, at, 2, 0.3, 0.3, 0.3, 0);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.8f);
        world.playSound(at, Sound.ENTITY_VILLAGER_CELEBRATE, 1.5f, 1.2f);
        world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    // ------------------------------------------------------------------ UFO abduction

    public void ufo(LivingEntity target) {
        World world = target.getWorld();
        title(target, Component.text("👽", NamedTextColor.GREEN, TextDecoration.BOLD), Component.text("you have been selected", NamedTextColor.GREEN));
        world.playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
        if (target instanceof Player p) safe.launch(p, new Vector());
        new BukkitRunnable() {
            int t = 0;
            Location ship;

            @Override
            public void run() {
                if (!target.isValid()) {
                    cancel();
                    return;
                }
                if (ship == null) ship = target.getLocation().add(0, 10, 0);
                if (t < 140) {
                    // Hover above the target and pull them up
                    ship.setX(target.getLocation().getX());
                    ship.setZ(target.getLocation().getZ());
                } else {
                    ship.add(1.5, 0.8, 0.5); // zoom off
                }
                drawSaucer(world, ship, t);
                if (t < 140) {
                    Location feet = target.getLocation();
                    FX.helix(feet, new Vector(0, 1, 0), Math.max(0.5, ship.getY() - feet.getY()), 1.0, 0.3, 0.25, t * 0.25,
                            p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(120, 255, 120, 0.9f)));
                    world.spawnParticle(Particle.END_ROD, feet.clone().add(0, 1, 0), 2, 0.6, 1, 0.6, 0.02);
                    double gap = ship.getY() - 1.5 - feet.getY();
                    target.setVelocity(new Vector(0, gap > 0.5 ? 0.25 : 0.03, 0));
                    target.setFallDistance(0);
                    if (t % 20 == 0) world.playSound(feet, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.6f);
                    if (t % 30 == 0) world.playSound(feet, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1f, 2f);
                }
                if (t == 140) {
                    title(target, Component.text("probed", NamedTextColor.GREEN, TextDecoration.ITALIC), Component.text("thank you for your cooperation", NamedTextColor.GRAY));
                    if (target instanceof Player p) safe.launch(p, new Vector(0, -0.2, 0));
                    world.playSound(ship, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 2f);
                }
                if (t++ > 170) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawSaucer(World world, Location c, int t) {
        Particle.DustOptions hull = FX.dust(170, 170, 185, 1.4f);
        for (double r = 0.6; r <= 3; r += 0.6) {
            double y = -(3 - r) * 0.12;
            FX.ring(c.clone().add(0, y, 0), r, (int) (r * 10), p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, hull));
        }
        FX.sphere(c.clone().add(0, 0.5, 0), 0.9, 24, p -> {
            if (p.getY() >= c.getY() + 0.4) world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(150, 230, 255, 1f));
        });
        for (int i = 0; i < 8; i++) {
            double a = t * 0.2 + Math.PI * 2 * i / 8;
            Location light = c.clone().add(Math.cos(a) * 3, -0.1, Math.sin(a) * 3);
            world.spawnParticle(Particle.DUST, light, 1, 0, 0, 0, 0, FX.dust(FX.RAINBOW[(i + t / 4) % FX.RAINBOW.length], 1.5f));
        }
    }

    // ------------------------------------------------------------------ orbit

    public void orbit(LivingEntity target) {
        World world = target.getWorld();
        Location center = target.getLocation().add(0, 3, 0);
        if (target instanceof Player p) safe.launch(p, new Vector(0, 0.5, 0));
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.2f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!target.isValid()) {
                    cancel();
                    return;
                }
                double a = t * 0.3;
                Location want = center.clone().add(Math.cos(a) * 3.5, Math.sin(t * 0.1) * 0.8, Math.sin(a) * 3.5);
                Vector v = want.toVector().subtract(target.getLocation().toVector()).multiply(0.5);
                target.setVelocity(v);
                target.setFallDistance(0);
                world.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0, FX.dust(FX.RAINBOW[t % FX.RAINBOW.length], 1.3f));
                world.spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 1, 0.1, 0.1, 0.1, 0.01);
                FX.sphere(center, 0.4, 10, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 255, 200, 1.2f)));
                if (t++ >= 100) {
                    cancel();
                    Vector fling = new Vector(-Math.sin(a), 0.5, Math.cos(a)).multiply(2);
                    push(target, fling);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    // ------------------------------------------------------------------ size wand

    public void cycleSize(Player user, LivingEntity target) {
        if (!(target instanceof Player p)) return;
        if (user.isSneaking()) {
            more.setPermanentSize(p, 1.0);
            user.sendActionBar(Component.text(p.getName() + " is back to normal size", NamedTextColor.GREEN));
            return;
        }
        AttributeInstance attr = p.getAttribute(Attribute.SCALE);
        double now = attr == null ? 1 : attr.getValue();
        double[] steps = {0.25, 0.5, 1.0, 1.5, 2.5, 4.0};
        double next = steps[0];
        for (double s : steps) {
            if (s > now + 0.01) {
                next = s;
                break;
            }
        }
        more.setPermanentSize(p, next);
        user.sendActionBar(Component.text(p.getName() + " is now size " + next + " (sneak + click to reset)", NamedTextColor.LIGHT_PURPLE));
    }

    // ------------------------------------------------------------------ command-only

    public void hiccups(Player target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;
            int next = 20;

            @Override
            public void run() {
                if (t++ > 600 || !target.isOnline()) {
                    cancel();
                    return;
                }
                if (t < next) return;
                next = t + r.nextInt(20, 60);
                if (target.isOnGround()) target.setVelocity(target.getVelocity().setY(0.45));
                target.getWorld().spawnParticle(Particle.BUBBLE_POP, target.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.05);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1.8f);
                target.sendActionBar(Component.text("*hic*", NamedTextColor.GRAY, TextDecoration.ITALIC));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void lowGravity(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 400, 3, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 400, 0, false, false));
        safe.launch(target, target.getVelocity());
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
        target.sendActionBar(Component.text("🌙 moon gravity 🌙", NamedTextColor.WHITE));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 100 || !target.isOnline()) {
                    cancel();
                    return;
                }
                target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation(), 2, 0.3, 0.1, 0.3, 0.01);
                target.getWorld().spawnParticle(Particle.WHITE_ASH, target.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public void earthquake(Player target) {
        World world = target.getWorld();
        Location center = target.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        BlockData stone = Material.STONE.createBlockData();
        world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 100) { // 5 seconds
                    cancel();
                    return;
                }
                for (Player p : center.getNearbyPlayers(15)) {
                    float pitch = Math.max(-90, Math.min(90, p.getPitch() + r.nextFloat(-3f, 3f)));
                    p.setRotation(p.getYaw() + r.nextFloat(-3f, 3f), pitch);
                    if (p.isOnGround() && t % 5 == 0) {
                        safe.launch(p, p.getVelocity().add(new Vector(r.nextDouble(-0.2, 0.2), 0.15, r.nextDouble(-0.2, 0.2))));
                    }
                    var under = p.getLocation().subtract(0, 0.5, 0).getBlock();
                    if (!under.getType().isAir()) world.spawnParticle(Particle.BLOCK, p.getLocation(), 8, 1.5, 0.1, 1.5, under.getBlockData());
                }
                world.spawnParticle(Particle.BLOCK, center, 40, 8, 0.2, 8, stone);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 3, 6, 0.2, 6, 0.01);
                if (t % 4 == 0) {
                    FallingBlock rock = nukes.spawnDebris(center.clone().add(r.nextDouble(-8, 8), 15, r.nextDouble(-8, 8)), stone);
                    rock.setVelocity(new Vector(0, -0.3, 0));
                }
                if (t % 10 == 0) {
                    world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.5f);
                    world.playSound(center, Sound.ENTITY_RAVAGER_STEP, 2f, 0.5f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void fireworkShow(Player target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 30 || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location at = target.getLocation().add(r.nextDouble(-8, 8), r.nextDouble(6, 16), r.nextDouble(-8, 8));
                nukes.firework(at, nukes.randomFirework());
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    public void fakeExplode(Player target) {
        World world = target.getWorld();
        Location at = target.getLocation();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        FX.burst(at, Particle.FLAME, 80, 0.3);
        FX.burst(at, Particle.LARGE_SMOKE, 40, 0.15);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
        safe.launch(target, new Vector(0, 1.6, 0));
    }

    public static final List<String> COMMAND_TROLLS = List.of(
            "meteor", "rocket", "rainbow", "pinata", "creeperparty", "ufo", "orbit", "hiccups", "lowgravity",
            "earthquake", "blackhole", "fireworks", "explode");

    public boolean commandTroll(Player target, String name) {
        switch (name.toLowerCase()) {
            case "meteor" -> meteors(target);
            case "rocket" -> rocket(target);
            case "rainbow" -> rainbow(target);
            case "pinata" -> pinata(target);
            case "creeperparty" -> creeperParty(target.getLocation());
            case "ufo" -> ufo(target);
            case "orbit" -> orbit(target);
            case "hiccups" -> hiccups(target);
            case "lowgravity" -> lowGravity(target);
            case "earthquake" -> earthquake(target);
            case "blackhole" -> nukes.blackHole(null, target.getLocation().add(target.getLocation().getDirection().setY(0).normalize().multiply(5)).add(0, 2, 0), false);
            case "fireworks" -> fireworkShow(target);
            case "explode" -> fakeExplode(target);
            default -> {
                return false;
            }
        }
        return true;
    }
}
