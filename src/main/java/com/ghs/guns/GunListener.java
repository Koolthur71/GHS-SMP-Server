package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GunListener implements Listener {

    /** Area where explosion damage to players gets capped so they never die. */
    private record BlastZone(World world, Vector center, double radius, int expiresTick) {}

    private final GHSGuns plugin;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Set<UUID> reloading = new HashSet<>();
    private final List<BlastZone> blastZones = new ArrayList<>();
    /** Players launched by blasts: their fall damage is capped too, until this tick. */
    private final Map<UUID, Integer> launched = new HashMap<>();
    private boolean applyingGunDamage;

    public GunListener(GHSGuns plugin) {
        this.plugin = plugin;
        // Projectile trails + cleanup of expired blast zones
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ------------------------------------------------------------------ input

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        GunType type = plugin.gunType(item);
        if (type == null) return;
        event.setCancelled(true);
        if (!player.hasPermission("ghsguns.use")) return;
        if (reloading.contains(player.getUniqueId())) return;

        if (player.isSneaking()) {
            startReload(player, type);
            return;
        }

        int now = Bukkit.getCurrentTick();
        GunStats stats = plugin.stats(type);
        Integer ready = cooldowns.get(player.getUniqueId());
        if (ready != null && now < ready) return;
        cooldowns.put(player.getUniqueId(), now + stats.cooldownTicks());

        if (getAmmo(item) <= 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.6f);
            startReload(player, type);
            return;
        }

        for (int i = 0; i < stats.shotsPerTrigger(); i++) {
            long delay = (long) i * stats.shotIntervalTicks();
            if (delay == 0) {
                shoot(player, type);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> shoot(player, type), delay);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        reloading.remove(event.getPlayer().getUniqueId());
        launched.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ ammo

    private int getAmmo(ItemStack item) {
        Integer ammo = item.getItemMeta().getPersistentDataContainer().get(plugin.ammoKey(), PersistentDataType.INTEGER);
        return ammo == null ? 0 : ammo;
    }

    private void setAmmo(Player player, int ammo) {
        ItemStack item = player.getInventory().getItemInMainHand();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(plugin.ammoKey(), PersistentDataType.INTEGER, ammo));
        player.getInventory().setItemInMainHand(item);
    }

    private void showAmmo(Player player, GunType type, int ammo) {
        GunStats stats = plugin.stats(type);
        Component name = MiniMessage.miniMessage().deserialize(stats.name());
        NamedTextColor color = ammo == 0 ? NamedTextColor.RED : NamedTextColor.WHITE;
        player.sendActionBar(name.append(Component.text("  " + ammo + " / " + stats.magazine(), color)
                .decoration(TextDecoration.BOLD, false)));
    }

    private void startReload(Player player, GunType type) {
        GunStats stats = plugin.stats(type);
        if (getAmmo(player.getInventory().getItemInMainHand()) >= stats.magazine()) {
            showAmmo(player, type, stats.magazine());
            return;
        }
        UUID id = player.getUniqueId();
        if (!reloading.add(id)) return;
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
        int slot = player.getInventory().getHeldItemSlot();

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.getInventory().getHeldItemSlot() != slot
                        || plugin.gunType(player.getInventory().getItemInMainHand()) != type) {
                    reloading.remove(id);
                    if (player.isOnline()) player.sendActionBar(Component.text("Reload cancelled", NamedTextColor.RED));
                    cancel();
                    return;
                }
                elapsed += 2;
                int bars = 20;
                int filled = Math.min(bars, elapsed * bars / stats.reloadTicks());
                player.sendActionBar(Component.text("Reloading ", NamedTextColor.YELLOW)
                        .append(Component.text("|".repeat(filled), NamedTextColor.GREEN))
                        .append(Component.text("|".repeat(bars - filled), NamedTextColor.DARK_GRAY)));
                if (elapsed >= stats.reloadTicks()) {
                    setAmmo(player, stats.magazine());
                    reloading.remove(id);
                    player.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.8f, 1.8f);
                    showAmmo(player, type, stats.magazine());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ------------------------------------------------------------------ shooting

    private void shoot(Player player, GunType type) {
        if (!player.isOnline() || player.isDead()) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (plugin.gunType(item) != type) return;
        int ammo = getAmmo(item);
        if (ammo <= 0) return;
        setAmmo(player, ammo - 1);
        showAmmo(player, type, ammo - 1);

        GunStats stats = plugin.stats(type);
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        if (stats.spread() > 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            dir.add(new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(stats.spread())).normalize();
        }

        muzzleFlash(player, eye, dir, type);
        if (type.explosive()) {
            launchExplosive(player, type, dir, stats);
        } else {
            fireBullet(player, eye, dir, stats);
        }
    }

    private void muzzleFlash(Player player, Location eye, Vector dir, GunType type) {
        World world = player.getWorld();
        Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(0, -0.15, 0);
        world.spawnParticle(Particle.FLAME, muzzle, 3, 0.03, 0.03, 0.03, 0.01);
        world.spawnParticle(Particle.SMOKE, muzzle, 4, 0.05, 0.05, 0.05, 0.02);
        switch (type) {
            case PISTOL -> world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.6f);
            case SMG -> world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 2.0f);
            case AR -> {
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.3f, 1.2f);
                world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.25f, 2.0f);
            }
            case RPG -> {
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.6f);
                world.spawnParticle(Particle.LARGE_SMOKE, muzzle, 15, 0.2, 0.2, 0.2, 0.05);
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.4)));
            }
            case NUKE -> {
                world.playSound(muzzle, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);
                world.spawnParticle(Particle.LARGE_SMOKE, muzzle, 30, 0.3, 0.3, 0.3, 0.08);
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.8)));
            }
        }
    }

    private void fireBullet(Player shooter, Location eye, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        RayTraceResult hit = world.rayTrace(eye, dir, stats.range(), FluidCollisionMode.NEVER, true, 0.25,
                e -> e != shooter && e instanceof LivingEntity && !e.isDead());

        double distance = stats.range();
        if (hit != null) distance = hit.getHitPosition().distance(eye.toVector());

        // Tracer
        Particle.DustOptions tracer = new Particle.DustOptions(Color.fromRGB(255, 220, 120), 0.6f);
        for (double d = 1.2; d < distance; d += 0.6) {
            world.spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, tracer);
        }
        if (hit == null) return;

        Location at = hit.getHitPosition().toLocation(world);
        if (hit.getHitEntity() instanceof LivingEntity target) {
            world.spawnParticle(Particle.DAMAGE_INDICATOR, at, 4, 0.1, 0.1, 0.1, 0.1);
            world.spawnParticle(Particle.BLOCK, at, 12, 0.15, 0.15, 0.15,
                    Material.REDSTONE_BLOCK.createBlockData());
            target.setNoDamageTicks(0); // let rapid fire actually hit
            applyingGunDamage = true;
            try {
                target.damage(stats.damage(), shooter);
            } finally {
                applyingGunDamage = false;
            }
            target.setVelocity(target.getVelocity().add(dir.clone().multiply(0.15)));
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1.4f);
            if (target instanceof Player && plugin.getConfig().getBoolean("troll.confetti-on-hit", true)) {
                confetti(at);
            }
        } else if (hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            world.spawnParticle(Particle.BLOCK, at, 10, 0.1, 0.1, 0.1, block.getBlockData());
            world.spawnParticle(Particle.CRIT, at, 4, 0.05, 0.05, 0.05, 0.2);
            world.playSound(at, block.getBlockSoundGroup().getHitSound(), 0.8f, 1.2f);
        }
    }

    private void confetti(Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 12; i++) {
            Color c = Color.fromRGB(r.nextInt(256), r.nextInt(256), r.nextInt(256));
            at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(c, 1.2f));
        }
    }

    // ------------------------------------------------------------------ rockets

    private final List<Projectile> rockets = new ArrayList<>();

    private void launchExplosive(Player shooter, GunType type, Vector dir, GunStats stats) {
        Snowball rocket = shooter.launchProjectile(Snowball.class, dir.clone().multiply(stats.projectileSpeed()));
        rocket.setItem(new ItemStack(type == GunType.NUKE ? Material.TNT : Material.FIRE_CHARGE));
        rocket.setGravity(type == GunType.NUKE); // RPG flies straight, nuke arcs
        rocket.getPersistentDataContainer().set(plugin.projectileKey(), PersistentDataType.STRING, type.id());
        rockets.add(rocket);
    }

    private void tick() {
        int now = Bukkit.getCurrentTick();
        blastZones.removeIf(z -> now > z.expiresTick());
        launched.values().removeIf(expires -> now > expires);

        Iterator<Projectile> it = rockets.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            if (!p.isValid()) {
                it.remove();
                continue;
            }
            GunType type = GunType.fromId(p.getPersistentDataContainer().get(plugin.projectileKey(), PersistentDataType.STRING));
            Location loc = p.getLocation();
            World world = p.getWorld();
            if (type == GunType.NUKE) {
                world.spawnParticle(Particle.FLAME, loc, 6, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 4, 0.15, 0.15, 0.15, 0.01);
                world.spawnParticle(Particle.DUST, loc, 3, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(80, 255, 80), 1.5f));
            } else {
                world.spawnParticle(Particle.FLAME, loc, 3, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.SMOKE, loc, 3, 0.08, 0.08, 0.08, 0.01);
                world.spawnParticle(Particle.FIREWORK, loc, 1, 0, 0, 0, 0.02);
            }
            if (p.getTicksLived() % 6 == 0) world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4f, 1.6f);
            // Out of range: detonate in the air
            if (p.getTicksLived() > 200) {
                it.remove();
                p.remove();
                detonate(p, type, loc);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile p = event.getEntity();
        String id = p.getPersistentDataContainer().get(plugin.projectileKey(), PersistentDataType.STRING);
        if (id == null) return;
        GunType type = GunType.fromId(id);
        if (type == null) return;
        event.setCancelled(true);
        Location loc = p.getLocation();
        if (event.getHitEntity() != null) loc = event.getHitEntity().getLocation().add(0, 1, 0);
        rockets.remove(p);
        p.remove();
        detonate(p, type, loc);
    }

    private void detonate(Projectile p, GunType type, Location loc) {
        Player shooter = p.getShooter() instanceof Player pl ? pl : null;
        if (type == GunType.NUKE) {
            nuke(shooter, loc);
        } else {
            rpgExplosion(shooter, loc, plugin.stats(type).explosionPower());
        }
    }

    // ------------------------------------------------------------------ explosions

    private int undoRadius(float power) {
        return (int) Math.ceil(power * 1.8) + 1;
    }

    private void boom(Player shooter, Location loc, float power) {
        World world = loc.getWorld();
        blastZones.add(new BlastZone(world, loc.toVector(), power * 2.5 + 2, Bukkit.getCurrentTick() + 2));
        boolean breakBlocks = plugin.getConfig().getBoolean("explosions-break-blocks", true);
        boolean fire = plugin.getConfig().getBoolean("explosions-set-fire", false);
        world.createExplosion(loc, power, fire, breakBlocks, shooter);
    }

    private void rpgExplosion(Player shooter, Location loc, float power) {
        World world = loc.getWorld();
        UndoManager.Pending undo = snapshot(shooter, loc, undoRadius(power));
        boom(shooter, loc, power);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        world.spawnParticle(Particle.FLAME, loc, 60, 1, 1, 1, 0.2);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 40, 1.2, 1.2, 1.2, 0.08);
        world.spawnParticle(Particle.LAVA, loc, 15, 0.8, 0.8, 0.8, 0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3f, 0.6f);
        launchNearby(loc, power * 1.5, 1.2);
        finishLater(undo, 3);
    }

    private void nuke(Player shooter, Location loc) {
        World world = loc.getWorld();
        float power = plugin.stats(GunType.NUKE).explosionPower();
        if (plugin.getConfig().getBoolean("troll.nuke-broadcast", true)) {
            String who = shooter == null ? "Someone" : shooter.getName();
            Bukkit.broadcast(Component.text("☢ " + who + " dropped a NUKE! ☢", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }

        // Arming countdown: beeps that speed up, glowing core
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t < 60) {
                    world.spawnParticle(Particle.DUST, loc, 20, 0.6, 0.6, 0.6, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 255, 80), 2f));
                    world.spawnParticle(Particle.END_ROD, loc, 3, 0.4, 0.4, 0.4, 0.05);
                    int interval = t < 30 ? 10 : t < 50 ? 5 : 2;
                    if (t % interval == 0) world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 3f, 1f + t / 60f);
                    t++;
                    return;
                }
                cancel();
                detonateNuke(shooter, loc, power);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void detonateNuke(Player shooter, Location loc, float power) {
        World world = loc.getWorld();
        // Snapshot covers the main blast plus the ring of secondary blasts
        UndoManager.Pending undo = snapshot(shooter, loc, undoRadius(power) + 8);

        boom(shooter, loc, power);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            Location ring = loc.clone().add(Math.cos(angle) * 7, r.nextDouble(-1, 2), Math.sin(angle) * 7);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                boom(shooter, ring, power * 0.45f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, ring, 1);
            }, 2L + i);
        }

        // Sound + flash for everyone in range
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distance(loc);
            if (d > 250) continue;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.6f);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
            if (d < 120) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false));
                p.showTitle(Title.title(
                        Component.text("☢", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text("NUCLEAR DETONATION", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(800))));
            }
        }
        world.spawnParticle(Particle.FLASH, loc, 5, 1, 1, 1, 0, Color.WHITE);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 10, 3, 3, 3, 0);
        world.spawnParticle(Particle.FLAME, loc, 300, 6, 3, 6, 0.4);
        launchNearby(loc, power * 2.5, 2.2);

        // Mushroom cloud
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 80) {
                    cancel();
                    return;
                }
                double height = Math.min(t * 0.5, 25);
                for (double y = 0; y < height; y += 1.5) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, y, 0), 2, 1.2, 0.3, 1.2, 0.01);
                }
                Location cap = loc.clone().add(0, height, 0);
                double capRadius = 3 + Math.min(t * 0.15, 9);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    Location c = cap.clone().add(Math.cos(a) * capRadius, r.nextDouble(-1.5, 1.5), Math.sin(a) * capRadius);
                    world.spawnParticle(Particle.LARGE_SMOKE, c, 1, 0.5, 0.5, 0.5, 0.01);
                    if (t < 40) world.spawnParticle(Particle.FLAME, c, 1, 0.5, 0.5, 0.5, 0.01);
                }
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, cap, 6, capRadius / 2, 1, capRadius / 2, 0.02);
            }
        }.runTaskTimer(plugin, 2L, 2L);

        finishLater(undo, 20); // after the ring explosions are done
    }

    private void launchNearby(Location loc, double radius, double strength) {
        if (!plugin.getConfig().getBoolean("troll.launch-players", true)) return;
        String exempt = plugin.getConfig().getString("anticheat-exempt-command", "");
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            if (exempt != null && !exempt.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exempt.replace("%player%", p.getName()));
            }
            Vector away = p.getLocation().toVector().subtract(loc.toVector());
            if (away.lengthSquared() < 0.01) away = new Vector(0, 1, 0);
            double falloff = 1 - Math.min(1, away.length() / radius);
            Vector v = away.normalize().multiply(strength * falloff).setY(0.6 + strength * falloff);
            p.setVelocity(p.getVelocity().add(v));
            launched.put(p.getUniqueId(), Bukkit.getCurrentTick() + 300);
        }
    }

    private UndoManager.Pending snapshot(Player shooter, Location loc, int radius) {
        if (!plugin.getConfig().getBoolean("explosions-break-blocks", true)) return null;
        return plugin.undo().begin(loc, radius,
                shooter == null ? null : shooter.getUniqueId(),
                shooter == null ? "unknown" : shooter.getName());
    }

    private void finishLater(UndoManager.Pending pending, long delay) {
        if (pending == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.undo().finish(pending), delay);
    }

    // ------------------------------------------------------------------ never kill players

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isGunDamage(event, player)) return;
        double minHealth = Math.max(0.5, plugin.getConfig().getDouble("min-health", 1.0));
        if (player.getHealth() - event.getFinalDamage() >= minHealth) return;

        // This hit would kill: leave them on min-health instead
        event.setCancelled(true);
        if (player.getHealth() > minHealth) player.setHealth(minHealth);
        player.playHurtAnimation(0);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.3);
    }

    private boolean isGunDamage(EntityDamageEvent event, Player player) {
        if (applyingGunDamage) return true;
        var cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL && launched.remove(player.getUniqueId()) != null) return true;
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return false;
        Vector pos = player.getLocation().toVector();
        for (BlastZone zone : blastZones) {
            if (zone.world().equals(player.getWorld()) && zone.center().distance(pos) <= zone.radius()) return true;
        }
        return false;
    }
}
