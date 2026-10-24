package com.ghs.guns;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** Useful (and fun) tools. */
public enum ToolType {
    DRILL("<gold><bold>Drill", Material.NETHERITE_PICKAXE,
            List.of("Mines 3x3", "Hold right-click on a wall to bore a tunnel")),
    MEGA_DRILL("<red><bold>Mega Drill", Material.NETHERITE_PICKAXE,
            List.of("Mines 5x5", "Hold right-click on a wall to bore a huge tunnel")),
    TREE_FELLER("<green>Tree Feller", Material.NETHERITE_AXE,
            List.of("Chop one log to cut down the whole tree")),
    GRAPPLING_HOOK("<aqua>Grappling Hook", Material.FISHING_ROD,
            List.of("Cast it, then reel in to fly to the hook", "Hook a player to yank them to you")),
    JETPACK("<yellow>Jetpack", Material.FIREWORK_STAR,
            List.of("Hold right-click to fly where you're looking")),
    TELEPORT_GUN("<dark_purple>Teleport Gun", Material.ENDER_EYE,
            List.of("Right-click to teleport where you're looking"));

    private final String name;
    private final Material material;
    private final List<String> lore;

    ToolType(String name, Material material, List<String> lore) {
        this.name = name;
        this.material = material;
        this.lore = lore;
    }

    public String displayName() {
        return name;
    }

    public Material material() {
        return material;
    }

    public List<String> lore() {
        return lore;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ToolType fromId(String id) {
        if (id == null) return null;
        for (ToolType t : values()) {
            if (t.id().equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
