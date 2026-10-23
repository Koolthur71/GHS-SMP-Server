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
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
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

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Set<UUID> reloading = new HashSet<>();
    private final Set<UUID> scoped = new HashSet<>();
    private final List<Projectile> rockets = new ArrayList<>();

    public GunListener(GHSGuns plugin, SafeDamage safe) {
        this.plugin = plugin;
        this.safe = safe;
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
            toggleScope(player);
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
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
        setAmmo(player, ammo - 1);
        showAmmo(player, type, ammo - 1);

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
            case NUKE -> {
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
            case NUKE -> Material.TNT;
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
            World world = p.getWorld();
            switch (type) {
                case NUKE -> {
                    world.spawnParticle(Particle.FLAME, loc, 8, 0.15, 0.15, 0.15, 0.02);
                    world.spawnParticle(Particle.LARGE_SMOKE, loc, 5, 0.2, 0.2, 0.2, 0.01);
                    world.spawnParticle(Particle.DUST, loc, 4, 0.25, 0.25, 0.25, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 255, 80), 1.8f));
                }
                case GRENADE_LAUNCHER -> world.spawnParticle(Particle.SMOKE, loc, 2, 0.05, 0.05, 0.05, 0.01);
                default -> {
                    world.spawnParticle(Particle.FLAME, loc, 3, 0.05, 0.05, 0.05, 0.01);
                    world.spawnParticle(Particle.SMOKE, loc, 3, 0.08, 0.08, 0.08, 0.01);
                    world.spawnParticle(Particle.FIREWORK, loc, 1, 0, 0, 0, 0.02);
                }
            }
            if (type != GunType.GRENADE_LAUNCHER && p.getTicksLived() % 6 == 0) {
                world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4f, 1.6f);
            }
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

    private UndoManager.Pending startUndo(Player shooter) {
        if (!plugin.getConfig().getBoolean("explosions-break-blocks", true)) return null;
        return plugin.undo().begin(shooter == null ? "unknown" : shooter.getName());
    }

    /** One explosion: snapshot the area for undo, make it non-lethal, then blow it up. */
    private void boom(Player shooter, Location loc, float power, UndoManager.Pending undo) {
        World world = loc.getWorld();
        if (undo != null) plugin.undo().capture(undo, loc, (int) Math.ceil(power * 1.8) + 1);
        safe.addBlastZone(loc, power * 2.5 + 2);
        boolean breakBlocks = plugin.getConfig().getBoolean("explosions-break-blocks", true);
        boolean fire = plugin.getConfig().getBoolean("explosions-set-fire", false);
        world.createExplosion(loc, power, fire, breakBlocks, shooter);
    }

    private void finishLater(UndoManager.Pending pending, long delay) {
        if (pending == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.undo().finish(pending), delay);
    }

    private void rpgExplosion(Player shooter, Location loc, float power) {
        World world = loc.getWorld();
        UndoManager.Pending undo = startUndo(shooter);
        boom(shooter, loc, power, undo);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        world.spawnParticle(Particle.FLAME, loc, (int) (15 * power), power / 4, power / 4, power / 4, 0.2);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, (int) (10 * power), power / 3, power / 3, power / 3, 0.08);
        world.spawnParticle(Particle.LAVA, loc, 15, 0.8, 0.8, 0.8, 0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.7f);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 3f, 0.6f);
        launchNearby(loc, power * 1.5, 1.2);
        finishLater(undo, 3);
    }

    private void nuke(Player shooter, Location loc) {
        World world = loc.getWorld();
        if (plugin.getConfig().getBoolean("troll.nuke-broadcast", true)) {
            String who = shooter == null ? "Someone" : shooter.getName();
            Bukkit.broadcast(Component.text("☢ " + who + " dropped a NUKE! ☢", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
        // Air raid siren for everyone nearby
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 300 * 300) {
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 4f, 1f);
            }
        }

        // Arming countdown: beeps that speed up, glowing core
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t < 80) {
                    world.spawnParticle(Particle.DUST, loc, 30, 0.8, 0.8, 0.8, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 255, 80), 2.5f));
                    world.spawnParticle(Particle.END_ROD, loc, 4, 0.5, 0.5, 0.5, 0.06);
                    int interval = t < 40 ? 10 : t < 65 ? 5 : 2;
                    if (t % interval == 0) world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 4f, 0.8f + t / 60f);
                    t++;
                    return;
                }
                cancel();
                detonateNuke(shooter, loc);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void detonateNuke(Player shooter, Location loc) {
        World world = loc.getWorld();
        GunStats stats = plugin.stats(GunType.NUKE);
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("nuke");
        int rings = cfg == null ? 3 : cfg.getInt("rings", 3);
        double ringSpacing = cfg == null ? 10 : cfg.getDouble("ring-spacing", 10);
        float ringPower = (float) (cfg == null ? 7 : cfg.getDouble("ring-power", 7));
        double shockwave = cfg == null ? 80 : cfg.getDouble("shockwave-radius", 80);
        int debris = cfg == null ? 60 : cfg.getInt("debris", 60);
        double totalRadius = rings * ringSpacing + ringPower * 2;

        UndoManager.Pending undo = startUndo(shooter);
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Core blast
        boom(shooter, loc, stats.explosionPower(), undo);

        // Rings of explosions rolling outwards, one blast per tick to spread the lag
        int delay = 2;
        for (int ring = 1; ring <= rings; ring++) {
            double radius = ring * ringSpacing;
            int count = 4 + ring * 4;
            double offset = r.nextDouble(Math.PI);
            for (int i = 0; i < count; i++) {
                double a = offset + Math.PI * 2 * i / count;
                Location at = loc.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                at.setY(world.getHighestBlockYAt(at) + r.nextDouble(-1, 1.5));
                if (Math.abs(at.getY() - loc.getY()) > radius) at.setY(loc.getY()); // don't jump to mountaintops/void
                float power = ringPower * (float) (1 - 0.15 * (ring - 1));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    boom(shooter, at, power, undo);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                    world.spawnParticle(Particle.FLAME, at, 40, 2, 2, 2, 0.15);
                }, delay++);
            }
            delay += 2;
        }

        // Sound, flash and shake for everyone in range
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distance(loc);
            if (d > 400) continue;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.4f);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.4f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
            if (d < 200) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false));
                p.showTitle(Title.title(
                        Component.text("☢", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text("NUCLEAR DETONATION", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(1000))));
            }
        }
        world.spawnParticle(Particle.FLASH, loc, 10, 3, 3, 3, 0, Color.WHITE);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 25, 6, 4, 6, 0);
        world.spawnParticle(Particle.FLAME, loc, 800, 10, 5, 10, 0.5);
        world.spawnParticle(Particle.LAVA, loc, 150, 8, 4, 8, 0);
        launchNearby(loc, totalRadius * 1.5, 3.0);
        throwDebris(loc, debris);
        shockwave(loc, shockwave);
        mushroomCloud(loc);
        radiation(loc, totalRadius);

        finishLater(undo, delay + 40); // after the last ring has gone off and debris has landed
    }

    /** Fake blocks flying out of the crater. They vanish when they land so they don't grief or break undo. */
    private void throwDebris(Location loc, int amount) {
        World world = loc.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        BlockData[] looks = {
                Material.DIRT.createBlockData(), Material.STONE.createBlockData(), Material.COBBLESTONE.createBlockData(),
                Material.MAGMA_BLOCK.createBlockData(), Material.NETHERRACK.createBlockData(), Material.GRAVEL.createBlockData()
        };
        for (int i = 0; i < amount; i++) {
            FallingBlock fb = world.spawn(loc.clone().add(0, 2, 0), FallingBlock.class, b -> {
                b.setBlockData(looks[r.nextInt(looks.length)]);
                b.setDropItem(false);
                b.setHurtEntities(false);
                b.setCancelDrop(true);
                b.getPersistentDataContainer().set(plugin.projectileKey(), PersistentDataType.STRING, "debris");
            });
            fb.setVelocity(new Vector(r.nextDouble(-1.6, 1.6), r.nextDouble(1.0, 2.6), r.nextDouble(-1.6, 1.6)));
        }
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

    private void shockwave(Location loc, double maxRadius) {
        World world = loc.getWorld();
        new BukkitRunnable() {
            double radius = 4;

            @Override
            public void run() {
                if (radius > maxRadius) {
                    cancel();
                    return;
                }
                int points = (int) Math.min(240, radius * 4);
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    Location p = loc.clone().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius);
                    world.spawnParticle(Particle.CLOUD, p, 1, 0.2, 0.3, 0.2, 0.02);
                    if (i % 4 == 0) world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p, 1, 0.3, 0.3, 0.3, 0.01);
                }
                radius += 3;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void mushroomCloud(Location loc) {
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
                double height = Math.min(t * 0.8, 55);
                double stemWidth = 2.5 + Math.min(t * 0.03, 3);
                for (double y = 0; y < height; y += 2) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, y, 0), 3, stemWidth, 0.6, stemWidth, 0.01);
                    if (t < 60 && y < height * 0.4) world.spawnParticle(Particle.FLAME, loc.clone().add(0, y, 0), 2, stemWidth, 0.5, stemWidth, 0.02);
                }
                Location cap = loc.clone().add(0, height, 0);
                double capRadius = 5 + Math.min(t * 0.2, 22);
                for (int i = 0; i < 40; i++) {
                    double a = Math.PI * 2 * i / 40;
                    double rr = capRadius * Math.sqrt(r.nextDouble());
                    Location c = cap.clone().add(Math.cos(a) * rr, r.nextDouble(-3, 4), Math.sin(a) * rr);
                    world.spawnParticle(Particle.LARGE_SMOKE, c, 1, 0.8, 0.8, 0.8, 0.01);
                    if (t < 70) {
                        world.spawnParticle(Particle.DUST, c, 1, 1, 1, 1, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 120 + r.nextInt(100), 20), 3f));
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
                world.spawnParticle(Particle.DUST, loc, 120, radius / 2, 3, radius / 2, 0,
                        new Particle.DustOptions(Color.fromRGB(90, 255, 60), 1.4f));
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc, 40, radius / 2, 3, radius / 2, 0);
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distanceSquared(loc) > radius * radius) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
                    if (t % 4 == 0) p.playSound(p.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 2f);
                }
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    private void launchNearby(Location loc, double radius, double strength) {
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
}
