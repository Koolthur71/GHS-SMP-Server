package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** The second batch of tools: farming, building, scanning, movement and admin wands. */
public final class MoreTools implements Listener {

    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART);

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final TempBlocks tempBlocks;
    private final NamespacedKey caughtKey;
    private final NamespacedKey caughtNameKey;
    private final NamespacedKey caughtBabyKey;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Set<UUID> divebombing = new HashSet<>();
    private final Map<Material, ItemStack> smeltCache = new HashMap<>();

    public MoreTools(GHSGuns plugin, SafeDamage safe, TempBlocks tempBlocks) {
        this.plugin = plugin;
        this.safe = safe;
        this.tempBlocks = tempBlocks;
        this.caughtKey = new NamespacedKey(plugin, "caught_mob");
        this.caughtNameKey = new NamespacedKey(plugin, "caught_name");
        this.caughtBabyKey = new NamespacedKey(plugin, "caught_baby");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeld, 2L, 2L);
    }

    private boolean onCooldown(Player player, int ticks) {
        int now = Bukkit.getCurrentTick();
        Integer ready = cooldowns.get(player.getUniqueId());
        if (ready != null && now < ready) return true;
        cooldowns.put(player.getUniqueId(), now + ticks);
        return false;
    }

    private ToolType held(Player p) {
        ToolType main = plugin.toolType(p.getInventory().getItemInMainHand());
        return main != null ? main : plugin.toolType(p.getInventory().getItemInOffHand());
    }

    // ------------------------------------------------------------------ input

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ToolType tool = plugin.toolType(player.getInventory().getItemInMainHand());
        if (tool == null) return;
        switch (tool) {
            case HARVEST_SCYTHE, BUILDERS_WAND, ORE_SCANNER, MOB_CATCHER, HEALING_STAFF, DASH_BLADE, QUAKE_HAMMER,
                 NIGHT_VISION_GOGGLES, RECALL_COMPASS, TIME_WAND, WEATHER_WAND, ITEM_MAGNET, SKY_WALKER -> event.setCancelled(true);
            default -> {
                return;
            }
        }
        if (!plugin.canUse(player)) return;
        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        switch (tool) {
            case HARVEST_SCYTHE -> harvest(player, clicked);
            case BUILDERS_WAND -> {
                if (clicked != null) build(player, clicked, face);
            }
            case ORE_SCANNER -> scan(player);
            case MOB_CATCHER -> {
                if (clicked != null) release(player, clicked.getRelative(face));
            }
            case HEALING_STAFF -> heal(player);
            case DASH_BLADE -> dash(player);
            case QUAKE_HAMMER -> quake(player);
            case NIGHT_VISION_GOGGLES -> nightVision(player);
            case RECALL_COMPASS -> recall(player);
            case TIME_WAND -> timeWand(player);
            case WEATHER_WAND -> weatherWand(player);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ToolType tool = plugin.toolType(player.getInventory().getItemInMainHand());
        if (tool == null) return;
        if (tool == ToolType.MOB_CATCHER) {
            event.setCancelled(true);
            if (!plugin.canUse(player)) return;
            if (event.getRightClicked() instanceof LivingEntity le) capture(player, le);
        } else if (tool == ToolType.HEALING_STAFF) {
            event.setCancelled(true);
            if (!plugin.canUse(player)) return;
            if (event.getRightClicked() instanceof LivingEntity le) healTarget(player, le);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        divebombing.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ smelter pick

    @EventHandler(ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        if (plugin.toolType(event.getPlayer().getInventory().getItemInMainHand()) != ToolType.SMELTER_PICK || !plugin.canUse(event.getPlayer())) return;
        boolean any = false;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            ItemStack result = smelted(stack.getType());
            if (result == null) continue;
            ItemStack out = result.clone();
            out.setAmount(Math.min(out.getMaxStackSize(), result.getAmount() * stack.getAmount()));
            item.setItemStack(out);
            any = true;
        }
        if (!any) return;
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        World world = at.getWorld();
        world.spawnParticle(Particle.FLAME, at, 20, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticle(Particle.LAVA, at, 3, 0.2, 0.2, 0.2, 0);
        world.spawnParticle(Particle.DUST, at, 10, 0.3, 0.3, 0.3, 0, FX.dust(255, 120, 20, 1.2f));
        world.playSound(at, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1f, 1.2f);
    }

    private ItemStack smelted(Material input) {
        if (smeltCache.containsKey(input)) return smeltCache.get(input);
        ItemStack found = null;
        ItemStack probe = new ItemStack(input);
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            if (it.next() instanceof FurnaceRecipe fr && fr.getInputChoice().test(probe)) {
                found = fr.getResult();
                break;
            }
        }
        smeltCache.put(input, found);
        return found;
    }

    // ------------------------------------------------------------------ harvest scythe

    private void harvest(Player player, Block clicked) {
        if (onCooldown(player, 10)) return;
        Block center = clicked;
        if (center == null) {
            RayTraceResult hit = player.rayTraceBlocks(6, FluidCollisionMode.NEVER);
            if (hit == null || hit.getHitBlock() == null) return;
            center = hit.getHitBlock();
        }
        World world = player.getWorld();
        int harvested = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -1; y <= 1; y++) {
                    Block b = center.getRelative(x, y, z);
                    Material type = b.getType();
                    if (!CROPS.contains(type) || !(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) continue;
                    if (!player.breakBlock(b)) continue;
                    Material soil = b.getRelative(BlockFace.DOWN).getType();
                    if (soil == Material.FARMLAND || soil == Material.SOUL_SAND) b.setType(type);
                    Location at = b.getLocation().add(0.5, 0.3, 0.5);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, at, 4, 0.3, 0.2, 0.3, 0);
                    world.spawnParticle(Particle.COMPOSTER, at, 3, 0.3, 0.2, 0.3, 0);
                    harvested++;
                }
            }
        }
        Location c = center.getLocation().add(0.5, 1, 0.5);
        FX.ring(c, 3.5, 40, p -> world.spawnParticle(Particle.SWEEP_ATTACK, p, 1));
        FX.ring(c, 3.5, 60, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 220, 80, 1f)));
        world.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        if (harvested > 0) world.playSound(c, Sound.BLOCK_CROP_BREAK, 1.2f, 1f);
    }

    // ------------------------------------------------------------------ builder's wand

    private void build(Player player, Block clicked, BlockFace face) {
        if (onCooldown(player, 6)) return;
        Material type = clicked.getType();
        if (!type.isItem() || !type.isSolid() || clicked.getState() instanceof org.bukkit.block.TileState) {
            player.sendActionBar(Component.text("Can't build with that block.", NamedTextColor.RED));
            return;
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int available = creative ? Integer.MAX_VALUE : countItems(player, type);
        if (available <= 0) {
            player.sendActionBar(Component.text("You need " + type.name().toLowerCase().replace('_', ' ') + " in your inventory.", NamedTextColor.RED));
            return;
        }
        // Grow across the face: same-type blocks whose front is free
        List<Block> targets = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(clicked);
        seen.add(clicked);
        BlockFace[] spread = spreadFaces(face);
        while (!queue.isEmpty() && targets.size() < Math.min(25, available)) {
            Block b = queue.poll();
            Block front = b.getRelative(face);
            if (b.getType() != type || !front.isReplaceable()) continue;
            targets.add(front);
            for (BlockFace f : spread) {
                Block n = b.getRelative(f);
                if (seen.add(n)) queue.add(n);
            }
        }
        World world = player.getWorld();
        int placed = 0;
        for (Block target : targets) {
            BlockState before = target.getState();
            target.setBlockData(clicked.getBlockData().clone(), false);
            BlockPlaceEvent check = new BlockPlaceEvent(target, before, target.getRelative(face.getOppositeFace()),
                    new ItemStack(type), player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled() || !check.canBuild()) {
                before.update(true, false);
                continue;
            }
            placed++;
            Location c = target.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.DUST, c, 8, 0.4, 0.4, 0.4, 0, FX.dust(255, 215, 80, 1f));
            world.spawnParticle(Particle.END_ROD, c, 1, 0.2, 0.2, 0.2, 0.02);
        }
        if (placed == 0) return;
        if (!creative) removeItems(player, type, placed);
        world.playSound(clicked.getLocation(), clicked.getBlockSoundGroup().getPlaceSound(), 1f, 0.9f);
        world.playSound(clicked.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
    }

    private static BlockFace[] spreadFaces(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.EAST, BlockFace.WEST};
            default -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH};
        };
    }

    private static int countItems(Player p, Material type) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == type && !s.hasItemMeta()) n += s.getAmount();
        }
        return n;
    }

    private static void removeItems(Player p, Material type, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != type || s.hasItemMeta()) continue;
            int take = Math.min(amount, s.getAmount());
            s.setAmount(s.getAmount() - take);
            amount -= take;
            contents[i] = s.getAmount() <= 0 ? null : s;
        }
        p.getInventory().setStorageContents(contents);
    }

    // ------------------------------------------------------------------ ore scanner

    private void scan(Player player) {
        if (onCooldown(player, 100)) return;
        World world = player.getWorld();
        Location origin = player.getLocation();
        int radius = 16;
        List<Block> ores = new ArrayList<>();
        for (int x = -radius; x <= radius && ores.size() < 300; x++) {
            for (int y = -radius; y <= radius && ores.size() < 300; y++) {
                for (int z = -radius; z <= radius && ores.size() < 300; z++) {
                    Block b = origin.getBlock().getRelative(x, y, z);
                    if (ToolListener.isOre(b.getType())) ores.add(b);
                }
            }
        }
        player.playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.6f);
        player.playSound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
        player.sendActionBar(Component.text("Found " + ores.size() + " ores nearby", NamedTextColor.AQUA));
        // Glowing outlines through walls (Java) only this player can see
        List<BlockDisplay> displays = new ArrayList<>();
        for (Block b : ores) {
            BlockDisplay d = world.spawn(b.getLocation(), BlockDisplay.class, e -> {
                e.setBlock(b.getBlockData());
                e.setGlowing(true);
                e.setGlowColorOverride(oreColor(b.getType()));
                e.setVisibleByDefault(false);
                e.setPersistent(false);
            });
            player.showEntity(plugin, d);
            displays.add(d);
        }
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 40 || !player.isOnline()) { // 10 seconds
                    cancel();
                    displays.forEach(Entity::remove);
                    return;
                }
                // Radar sweep plus particles on each ore (Bedrock players see these)
                double sweep = (t % 10) * 1.8;
                FX.ring(origin.clone().add(0, 0.2, 0), sweep, (int) (sweep * 6) + 6,
                        p -> player.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(80, 255, 220, 0.8f)));
                for (Block b : ores) {
                    if (b.getLocation().distanceSquared(player.getLocation()) > 24 * 24) continue;
                    Color c = oreColor(b.getType());
                    player.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3, 0, FX.dust(c, 1.4f));
                }
                if (t % 10 == 0) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.8f);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private static Color oreColor(Material m) {
        String n = m.name();
        if (n.contains("DIAMOND")) return Color.fromRGB(80, 240, 255);
        if (n.contains("EMERALD")) return Color.fromRGB(40, 255, 90);
        if (n.contains("GOLD")) return Color.fromRGB(255, 215, 0);
        if (n.contains("IRON")) return Color.fromRGB(230, 180, 140);
        if (n.contains("REDSTONE")) return Color.fromRGB(255, 30, 30);
        if (n.contains("LAPIS")) return Color.fromRGB(40, 80, 255);
        if (n.contains("COPPER")) return Color.fromRGB(230, 120, 60);
        if (n.contains("QUARTZ")) return Color.fromRGB(255, 255, 255);
        if (m == Material.ANCIENT_DEBRIS) return Color.fromRGB(150, 80, 200);
        return Color.fromRGB(60, 60, 60);
    }

    // ------------------------------------------------------------------ item magnet + sky walker (while held)

    private void tickHeld() {
        int tick = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ToolType tool = held(p);
            if (tool == null || !plugin.canUse(p)) continue;
            if (tool == ToolType.ITEM_MAGNET) magnet(p, tick);
            else if (tool == ToolType.SKY_WALKER) skyWalk(p, tick);
        }
    }

    private void magnet(Player p, int tick) {
        Location target = p.getLocation().add(0, 0.8, 0);
        World world = p.getWorld();
        for (Entity e : world.getNearbyEntities(target, 10, 6, 10)) {
            boolean item = e instanceof Item it && it.canPlayerPickup() && it.getPickupDelay() < 40;
            if (!item && !(e instanceof ExperienceOrb)) continue;
            Vector to = target.toVector().subtract(e.getLocation().toVector());
            if (to.length() < 0.8) continue;
            e.setVelocity(to.normalize().multiply(0.6));
            world.spawnParticle(Particle.DUST, e.getLocation(), 1, 0.05, 0.05, 0.05, 0, FX.dust(255, 60, 60, 0.8f));
            if (tick % 6 == 0) world.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation(), 1, 0.1, 0.1, 0.1, 0.02);
        }
        if (tick % 10 == 0) FX.ring(target, 1.2, 12, pt -> p.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, FX.dust(255, 80, 80, 0.6f)));
    }

    private void skyWalk(Player p, int tick) {
        if (p.isSneaking() || p.isFlying()) return;
        Block below = p.getLocation().subtract(0, 0.2, 0).getBlock();
        if (!below.getType().isAir() && below.getType() != Material.WATER && below.getType() != Material.LAVA
                && below.getType() != Material.LIGHT_BLUE_STAINED_GLASS) return;
        if (p.getVelocity().getY() > 0.2) return; // jumping
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block b = below.getRelative(x, 0, z);
                if (tempBlocks.place(b, Material.LIGHT_BLUE_STAINED_GLASS, 50, true) && tick % 4 == 0) {
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, b.getLocation().add(0.5, 1.05, 0.5), 1, 0.3, 0, 0.3, 0);
                }
            }
        }
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(), 1, 0.4, 0, 0.4, 0.01);
    }

    // ------------------------------------------------------------------ mob catcher

    private void capture(Player player, LivingEntity target) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getItemMeta().getPersistentDataContainer().has(caughtKey)) {
            player.sendActionBar(Component.text("Already holding a mob. Right-click a block to release it.", NamedTextColor.RED));
            return;
        }
        if (target instanceof Player || target instanceof Boss || target instanceof EnderDragon || target instanceof Wither) {
            player.sendActionBar(Component.text("Can't catch that.", NamedTextColor.RED));
            return;
        }
        if (onCooldown(player, 10)) return;
        EntityType type = target.getType();
        String name = target.customName() == null ? null : PlainTextComponentSerializer.plainText().serialize(target.customName());
        boolean baby = target instanceof org.bukkit.entity.Ageable a && !a.isAdult();
        World world = target.getWorld();
        Location from = target.getLocation().add(0, target.getHeight() / 2, 0);
        FX.line(from, player.getEyeLocation().add(0, -0.3, 0), 0.25, p -> world.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05, 0, FX.dust(255, 200, 60, 1f)));
        FX.implode(from, Particle.END_ROD, 1.5, 30, 0.15);
        world.spawnParticle(Particle.POOF, from, 15, 0.3, 0.3, 0.3, 0.05);
        world.playSound(from, Sound.ENTITY_ITEM_PICKUP, 1f, 0.5f);
        world.playSound(from, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 2f);
        target.remove();
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(caughtKey, PersistentDataType.STRING, type.getKey().toString());
            if (name != null) meta.getPersistentDataContainer().set(caughtNameKey, PersistentDataType.STRING, name);
            meta.getPersistentDataContainer().set(caughtBabyKey, PersistentDataType.BYTE, (byte) (baby ? 1 : 0));
            meta.lore(List.of(Component.text("Contains: " + pretty(type) + (name != null ? " (" + name + ")" : ""), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click a block to release it", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        });
        player.getInventory().setItemInMainHand(item);
        player.sendActionBar(Component.text("Caught a " + pretty(type) + "!", NamedTextColor.GOLD));
    }

    private void release(Player player, Block at) {
        ItemStack item = player.getInventory().getItemInMainHand();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String key = pdc.get(caughtKey, PersistentDataType.STRING);
        if (key == null) return;
        NamespacedKey nk = NamespacedKey.fromString(key);
        EntityType type = nk == null ? null : Registry.ENTITY_TYPE.get(nk);
        if (type == null || type.getEntityClass() == null) return;
        String name = pdc.get(caughtNameKey, PersistentDataType.STRING);
        boolean baby = Byte.valueOf((byte) 1).equals(pdc.get(caughtBabyKey, PersistentDataType.BYTE));
        Location spawn = at.getLocation().add(0.5, 0, 0.5);
        World world = spawn.getWorld();
        Entity e = world.spawnEntity(spawn, type);
        if (name != null) e.customName(Component.text(name));
        if (baby && e instanceof org.bukkit.entity.Ageable a) a.setBaby();
        FX.burst(spawn.clone().add(0, 0.5, 0), Particle.END_ROD, 30, 0.2);
        world.spawnParticle(Particle.DUST, spawn.clone().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4, 0, FX.dust(255, 200, 60, 1.2f));
        world.spawnParticle(Particle.POOF, spawn, 10, 0.3, 0.3, 0.3, 0.05);
        world.playSound(spawn, Sound.ENTITY_CHICKEN_EGG, 1f, 0.6f);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(caughtKey);
            meta.getPersistentDataContainer().remove(caughtNameKey);
            meta.getPersistentDataContainer().remove(caughtBabyKey);
            List<Component> lore = new ArrayList<>();
            for (String line : ToolType.MOB_CATCHER.lore()) lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        player.getInventory().setItemInMainHand(item);
    }

    private static String pretty(EntityType type) {
        String n = type.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    // ------------------------------------------------------------------ healing staff

    private void heal(Player player) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 25, 0.6,
                e -> e != player && e instanceof LivingEntity le && !le.isDead());
        LivingEntity target = hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : player;
        healTarget(player, target);
    }

    private void healTarget(Player healer, LivingEntity target) {
        if (onCooldown(healer, 40)) return;
        World world = target.getWorld();
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = max == null ? 20 : max.getValue();
        target.setHealth(Math.min(maxHp, target.getHealth() + 8));
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true));
        Location c = target.getLocation().add(0, target.getHeight() / 2, 0);
        if (target != healer) {
            FX.line(healer.getEyeLocation().add(0, -0.3, 0), c, 0.25, p -> world.spawnParticle(Particle.DUST, p, 1, 0.03, 0.03, 0.03, 0, FX.dust(255, 120, 200, 1f)));
        }
        FX.helix(target.getLocation(), new Vector(0, 1, 0), target.getHeight() + 0.5, 0.8, 1.2, 0.08, 0,
                p -> world.spawnParticle(Particle.HAPPY_VILLAGER, p, 1, 0, 0, 0, 0));
        FX.helix(target.getLocation(), new Vector(0, 1, 0), target.getHeight() + 0.5, 0.8, 1.2, 0.08, Math.PI,
                p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 150, 220, 1f)));
        world.spawnParticle(Particle.HEART, c, 10, 0.5, 0.6, 0.5, 0);
        FX.ring(target.getLocation().add(0, 0.1, 0), 1.2, 24, p -> world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0.01));
        world.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 2f);
        world.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
    }

    // ------------------------------------------------------------------ dash blade

    private void dash(Player player) {
        if (onCooldown(player, 30)) return;
        World world = player.getWorld();
        Vector dir = player.getLocation().getDirection();
        safe.launch(player, dir.clone().multiply(2.4).setY(Math.max(0.2, dir.getY() * 1.5)));
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.4f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ > 8 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location l = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.SWEEP_ATTACK, l, 2, 0.3, 0.3, 0.3, 0);
                world.spawnParticle(Particle.END_ROD, l, 4, 0.2, 0.4, 0.2, 0.02);
                FX.ring(l, player.getVelocity(), 0.8, 12, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(220, 240, 255, 1f)));
                for (Entity e : world.getNearbyEntities(l, 1.8, 1.8, 1.8)) {
                    if (e == player || !(e instanceof LivingEntity le) || !hit.add(le.getUniqueId())) continue;
                    le.setNoDamageTicks(0);
                    safe.gunDamage(() -> le.damage(5, player));
                    world.spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.3);
                    world.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ quake hammer

    private void quake(Player player) {
        if (divebombing.contains(player.getUniqueId())) return;
        if (onCooldown(player, 20)) return;
        if (player.isOnGround()) {
            slam(player, 1.0);
            return;
        }
        // In the air: dive-bomb, then slam harder on landing
        divebombing.add(player.getUniqueId());
        safe.launch(player, player.getLocation().getDirection().setY(0).multiply(0.4).setY(-2.2));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.5f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline() || t++ > 80) {
                    divebombing.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 6, 0.2, 0.2, 0.2, 0.02);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 2, 0.2, 0.2, 0.2, 0.01);
                if (player.isOnGround()) {
                    divebombing.remove(player.getUniqueId());
                    cancel();
                    slam(player, 1.8);
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    private void slam(Player player, double power) {
        World world = player.getWorld();
        Location center = player.getLocation();
        Block ground = center.clone().subtract(0, 0.5, 0).getBlock();
        var groundData = ground.getType().isAir() ? Material.DIRT.createBlockData() : ground.getBlockData();
        double maxRadius = 8 * power;
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, 0.7f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.2, 0.5, 0);
        new BukkitRunnable() {
            double r = 1;

            @Override
            public void run() {
                if (r > maxRadius) {
                    cancel();
                    return;
                }
                FX.ring(center.clone().add(0, 0.2, 0), r, (int) (r * 8), p -> {
                    world.spawnParticle(Particle.BLOCK, p, 3, 0.2, 0.1, 0.2, groundData);
                    world.spawnParticle(Particle.CLOUD, p, 1, 0.1, 0.1, 0.1, 0.02);
                });
                FX.ring(center.clone().add(0, 0.5, 0), r, (int) (r * 4), p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0.2, 0, 0, FX.dust(255, 140, 60, 1.5f)));
                r += 1.2;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        for (Entity e : world.getNearbyEntities(center, maxRadius, 4, maxRadius)) {
            if (e == player || !(e instanceof LivingEntity le)) continue;
            Vector away = le.getLocation().toVector().subtract(center.toVector()).setY(0);
            double falloff = 1 - Math.min(1, away.length() / maxRadius);
            if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
            Vector v = away.normalize().multiply(0.8 * power * falloff + 0.2).setY(0.6 + power * falloff * 0.7);
            if (le instanceof Player p) safe.launch(p, v); else le.setVelocity(v);
            le.setNoDamageTicks(0);
            safe.gunDamage(() -> le.damage(3 * power * falloff + 1, player));
        }
        for (Player p : center.getNearbyPlayers(maxRadius * 2)) {
            if (p != player) p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 0.6f, 1.6f);
        }
    }

    // ------------------------------------------------------------------ goggles / recall / time / weather

    private void nightVision(Player player) {
        if (onCooldown(player, 10)) return;
        World world = player.getWorld();
        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f);
            player.sendActionBar(Component.text("Night vision OFF", NamedTextColor.GRAY));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, true));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
            world.spawnParticle(Particle.GLOW, player.getEyeLocation(), 15, 0.3, 0.2, 0.3, 0.02);
            FX.ring(player.getEyeLocation(), player.getLocation().getDirection(), 0.5, 16,
                    p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(80, 255, 80, 0.8f)));
            player.sendActionBar(Component.text("Night vision ON", NamedTextColor.GREEN));
        }
    }

    private void recall(Player player) {
        if (onCooldown(player, 200)) return;
        Location dest = player.getRespawnLocation();
        if (dest == null) dest = player.getWorld().getSpawnLocation();
        Location target = dest.clone();
        Location start = player.getLocation();
        World world = player.getWorld();
        player.sendActionBar(Component.text("Recalling... stand still", NamedTextColor.YELLOW));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getLocation().distanceSquared(start) > 1.0) {
                    cancel();
                    player.sendActionBar(Component.text("Recall cancelled (you moved)", NamedTextColor.RED));
                    cooldowns.remove(player.getUniqueId());
                    return;
                }
                Location l = player.getLocation();
                double h = t / 60.0 * 2.2;
                FX.helix(l, new Vector(0, 1, 0), h, 0.9, 1, 0.15, t * 0.3, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(255, 220, 80, 1f)));
                FX.ring(l.clone().add(0, 0.1, 0), 1.1, 20, p -> world.spawnParticle(Particle.ENCHANT, p, 1, 0, 0, 0, 0.5));
                world.spawnParticle(Particle.PORTAL, l.clone().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0.4);
                if (t % 10 == 0) world.playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.8f + t / 60f);
                if (t++ < 60) return;
                cancel();
                world.spawnParticle(Particle.FLASH, l.clone().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 230, 120));
                FX.burst(l.clone().add(0, 1, 0), Particle.END_ROD, 60, 0.3);
                world.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                player.setFallDistance(0);
                player.teleport(target);
                target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.1);
                target.getWorld().playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void timeWand(Player player) {
        if (!player.hasPermission("ghsguns.admin")) {
            player.sendActionBar(Component.text("Only admins can control time.", NamedTextColor.RED));
            return;
        }
        if (onCooldown(player, 60)) return;
        World world = player.getWorld();
        long now = world.getTime();
        boolean toNight = now < 12500 || now > 23500;
        long target = toNight ? 14000 : 0;
        long distance = Math.floorMod(target - now, 24000);
        long step = Math.max(1, distance / 40);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2f, toNight ? 0.6f : 1.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 40) {
                    cancel();
                    world.setTime(target);
                    return;
                }
                world.setTime(world.getTime() + step);
                Location c = player.getLocation().add(0, 1, 0);
                FX.ring(c, new Vector(Math.sin(t * 0.2), 1, Math.cos(t * 0.2)), 2.5, 30,
                        p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(toNight ? Color.fromRGB(100, 120, 255) : Color.fromRGB(255, 220, 80), 1.2f)));
                world.spawnParticle(Particle.END_ROD, c, 4, 1.5, 1.5, 1.5, 0.02);
                if (t % 8 == 0) world.playSound(c, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.5f + t / 40f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void weatherWand(Player player) {
        if (!player.hasPermission("ghsguns.admin")) {
            player.sendActionBar(Component.text("Only admins can control the weather.", NamedTextColor.RED));
            return;
        }
        if (onCooldown(player, 40)) return;
        World world = player.getWorld();
        Location c = player.getLocation().add(0, 1, 0);
        String now;
        if (world.isThundering()) {
            world.setStorm(false);
            world.setThundering(false);
            now = "Clear skies";
            world.spawnParticle(Particle.END_ROD, c.clone().add(0, 2, 0), 60, 2, 1, 2, 0.05);
            FX.burst(c.clone().add(0, 2, 0), Particle.WAX_ON, 60, 0.5);
            world.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } else if (world.hasStorm()) {
            world.setThundering(true);
            now = "Thunderstorm";
            for (int i = 0; i < 3; i++) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                world.strikeLightningEffect(player.getLocation().add(r.nextDouble(-8, 8), 0, r.nextDouble(-8, 8)));
            }
        } else {
            world.setStorm(true);
            now = "Rain";
            world.spawnParticle(Particle.CLOUD, c.clone().add(0, 3, 0), 80, 2.5, 0.5, 2.5, 0.02);
            world.spawnParticle(Particle.FALLING_WATER, c.clone().add(0, 3, 0), 80, 2.5, 0.2, 2.5, 0);
            world.playSound(c, Sound.WEATHER_RAIN, 1f, 1f);
        }
        FX.helix(player.getLocation(), new Vector(0, 1, 0), 3, 1, 1, 0.08, 0,
                p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(120, 180, 255, 1f)));
        player.sendActionBar(Component.text("Weather: " + now, NamedTextColor.AQUA));
    }
}
