package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/** Troll items and the effects behind /guns troll. */
public final class TrollListener implements Listener {

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final NamespacedKey trollKey;
    private final MoreTrolls more;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();

    public TrollListener(GHSGuns plugin, SafeDamage safe, MoreTrolls more) {
        this.plugin = plugin;
        this.safe = safe;
        this.more = more;
        this.trollKey = new NamespacedKey(plugin, "troll_projectile");
    }

    // ------------------------------------------------------------------ item use

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        TrollTool tool = plugin.trollTool(event.getPlayer().getInventory().getItemInMainHand());
        if (tool == null) return;
        event.setCancelled(true); // never place the TNT / anvil / egg
        use(event.getPlayer(), tool, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        TrollTool tool = plugin.trollTool(event.getPlayer().getInventory().getItemInMainHand());
        if (tool == null) return;
        event.setCancelled(true);
        use(event.getPlayer(), tool, event.getRightClicked() instanceof LivingEntity le ? le : null);
    }

    private void use(Player player, TrollTool tool, LivingEntity clicked) {
        if (!player.hasPermission("ghsguns.use")) return;
        int now = Bukkit.getCurrentTick();
        Integer ready = cooldowns.get(player.getUniqueId());
        if (ready != null && now < ready) return;

        if (tool.thrown()) {
            cooldowns.put(player.getUniqueId(), now + tool.cooldownTicks());
            Snowball ball = player.launchProjectile(Snowball.class, player.getEyeLocation().getDirection().multiply(1.3));
            ball.setItem(new ItemStack(tool.material()));
            ball.getPersistentDataContainer().set(trollKey, PersistentDataType.STRING, tool.id());
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.7f);
            return;
        }

        LivingEntity target = clicked != null ? clicked : lookTarget(player, tool == TrollTool.FISH_SLAP ? 4 : 50);
        if (target == null) {
            player.sendActionBar(Component.text("No target! Aim at a player or mob.", NamedTextColor.RED));
            return;
        }
        if ((tool == TrollTool.HOT_POTATO || tool == TrollTool.JAIL_WAND) && !(target instanceof Player)) {
            player.sendActionBar(Component.text("That only works on players.", NamedTextColor.RED));
            return;
        }
        cooldowns.put(player.getUniqueId(), now + tool.cooldownTicks());
        beam(player, target);
        switch (tool) {
            case YEET_STICK -> yeet(target);
            case SWAP_STICK -> swap(player, target);
            case FREEZE_RAY -> freeze(target);
            case LEVITATION_WAND -> levitate(target);
            case ANVIL_RAIN -> anvilRain(target);
            case SHRINK_RAY -> more.resize(target, player.isSneaking() ? 3.0 : 0.25, 30);
            case MAGNET -> more.magnet(player, target);
            case FISH_SLAP -> more.fishSlap(player, target);
            case HOT_POTATO -> more.givePotato((Player) target, true);
            case JAIL_WAND -> more.cage((Player) target);
            default -> { }
        }
    }

    private LivingEntity lookTarget(Player player, double range) {
        RayTraceResult hit = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, FluidCollisionMode.NEVER, true, 0.5,
                e -> e != player && e instanceof LivingEntity && !e.isDead());
        return hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    private void beam(Player from, LivingEntity to) {
        Location start = from.getEyeLocation().add(0, -0.2, 0);
        Vector diff = to.getLocation().add(0, 1, 0).toVector().subtract(start.toVector());
        double len = diff.length();
        diff.normalize();
        Particle.DustOptions color = new Particle.DustOptions(Color.fromRGB(200, 120, 255), 0.8f);
        for (double d = 0.8; d < len; d += 0.5) {
            from.getWorld().spawnParticle(Particle.DUST, start.clone().add(diff.clone().multiply(d)), 1, 0, 0, 0, 0, color);
        }
    }

    @EventHandler
    public void onTrollProjectileHit(ProjectileHitEvent event) {
        Projectile p = event.getEntity();
        TrollTool tool = TrollTool.fromId(p.getPersistentDataContainer().get(trollKey, PersistentDataType.STRING));
        if (tool == null) return;
        event.setCancelled(true);
        Location at = p.getLocation();
        p.remove();
        switch (tool) {
            case CHICKEN_BOMB -> chickenBomb(at);
            case FAKE_TNT -> fakeTnt(at);
            case DISCO_BALL -> disco(at);
            case BANANA_PEEL -> more.bananaPeel(at);
            case WEB_SHOOTER -> more.webs(at);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ effects (also used by /guns troll)

    public void yeet(LivingEntity target) {
        World world = target.getWorld();
        Vector v = new Vector(0, 3.5, 0);
        if (target instanceof Player p) {
            safe.launch(p, v);
        } else {
            target.setVelocity(v);
        }
        world.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.6f);
        world.playSound(target.getLocation(), Sound.ENTITY_GOAT_SCREAMING_LONG_JUMP, 1.5f, 1f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 30 || !target.isValid()) {
                    cancel();
                    return;
                }
                Location l = target.getLocation();
                world.spawnParticle(Particle.FIREWORK, l, 4, 0.2, 0.1, 0.2, 0.05);
                world.spawnParticle(Particle.FLAME, l, 2, 0.1, 0, 0.1, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void swap(Player player, LivingEntity target) {
        Location a = player.getLocation();
        Location b = target.getLocation();
        Location aTo = b.clone();
        aTo.setYaw(a.getYaw());
        aTo.setPitch(a.getPitch());
        Location bTo = a.clone();
        bTo.setYaw(b.getYaw());
        bTo.setPitch(b.getPitch());
        player.teleport(aTo);
        target.teleport(bTo);
        for (Location l : List.of(a, b)) {
            l.getWorld().spawnParticle(Particle.PORTAL, l.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
            l.getWorld().playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1f);
        }
    }

    public void freeze(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 6, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 250, false, false)); // 250 = can't jump
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 3, false, false));
        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() - 1, 139)); // frost overlay, no freeze damage
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 1f);
        if (target instanceof Player p) {
            p.showTitle(Title.title(Component.text("❄ FROZEN ❄", NamedTextColor.AQUA, TextDecoration.BOLD), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 50 || !target.isValid()) {
                    cancel();
                    return;
                }
                Location l = target.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.SNOWFLAKE, l, 6, 0.4, 0.8, 0.4, 0.01);
                world.spawnParticle(Particle.BLOCK, l, 3, 0.4, 0.8, 0.4, Material.ICE.createBlockData());
                if (target.getFreezeTicks() < 100) target.setFreezeTicks(139);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void levitate(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 2, false, true));
        if (target instanceof Player p) safe.launch(p, p.getVelocity());
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.2f, 1f);
        world.spawnParticle(Particle.END_ROD, target.getLocation(), 30, 0.4, 0.2, 0.4, 0.05);
    }

    public void anvilRain(LivingEntity target) {
        World world = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        world.playSound(target.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.5f, 0.5f);
        for (int i = 0; i < 14; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid()) return;
                Location spawn = target.getLocation().add(r.nextDouble(-2.5, 2.5), 14 + r.nextDouble(4), r.nextDouble(-2.5, 2.5));
                world.spawn(spawn, FallingBlock.class, fb -> {
                    fb.setBlockData(Material.ANVIL.createBlockData());
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.setCancelDrop(true);
                    // Same tag as nuke debris: vanishes on landing (handled in GunListener)
                    fb.getPersistentDataContainer().set(plugin.projectileKey(), PersistentDataType.STRING, "debris");
                });
            }, i * 3L);
        }
    }

    public void chickenBomb(Location at) {
        World world = at.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.8f);
        world.playSound(at, Sound.ENTITY_CHICKEN_HURT, 2f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.spawnParticle(Particle.ITEM, at, 40, 0.5, 0.5, 0.5, 0.2, new ItemStack(Material.FEATHER));
        List<Chicken> chickens = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Chicken c = world.spawn(at.clone().add(0, 0.5, 0), Chicken.class, ch -> {
                ch.setPersistent(false);
                ch.setRemoveWhenFarAway(true);
                if (r.nextInt(4) == 0) ch.setBaby();
            });
            c.setVelocity(new Vector(r.nextDouble(-0.8, 0.8), r.nextDouble(0.5, 1.1), r.nextDouble(-0.8, 0.8)));
            chickens.add(c);
        }
        // Chickens pop after a few seconds
        for (Chicken c : chickens) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!c.isValid()) return;
                Location l = c.getLocation();
                world.spawnParticle(Particle.POOF, l, 8, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticle(Particle.ITEM, l, 10, 0.2, 0.2, 0.2, 0.1, new ItemStack(Material.FEATHER));
                world.playSound(l, Sound.ENTITY_CHICKEN_DEATH, 1f, 1.5f);
                c.remove();
            }, 100L + r.nextInt(60));
        }
    }

    public void fakeTnt(Location at) {
        at.getWorld().spawn(at, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(60);
            tnt.getPersistentDataContainer().set(trollKey, PersistentDataType.STRING, "fake_tnt");
        });
    }

    @EventHandler
    public void onFakeTntPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) return;
        if (!tnt.getPersistentDataContainer().has(trollKey)) return;
        event.setCancelled(true);
        Location at = tnt.getLocation();
        tnt.remove();
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        Effects.confetti(at.clone().add(0, 1, 0), 40);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.playSound(at, Sound.ENTITY_VILLAGER_CELEBRATE, 2f, 1f);
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BIT, 2f, 0.5f);
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(at) < 16 * 16) {
                    p.sendActionBar(Component.text("gotcha :)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                }
            }
        }, 10L);
    }

    public void disco(Location at) {
        World world = at.getWorld();
        Sound[] notes = {Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_BIT,
                Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE};
        float[] melody = {0.7f, 0.9f, 1.05f, 1.2f, 1.4f, 1.2f, 1.05f, 0.9f};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t > 200) { // 10 seconds
                    cancel();
                    return;
                }
                Location ball = at.clone().add(0, 3, 0);
                world.spawnParticle(Particle.END_ROD, ball, 3, 0.3, 0.3, 0.3, 0.05);
                for (int i = 0; i < 6; i++) {
                    double a = t * 0.2 + i * Math.PI / 3;
                    Location beam = ball.clone().add(Math.cos(a) * 5, -2.5, Math.sin(a) * 5);
                    world.spawnParticle(Particle.DUST, beam, 4, 0.5, 0.1, 0.5, 0,
                            new Particle.DustOptions(Effects.randomColor(), 2f));
                }
                if (t % 5 == 0) {
                    world.playSound(at, notes[(t / 20) % notes.length], 2f, melody[(t / 5) % melody.length]);
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, 1f);
                }
                if (t % 10 == 0) {
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(at) > 12 * 12) continue;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, false, false));
                        if (p.isOnGround()) {
                            p.setVelocity(new Vector(r.nextDouble(-0.2, 0.2), 0.45, r.nextDouble(-0.2, 0.2)));
                        }
                        world.spawnParticle(Particle.NOTE, p.getLocation().add(0, 2.2, 0), 1, 0, 0, 0, r.nextDouble());
                    }
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ admin-only command trolls

    public void fakeOp(Player target) {
        target.sendMessage(Component.text("[Server: Made " + target.getName() + " a server operator]",
                NamedTextColor.GRAY, TextDecoration.ITALIC));
    }

    public void creeper(Player target) {
        Location behind = target.getLocation().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        target.playSound(behind, Sound.ENTITY_CREEPER_PRIMED, 1.5f, 1f);
    }

    public void spin(Player target) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 40 || !target.isOnline()) {
                    cancel();
                    return;
                }
                target.setRotation(target.getYaw() + 36, target.getPitch());
                target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 2, 0.3, 0, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
    }

    public void fakeNuke(Player target) {
        target.playSound(target.getLocation(), Sound.EVENT_RAID_HORN, 4f, 1f);
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (!target.isOnline()) {
                    cancel();
                    return;
                }
                if (count > 0) {
                    target.showTitle(Title.title(
                            Component.text("☢ " + count + " ☢", NamedTextColor.RED, TextDecoration.BOLD),
                            Component.text("INCOMING NUKE", NamedTextColor.DARK_RED),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                    target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 2f, 0.6f + (5 - count) * 0.2f);
                    count--;
                    return;
                }
                cancel();
                target.showTitle(Title.title(Component.text("jk", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        Component.text("you got trolled", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(500))));
                target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1.5f, 1f);
                Effects.confetti(target.getLocation().add(0, 2, 0), 30);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void fakeBan(Player target) {
        target.kick(Component.text("You are banned from this server.\nReason: being too good at the game\n\n", NamedTextColor.RED)
                .append(Component.text("(jk, you can rejoin)", NamedTextColor.GRAY)));
    }

    public void demo(Player target) {
        target.showDemoScreen();
    }

    public static final List<String> COMMAND_TROLLS = Stream.concat(Stream.of(
            "launch", "anvil", "chickens", "freeze", "levitate", "disco", "faketnt",
            "fakeop", "creeper", "spin", "fakenuke", "fakeban", "demo"), MoreTrolls.COMMAND_TROLLS.stream()).toList();

    /** Run a troll on a player from the command. Returns false if the troll name is unknown. */
    public boolean commandTroll(Player target, String name) {
        switch (name.toLowerCase()) {
            case "launch" -> yeet(target);
            case "anvil" -> anvilRain(target);
            case "chickens" -> chickenBomb(target.getLocation());
            case "freeze" -> freeze(target);
            case "levitate" -> levitate(target);
            case "disco" -> disco(target.getLocation());
            case "faketnt" -> fakeTnt(target.getLocation());
            case "fakeop" -> fakeOp(target);
            case "creeper" -> creeper(target);
            case "spin" -> spin(target);
            case "fakenuke" -> fakeNuke(target);
            case "fakeban" -> fakeBan(target);
            case "demo" -> demo(target);
            default -> {
                return more.commandTroll(target, name);
            }
        }
        return true;
    }
}
