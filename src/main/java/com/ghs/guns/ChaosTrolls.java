package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The third batch of trolls: lightning, flashbangs, tornadoes, exploding sheep and more. */
public final class ChaosTrolls {

    private final GHSGuns plugin;
    private final SafeDamage safe;

    public ChaosTrolls(GHSGuns plugin, SafeDamage safe) {
        this.plugin = plugin;
        this.safe = safe;
    }

    private static void push(SafeDamage safe, LivingEntity e, Vector v) {
        if (e instanceof Player p) safe.launch(p, v); else e.setVelocity(v);
    }

    // ------------------------------------------------------------------ lightning (visual only)

    public void lightning(LivingEntity target) {
        World world = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid()) return;
                Location at = target.getLocation().add(r.nextDouble(-1.5, 1.5), 0, r.nextDouble(-1.5, 1.5));
                world.strikeLightningEffect(at); // no damage, no fire
                world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.3);
            }, i * 6L);
        }
        if (target instanceof Player p) p.sendActionBar(Component.text("⚡ smited ⚡", NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    // ------------------------------------------------------------------ bouncy

    public void bouncy(LivingEntity target) {
        World world = target.getWorld();
        if (target instanceof Player p) {
            safe.launch(p, p.getVelocity());
            p.sendActionBar(Component.text("boing boing boing", NamedTextColor.GREEN, TextDecoration.BOLD));
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 300 || !target.isValid()) { // 15 seconds
                    cancel();
                    return;
                }
                if (target.isOnGround()) {
                    Vector v = target.getVelocity();
                    push(safe, target, new Vector(v.getX() * 1.3, 1.0, v.getZ() * 1.3));
                    world.playSound(target.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 1.4f);
                    world.spawnParticle(Particle.ITEM_SLIME, target.getLocation(), 12, 0.3, 0.05, 0.3, 0.1);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ thrown: flashbang, stink bomb, tornado

    public void flashbang(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_METAL_HIT, 1.5f, 1.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.FLASH, at.clone().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0, Color.WHITE);
            world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.4);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 2f);
            for (Player p : at.getNearbyPlayers(14)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false));
                p.spawnParticle(Particle.FLASH, p.getEyeLocation().add(p.getLocation().getDirection()), 1, 0, 0, 0, 0, Color.WHITE);
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 2f, 2f); // ear ringing
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
            }
        }, 20L);
    }

    public void stinkBomb(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_HONEY_BLOCK_SLIDE, 2f, 0.5f);
        world.playSound(at, Sound.ENTITY_SNIFFER_SNIFFING, 2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 16) { // 8 seconds
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 60, 2.5, 1, 2.5, 0,
                        new Particle.DustOptions(Color.fromRGB(110, 140, 30), 2.2f));
                world.spawnParticle(Particle.SNEEZE, at.clone().add(0, 1, 0), 10, 2, 0.8, 2, 0.01);
                if (t % 4 == 0) world.playSound(at, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1.5f, 0.4f);
                for (Player p : at.getNearbyPlayers(5)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                    p.sendActionBar(Component.text("🤢 it smells TERRIBLE in here", NamedTextColor.DARK_GREEN));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public void tornado(Location at) {
        World world = at.getWorld();
        world.playSound(at, Sound.ITEM_ELYTRA_FLYING, 2f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 120) { // 6 seconds
                    cancel();
                    return;
                }
                // Funnel
                for (int i = 0; i < 18; i++) {
                    double y = i * 0.8;
                    double radius = 0.5 + y * 0.3;
                    double a = t * 0.5 + i * 0.7;
                    Location p = at.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                    world.spawnParticle(Particle.CLOUD, p, 1, 0.1, 0.1, 0.1, 0.01);
                    if (i % 3 == 0) world.spawnParticle(Particle.SWEEP_ATTACK, p, 1);
                }
                if (t % 20 == 0) world.playSound(at, Sound.ITEM_ELYTRA_FLYING, 1.5f, 0.7f);
                // Suck in and spin everything nearby
                for (Entity e : world.getNearbyEntities(at.clone().add(0, 4, 0), 7, 8, 7)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    Vector toCenter = at.toVector().subtract(le.getLocation().toVector()).setY(0);
                    Vector swirl = new Vector(-toCenter.getZ(), 0, toCenter.getX());
                    if (swirl.lengthSquared() > 0.01) swirl.normalize();
                    Vector v = toCenter.multiply(0.12).add(swirl.multiply(0.6)).setY(0.35);
                    if (le instanceof Player p && t % 20 == 1) safe.launch(p, v); else le.setVelocity(v);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ exploding sheep

    public void launchSheep(Player shooter) {
        World world = shooter.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location spawn = shooter.getEyeLocation().add(shooter.getLocation().getDirection());
        Sheep sheep = world.spawn(spawn, Sheep.class, s -> {
            s.setColor(DyeColor.values()[r.nextInt(DyeColor.values().length)]);
            s.setInvulnerable(true);
            s.setPersistent(false);
        });
        sheep.setVelocity(shooter.getLocation().getDirection().multiply(1.6).add(new Vector(0, 0.3, 0)));
        world.playSound(spawn, Sound.ENTITY_SHEEP_AMBIENT, 2f, 1.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!sheep.isValid()) {
                    cancel();
                    return;
                }
                t++;
                sheep.setColor(DyeColor.values()[t % DyeColor.values().length]); // rainbow while flying
                world.spawnParticle(Particle.DUST, sheep.getLocation().add(0, 0.6, 0), 3, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Effects.randomColor(), 1.5f));
                if (t % 5 == 0) world.playSound(sheep.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 2f);
                if (t > 50 || (t > 8 && sheep.isOnGround())) {
                    cancel();
                    Location at = sheep.getLocation();
                    sheep.remove();
                    sheepBoom(at);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void sheepBoom(Location at) {
        World world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1.3f);
        world.playSound(at, Sound.ENTITY_SHEEP_HURT, 2f, 0.6f);
        Material[] wool = {Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
                Material.LIGHT_BLUE_WOOL, Material.PURPLE_WOOL, Material.PINK_WOOL, Material.WHITE_WOOL};
        for (Material m : wool) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 25, 1, 1, 1, 0.5, m.createBlockData());
        }
        Effects.confetti(at.clone().add(0, 1, 0), 30);
        for (Entity e : world.getNearbyEntities(at, 5, 5, 5)) {
            if (!(e instanceof LivingEntity le)) continue;
            Vector away = le.getLocation().toVector().subtract(at.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(0, 1, 0);
            push(safe, le, away.normalize().multiply(1.4).setY(1.0));
        }
    }

    // ------------------------------------------------------------------ command-only

    public void fakeAdvancement(Player target) {
        String[] names = {"Professional Idiot", "Skill Issue", "Touch Grass", "Certified Noob", "Fell For It Again",
                "Main Character Syndrome", "Diamond Hands (Wooden)", "Ratio'd by a Chicken"};
        String name = names[ThreadLocalRandom.current().nextInt(names.length)];
        Component adv = Component.text("[" + name + "]", NamedTextColor.GREEN)
                .hoverEvent(Component.text(name, NamedTextColor.GREEN).appendNewline()
                        .append(Component.text("Awarded for being " + target.getName(), NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.translatable("chat.type.advancement.task", Component.text(target.getName()), adv));
        target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    public void slowmo(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 3, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 2, false, false));
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f);
        target.sendActionBar(Component.text("s l o w   m o t i o n", NamedTextColor.GRAY, TextDecoration.ITALIC));
    }

    public void zoomies(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 9, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 3, false, false));
        safe.launch(target, target.getVelocity());
        target.playSound(target.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1.5f, 2f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 100 || !target.isOnline()) {
                    cancel();
                    return;
                }
                target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 2, 0.2, 0, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void screenShake(Player target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 40 || !target.isOnline()) {
                    cancel();
                    return;
                }
                float pitch = Math.max(-90, Math.min(90, target.getPitch() + r.nextFloat(-5f, 5f)));
                target.setRotation(target.getYaw() + r.nextFloat(-5f, 5f), pitch);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1f, 1.2f);
    }

    public void chickenHat(Player target) {
        Chicken hat = target.getWorld().spawn(target.getLocation().add(0, 2, 0), Chicken.class, c -> {
            c.setInvulnerable(true);
            c.setPersistent(false);
            c.customName(Component.text("hat", NamedTextColor.YELLOW));
        });
        target.addPassenger(hat);
        target.playSound(target.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 1.5f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!hat.isValid()) return;
            hat.getWorld().spawnParticle(Particle.POOF, hat.getLocation(), 10, 0.2, 0.2, 0.2, 0.05);
            hat.remove();
        }, 400L);
    }

    public void cakeRain(Player target) {
        World world = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Material[] food = {Material.CAKE, Material.COOKIE, Material.PUMPKIN_PIE, Material.GOLDEN_APPLE, Material.MELON_SLICE};
        for (int i = 0; i < 40; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isOnline()) return;
                Location at = target.getLocation().add(r.nextDouble(-5, 5), 10 + r.nextDouble(4), r.nextDouble(-5, 5));
                Item item = world.dropItem(at, new ItemStack(food[r.nextInt(food.length)]), it -> {
                    it.setCanPlayerPickup(false); // look but don't touch
                    it.setCanMobPickup(false);
                });
                Bukkit.getScheduler().runTaskLater(plugin, item::remove, 160L);
            }, i * 2L);
        }
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
    }

    public static final List<String> COMMAND_TROLLS = List.of(
            "lightning", "bouncy", "flashbang", "stinkbomb", "tornado", "sheep",
            "fakeadvancement", "slowmo", "zoomies", "screenshake", "chickenhat", "cakerain");

    public boolean commandTroll(Player target, String name) {
        switch (name.toLowerCase()) {
            case "lightning" -> lightning(target);
            case "bouncy" -> bouncy(target);
            case "flashbang" -> flashbang(target.getLocation());
            case "stinkbomb" -> stinkBomb(target.getLocation());
            case "tornado" -> tornado(target.getLocation().add(target.getLocation().getDirection().setY(0).normalize().multiply(3)));
            case "sheep" -> sheepBoom(target.getLocation());
            case "fakeadvancement" -> fakeAdvancement(target);
            case "slowmo" -> slowmo(target);
            case "zoomies" -> zoomies(target);
            case "screenshake" -> screenShake(target);
            case "chickenhat" -> chickenHat(target);
            case "cakerain" -> cakeRain(target);
            default -> {
                return false;
            }
        }
        return true;
    }
}
