package dev.vibe.plus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin on/off switches (true = vanilla behaviour stays on). Saved in config.yml under "toggles". */
public final class Toggles implements Listener, CommandExecutor, TabCompleter {
    private record T(String key, String label, Material icon, String desc) { }

    private static final List<T> ALL = List.of(
            new T("mobs.hostile", "Hostile mob spawning", Material.ZOMBIE_HEAD, "Natural zombies, skeletons, creepers..."),
            new T("mobs.passive", "Passive mob spawning", Material.WHEAT, "Natural cows, sheep, pigs..."),
            new T("mobs.water", "Water mob spawning", Material.COD, "Squid, fish, dolphins..."),
            new T("mobs.ambient", "Ambient mob spawning", Material.BAT_SPAWN_EGG, "Bats"),
            new T("mobs.phantoms", "Phantoms", Material.PHANTOM_MEMBRANE, "Insomnia phantoms"),
            new T("mobs.spawners", "Spawner mobs", Material.SPAWNER, "Mobs from mob spawner blocks"),
            new T("mobs.raids", "Raids & patrols", Material.CROSSBOW, "Pillager patrols and raids"),
            new T("mobs.griefing", "Mob griefing", Material.ENDER_PEARL, "Endermen, ravagers, sheep eating grass..."),
            new T("explosions.creeper-blocks", "Creeper block damage", Material.GUNPOWDER, "Creeper blasts break blocks"),
            new T("explosions.tnt-blocks", "TNT block damage", Material.TNT, "TNT and TNT minecarts break blocks"),
            new T("fire-spread", "Fire spread", Material.FLINT_AND_STEEL, "Fire spreading / burning blocks"),
            new T("weather", "Rain & storms", Material.WATER_BUCKET, "Off = always clear weather"),
            new T("hunger", "Hunger", Material.COOKED_BEEF, "Off = food bar never drops"),
            new T("fall-damage", "Fall damage", Material.FEATHER, "Off = no fall damage"));

    static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private final VibeEconomyPlus pl;
    private final Map<String, Boolean> cache = new HashMap<>();

    public Toggles(VibeEconomyPlus pl) {
        this.pl = pl;
        for (T t : ALL) cache.put(t.key, pl.getConfig().getBoolean("toggles." + t.key, true));
    }

    private boolean on(String key) { return cache.getOrDefault(key, true); }

    private void set(String key, boolean v) {
        cache.put(key, v);
        pl.getConfig().set("toggles." + key, v);
        pl.saveConfig();
        if (key.equals("weather") && !v) for (World w : Bukkit.getWorlds()) { w.setStorm(false); w.setThundering(false); }
    }

    // ---------- events ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        Entity en = e.getEntity();
        switch (r) {
            case SPAWNER -> { if (!on("mobs.spawners")) e.setCancelled(true); return; }
            case RAID, PATROL -> { if (!on("mobs.raids")) e.setCancelled(true); return; }
            case NATURAL, CHUNK_GEN, REINFORCEMENTS, JOCKEY, VILLAGE_INVASION -> { }
            default -> { return; } // eggs, commands, breeding, custom plugins: never touched
        }
        if (en instanceof Phantom) { if (!on("mobs.phantoms")) e.setCancelled(true); return; }
        if (en instanceof Enemy) { if (!on("mobs.hostile")) e.setCancelled(true); }
        else if (en instanceof WaterMob) { if (!on("mobs.water")) e.setCancelled(true); }
        else if (en instanceof Ambient) { if (!on("mobs.ambient")) e.setCancelled(true); }
        else if (en instanceof Animals) { if (!on("mobs.passive")) e.setCancelled(true); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobGrief(EntityChangeBlockEvent e) {
        if (on("mobs.griefing")) return;
        if (e.getEntity() instanceof Player || e.getEntity() instanceof FallingBlock) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Entity en = e.getEntity();
        if (en instanceof Creeper && !on("explosions.creeper-blocks")) e.blockList().clear();
        else if ((en instanceof TNTPrimed || en instanceof org.bukkit.entity.minecart.ExplosiveMinecart)
                && !on("explosions.tnt-blocks")) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (!on("fire-spread")) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (on("fire-spread")) return;
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA)
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) { if (e.toWeatherState() && !on("weather")) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (on("hunger") || !(e.getEntity() instanceof Player p)) return;
        if (e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (!on("fall-damage") && e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL)
            e.setCancelled(true);
    }

    // ---------- command + GUI ----------
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            if (s instanceof org.bukkit.entity.Player p) { openGui(p); return true; }
            for (T t : ALL) s.sendMessage(t.key + " = " + (on(t.key) ? "on" : "off"));
            return true;
        }
        T t = find(a[0]);
        if (t == null) { s.sendMessage(Bridge.c("&cUnknown toggle. Keys: &f" + keys())); return true; }
        boolean v = a.length > 1 ? a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("true") : !on(t.key);
        set(t.key, v);
        s.sendMessage(Bridge.c("&7" + t.label + ": " + (v ? "&aON" : "&cOFF")));
        return true;
    }

    private T find(String k) { for (T t : ALL) if (t.key.equalsIgnoreCase(k)) return t; return null; }

    private String keys() {
        List<String> l = new ArrayList<>();
        for (T t : ALL) l.add(t.key);
        return String.join(", ", l);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) for (T t : ALL) { if (t.key.startsWith(a[0].toLowerCase())) out.add(t.key); }
        else if (a.length == 2) { out.add("on"); out.add("off"); }
        return out;
    }

    private void openGui(org.bukkit.entity.Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 36, Bridge.c("&5&lServer Toggles"));
        render(h.inv);
        p.openInventory(h.inv);
    }

    private void render(Inventory inv) {
        inv.clear();
        for (int i = 0; i < ALL.size(); i++) {
            T t = ALL.get(i);
            boolean v = on(t.key);
            ItemStack it = new ItemStack(t.icon);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(Bridge.c((v ? "&a" : "&c") + t.label + (v ? " &7(ON)" : " &7(OFF)")));
            m.setLore(List.of(Bridge.c("&7" + t.desc), Bridge.c("&eClick to toggle")));
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= ALL.size()) return;
        T t = ALL.get(slot);
        set(t.key, !on(t.key));
        render(h.inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
