package com.ghs.guns;

import java.util.Locale;

public enum GunType {
    PISTOL(false),
    SMG(false),
    AR(false),
    RPG(true),
    NUKE(true);

    private final boolean explosive;

    GunType(boolean explosive) {
        this.explosive = explosive;
    }

    public boolean explosive() {
        return explosive;
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
