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
    DISCO_BALL("<gradient:red:yellow:green:aqua:light_purple>Disco Ball", Material.AMETHYST_CLUSTER, true, 200, "Throw it to start a dance party"),
    SHRINK_RAY("<light_purple>Shrink Ray", Material.AMETHYST_SHARD, false, 100, "Right-click a player to shrink them (sneak to make them a giant)"),
    MAGNET("<red>Magnet", Material.IRON_INGOT, false, 60, "Right-click a player to yank them over to you"),
    FISH_SLAP("<aqua>The Fish", Material.COD, false, 15, "Hit someone with it. Trust me."),
    HOT_POTATO("<gold>Hot Potato", Material.BAKED_POTATO, false, 200, "Right-click a player to give them the hot potato"),
    JAIL_WAND("<gray>Jail Wand", Material.IRON_BARS, false, 200, "Right-click a player to lock them in a glass cage for 10 seconds"),
    BANANA_PEEL("<yellow>Banana Peel", Material.YELLOW_DYE, true, 40, "Throw it on the ground and wait for someone to slip"),
    WEB_SHOOTER("<white>Web Shooter", Material.COBWEB, true, 60, "Throw it to make a web trap (disappears after 6 seconds)"),
    LIGHTNING_WAND("<yellow><bold>Smite Wand", Material.LIGHTNING_ROD, false, 40, "Right-click a player to smite them with (harmless) lightning"),
    BOING_GUN("<green>Boing Gun", Material.SLIME_BALL, false, 100, "Right-click a player to make them bounce nonstop for 15 seconds"),
    FLASHBANG("<white><bold>Flashbang", Material.IRON_NUGGET, true, 60, "Throw it to blind everyone nearby"),
    STINK_BOMB("<dark_green>Stink Bomb", Material.FERMENTED_SPIDER_EYE, true, 60, "Throw it. Everyone nearby will hate you."),
    TORNADO("<gray><bold>Tornado in a Bottle", Material.WIND_CHARGE, true, 160, "Throw it to start a tornado that sucks everyone in"),
    SHEEP_LAUNCHER("<light_purple>Sheep Launcher", Material.WHITE_WOOL, false, 30, "Right-click to fire an exploding rainbow sheep"),
    METEOR_CALLER("<gold><bold>Meteor Caller", Material.FIRE_CHARGE, false, 160, "Right-click a player to call a meteor shower on them (no block damage)"),
    ROCKET_WAND("<red>Rocket Wand", Material.FIREWORK_ROCKET, false, 60, "Right-click a player to blast them off in a firework"),
    RAINBOW_WAND("<gradient:red:yellow:green:aqua:light_purple>Rainbow Wand", Material.PRISMARINE_CRYSTALS, false, 40, "Right-click a player to give them a rainbow trail for 30 seconds"),
    PINATA_STICK("<light_purple>Piñata Stick", Material.BAMBOO, false, 200, "Right-click a player to turn them into a piñata for 30 seconds"),
    PARTY_CREEPERS("<green><bold>Party Creepers", Material.CREEPER_HEAD, true, 200, "Throw it to summon creepers that explode into confetti"),
    UFO_REMOTE("<green><bold>UFO Remote", Material.ECHO_SHARD, false, 200, "Right-click a player to have them abducted by a UFO"),
    ORBIT_WAND("<white>Orbit Wand", Material.ENDER_PEARL, false, 100, "Right-click a player to send them spinning in orbit"),
    SIZE_WAND("<light_purple><bold>Size Wand", Material.BLAZE_POWDER, false, 10, "Right-click a player to make them bigger (permanent!). Sneak + click to reset.");

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

    /** Which section it's listed under in the menu. */
    public String group() {
        if (thrown || this == SHEEP_LAUNCHER) return "Throwables";
        return switch (this) {
            case SHRINK_RAY, SIZE_WAND -> "Size";
            case YEET_STICK, LEVITATION_WAND, ROCKET_WAND, ORBIT_WAND, UFO_REMOTE, MAGNET, SWAP_STICK, FISH_SLAP -> "Movement Wands";
            default -> "Chaos Wands";
        };
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
