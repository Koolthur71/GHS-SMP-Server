package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GHSGuns extends JavaPlugin implements TabExecutor {

    private final Map<GunType, GunStats> stats = new EnumMap<>(GunType.class);
    private NamespacedKey gunKey;
    private NamespacedKey ammoKey;
    private NamespacedKey projectileKey;
    private NamespacedKey trollKey;
    private NamespacedKey toolKey;
    private UndoManager undo;
    private TrollListener trolls;
    private TempBlocks tempBlocks;
    private MoreTrolls moreTrolls;

    @Override
    public void onEnable() {
        gunKey = new NamespacedKey(this, "gun");
        ammoKey = new NamespacedKey(this, "ammo");
        projectileKey = new NamespacedKey(this, "projectile");
        trollKey = new NamespacedKey(this, "troll");
        toolKey = new NamespacedKey(this, "tool");
        undo = new UndoManager(this);
        saveDefaultConfig();
        loadStats();
        SafeDamage safe = new SafeDamage(this);
        tempBlocks = new TempBlocks(this);
        moreTrolls = new MoreTrolls(this, safe, tempBlocks);
        trolls = new TrollListener(this, safe, moreTrolls, new ChaosTrolls(this, safe));
        getServer().getPluginManager().registerEvents(new ToolListener(this, safe), this);
        getServer().getPluginManager().registerEvents(new MoreTools(this, safe, tempBlocks), this);
        getServer().getPluginManager().registerEvents(safe, this);
        getServer().getPluginManager().registerEvents(tempBlocks, this);
        getServer().getPluginManager().registerEvents(moreTrolls, this);
        Nukes nukes = new Nukes(this, safe, trolls, tempBlocks);
        getServer().getPluginManager().registerEvents(nukes, this);
        MegaTrolls megaTrolls = new MegaTrolls(this, safe, nukes, moreTrolls);
        trolls.setMega(megaTrolls);
        getServer().getPluginManager().registerEvents(megaTrolls, this);
        getServer().getPluginManager().registerEvents(new GunListener(this, safe, nukes, new SciFiGuns(this, safe, nukes)), this);
        getServer().getPluginManager().registerEvents(trolls, this);
        var cmd = getCommand("guns");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (tempBlocks != null) tempBlocks.revertAll();
        if (moreTrolls != null) moreTrolls.cleanup();
    }

    public void loadStats() {
        reloadConfig();
        // Add settings from newer versions to an existing config.yml
        getConfig().options().copyDefaults(true);
        saveConfig();
        stats.clear();
        ConfigurationSection guns = getConfig().getConfigurationSection("guns");
        for (GunType type : GunType.values()) {
            ConfigurationSection s = guns == null ? null : guns.getConfigurationSection(type.id());
            if (s == null) {
                getLogger().warning("Missing config for gun '" + type.id() + "', using defaults");
                s = getConfig().createSection("guns." + type.id());
            }
            stats.put(type, GunStats.load(s));
        }
    }

    public GunStats stats(GunType type) {
        return stats.get(type);
    }

    public UndoManager undo() {
        return undo;
    }

    public NamespacedKey gunKey() {
        return gunKey;
    }

    public NamespacedKey ammoKey() {
        return ammoKey;
    }

    public NamespacedKey projectileKey() {
        return projectileKey;
    }

    public ItemStack createGun(GunType type) {
        GunStats s = stats(type);
        ItemStack item = new ItemStack(Material.CARROT_ON_A_STICK);
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize(s.name()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Right-click to shoot", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Sneak + right-click to reload", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            if (s.itemModel() != null && !s.itemModel().isBlank()) {
                NamespacedKey model = NamespacedKey.fromString(s.itemModel());
                if (model != null) meta.setItemModel(model);
            }
            meta.getPersistentDataContainer().set(gunKey, PersistentDataType.STRING, type.id());
            meta.getPersistentDataContainer().set(ammoKey, PersistentDataType.INTEGER, s.magazine());
        });
        return item;
    }

    public ItemStack createTrollTool(TrollTool tool) {
        ItemStack item = new ItemStack(tool.material());
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize(tool.displayName()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text(tool.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.setMaxStackSize(1);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(trollKey, PersistentDataType.STRING, tool.id());
        });
        return item;
    }

    public ItemStack createTool(ToolType tool) {
        ItemStack item = new ItemStack(tool.material());
        item.editMeta(meta -> {
            meta.displayName(MiniMessage.miniMessage().deserialize(tool.displayName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : tool.lore()) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            if (tool.material().name().startsWith("NETHERITE_") && tool != ToolType.DASH_BLADE) {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            }
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.id());
        });
        return item;
    }

    public ToolType toolType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return ToolType.fromId(item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING));
    }

    public TrollTool trollTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return TrollTool.fromId(item.getItemMeta().getPersistentDataContainer().get(trollKey, PersistentDataType.STRING));
    }

    public GunType gunType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return GunType.fromId(item.getItemMeta().getPersistentDataContainer().get(gunKey, PersistentDataType.STRING));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/guns give <item|guns|nukes|trolls|tools|all> [player] | /guns size <player|all> <tiny|small|normal|big|huge|giant|number> | /guns troll <player> <effect> | /guns undo [amount|all|player <name>] | /guns list | /guns reload", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                loadStats();
                sender.sendMessage(Component.text("GHSGuns config reloaded.", NamedTextColor.GREEN));
            }
            case "size" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /guns size <player|all> <tiny|small|normal|big|huge|giant|number>", NamedTextColor.RED));
                    return true;
                }
                Double scale = switch (args[2].toLowerCase()) {
                    case "tiny" -> 0.25;
                    case "small" -> 0.5;
                    case "normal", "reset", "regular" -> 1.0;
                    case "big" -> 1.5;
                    case "huge" -> 2.5;
                    case "giant" -> 4.0;
                    default -> {
                        try {
                            yield Math.max(0.1, Math.min(10.0, Double.parseDouble(args[2])));
                        } catch (NumberFormatException e) {
                            yield null;
                        }
                    }
                };
                if (scale == null) {
                    sender.sendMessage(Component.text("Unknown size: " + args[2], NamedTextColor.RED));
                    return true;
                }
                List<Player> targets = new ArrayList<>();
                if (args[1].equalsIgnoreCase("all")) {
                    targets.addAll(Bukkit.getOnlinePlayers());
                } else {
                    Player p = Bukkit.getPlayerExact(args[1]);
                    if (p == null) {
                        sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                        return true;
                    }
                    targets.add(p);
                }
                for (Player p : targets) moreTrolls.setPermanentSize(p, scale);
                sender.sendMessage(Component.text("Set " + args[1] + " to size " + scale + (scale == 1.0 ? " (normal)" : " (stays after relog/restart)"), NamedTextColor.GREEN));
            }
            case "undo" -> {
                int done;
                if (args.length >= 3 && args[1].equalsIgnoreCase("player")) {
                    done = undo.undoPlayer(args[2]);
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                    done = undo.undo(Integer.MAX_VALUE);
                } else {
                    int amount = 1;
                    if (args.length >= 2) {
                        try {
                            amount = Math.max(1, Integer.parseInt(args[1]));
                        } catch (NumberFormatException e) {
                            sender.sendMessage(Component.text("Usage: /guns undo [amount|all|player <name>]", NamedTextColor.RED));
                            return true;
                        }
                    }
                    done = undo.undo(amount);
                }
                sender.sendMessage(Component.text("Undid " + done + " explosion(s). " + undo.size() + " left in history.",
                        done > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "list" -> {
                List<String> guns = new ArrayList<>();
                for (GunType t : GunType.values()) guns.add(t.id());
                List<String> tools = new ArrayList<>();
                for (TrollTool t : TrollTool.values()) tools.add(t.id());
                sender.sendMessage(Component.text("Guns: " + String.join(", ", guns), NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Troll items: " + String.join(", ", tools), NamedTextColor.LIGHT_PURPLE));
                List<String> utils = new ArrayList<>();
                for (ToolType t : ToolType.values()) utils.add(t.id());
                sender.sendMessage(Component.text("Tools: " + String.join(", ", utils), NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Troll commands: " + String.join(", ", TrollListener.COMMAND_TROLLS), NamedTextColor.AQUA));
            }
            case "troll" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /guns troll <player|all> <" + String.join("|", TrollListener.COMMAND_TROLLS) + ">", NamedTextColor.RED));
                    return true;
                }
                if (!TrollListener.COMMAND_TROLLS.contains(args[2].toLowerCase())) {
                    sender.sendMessage(Component.text("Unknown troll: " + args[2], NamedTextColor.RED));
                    return true;
                }
                List<Player> targets = new ArrayList<>();
                if (args[1].equalsIgnoreCase("all")) {
                    targets.addAll(Bukkit.getOnlinePlayers());
                } else {
                    Player p = Bukkit.getPlayerExact(args[1]);
                    if (p == null) {
                        sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                        return true;
                    }
                    targets.add(p);
                }
                for (Player p : targets) {
                    if (!trolls.commandTroll(p, args[2])) {
                        sender.sendMessage(Component.text("Unknown troll: " + args[2], NamedTextColor.RED));
                        return true;
                    }
                }
                sender.sendMessage(Component.text("Trolled " + args[1] + " with " + args[2] + ".", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /guns give <item|guns|nukes|trolls|tools|all> [player]", NamedTextColor.RED));
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
                    return true;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                List<ItemStack> toGive = new ArrayList<>();
                String what = args[1].toLowerCase();
                if (what.equals("all") || what.equals("guns")) {
                    for (GunType t : GunType.values()) if (!t.nuke()) toGive.add(createGun(t));
                }
                if (what.equals("all") || what.equals("nukes")) {
                    for (GunType t : GunType.values()) if (t.nuke()) toGive.add(createGun(t));
                }
                if (what.equals("all") || what.equals("trolls")) {
                    for (TrollTool t : TrollTool.values()) toGive.add(createTrollTool(t));
                }
                if (what.equals("all") || what.equals("tools")) {
                    for (ToolType t : ToolType.values()) toGive.add(createTool(t));
                }
                if (toGive.isEmpty()) {
                    GunType type = GunType.fromId(what);
                    TrollTool tool = TrollTool.fromId(what);
                    ToolType util = ToolType.fromId(what);
                    if (type != null) toGive.add(createGun(type));
                    else if (tool != null) toGive.add(createTrollTool(tool));
                    else if (util != null) toGive.add(createTool(util));
                    else {
                        sender.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                }
                for (ItemStack item : toGive) {
                    target.getInventory().addItem(item).values()
                            .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
                }
                sender.sendMessage(Component.text("Gave " + args[1] + " to " + target.getName(), NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("give", "troll", "size", "undo", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("all", "guns", "nukes", "trolls", "tools"));
            for (ToolType t : ToolType.values()) options.add(t.id());
            for (GunType t : GunType.values()) options.add(t.id());
            for (TrollTool t : TrollTool.values()) options.add(t.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("size")) {
            options.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("size")) {
            options.addAll(List.of("tiny", "small", "normal", "big", "huge", "giant"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("troll")) {
            options.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("troll")) {
            options.addAll(TrollListener.COMMAND_TROLLS);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("undo")) {
            options.addAll(List.of("1", "5", "all", "player"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("undo") && args[1].equalsIgnoreCase("player")) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        }
        String last = args[args.length - 1].toLowerCase();
        options.removeIf(o -> !o.toLowerCase().startsWith(last));
        return options;
    }
}
