package com.ghs.guns;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ToolListener implements Listener {

    private static final int TREE_LIMIT = 300;

    private final GHSGuns plugin;
    private final SafeDamage safe;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    /** Players whose extra block breaks are being done by us, so we don't recurse. */
    private final Set<UUID> breaking = new HashSet<>();

    public ToolListener(GHSGuns plugin, SafeDamage safe) {
        this.plugin = plugin;
        this.safe = safe;
    }

    private boolean onCooldown(Player player, int ticks) {
        int now = Bukkit.getCurrentTick();
        Integer ready = cooldowns.get(player.getUniqueId());
        if (ready != null && now < ready) return true;
        cooldowns.put(player.getUniqueId(), now + ticks);
        return false;
    }

    // ------------------------------------------------------------------ mining with drills / tree feller

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (breaking.contains(player.getUniqueId())) return;
        ToolType tool = plugin.toolType(player.getInventory().getItemInMainHand());
        if (tool == null || !plugin.canUse(player)) return;
        switch (tool) {
            case DRILL, MEGA_DRILL, EXCAVATOR -> {
                RayTraceResult hit = player.rayTraceBlocks(6, FluidCollisionMode.NEVER);
                BlockFace face = hit != null && hit.getHitBlockFace() != null ? hit.getHitBlockFace() : BlockFace.UP;
                mineArea(player, event.getBlock(), face, tool == ToolType.MEGA_DRILL ? 2 : 1, false);
            }
            case VEIN_MINER -> {
                if (isOre(event.getBlock().getType())) mineVein(player, event.getBlock());
            }
            case TREE_FELLER -> {
                if (Tag.LOGS.isTagged(event.getBlock().getType())) fellTree(player, event.getBlock());
            }
            default -> { }
        }
    }

    /** Break a square around center, flat against the face that was hit. */
    private void mineArea(Player player, Block center, BlockFace face, int radius, boolean includeCenter) {
        int ax = face.getModX() != 0 ? 0 : 1;
        int ay = face.getModY() != 0 ? 0 : 1;
        int az = face.getModZ() != 0 ? 0 : 1;
        List<Block> blocks = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                int dx, dy, dz;
                if (ax == 0) { dx = 0; dy = a; dz = b; }
                else if (ay == 0) { dx = a; dy = 0; dz = b; }
                else { dx = a; dy = b; dz = 0; }
                if (!includeCenter && dx == 0 && dy == 0 && dz == 0) continue;
                blocks.add(center.getRelative(dx, dy, dz));
            }
        }
        breakAll(player, blocks);
    }

    /** Break blocks as the player, so claim/protection plugins still get a say on every block. */
    private int breakAll(Player player, List<Block> blocks) {
        int broken = 0;
        breaking.add(player.getUniqueId());
        try {
            for (Block b : blocks) {
                Material type = b.getType();
                if (type.isAir() || b.isLiquid()) continue;
                float hardness = type.getHardness();
                if (hardness < 0 || type == Material.BEDROCK) continue; // unbreakable stuff
                if (player.breakBlock(b)) broken++;
            }
        } finally {
            breaking.remove(player.getUniqueId());
        }
        return broken;
    }

    static boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    private void mineVein(Player player, Block start) {
        Material type = start.getType();
        List<Block> vein = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && vein.size() < 64) {
            Block b = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block n = b.getRelative(x, y, z);
                        if (seen.add(n) && n.getType() == type) {
                            vein.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        if (vein.isEmpty()) return;
        World world = start.getWorld();
        Location from = start.getLocation().add(0.5, 0.5, 0.5);
        for (Block b : vein) {
            Location to = b.getLocation().add(0.5, 0.5, 0.5);
            FX.line(from, to, 0.3, p -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, FX.dust(120, 255, 255, 0.7f)));
            world.spawnParticle(Particle.BLOCK, to, 10, 0.3, 0.3, 0.3, b.getBlockData());
            world.spawnParticle(Particle.WAX_OFF, to, 4, 0.3, 0.3, 0.3, 0.5);
        }
        breakAll(player, vein);
        world.playSound(from, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 1.2f);
        world.playSound(from, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
    }

    private void fellTree(Player player, Block start) {
        List<Block> logs = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && logs.size() < TREE_LIMIT) {
            Block b = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block n = b.getRelative(x, y, z);
                        if (seen.add(n) && Tag.LOGS.isTagged(n.getType())) {
                            logs.add(n);
                            queue.add(n);
                        }
                    }
                }
            }
        }
        if (logs.isEmpty()) return;
        breakAll(player, logs);
        World world = start.getWorld();
        world.playSound(start.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.5f, 0.6f);
        world.playSound(start.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 0.8f);
    }

    // ------------------------------------------------------------------ right-click tools

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ToolType tool = plugin.toolType(player.getInventory().getItemInMainHand());
        if (tool != ToolType.DRILL && tool != ToolType.MEGA_DRILL && tool != ToolType.EXCAVATOR
                && tool != ToolType.JETPACK && tool != ToolType.TELEPORT_GUN) return; // the rest live in MoreTools
        event.setCancelled(true);
        if (!plugin.canUse(player)) return;
        switch (tool) {
            case DRILL, MEGA_DRILL, EXCAVATOR -> bore(player, tool == ToolType.MEGA_DRILL ? 2 : 1);
            case JETPACK -> jetpack(player);
            case TELEPORT_GUN -> teleport(player);
            default -> { }
        }
    }

    private void bore(Player player, int radius) {
        if (onCooldown(player, 4)) return;
        RayTraceResult hit = player.rayTraceBlocks(5, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return;
        Block center = hit.getHitBlock();
        Location at = center.getLocation().add(0.5, 0.5, 0.5);
        World world = player.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 30, radius * 0.6, radius * 0.6, radius * 0.6, center.getBlockData());
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.4, 0.4, 0.4, 0.1);
        world.playSound(at, Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.6f);
        world.playSound(at, Sound.ENTITY_MINECART_RIDING, 0.3f, 2f);
        mineArea(player, center, hit.getHitBlockFace(), radius, true);
    }

    private void jetpack(Player player) {
        Vector dir = player.getLocation().getDirection();
        Vector v = player.getVelocity().add(dir.multiply(0.55)).add(new Vector(0, 0.3, 0));
        if (v.length() > 2.2) v = v.normalize().multiply(2.2);
        safe.launch(player, v);
        Location back = player.getLocation().add(0, 0.8, 0).subtract(player.getLocation().getDirection().setY(0).normalize().multiply(0.4));
        World world = player.getWorld();
        world.spawnParticle(Particle.FLAME, back, 8, 0.1, 0.1, 0.1, 0.05);
        world.spawnParticle(Particle.LARGE_SMOKE, back, 3, 0.1, 0.1, 0.1, 0.02);
        world.playSound(back, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);
    }

    private void teleport(Player player) {
        if (onCooldown(player, 10)) return;
        RayTraceResult hit = player.rayTraceBlocks(80, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.2f);
            return;
        }
        Block feet = hit.getHitBlock().getRelative(hit.getHitBlockFace());
        if (hit.getHitBlockFace() != BlockFace.UP && hit.getHitBlockFace() != BlockFace.DOWN) {
            // Hit a wall: land on top of it if there's room, else at its base
            Block top = hit.getHitBlock().getRelative(BlockFace.UP);
            if (top.isPassable() && top.getRelative(BlockFace.UP).isPassable()) feet = top;
        } else if (hit.getHitBlockFace() == BlockFace.DOWN) {
            feet = feet.getRelative(BlockFace.DOWN);
        }
        if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.2f);
            return;
        }
        Location from = player.getLocation();
        Location to = feet.getLocation().add(0.5, 0, 0.5);
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        World world = player.getWorld();
        world.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.5);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        player.setFallDistance(0);
        player.teleport(to);
        world.spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 50, 0.4, 0.8, 0.4, 0.1);
        world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
    }

    // ------------------------------------------------------------------ grappling hook

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (plugin.toolType(player.getInventory().getItemInMainHand()) != ToolType.GRAPPLING_HOOK || !plugin.canUse(player)) return;
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> hook.setVelocity(hook.getVelocity().multiply(1.8)); // casts further
            case CAUGHT_ENTITY -> {
                Entity caught = event.getCaught();
                if (caught instanceof LivingEntity le) {
                    Vector pull = player.getLocation().toVector().subtract(le.getLocation().toVector()).multiply(0.2).setY(0.6);
                    if (le instanceof Player p) safe.launch(p, pull); else le.setVelocity(pull);
                    player.getWorld().playSound(le.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.5f, 0.6f);
                }
            }
            case IN_GROUND, REEL_IN, FAILED_ATTEMPT -> {
                Vector diff = hook.getLocation().toVector().subtract(player.getLocation().toVector());
                if (diff.length() < 2.5) return;
                Vector pull = diff.multiply(0.18).add(new Vector(0, 0.45, 0));
                if (pull.length() > 3.2) pull = pull.normalize().multiply(3.2);
                safe.launch(player, pull);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.5f, 0.6f);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
            }
            default -> { }
        }
    }
}
