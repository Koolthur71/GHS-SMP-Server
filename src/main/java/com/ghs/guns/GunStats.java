package com.ghs.guns;

import org.bukkit.configuration.ConfigurationSection;

public record GunStats(
        String name,
        double damage,
        int cooldownTicks,
        int shotsPerTrigger,
        int shotIntervalTicks,
        int pellets,
        double spread,
        double range,
        int magazine,
        int reloadTicks,
        double headshotMultiplier,
        float explosionPower,
        double projectileSpeed,
        String itemModel
) {
    public static GunStats load(ConfigurationSection s) {
        return new GunStats(
                s.getString("name", "Gun"),
                s.getDouble("damage", 4.0),
                Math.max(1, s.getInt("cooldown-ticks", 5)),
                Math.max(1, s.getInt("shots-per-trigger", 1)),
                Math.max(1, s.getInt("shot-interval-ticks", 1)),
                Math.max(1, s.getInt("pellets", 1)),
                s.getDouble("spread", 0.0),
                s.getDouble("range", 60),
                Math.max(1, s.getInt("magazine", 10)),
                Math.max(1, s.getInt("reload-ticks", 40)),
                s.getDouble("headshot-multiplier", 1.0),
                (float) s.getDouble("explosion-power", 4.0),
                s.getDouble("projectile-speed", 1.5),
                s.getString("item-model", "")
        );
    }
}
