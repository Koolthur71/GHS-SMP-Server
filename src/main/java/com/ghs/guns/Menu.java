package com.ghs.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** The /guns chest menu: everything sorted into categories, plus a "troll a player" picker. */
public final class Menu implements Listener {

    /** One clickable icon. */
    private record Entry(ItemStack icon, Consumer<Player> action) {}

    /** A titled group of entries; each section starts on a new row with a colored header pane. */
    private record Section(String name, Material pane, List<Entry> entries) {}

    /** A /guns troll effect shown in the menu. */
    private record Effect(String id, String name, Material icon, String description) {}

    private static final class Holder implements InventoryHolder {
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int ROWS_PER_PAGE = 5;

    private static final List<Effect> PHYSICAL = List.of(
            new Effect("launch", "Launch", Material.FEATHER, "Yeet them into the sky"),
            new Effect("rocket", "Rocket Ride", Material.FIREWORK_ROCKET, "Blast off in a firework"),
            new Effect("ufo", "UFO Abduction", Material.ECHO_SHARD, "A UFO beams them up"),
            new Effect("orbit", "Orbit", Material.ENDER_PEARL, "Spin around in orbit, then get flung"),
            new Effect("skydrop", "Sky Drop", Material.ELYTRA, "Teleport 120 blocks up (fall can't kill)"),
            new Effect("levitate", "Levitate", Material.END_ROD, "Float away"),
            new Effect("lowgravity", "Moon Gravity", Material.END_STONE, "Huge floaty jumps for 20s"),
            new Effect("bouncy", "Bouncy", Material.SLIME_BALL, "Nonstop bouncing for 15s"),
            new Effect("freeze", "Freeze", Material.ICE, "Frozen in place for 5s"),
            new Effect("cage", "Cage", Material.IRON_BARS, "Glass cage for 10s"),
            new Effect("webs", "Web Trap", Material.COBWEB, "Cobwebs around them"),
            new Effect("anvil", "Anvil Rain", Material.ANVIL, "Harmless anvils fall on them"),
            new Effect("meteor", "Meteor Shower", Material.FIRE_CHARGE, "Meteors rain around them"),
            new Effect("lightning", "Smite", Material.LIGHTNING_ROD, "Harmless lightning strikes"),
            new Effect("earthquake", "Earthquake", Material.CRACKED_STONE_BRICKS, "The ground shakes for 5s"),
            new Effect("tornado", "Tornado", Material.WIND_CHARGE, "A tornado sucks them up"),
            new Effect("blackhole", "Black Hole", Material.OBSIDIAN, "A mini black hole pulls them in"),
            new Effect("explode", "Fake Explosion", Material.TNT, "Boom (no damage) and up they go"),
            new Effect("faketnt", "Fake TNT", Material.TNT_MINECART, "Hissing TNT that pops into confetti"),
            new Effect("flashbang", "Flashbang", Material.IRON_NUGGET, "Blind and ears ringing"),
            new Effect("stinkbomb", "Stink Bomb", Material.FERMENTED_SPIDER_EYE, "Nausea cloud"),
            new Effect("sheep", "Exploding Sheep", Material.WHITE_WOOL, "A rainbow sheep explodes on them"),
            new Effect("chickens", "Chicken Bomb", Material.EGG, "20 chickens burst out"),
            new Effect("pigrain", "Pig Rain", Material.PORKCHOP, "Pigs fall from the sky"),
            new Effect("creeperparty", "Creeper Party", Material.CREEPER_HEAD, "Creepers that pop into confetti"),
            new Effect("fireworks", "Firework Show", Material.FIREWORK_STAR, "Fireworks all around them"),
            new Effect("disco", "Disco", Material.JUKEBOX, "10-second dance party"),
            new Effect("rainbow", "Rainbow Trail", Material.PRISMARINE_CRYSTALS, "Rainbow trail and halo for 30s"),
            new Effect("pinata", "Piñata", Material.BAMBOO, "Candy flies out when they get hit"),
            new Effect("potato", "Hot Potato", Material.BAKED_POTATO, "Pass it in 15s or it blows up"),
            new Effect("hiccups", "Hiccups", Material.GLASS_BOTTLE, "*hic* for 30s"),
            new Effect("shrink", "Shrink (30s)", Material.AMETHYST_SHARD, "Temporarily tiny"),
            new Effect("giant", "Giant (30s)", Material.GOLDEN_APPLE, "Temporarily huge"));

    private static final List<Effect> PRANKS = List.of(
            new Effect("fakeop", "Fake OP", Material.COMMAND_BLOCK, "Fake 'you are now op' message"),
            new Effect("fakeban", "Fake Ban", Material.BARRIER, "Kicks them with a fake ban (they can rejoin)"),
            new Effect("fakedeath", "Fake Death", Material.SKELETON_SKULL, "Fake death message to everyone"),
            new Effect("fakeleave", "Fake Leave", Material.OAK_DOOR, "Fake 'left the game' message"),
            new Effect("fakeadvancement", "Fake Advancement", Material.KNOWLEDGE_BOOK, "[Skill Issue] and friends"),
            new Effect("fakenuke", "Fake Nuke", Material.RED_CONCRETE, "Siren and countdown... jk"),
            new Effect("fakediamonds", "Fake Diamonds", Material.DIAMOND, "Diamonds that vanish when they get close"),
            new Effect("creeper", "Creeper Hiss", Material.GUNPOWDER, "Hiss sound right behind them"),
            new Effect("jumpscare", "Jumpscare", Material.WITHER_SKELETON_SKULL, "Elder guardian face + scream"),
            new Effect("paranoia", "Paranoia", Material.SCULK_SENSOR, "Creepy sounds around them for 30s"),
            new Effect("demo", "Demo Screen", Material.MAP, "Shows the 'demo mode' screen"),
            new Effect("drunk", "Drunk", Material.HONEY_BOTTLE, "Wobbly walking, slurred chat"),
            new Effect("uwu", "UwU Chat", Material.PINK_WOOL, "Their chat turns uwu for 60s"),
            new Effect("spin", "Spin", Material.COMPASS, "Spins their camera"),
            new Effect("screenshake", "Screen Shake", Material.TRIPWIRE_HOOK, "Shaky camera"),
            new Effect("shuffle", "Hotbar Shuffle", Material.CHEST, "Mixes up their hotbar (nothing lost)"),
            new Effect("slowmo", "Slow-mo", Material.CLOCK, "Everything slow for 10s"),
            new Effect("zoomies", "Zoomies", Material.SUGAR, "Super speed for 10s"),
            new Effect("chickenhat", "Chicken Hat", Material.CHICKEN, "A chicken rides on their head"),
            new Effect("cakerain", "Cake Rain", Material.CAKE, "Food they can't pick up"));

    private final GHSGuns plugin;
    private final TrollListener trolls;
    private final MoreTrolls moreTrolls;

    public Menu(GHSGuns plugin, TrollListener trolls, MoreTrolls moreTrolls) {
        this.plugin = plugin;
        this.trolls = trolls;
        this.moreTrolls = moreTrolls;
    }

    // ------------------------------------------------------------------ icons

    private static ItemStack icon(Material material, Component name, List<String> lore, NamedTextColor loreColor) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> l = new ArrayList<>();
            for (String line : lore) l.add(Component.text(line, loreColor).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color, String... lore) {
        return icon(material, Component.text(name, color, TextDecoration.BOLD), List.of(lore), NamedTextColor.GRAY);
    }

    private static ItemStack pane(Material pane, String name) {
        ItemStack item = new ItemStack(pane);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return item;
    }

    /** The real item with a "click to get" line added to its tooltip. */
    private static ItemStack withClickHint(ItemStack real) {
        ItemStack shown = real.clone();
        shown.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(Component.text("▶ Click to get", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return shown;
    }

    private Entry giveEntry(ItemStack real) {
        return new Entry(withClickHint(real), p -> give(p, real));
    }

    private void give(Player p, ItemStack item) {
        p.getInventory().addItem(item.clone()).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        Component name = item.getItemMeta().displayName();
        p.sendActionBar(Component.text("Got ", NamedTextColor.GREEN).append(name == null ? Component.text(item.getType().name()) : name));
    }

    // ------------------------------------------------------------------ screens

    public void openMain(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 45, Component.text("GHS Arsenal", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        h.inventory = inv;
        fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        set(h, 10, icon(Material.DIAMOND_HORSE_ARMOR, "Guns", NamedTextColor.GOLD,
                "Classic guns, launchers and sci-fi weapons", "", count(GunType.values().length - nukeCount()) + " items"), this::openGuns);
        set(h, 12, icon(Material.TNT, "Nukes", NamedTextColor.RED,
                "Nuke, Tsar Bomba, Cluster, Cryo,", "Chicken, Party and Black Hole", "", count(nukeCount()) + " items"), this::openNukes);
        set(h, 14, icon(Material.NETHERITE_PICKAXE, "Tools", NamedTextColor.GREEN,
                "Mining, building, movement and utility", "", count(ToolType.values().length) + " items"), this::openTools);
        set(h, 16, icon(Material.SLIME_BALL, "Troll Items", NamedTextColor.LIGHT_PURPLE,
                "Wands and throwables to mess with people", "", count(TrollTool.values().length) + " items"), this::openTrollItems);
        set(h, 28, icon(Material.PLAYER_HEAD, "Troll a Player", NamedTextColor.AQUA,
                "Pick a player, then pick an effect.", "Includes permanent size changes."), pl -> openPlayers(pl, 0));
        set(h, 30, icon(Material.RECOVERY_COMPASS, "Undo Last Explosion", NamedTextColor.YELLOW,
                "Puts back the blocks from the most", "recent gun or nuke explosion"), pl -> {
            int n = plugin.undo().undo(1);
            pl.sendActionBar(Component.text(n > 0 ? "Undid the last explosion" : "Nothing to undo", n > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            pl.playSound(pl.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        });
        boolean infinite = plugin.infiniteAmmo();
        set(h, 32, icon(infinite ? Material.LIME_DYE : Material.GRAY_DYE, "Infinite Ammo: " + (infinite ? "ON" : "OFF"),
                infinite ? NamedTextColor.GREEN : NamedTextColor.GRAY, infinite ? "Guns never need reloading" : "Guns use magazines and reload",
                "", "▶ Click to switch " + (infinite ? "off" : "on")), pl -> {
            plugin.setInfiniteAmmo(!plugin.infiniteAmmo());
            openMain(pl);
        });
        set(h, 34, icon(Material.BARRIER, "Close", NamedTextColor.RED), HumanEntity::closeInventory);
        p.openInventory(inv);
    }

    private static String count(int n) {
        return String.valueOf(n);
    }

    private static int nukeCount() {
        int n = 0;
        for (GunType t : GunType.values()) if (t.nuke()) n++;
        return n;
    }

    private void openGuns(Player p) {
        openItems(p, "Guns", NamedTextColor.GOLD, sections(List.of(GunType.values()), t -> !t.nuke(), GunType::group,
                t -> giveEntry(plugin.createGun(t)), Map.of("Classic Guns", Material.ORANGE_STAINED_GLASS_PANE,
                        "Launchers", Material.RED_STAINED_GLASS_PANE, "Sci-Fi Guns", Material.CYAN_STAINED_GLASS_PANE)), 0);
    }

    private void openNukes(Player p) {
        openItems(p, "Nukes", NamedTextColor.RED, sections(List.of(GunType.values()), GunType::nuke, GunType::group,
                t -> giveEntry(plugin.createGun(t)), Map.of("Nukes", Material.RED_STAINED_GLASS_PANE)), 0);
    }

    private void openTools(Player p) {
        openItems(p, "Tools", NamedTextColor.GREEN, sections(List.of(ToolType.values()), t -> true, ToolType::group,
                t -> giveEntry(plugin.createTool(t)), Map.of("Mining", Material.BROWN_STAINED_GLASS_PANE,
                        "Building & Farming", Material.LIME_STAINED_GLASS_PANE, "Movement", Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                        "Utility", Material.YELLOW_STAINED_GLASS_PANE, "Admin", Material.RED_STAINED_GLASS_PANE)), 0);
    }

    private void openTrollItems(Player p) {
        openItems(p, "Troll Items", NamedTextColor.LIGHT_PURPLE, sections(List.of(TrollTool.values()), t -> true, TrollTool::group,
                t -> giveEntry(plugin.createTrollTool(t)), Map.of("Movement Wands", Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                        "Chaos Wands", Material.PURPLE_STAINED_GLASS_PANE, "Size", Material.MAGENTA_STAINED_GLASS_PANE,
                        "Throwables", Material.LIME_STAINED_GLASS_PANE)), 0);
    }

    /** Group items into sections, keeping the order they're declared in. */
    private <T> List<Section> sections(List<T> all, java.util.function.Predicate<T> filter, Function<T, String> group,
                                       Function<T, Entry> entry, Map<String, Material> panes) {
        Map<String, List<Entry>> grouped = new LinkedHashMap<>();
        for (T t : all) {
            if (!filter.test(t)) continue;
            grouped.computeIfAbsent(group.apply(t), k -> new ArrayList<>()).add(entry.apply(t));
        }
        List<Section> out = new ArrayList<>();
        grouped.forEach((name, entries) -> out.add(new Section(name, panes.getOrDefault(name, Material.GRAY_STAINED_GLASS_PANE), entries)));
        return out;
    }

    /** A category page: sections laid out in rows, with paging and a "give all" button. */
    private void openItems(Player p, String title, NamedTextColor color, List<Section> sections, int page) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text(title, color, TextDecoration.BOLD));
        h.inventory = inv;
        int pages = layout(h, sections, page);
        nav(h, page, pages, pl -> openMain(pl), pg -> pl -> openItems(pl, title, color, sections, pg));
        List<Entry> everything = new ArrayList<>();
        sections.forEach(s -> everything.addAll(s.entries()));
        set(h, 49, icon(Material.CHEST, "Give me all " + title, NamedTextColor.GREEN, everything.size() + " items",
                "Extras drop at your feet if your inventory is full"), pl -> everything.forEach(e -> e.action().accept(pl)));
        p.openInventory(inv);
    }

    /** Lays out sections from row 0; returns how many pages there are. */
    private int layout(Holder h, List<Section> sections, int page) {
        // Build virtual rows: [header, up to 8 entries]
        List<Entry[]> rows = new ArrayList<>();
        List<ItemStack> headers = new ArrayList<>();
        for (Section s : sections) {
            List<Entry> e = s.entries();
            for (int i = 0; i < e.size(); i += 8) {
                Entry[] row = new Entry[8];
                for (int j = 0; j < 8 && i + j < e.size(); j++) row[j] = e.get(i + j);
                rows.add(row);
                headers.add(pane(s.pane(), i == 0 ? s.name() : s.name() + " (cont.)"));
            }
        }
        int pages = Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        for (int r = 0; r < ROWS_PER_PAGE; r++) {
            int idx = page * ROWS_PER_PAGE + r;
            if (idx >= rows.size()) break;
            h.inventory.setItem(r * 9, headers.get(idx));
            Entry[] row = rows.get(idx);
            for (int j = 0; j < 8; j++) {
                if (row[j] != null) set(h, r * 9 + 1 + j, row[j].icon(), row[j].action());
            }
        }
        return pages;
    }

    private void nav(Holder h, int page, int pages, Consumer<Player> back, Function<Integer, Consumer<Player>> toPage) {
        for (int i = 45; i < 54; i++) h.inventory.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE, " "));
        set(h, 45, icon(Material.ARROW, "Back", NamedTextColor.YELLOW), back);
        if (page > 0) set(h, 47, icon(Material.SPECTRAL_ARROW, "Previous Page", NamedTextColor.YELLOW, "Page " + page + " of " + pages), toPage.apply(page - 1));
        if (page < pages - 1) set(h, 51, icon(Material.SPECTRAL_ARROW, "Next Page", NamedTextColor.YELLOW, "Page " + (page + 2) + " of " + pages), toPage.apply(page + 1));
        set(h, 53, icon(Material.BARRIER, "Close", NamedTextColor.RED), HumanEntity::closeInventory);
    }

    // ------------------------------------------------------------------ troll a player

    private void openPlayers(Player p, int page) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Troll a Player", NamedTextColor.AQUA, TextDecoration.BOLD));
        h.inventory = inv;
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(icon(Material.BEACON, "Everyone Else", NamedTextColor.GOLD, "Troll every online player except you"),
                pl -> openEffects(pl, null, 0)));
        for (Player target : Bukkit.getOnlinePlayers()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(target);
                meta.displayName(Component.text(target.getName(), NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text("Click to pick a troll", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            });
            UUID id = target.getUniqueId();
            entries.add(new Entry(head, pl -> openEffects(pl, id, 0)));
        }
        int pages = layout(h, List.of(new Section("Players", Material.CYAN_STAINED_GLASS_PANE, entries)), page);
        nav(h, page, pages, this::openMain, pg -> pl -> openPlayers(pl, pg));
        p.openInventory(inv);
    }

    /** target null = everyone. */
    private void openEffects(Player p, UUID targetId, int page) {
        Player single = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (targetId != null && single == null) {
            p.sendActionBar(Component.text("That player left.", NamedTextColor.RED));
            openPlayers(p, 0);
            return;
        }
        String who = single == null ? "Everyone Else" : single.getName();
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Troll: " + who, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        h.inventory = inv;

        List<Entry> sizes = new ArrayList<>();
        Object[][] sizeList = {{"Tiny", 0.25, Material.BROWN_MUSHROOM}, {"Small", 0.5, Material.RED_MUSHROOM},
                {"Normal", 1.0, Material.ARMOR_STAND}, {"Big", 1.5, Material.PUMPKIN}, {"Huge", 2.5, Material.MELON}, {"Giant", 4.0, Material.IRON_GOLEM_SPAWN_EGG}};
        for (Object[] s : sizeList) {
            double scale = (double) s[1];
            Material mat = (Material) s[2];
            String lore = scale == 1.0 ? "Back to regular size" : "Permanent: stays after relog/restart";
            sizes.add(new Entry(icon(mat, "Size: " + s[0], NamedTextColor.LIGHT_PURPLE, lore, "", "▶ Click to apply"),
                    pl -> forTargets(pl, targetId, t -> moreTrolls.setPermanentSize(t, scale), "size " + s[0])));
        }
        List<Section> sections = List.of(
                new Section("Size (permanent)", Material.MAGENTA_STAINED_GLASS_PANE, sizes),
                new Section("Physical", Material.ORANGE_STAINED_GLASS_PANE, effectEntries(PHYSICAL, targetId)),
                new Section("Pranks", Material.LIME_STAINED_GLASS_PANE, effectEntries(PRANKS, targetId)));
        int pages = layout(h, sections, page);
        nav(h, page, pages, pl -> openPlayers(pl, 0), pg -> pl -> openEffects(pl, targetId, pg));
        p.openInventory(inv);
    }

    private List<Entry> effectEntries(List<Effect> effects, UUID targetId) {
        List<Entry> out = new ArrayList<>();
        for (Effect e : effects) {
            ItemStack ic = icon(e.icon(), Component.text(e.name(), NamedTextColor.YELLOW, TextDecoration.BOLD),
                    List.of(e.description(), "", "▶ Click to troll"), NamedTextColor.GRAY);
            out.add(new Entry(ic, pl -> forTargets(pl, targetId, t -> trolls.commandTroll(t, e.id()), e.name())));
        }
        return out;
    }

    private void forTargets(Player user, UUID targetId, Consumer<Player> action, String what) {
        List<Player> targets = new ArrayList<>();
        if (targetId == null) {
            targets.addAll(Bukkit.getOnlinePlayers());
            targets.remove(user);
        } else {
            Player t = Bukkit.getPlayer(targetId);
            if (t != null) targets.add(t);
        }
        if (targets.isEmpty()) {
            user.sendActionBar(Component.text("Nobody to troll.", NamedTextColor.RED));
            return;
        }
        targets.forEach(action);
        user.playSound(user.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.8f, 1.4f);
        user.sendActionBar(Component.text(what + " → " + (targetId == null ? "everyone else" : targets.get(0).getName()), NamedTextColor.LIGHT_PURPLE));
    }

    // ------------------------------------------------------------------ plumbing

    private static void fill(Inventory inv, Material pane) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, Menu.pane(pane, " "));
    }

    private static void set(Holder h, int slot, ItemStack item, Consumer<Player> action) {
        h.inventory.setItem(slot, item);
        h.actions.put(slot, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder h)) return;
        event.setCancelled(true); // nothing can be taken out or put in
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Consumer<Player> action = h.actions.get(event.getRawSlot());
        if (action == null) return;
        if (!p.hasPermission("ghsguns.admin")) {
            p.closeInventory();
            return;
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        // Run next tick so opening another menu from a click is safe
        Bukkit.getScheduler().runTask(plugin, () -> action.accept(p));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ hint when you pick up / switch to an item

    @EventHandler
    public void onHold(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        ItemStack item = p.getInventory().getItem(event.getNewSlot());
        if (item == null) return;
        String hint = null;
        GunType gun = plugin.gunType(item);
        if (gun != null) hint = gun.description();
        ToolType tool = plugin.toolType(item);
        if (tool != null) hint = String.join(" · ", tool.lore());
        TrollTool troll = plugin.trollTool(item);
        if (troll != null) hint = troll.description();
        if (hint == null) return;
        if (!plugin.canUse(p)) {
            p.sendActionBar(Component.text("You don't have permission to use this item.", NamedTextColor.RED));
            return;
        }
        Component name = item.getItemMeta().displayName();
        p.sendActionBar((name == null ? Component.empty() : name.append(Component.text("  ", NamedTextColor.GRAY)))
                .append(Component.text(hint, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
    }
}
