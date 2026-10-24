package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Set<UUID> reloading = new HashSet<>();
    private final Set<UUID> scoped = new HashSet<>();
    private final List<Projectile> rockets = new ArrayList<>();
    private final Nukes nukes;
    private final SciFiGuns scifi;

    public GunListener(GHSGuns plugin, SafeDamage safe, Nukes nukes, SciFiGuns scifi) {
        this.plugin = plugin;
        this.safe = safe;
        this.nukes = nukes;
        this.scifi = scifi;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRockets, 1L, 1L);
    }

    // ------------------------------------------------------------------ input

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        GunType type = plugin.gunType(item);
        if (type == null) return;
        Action action = event.getAction();

        // Sniper: left-click toggles the scope
        if (type == GunType.SNIPER && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (!plugin.canUse(player)) return;
            toggleScope(player);
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        if (!plugin.canUse(player)) return;
        if (reloading.contains(player.getUniqueId())) return;

        if (player.isSneaking()) {
            startReload(player, type);
            return;
        }

        int now = Bukkit.getCurrentTick();
        GunStats stats = plugin.stats(type);
        // With cooldowns off, still ignore a second click in the same tick (one click can fire two events)
        int cooldown = plugin.getConfig().getBoolean("gun-cooldowns", false) ? stats.cooldownTicks() : 1;
        Integer ready = cooldowns.get(player.getUniqueId());
        if (ready != null && now < ready) return;
        cooldowns.put(player.getUniqueId(), now + cooldown);

        if (type == GunType.GRAVITY_GUN) { // grab/throw, no ammo
            scifi.gravity(player);
            return;
        }
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
    public void onSwitchItem(PlayerItemHeldEvent event) {
        unscope(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        reloading.remove(id);
        unscope(event.getPlayer());
    }

    private void toggleScope(Player player) {
        if (scoped.contains(player.getUniqueId())) {
            unscope(player);
        } else {
            scoped.add(player.getUniqueId());
            // Heavy slowness zooms the camera in on Java; Bedrock gets the slowdown + overlay effect
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 6, false, false, false));
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 1f);
        }
    }

    private void unscope(Player player) {
        if (scoped.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 1f, 1f);
        }
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
        unscope(player);
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
        if (!plugin.getConfig().getBoolean("infinite-ammo", false)) {
            ammo--;
            setAmmo(player, ammo);
        }
        showAmmo(player, type, ammo);

        GunStats stats = plugin.stats(type);
        Location eye = player.getEyeLocation();
        Vector aim = eye.getDirection();
        // Scoped sniper is perfectly accurate
        double spread = type == GunType.SNIPER && scoped.contains(player.getUniqueId()) ? 0 : stats.spread();

        muzzleFlash(player, eye, aim, type);
        if (type == GunType.FLAMETHROWER) {
            flame(player, eye, aim, stats);
            return;
        }
        if (type.kind() == GunType.Kind.SPECIAL) {
            for (int i = 0; i < stats.pellets(); i++) scifi.fire(player, type, eye, spread(aim, spread), stats);
            return;
        }
        for (int i = 0; i < stats.pellets(); i++) {
            Vector dir = spread(aim, spread);
            if (type.explosive()) {
                launchExplosive(player, type, dir, stats);
            } else {
                fireBullet(player, type, eye, dir, stats);
            }
        }
    }

    private Vector spread(Vector aim, double spread) {
        Vector dir = aim.clone();
        if (spread <= 0) return dir;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return dir.add(new Vector(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).multiply(spread)).normalize();
    }

    private void muzzleFlash(Player player, Location eye, Vector dir, GunType type) {
        World world = player.getWorld();
        Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(0, -0.15, 0);
        if (type.kind() == GunType.Kind.SPECIAL) return; // sci-fi guns draw their own
        if (type != GunType.FLAMETHROWER) {
            world.spawnParticle(Particle.FLAME, muzzle, 3, 0.03, 0.03, 0.03, 0.01);
            world.spawnParticle(Particle.SMOKE, muzzle, 4, 0.05, 0.05, 0.05, 0.02);
        }
        switch (type) {
            case PISTOL -> world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.6f);
            case SMG -> world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 2.0f);
            case AR -> {
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.3f, 1.2f);
                world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.25f, 2.0f);
            }
            case SNIPER -> {
                world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.6f);
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 2f, 0.6f);
                world.spawnParticle(Particle.LARGE_SMOKE, muzzle, 6, 0.1, 0.1, 0.1, 0.03);
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.25)));
            }
            case SHOTGUN -> {
                world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 0.8f);
                world.spawnParticle(Particle.FLAME, muzzle, 10, 0.1, 0.1, 0.1, 0.05);
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.3)));
            }
            case MINIGUN -> {
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.8f);
                world.playSound(muzzle, Sound.BLOCK_PISTON_EXTEND, 0.3f, 2.0f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 1, false, false, false));
            }
            case FLAMETHROWER -> {
                world.playSound(muzzle, Sound.ITEM_FIRECHARGE_USE, 0.6f, 0.7f);
                world.playSound(muzzle, Sound.BLOCK_FIRE_AMBIENT, 1f, 1.5f);
            }
            case GRENADE_LAUNCHER -> world.playSound(muzzle, Sound.ENTITY_SNOWBALL_THROW, 1.5f, 0.5f);
            case RPG -> {
                world.playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.6f);
                world.spawnParticle(Particle.LARGE_SMOKE, muzzle, 15, 0.2, 0.2, 0.2, 0.05);
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.4)));
            }
            case NUKE, TSAR_BOMBA, CLUSTER_NUKE, CRYO_NUKE, CHICKEN_NUKE, PARTY_NUKE, BLACK_HOLE_BOMB -> {
                world.playSound(muzzle, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);
                world.spawnParticle(Particle.LARGE_SMOKE, muzzle, 30, 0.3, 0.3, 0.3, 0.08);
                player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.8)));
            }
        }
    }

    private void fireBullet(Player shooter, GunType type, Location eye, Vector dir, GunStats stats) {
        World world = shooter.getWorld();
        RayTraceResult hit = world.rayTrace(eye, dir, stats.range(), FluidCollisionMode.NEVER, true, 0.25,
                e -> e != shooter && e instanceof LivingEntity && !e.isDead());

        double distance = stats.range();
        if (hit != null) distance = hit.getHitPosition().distance(eye.toVector());

        // Tracer
        boolean sniper = type == GunType.SNIPER;
        Particle.DustOptions tracer = sniper
                ? new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.9f)
                : new Particle.DustOptions(Color.fromRGB(255, 220, 120), 0.6f);
        double step = sniper ? 0.4 : 0.6;
        for (double d = 1.2; d < distance; d += step) {
            world.spawnParticle(Particle.DUST, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, tracer);
        }
        if (hit == null) return;

        Location at = hit.getHitPosition().toLocation(world);
        if (hit.getHitEntity() instanceof LivingEntity target) {
            double damage = stats.damage();
            boolean headshot = at.getY() >= target.getEyeLocation().getY() - 0.3;
            if (headshot && stats.headshotMultiplier() > 1) {
                damage *= stats.headshotMultiplier();
                shooter.playSound(shooter.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 2f);
                shooter.sendActionBar(Component.text("HEADSHOT!", NamedTextColor.RED, TextDecoration.BOLD));
                world.spawnParticle(Particle.CRIT, at, 20, 0.2, 0.2, 0.2, 0.4);
            }
            world.spawnParticle(Particle.DAMAGE_INDICATOR, at, 4, 0.1, 0.1, 0.1, 0.1);
            world.spawnParticle(Particle.BLOCK, at, 12, 0.15, 0.15, 0.15, Material.REDSTONE_BLOCK.createBlockData());
            target.setNoDamageTicks(0); // let rapid fire and shotgun pellets all hit
            double finalDamage = damage;
            safe.gunDamage(() -> target.damage(finalDamage, shooter));
            double knock = type == GunType.SHOTGUN ? 0.35 : sniper ? 0.6 : 0.15;
            target.setVelocity(target.getVelocity().add(dir.clone().multiply(knock)));
            shooter.playSound(shooter.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1.4f);
            if (target instanceof Player && plugin.getConfig().getBoolean("troll.confetti-on-hit", true)) {
                Effects.confetti(at, 12);
            }
        } else if (hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            world.spawnParticle(Particle.BLOCK, at, 10, 0.1, 0.1, 0.1, block.getBlockData());
            world.spawnParticle(Particle.CRIT, at, 4, 0.05, 0.05, 0.05, 0.2);
            world.playSound(at, block.getBlockSoundGroup().getHitSound(), 0.8f, 1.2f);
        }
    }

    private void flame(Player shooter, Location eye, Vector aim, GunStats stats) {
        World world = shooter.getWorld();
        double range = stats.range();
        Location start = eye.clone().add(0, -0.2, 0);
        for (int i = 0; i < 14; i++) {
            Vector v = spread(aim, stats.spread()).multiply(range / 10);
            Location p = start.clone().add(aim.clone().multiply(0.8));
            world.spawnParticle(Particle.FLAME, p, 0, v.getX(), v.getY(), v.getZ(), 1);
            if (i % 3 == 0) world.spawnParticle(Particle.SMOKE, p, 0, v.getX(), v.getY(), v.getZ(), 0.8);
        }
        for (Entity e : world.getNearbyEntities(start, range, range, range)) {
            if (e == shooter || !(e instanceof LivingEntity target) || target.isDead()) continue;
            Vector to = target.getLocation().add(0, 1, 0).toVector().subtract(start.toVector());
            if (to.length() > range || to.clone().normalize().dot(aim) < 0.88) continue;
            if (target instanceof Player p) safe.markBurning(p, 100);
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            target.setNoDamageTicks(0);
            safe.gunDamage(() -> target.damage(stats.damage(), shooter));
        }
    }

    // ------------------------------------------------------------------ rockets

    private void launchExplosive(Player shooter, GunType type, Vector dir, GunStats stats) {
        Snowball rocket = shooter.launchProjectile(Snowball.class, dir.clone().multiply(stats.projectileSpeed()));
        rocket.setItem(new ItemStack(switch (type) {
            case NUKE, TSAR_BOMBA -> Material.TNT;
            case CLUSTER_NUKE -> Material.TNT_MINECART;
            case CRYO_NUKE -> Material.BLUE_ICE;
            case CHICKEN_NUKE -> Material.EGG;
            case PARTY_NUKE -> Material.CAKE;
            case BLACK_HOLE_BOMB -> Material.CRYING_OBSIDIAN;
            case GRENADE_LAUNCHER -> Material.MAGMA_CREAM;
            default -> Material.FIRE_CHARGE;
        }));
        rocket.setGravity(type != GunType.RPG); // RPG flies straight, the rest arc
        rocket.getPersistentDataContainer().set(plugin.projectileKey(), PersistentDataType.STRING, type.id());
        rockets.add(rocket);
    }

    private void tickRockets() {
        Iterator<Projectile> it = rockets.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            if (!p.isValid()) {
                it.remove();
                continue;
            }
            GunType type = GunType.fromId(p.getPersistentDataContainer().get(plugin.projectileKey(), PersistentDataType.STRING));
            Location loc = p.getLocation();
            nukes.trail(type, loc, p.getTicksLived());
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
        nukes.detonate(shooter, type, loc);
    }
}
