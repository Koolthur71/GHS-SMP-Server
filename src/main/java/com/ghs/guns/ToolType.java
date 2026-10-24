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
            List.of("Right-click to teleport where you're looking")),
    EXCAVATOR("<gold>Excavator", Material.NETHERITE_SHOVEL,
            List.of("Digs 3x3", "Hold right-click to dig a tunnel")),
    VEIN_MINER("<aqua>Vein Miner", Material.NETHERITE_PICKAXE,
            List.of("Mine one ore to mine the whole vein")),
    SMELTER_PICK("<red>Smelter Pick", Material.NETHERITE_PICKAXE,
            List.of("Everything you mine comes out already smelted")),
    HARVEST_SCYTHE("<green>Harvest Scythe", Material.NETHERITE_HOE,
            List.of("Right-click crops to harvest and replant a 7x7 area")),
    BUILDERS_WAND("<yellow>Builder's Wand", Material.BREEZE_ROD,
            List.of("Right-click a block face to extend it (up to 25 blocks)", "Uses blocks from your inventory")),
    ORE_SCANNER("<aqua>Ore Scanner", Material.RECOVERY_COMPASS,
            List.of("Right-click to highlight ores around you for 10 seconds")),
    ITEM_MAGNET("<red>Item Magnet", Material.HEART_OF_THE_SEA,
            List.of("Hold it (either hand) to pull in nearby items and XP")),
    MOB_CATCHER("<gold>Mob Catcher", Material.LEAD,
            List.of("Right-click a mob to catch it", "Right-click a block to release it")),
    HEALING_STAFF("<light_purple>Healing Staff", Material.GOLDEN_HOE,
            List.of("Right-click a player to heal them (or yourself if you aim at nothing)")),
    DASH_BLADE("<white><bold>Dash Blade", Material.NETHERITE_SWORD,
            List.of("Right-click to dash forward, slicing through anything in the way")),
    QUAKE_HAMMER("<dark_red><bold>Quake Hammer", Material.MACE,
            List.of("Right-click on the ground to slam a shockwave", "Right-click in the air to dive-bomb")),
    SKY_WALKER("<aqua>Sky Walker", Material.FEATHER,
            List.of("Hold it (either hand) to walk on air, water and lava", "Sneak to drop down")),
    NIGHT_VISION_GOGGLES("<green>Night Vision Goggles", Material.SPYGLASS,
            List.of("Right-click to toggle night vision")),
    RECALL_COMPASS("<yellow>Recall Compass", Material.COMPASS,
            List.of("Right-click and stand still to teleport home (bed or spawn)")),
    TIME_WAND("<gold>Time Wand", Material.CLOCK,
            List.of("Right-click to switch between day and night (admins only)")),
    WEATHER_WAND("<blue>Weather Wand", Material.TRIDENT,
            List.of("Right-click to cycle clear / rain / thunder (admins only)"));

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

    /** Which section it's listed under in the menu. */
    public String group() {
        return switch (this) {
            case DRILL, MEGA_DRILL, EXCAVATOR, TREE_FELLER, VEIN_MINER, SMELTER_PICK -> "Mining";
            case HARVEST_SCYTHE, BUILDERS_WAND -> "Building & Farming";
            case GRAPPLING_HOOK, JETPACK, TELEPORT_GUN, DASH_BLADE, SKY_WALKER, RECALL_COMPASS -> "Movement";
            case ORE_SCANNER, ITEM_MAGNET, MOB_CATCHER, HEALING_STAFF, NIGHT_VISION_GOGGLES, QUAKE_HAMMER -> "Utility";
            case TIME_WAND, WEATHER_WAND -> "Admin";
        };
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
