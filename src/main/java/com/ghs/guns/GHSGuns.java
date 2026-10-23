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
    private UndoManager undo;

    @Override
    public void onEnable() {
        gunKey = new NamespacedKey(this, "gun");
        ammoKey = new NamespacedKey(this, "ammo");
        projectileKey = new NamespacedKey(this, "projectile");
        undo = new UndoManager(this);
        saveDefaultConfig();
        loadStats();
        getServer().getPluginManager().registerEvents(new GunListener(this), this);
        var cmd = getCommand("guns");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    public void loadStats() {
        reloadConfig();
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

    public GunType gunType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return GunType.fromId(item.getItemMeta().getPersistentDataContainer().get(gunKey, PersistentDataType.STRING));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/guns give <type|all> [player] | /guns undo [amount|all|player <name>] | /guns list | /guns reload", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                loadStats();
                sender.sendMessage(Component.text("GHSGuns config reloaded.", NamedTextColor.GREEN));
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
                List<String> ids = new ArrayList<>();
                for (GunType t : GunType.values()) ids.add(t.id());
                sender.sendMessage(Component.text("Guns: " + String.join(", ", ids), NamedTextColor.YELLOW));
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /guns give <type|all> [player]", NamedTextColor.RED));
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
                List<GunType> toGive = new ArrayList<>();
                if (args[1].equalsIgnoreCase("all")) {
                    toGive.addAll(List.of(GunType.values()));
                } else {
                    GunType type = GunType.fromId(args[1]);
                    if (type == null) {
                        sender.sendMessage(Component.text("Unknown gun: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                    toGive.add(type);
                }
                for (GunType type : toGive) {
                    target.getInventory().addItem(createGun(type)).values()
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
            options.addAll(List.of("give", "undo", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            options.add("all");
            for (GunType t : GunType.values()) options.add(t.id());
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
