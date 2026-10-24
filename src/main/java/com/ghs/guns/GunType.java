package com.ghs.guns;

import org.bukkit.Material;

import java.util.Locale;

public enum GunType {
    // Classic guns
    PISTOL(Kind.BULLET, "Classic Guns", Material.IRON_HORSE_ARMOR, "Accurate semi-auto pistol. Headshots do extra damage."),
    SMG(Kind.BULLET, "Classic Guns", Material.GOLDEN_HORSE_ARMOR, "Fires fast 3-round bursts. Hold right-click."),
    AR(Kind.BULLET, "Classic Guns", Material.DIAMOND_HORSE_ARMOR, "Assault rifle: 2-round bursts with long range."),
    SNIPER(Kind.BULLET, "Classic Guns", Material.CROSSBOW, "Huge damage, 2x headshots. Left-click to scope in."),
    SHOTGUN(Kind.BULLET, "Classic Guns", Material.LEATHER_HORSE_ARMOR, "8 pellets per shot with massive knockback."),
    MINIGUN(Kind.BULLET, "Classic Guns", Material.NETHERITE_INGOT, "200-round belt that shreds everything. Slows you down."),
    FLAMETHROWER(Kind.SPECIAL, "Classic Guns", Material.BLAZE_ROD, "Short-range cone of fire that sets targets alight."),
    // Launchers
    GRENADE_LAUNCHER(Kind.EXPLOSIVE, "Launchers", Material.BOW, "Lobs grenades that explode where they land."),
    RPG(Kind.EXPLOSIVE, "Launchers", Material.FIREWORK_ROCKET, "Rocket that flies straight, explodes and launches people."),
    // Sci-fi
    RAYGUN(Kind.SPECIAL, "Sci-Fi Guns", Material.EMERALD, "Green helix beam that makes targets glow."),
    LASER_RIFLE(Kind.SPECIAL, "Sci-Fi Guns", Material.REDSTONE, "Rapid red laser that pierces everything in a line and burns."),
    RAILGUN(Kind.SPECIAL, "Sci-Fi Guns", Material.DIAMOND, "Charges up, then fires through walls and everything in its path."),
    TESLA_GUN(Kind.SPECIAL, "Sci-Fi Guns", Material.COPPER_INGOT, "Chain lightning that jumps between up to 6 targets."),
    PLASMA_CANNON(Kind.SPECIAL, "Sci-Fi Guns", Material.SOUL_LANTERN, "Glowing plasma ball that explodes in blue rings."),
    HOMING_LAUNCHER(Kind.SPECIAL, "Sci-Fi Guns", Material.TARGET, "Missiles lock onto what you aim at and chase it."),
    GRAVITY_GUN(Kind.SPECIAL, "Sci-Fi Guns", Material.NETHER_STAR, "Right-click to pick up a mob/player/item, again to throw it."),
    BLACK_HOLE_GUN(Kind.SPECIAL, "Sci-Fi Guns", Material.OBSIDIAN, "Fires a mini black hole that sucks things in (no block damage)."),
    BUBBLE_GUN(Kind.SPECIAL, "Sci-Fi Guns", Material.NAUTILUS_SHELL, "Traps whoever it hits in a floating bubble."),
    CONFETTI_CANNON(Kind.SPECIAL, "Sci-Fi Guns", Material.GLOW_INK_SAC, "Confetti and fireworks that push people back. No damage."),
    // Nukes
    NUKE(Kind.NUKE, "Nukes", Material.TNT, "Countdown, fireball, rings of explosions and a mushroom cloud."),
    TSAR_BOMBA(Kind.NUKE, "Nukes", Material.RESPAWN_ANCHOR, "The nuke, but way bigger. Expect lag!"),
    CLUSTER_NUKE(Kind.NUKE, "Nukes", Material.TNT_MINECART, "Explodes, then flings 14 bomblets everywhere."),
    CRYO_NUKE(Kind.NUKE, "Nukes", Material.BLUE_ICE, "Freezes everyone nearby, snowstorm and a blanket of snow."),
    CHICKEN_NUKE(Kind.NUKE, "Nukes", Material.EGG, "Feather mushroom cloud and 60 chickens. No block damage."),
    PARTY_NUKE(Kind.NUKE, "Nukes", Material.CAKE, "10-second firework show with music. No block damage."),
    BLACK_HOLE_BOMB(Kind.NUKE, "Nukes", Material.CRYING_OBSIDIAN, "Sucks everything in, rips up the ground, then collapses.");

    public enum Kind {
        /** Hitscan bullets. */
        BULLET,
        /** Thrown as a projectile that explodes. */
        EXPLOSIVE,
        /** A nuke: thrown, then something big happens. */
        NUKE,
        /** Has its own firing logic. */
        SPECIAL
    }

    private final Kind kind;
    private final String group;
    private final Material material;
    private final String description;

    GunType(Kind kind, String group, Material material, String description) {
        this.kind = kind;
        this.group = group;
        this.material = material;
        this.description = description;
    }

    public Kind kind() {
        return kind;
    }

    /** Which section it's listed under in the menu. */
    public String group() {
        return group;
    }

    public Material material() {
        return material;
    }

    public String description() {
        return description;
    }

    /** Fired as a thrown projectile (launchers and nukes). */
    public boolean explosive() {
        return kind == Kind.EXPLOSIVE || kind == Kind.NUKE;
    }

    public boolean nuke() {
        return kind == Kind.NUKE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GunType fromId(String id) {
        if (id == null) return null;
        for (GunType type : values()) {
            if (type.id().equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
