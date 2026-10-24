package com.ghs.guns;

import java.util.Locale;

public enum GunType {
    // Bullet guns
    PISTOL(Kind.BULLET),
    SMG(Kind.BULLET),
    AR(Kind.BULLET),
    SNIPER(Kind.BULLET),
    SHOTGUN(Kind.BULLET),
    MINIGUN(Kind.BULLET),
    FLAMETHROWER(Kind.SPECIAL),
    // Launchers
    GRENADE_LAUNCHER(Kind.EXPLOSIVE),
    RPG(Kind.EXPLOSIVE),
    // Sci-fi
    RAYGUN(Kind.SPECIAL),
    LASER_RIFLE(Kind.SPECIAL),
    RAILGUN(Kind.SPECIAL),
    TESLA_GUN(Kind.SPECIAL),
    PLASMA_CANNON(Kind.SPECIAL),
    HOMING_LAUNCHER(Kind.SPECIAL),
    GRAVITY_GUN(Kind.SPECIAL),
    BLACK_HOLE_GUN(Kind.SPECIAL),
    BUBBLE_GUN(Kind.SPECIAL),
    CONFETTI_CANNON(Kind.SPECIAL),
    // Nukes
    NUKE(Kind.NUKE),
    TSAR_BOMBA(Kind.NUKE),
    CLUSTER_NUKE(Kind.NUKE),
    CRYO_NUKE(Kind.NUKE),
    CHICKEN_NUKE(Kind.NUKE),
    PARTY_NUKE(Kind.NUKE),
    BLACK_HOLE_BOMB(Kind.NUKE);

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

    GunType(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
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
