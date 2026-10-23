package com.ghs.guns;

import org.bukkit.Material;

import java.util.Locale;

/** Non-gun troll items. Targeted tools hit whoever you're looking at, thrown tools land and go off. */
public enum TrollTool {
    YEET_STICK("<aqua><bold>Yeet Stick", Material.STICK, false, 20, "Right-click a player to launch them into orbit"),
    SWAP_STICK("<light_purple>Swap Stick", Material.BLAZE_ROD, false, 40, "Right-click a player to swap places with them"),
    FREEZE_RAY("<aqua>Freeze Ray", Material.PRISMARINE_SHARD, false, 60, "Right-click a player to freeze them solid"),
    LEVITATION_WAND("<white>Levitation Wand", Material.END_ROD, false, 40, "Right-click a player to make them float away"),
    ANVIL_RAIN("<gray>Anvil Rain", Material.ANVIL, false, 100, "Right-click a player to drop (harmless) anvils on them"),
    CHICKEN_BOMB("<yellow>Chicken Bomb", Material.EGG, true, 60, "Throw it for a chicken explosion"),
    FAKE_TNT("<red>Totally Real TNT", Material.TNT, true, 60, "Throw it. It's definitely real."),
    DISCO_BALL("<gradient:red:yellow:green:aqua:light_purple>Disco Ball", Material.AMETHYST_CLUSTER, true, 200, "Throw it to start a dance party");

    private final String name;
    private final Material material;
    private final boolean thrown;
    private final int cooldownTicks;
    private final String description;

    TrollTool(String name, Material material, boolean thrown, int cooldownTicks, String description) {
        this.name = name;
        this.material = material;
        this.thrown = thrown;
        this.cooldownTicks = cooldownTicks;
        this.description = description;
    }

    public String displayName() {
        return name;
    }

    public Material material() {
        return material;
    }

    public boolean thrown() {
        return thrown;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public String description() {
        return description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TrollTool fromId(String id) {
        if (id == null) return null;
        for (TrollTool t : values()) {
            if (t.id().equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
