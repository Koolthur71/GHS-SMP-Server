package com.ghs.guns;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makes sure guns and troll items never kill players: any damage they cause
 * (bullets, explosions, fire, falling after being launched) stops at min-health.
 */
public final class SafeDamage implements Listener {

    /** Area where explosion damage to players gets capped. */
    private record BlastZone(World world, Vector center, double radius, int expiresTick) {}

    private final GHSGuns plugin;
    private final List<BlastZone> blastZones = new ArrayList<>();
    /** Player -> tick until which fall damage is protected. */
    private final Map<UUID, Integer> launched = new HashMap<>();
    /** Player -> tick until which fire damage is protected. */
    private final Map<UUID, Integer> burning = new HashMap<>();
    private boolean applyingGunDamage;

    public SafeDamage(GHSGuns plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int now = Bukkit.getCurrentTick();
            blastZones.removeIf(z -> now > z.expiresTick());
            launched.values().removeIf(t -> now > t);
            burning.values().removeIf(t -> now > t);
        }, 20L, 20L);
    }

    /** Run damage that should be treated as gun damage (never lethal to players). */
    public void gunDamage(Runnable damage) {
        applyingGunDamage = true;
        try {
            damage.run();
        } finally {
            applyingGunDamage = false;
        }
    }

    public void addBlastZone(Location loc, double radius) {
        blastZones.add(new BlastZone(loc.getWorld(), loc.toVector(), radius, Bukkit.getCurrentTick() + 3));
    }

    public void markBurning(Player p, int ticks) {
        burning.merge(p.getUniqueId(), Bukkit.getCurrentTick() + ticks + 20, Math::max);
    }

    /** Throw a player and protect them from the fall. Also tells the anticheat to ignore it. */
    public void launch(Player p, Vector velocity) {
        String exempt = plugin.getConfig().getString("anticheat-exempt-command", "");
        if (exempt != null && !exempt.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), exempt.replace("%player%", p.getName()));
        }
        p.setVelocity(velocity);
        launched.put(p.getUniqueId(), Bukkit.getCurrentTick() + 400);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        launched.remove(event.getPlayer().getUniqueId());
        burning.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isProtected(event, player)) return;
        double minHealth = Math.max(0.5, plugin.getConfig().getDouble("min-health", 1.0));
        if (player.getHealth() - event.getFinalDamage() >= minHealth) return;

        // This hit would kill: leave them on min-health instead
        event.setCancelled(true);
        if (player.getHealth() > minHealth) player.setHealth(minHealth);
        if (event.getCause() == DamageCause.FALL) launched.remove(player.getUniqueId());
        player.playHurtAnimation(0);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.3);
    }

    private boolean isProtected(EntityDamageEvent event, Player player) {
        if (applyingGunDamage) return true;
        UUID id = player.getUniqueId();
        DamageCause cause = event.getCause();
        if (cause == DamageCause.FALL || cause == DamageCause.FLY_INTO_WALL) return launched.containsKey(id);
        if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK) return burning.containsKey(id);
        if (cause != DamageCause.BLOCK_EXPLOSION && cause != DamageCause.ENTITY_EXPLOSION
                && cause != DamageCause.FALLING_BLOCK) return false;
        Vector pos = player.getLocation().toVector();
        for (BlastZone zone : blastZones) {
            if (zone.world().equals(player.getWorld()) && zone.center().distance(pos) <= zone.radius()) return true;
        }
        return false;
    }
}
