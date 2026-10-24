package com.ghs.guns;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** The second batch of trolls: size changes, hot potato, banana peels, chat trolls and more. */
public final class MoreTrolls implements Listener {

    /** An item on the ground that does something when a player walks into it. */
    private record Trap(Item item, String kind, int expiresTick) {}

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final TempBlocks tempBlocks;
    private final NamespacedKey sizeKey;
    private final NamespacedKey permSizeKey;
    private final NamespacedKey trapKey;
    private final List<Trap> traps = new ArrayList<>();
    /** Chat trolls: player -> mode ("drunk" / "uwu"), until a time in millis. Read from the async chat thread. */
    private final Map<UUID, String> chatMode = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatModeUntil = new ConcurrentHashMap<>();

    private UUID potatoHolder;
    private int potatoTicksLeft;

    public MoreTrolls(GHSGuns plugin, SafeDamage safe, TempBlocks tempBlocks) {
        this.plugin = plugin;
        this.safe = safe;
        this.tempBlocks = tempBlocks;
        this.sizeKey = new NamespacedKey(plugin, "troll_size");
        this.permSizeKey = new NamespacedKey(plugin, "perm_size");
        this.trapKey = new NamespacedKey(plugin, "trap");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    // ------------------------------------------------------------------ size

    public void resize(LivingEntity target, double scale, int seconds) {
        AttributeInstance attr = target.getAttribute(Attribute.SCALE);
        if (attr == null) return;
        attr.removeModifier(sizeKey);
        // Transient: never saved, so nobody stays tiny after a restart
        attr.addTransientModifier(new AttributeModifier(sizeKey, scale - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        World world = target.getWorld();
        world.spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
        world.playSound(target.getLocation(), scale < 1 ? Sound.ENTITY_ILLUSIONER_CAST_SPELL : Sound.ENTITY_RAVAGER_ROAR, 1.2f, scale < 1 ? 2f : 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid()) return;
            attr.removeModifier(sizeKey);
            target.getWorld().spawnParticle(Particle.POOF, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
        }, seconds * 20L);
    }

    /** Size that stays until changed back (saved with the player, survives relogs and restarts). 1.0 = normal. */
    public void setPermanentSize(Player target, double scale) {
        AttributeInstance attr = target.getAttribute(Attribute.SCALE);
        if (attr == null) return;
        attr.removeModifier(permSizeKey);
        attr.removeModifier(sizeKey);
        if (scale != 1.0) {
            attr.addModifier(new AttributeModifier(permSizeKey, scale - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        World world = target.getWorld();
        Location c = target.getLocation().add(0, target.getHeight() / 2, 0);
        double r = Math.max(1, scale);
        FX.sphere(c, r, 80, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(200, 120, 255, 1.2f)));
        FX.helix(target.getLocation(), new Vector(0, 1, 0), 2.5 * r, 0.8 * r, 1, 0.1, 0, p -> world.spawnParticle(Particle.WITCH, p, 1, 0, 0, 0, 0));
        world.spawnParticle(Particle.END_ROD, c, 30, r / 2, r, r / 2, 0.05);
        world.playSound(target.getLocation(), scale < 1 ? Sound.ENTITY_ILLUSIONER_CAST_SPELL : scale > 1 ? Sound.ENTITY_RAVAGER_ROAR : Sound.BLOCK_BEACON_POWER_SELECT,
                1.2f, scale < 1 ? 2f : scale > 1 ? 0.8f : 1.2f);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        AttributeInstance attr = event.getPlayer().getAttribute(Attribute.SCALE);
        if (attr != null) attr.removeModifier(sizeKey);
    }

    // ------------------------------------------------------------------ magnet / fish slap / cage / skydrop

    public void magnet(Player puller, LivingEntity target) {
        if (target instanceof Player p) safe.launch(p, p.getVelocity());
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 15 || !target.isValid() || !puller.isOnline()) {
                    cancel();
                    return;
                }
                Vector to = puller.getLocation().toVector().subtract(target.getLocation().toVector());
                if (to.length() < 2) {
                    cancel();
                    return;
                }
                target.setVelocity(to.normalize().multiply(1.1).setY(0.25));
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public void fishSlap(Player slapper, LivingEntity target) {
        Vector dir = target.getLocation().toVector().subtract(slapper.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) dir = slapper.getLocation().getDirection().setY(0);
        Vector v = dir.normalize().multiply(2.8).setY(0.9);
        if (target instanceof Player p) safe.launch(p, v); else target.setVelocity(v);
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.ENTITY_COD_FLOP, 2f, 0.6f);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.6f);
        world.spawnParticle(Particle.SPLASH, target.getLocation().add(0, 1.5, 0), 40, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1.2, 0), 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (plugin.trollTool(attacker.getInventory().getItemInMainHand()) == TrollTool.FISH_SLAP) {
            event.setCancelled(true);
            fishSlap(attacker, victim);
            return;
        }
        // Passing the hot potato
        if (attacker.getUniqueId().equals(potatoHolder) && victim instanceof Player next) {
            givePotato(next, false);
            attacker.sendActionBar(Component.text("Passed it! Phew.", NamedTextColor.GREEN));
        }
    }

    public void cage(Player target) {
        Location base = target.getLocation().getBlock().getLocation();
        World world = target.getWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 2; y++) {
                    boolean wall = Math.abs(x) == 1 || Math.abs(z) == 1 || y == -1 || y == 2;
                    if (!wall) continue;
                    Block b = base.clone().add(x, y, z).getBlock();
                    tempBlocks.place(b, y == -1 || y == 2 ? Material.IRON_BARS : Material.GLASS, 200);
                }
            }
        }
        target.teleport(base.clone().add(0.5, 0, 0.5).setDirection(target.getLocation().getDirection()));
        world.playSound(base, Sound.BLOCK_IRON_DOOR_CLOSE, 1.5f, 0.6f);
        world.playSound(base, Sound.ENTITY_VILLAGER_NO, 1.5f, 1f);
        target.showTitle(Title.title(Component.text("CAUGHT", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("10 second timeout", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
    }

    public void skyDrop(Player target) {
        Location up = target.getLocation().add(0, 120, 0);
        up.setY(Math.min(up.getY(), target.getWorld().getMaxHeight() - 2));
        safe.launch(target, new Vector());
        target.teleport(up);
        target.playSound(target.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 2f, 0.8f);
        target.showTitle(Title.title(Component.text("have a nice trip", NamedTextColor.AQUA), Component.text("see you next fall", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(2000), Duration.ofMillis(500))));
    }

    // ------------------------------------------------------------------ hot potato

    public void givePotato(Player target, boolean fresh) {
        if (fresh || potatoHolder == null) potatoTicksLeft = 300; // 15 seconds
        potatoHolder = target.getUniqueId();
        target.playSound(target.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 1.4f);
        target.showTitle(Title.title(Component.text("🥔 HOT POTATO! 🥔", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Hit someone to pass it before it blows!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(300))));
        if (fresh) {
            Bukkit.broadcast(Component.text(target.getName() + " has the HOT POTATO! Run!", NamedTextColor.GOLD));
        }
    }

    private void tickPotato() {
        if (potatoHolder == null) return;
        Player holder = Bukkit.getPlayer(potatoHolder);
        if (holder == null) {
            potatoHolder = null;
            return;
        }
        potatoTicksLeft -= 2;
        Location l = holder.getLocation().add(0, 2.3, 0);
        holder.getWorld().spawnParticle(Particle.FLAME, l, 2, 0.1, 0.1, 0.1, 0.01);
        holder.getWorld().spawnParticle(Particle.SMOKE, l, 2, 0.1, 0.1, 0.1, 0.01);
        int secs = (potatoTicksLeft + 19) / 20;
        holder.sendActionBar(Component.text("🥔 HOT POTATO: " + secs + "s 🥔", secs <= 3 ? NamedTextColor.RED : NamedTextColor.GOLD, TextDecoration.BOLD));
        if (potatoTicksLeft % 20 == 0) {
            holder.getWorld().playSound(holder.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.5f, 2f - secs / 10f);
        }
        if (potatoTicksLeft > 0) return;
        potatoHolder = null;
        Location at = holder.getLocation();
        World world = holder.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        world.spawnParticle(Particle.ITEM, at.clone().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.3, new ItemStack(Material.BAKED_POTATO));
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 1.2f);
        safe.launch(holder, new Vector(0, 2.2, 0));
        Bukkit.broadcast(Component.text("💥 The hot potato blew up on " + holder.getName() + "!", NamedTextColor.RED));
    }

    // ------------------------------------------------------------------ ground traps: banana peels and fake diamonds

    public void bananaPeel(Location at) {
        dropTrap(at, new ItemStack(Material.YELLOW_DYE), "banana", 1200);
        at.getWorld().playSound(at, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 1.5f);
    }

    public void fakeDiamonds(Player target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = target.getLocation().add(target.getLocation().getDirection().setY(0).normalize().multiply(4));
        for (int i = 0; i < 8; i++) {
            dropTrap(base.clone().add(r.nextDouble(-1.5, 1.5), 0.5, r.nextDouble(-1.5, 1.5)), new ItemStack(Material.DIAMOND), "diamond", 1200);
        }
        target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
    }

    private void dropTrap(Location at, ItemStack look, String kind, int ticks) {
        Item item = at.getWorld().dropItem(at, look, i -> {
            i.setCanPlayerPickup(false);
            i.setCanMobPickup(false);
            i.setVelocity(new Vector());
            i.getPersistentDataContainer().set(trapKey, PersistentDataType.STRING, kind);
        });
        traps.add(new Trap(item, kind, Bukkit.getCurrentTick() + ticks));
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (event.getItem().getPersistentDataContainer().has(trapKey)) event.setCancelled(true);
    }

    private void tickTraps() {
        int now = Bukkit.getCurrentTick();
        Iterator<Trap> it = traps.iterator();
        while (it.hasNext()) {
            Trap trap = it.next();
            Item item = trap.item();
            if (!item.isValid() || now > trap.expiresTick()) {
                item.remove();
                it.remove();
                continue;
            }
            for (Player p : item.getLocation().getNearbyPlayers(trap.kind().equals("banana") ? 0.9 : 1.6)) {
                if (trap.kind().equals("banana")) slip(p); else diamondPoof(p, item.getLocation());
                item.remove();
                it.remove();
                break;
            }
        }
    }

    private void slip(Player p) {
        World world = p.getWorld();
        Vector forward = p.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.01) forward = new Vector(1, 0, 0);
        safe.launch(p, forward.normalize().multiply(1.4).setY(0.7));
        world.playSound(p.getLocation(), Sound.BLOCK_HONEY_BLOCK_SLIDE, 2f, 0.6f);
        world.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 12 || !p.isOnline()) {
                    cancel();
                    return;
                }
                p.setRotation(p.getYaw() + 60, p.getPitch());
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void diamondPoof(Player p, Location at) {
        at.getWorld().spawnParticle(Particle.POOF, at, 10, 0.2, 0.2, 0.2, 0.03);
        p.playSound(at, Sound.ENTITY_VILLAGER_NO, 1.2f, 1f);
        p.sendActionBar(Component.text("they were never real", NamedTextColor.AQUA, TextDecoration.ITALIC));
    }

    // ------------------------------------------------------------------ web shooter

    public void webs(Location at) {
        Block center = at.getBlock();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    tempBlocks.place(center.getRelative(x, y, z), Material.COBWEB, 120);
                }
            }
        }
        at.getWorld().playSound(at, Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 0.8f);
        at.getWorld().spawnParticle(Particle.ITEM, at, 20, 0.6, 0.6, 0.6, 0.1, new ItemStack(Material.STRING));
    }

    // ------------------------------------------------------------------ chat trolls

    public void setChatMode(Player target, String mode, int seconds) {
        chatMode.put(target.getUniqueId(), mode);
        chatModeUntil.put(target.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        String mode = chatMode.get(id);
        if (mode == null) return;
        if (System.currentTimeMillis() > chatModeUntil.getOrDefault(id, 0L)) {
            chatMode.remove(id);
            chatModeUntil.remove(id);
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.message(Component.text(mode.equals("uwu") ? uwu(text) : drunk(text)));
    }

    private static String uwu(String s) {
        String out = s.replace("r", "w").replace("l", "w").replace("R", "W").replace("L", "W")
                .replace("na", "nya").replace("no", "nyo").replace("ove", "uv");
        String[] faces = {" uwu", " owo", " >w<", " ^w^", " :3"};
        return out + faces[ThreadLocalRandom.current().nextInt(faces.length)];
    }

    private static String drunk(String s) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(c);
            if (Character.isLetter(c) && r.nextInt(6) == 0) b.append(c);
            if (c == 's' && r.nextInt(2) == 0) b.append('h');
        }
        if (r.nextBoolean()) b.append(" *hic*");
        return b.toString();
    }

    public void drunk(Player target) {
        setChatMode(target, "drunk", 30);
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 600, 0, false, false));
        target.playSound(target.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.7f);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 0.8f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 30 || !target.isOnline()) {
                    cancel();
                    return;
                }
                if (target.isOnGround()) {
                    target.setVelocity(target.getVelocity().add(new Vector(r.nextDouble(-0.35, 0.35), 0, r.nextDouble(-0.35, 0.35))));
                }
                if (r.nextInt(5) == 0) target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.7f, r.nextFloat(0.6f, 1.2f));
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ------------------------------------------------------------------ scares and fakes

    public void jumpscare(Player target) {
        target.showElderGuardian(true);
        target.playSound(target.getLocation(), Sound.ENTITY_GHAST_HURT, 2f, 0.5f);
        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 2f, 1f);
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
    }

    public void paranoia(Player target) {
        Sound[] sounds = {Sound.ENTITY_CREEPER_PRIMED, Sound.AMBIENT_CAVE, Sound.BLOCK_STONE_STEP, Sound.ENTITY_ZOMBIE_AMBIENT,
                Sound.BLOCK_WOODEN_DOOR_OPEN, Sound.ENTITY_SKELETON_AMBIENT, Sound.ENTITY_ENDERMAN_AMBIENT,
                Sound.ENTITY_TNT_PRIMED, Sound.ENTITY_SPIDER_STEP, Sound.ENTITY_WARDEN_HEARTBEAT};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 15 || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location near = target.getLocation().add(r.nextDouble(-4, 4), r.nextDouble(-1, 2), r.nextDouble(-4, 4));
                target.playSound(near, sounds[r.nextInt(sounds.length)], 1.2f, r.nextFloat(0.8f, 1.1f));
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    public void fakeLeave(Player target) {
        Bukkit.broadcast(Component.translatable("multiplayer.player.left", NamedTextColor.YELLOW, Component.text(target.getName())));
    }

    public void fakeDeath(Player target) {
        String[] deaths = {
                "%s was slain by a Chicken", "%s tripped over a banana peel", "%s was pummeled by a Baby Zombie",
                "%s drowned in a puddle", "%s was blown up by their own hot potato", "%s fell out of the world (somehow)"
        };
        String msg = deaths[ThreadLocalRandom.current().nextInt(deaths.length)].formatted(target.getName());
        Bukkit.broadcast(Component.text(msg));
    }

    public void shuffleHotbar(Player target) {
        PlayerInventory inv = target.getInventory();
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) slots.add(inv.getItem(i));
        Collections.shuffle(slots);
        for (int i = 0; i < 9; i++) inv.setItem(i, slots.get(i));
        target.playSound(target.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 1f);
    }

    public void pigRain(Player target) {
        World world = target.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 15; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isOnline()) return;
                Location at = target.getLocation().add(r.nextDouble(-6, 6), 20 + r.nextDouble(8), r.nextDouble(-6, 6));
                Pig pig = world.spawn(at, Pig.class, p -> {
                    p.setInvulnerable(true); // no porkchop farming, no dead pigs
                    p.setPersistent(false);
                });
                world.playSound(at, Sound.ENTITY_PIG_AMBIENT, 2f, 0.7f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!pig.isValid()) return;
                    world.spawnParticle(Particle.POOF, pig.getLocation(), 10, 0.3, 0.3, 0.3, 0.05);
                    pig.remove();
                }, 300L);
            }, i * 4L);
        }
    }

    // ------------------------------------------------------------------

    private void tick() {
        tickPotato();
        tickTraps();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        chatMode.remove(id);
        chatModeUntil.remove(id);
        if (id.equals(potatoHolder)) potatoHolder = null;
    }

    public void cleanup() {
        for (Trap trap : traps) trap.item().remove();
        traps.clear();
    }

    public static final List<String> COMMAND_TROLLS = List.of(
            "shrink", "giant", "cage", "skydrop", "potato", "drunk", "uwu", "jumpscare", "paranoia",
            "fakediamonds", "fakeleave", "fakedeath", "shuffle", "pigrain", "webs");

    public boolean commandTroll(Player target, String name) {
        switch (name.toLowerCase()) {
            case "shrink" -> resize(target, 0.25, 30);
            case "giant" -> resize(target, 3.0, 30);
            case "cage" -> cage(target);
            case "skydrop" -> skyDrop(target);
            case "potato" -> givePotato(target, true);
            case "drunk" -> drunk(target);
            case "uwu" -> {
                setChatMode(target, "uwu", 60);
                target.playSound(target.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1.5f);
            }
            case "jumpscare" -> jumpscare(target);
            case "paranoia" -> paranoia(target);
            case "fakediamonds" -> fakeDiamonds(target);
            case "fakeleave" -> fakeLeave(target);
            case "fakedeath" -> fakeDeath(target);
            case "shuffle" -> shuffleHotbar(target);
            case "pigrain" -> pigRain(target);
            case "webs" -> webs(target.getLocation());
            default -> {
                return false;
            }
        }
        return true;
    }
}
