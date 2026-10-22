package dev.vibe.plus;

import dev.vibe.economy.Travel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Dimensional /rtp: overworld, nether and end, each with its own radius and safe-spot rules. */
public final class RtpModule implements Listener {
    private static final Set<Material> UNSAFE = EnumSet.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.WITHER_ROSE, Material.LAVA, Material.WATER, Material.BEDROCK);

    private record Dim(String key, String display, Material icon, World.Environment env, String worldName,
                       int min, int max, int minY, int maxY) {
        World world() {
            if (!worldName.isEmpty()) return Bukkit.getWorld(worldName);
            for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == env) return w;
            return null;
        }
    }

    static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, String> slots = new HashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private final VibeEconomyPlus pl;
    private final Bridge br;
    private final Map<String, Dim> dims = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();
    private final Random rnd = new Random();

    public RtpModule(VibeEconomyPlus pl, Bridge br) {
        this.pl = pl;
        this.br = br;
        load();
    }

    public void load() {
        dims.clear();
        ConfigurationSection root = pl.getConfig().getConfigurationSection("rtp.dimensions");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            World.Environment env;
            Material icon;
            try {
                env = World.Environment.valueOf(s.getString("environment", "NORMAL").toUpperCase());
                icon = Material.valueOf(s.getString("icon", "GRASS_BLOCK").toUpperCase());
            } catch (IllegalArgumentException ex) {
                pl.getLogger().warning("Bad rtp dimension '" + k + "': " + ex.getMessage());
                continue;
            }
            dims.put(k.toLowerCase(), new Dim(k.toLowerCase(), s.getString("display", k), icon, env,
                    s.getString("world", ""), s.getInt("min-radius", 500), s.getInt("max-radius", 5000),
                    s.getInt("min-y", 32), s.getInt("max-y", 120)));
        }
    }

    private String alias(String s) {
        return switch (s) {
            case "world", "normal", "over", "ow" -> "overworld";
            case "hell" -> "nether";
            case "the_end", "theend" -> "end";
            default -> s;
        };
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!pl.getConfig().getBoolean("rtp.enabled", true)) return;
        String[] parts = e.getMessage().substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!label.equals("rtp")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (parts.length > 1) start(p, alias(parts[1].toLowerCase()));
        else if (pl.getConfig().getBoolean("rtp.menu", true)) openMenu(p);
        else start(p, "overworld");
    }

    // ---------- menu ----------
    private void openMenu(Player p) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, 27, Bridge.c("&5&lRandom Teleport"));
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = fill.getItemMeta();
        fm.setDisplayName(" ");
        fill.setItemMeta(fm);
        for (int i = 0; i < 27; i++) m.inv.setItem(i, fill);
        int[] spots = dims.size() == 1 ? new int[]{13} : dims.size() == 2 ? new int[]{12, 14} : new int[]{11, 13, 15};
        int i = 0;
        for (Dim d : dims.values()) {
            if (i >= spots.length) break;
            ItemStack it = new ItemStack(d.icon());
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(Bridge.c(d.display()));
            meta.setLore(List.of(Bridge.c("&7Radius: &f" + d.min() + "&7 - &f" + d.max()), Bridge.c("&eClick to teleport")));
            it.setItemMeta(meta);
            m.inv.setItem(spots[i], it);
            m.slots.put(spots[i], d.key());
            i++;
        }
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        String key = m.slots.get(e.getRawSlot());
        if (key == null || !(e.getWhoClicked() instanceof Player p)) return;
        p.closeInventory();
        start(p, key);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { searching.remove(e.getPlayer().getUniqueId()); }

    // ---------- teleporting ----------
    private void start(Player p, String key) {
        Dim d = dims.get(key);
        if (d == null) { br.msg(p, "&cUnknown or disabled dimension. Try: &f" + String.join(", ", dims.keySet())); return; }
        World w = d.world();
        if (w == null) { br.msg(p, "&cThat dimension isn't loaded on this server."); return; }
        if (((Travel) br.travel()).inCombat(p)) { br.msg(p, "&cYou can't RTP while in combat."); return; }
        boolean admin = p.hasPermission("vibeeconomy.admin");
        long now = System.currentTimeMillis();
        long cd = pl.getConfig().getLong("rtp.cooldown-seconds", 30) * 1000L;
        Long last = cooldown.get(p.getUniqueId());
        if (!admin && last != null && now - last < cd) {
            br.msg(p, "&cRTP is on cooldown for &f" + ((cd - (now - last)) / 1000 + 1) + "s&c.");
            return;
        }
        if (!searching.add(p.getUniqueId())) return;
        br.msg(p, "&7Searching for a safe spot in the " + d.display() + "&7...");
        attempt(p, d, w, 0);
    }

    private void attempt(Player p, Dim d, World w, int tries) {
        UUID id = p.getUniqueId();
        if (!p.isOnline()) { searching.remove(id); return; }
        if (tries >= pl.getConfig().getInt("rtp.max-attempts", 25)) {
            searching.remove(id);
            br.msg(p, "&cCouldn't find a safe spot. Try again.");
            return;
        }
        double ang = rnd.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(rnd.nextDouble() * ((double) d.max() * d.max() - (double) d.min() * d.min())
                + (double) d.min() * d.min());
        int x = (int) (Math.cos(ang) * r), z = (int) (Math.sin(ang) * r);
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) { attempt(p, d, w, tries + 1); return; }
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask(pl, () -> {
            Location loc = safe(w, d, x, z);
            if (loc == null) { attempt(p, d, w, tries + 1); return; }
            searching.remove(id);
            cooldown.put(id, System.currentTimeMillis());
            ((Travel) br.travel()).teleport(p, loc, "RTP");
        }));
    }

    private Location safe(World w, Dim d, int x, int z) {
        switch (d.env()) {
            case NETHER -> {
                int top = Math.min(d.maxY(), w.getMaxHeight() - 3);
                for (int y = top; y >= Math.max(d.minY(), w.getMinHeight() + 1); y--) {
                    if (ok(w, x, y, z)) return new Location(w, x + 0.5, y + 1, z + 0.5);
                }
                return null;
            }
            default -> {
                int y = w.getHighestBlockYAt(x, z);
                if (y <= w.getMinHeight() + 1) return null; // void column (End gaps)
                return ok(w, x, y, z) ? new Location(w, x + 0.5, y + 1, z + 0.5) : null;
            }
        }
    }

    private boolean ok(World w, int x, int y, int z) {
        Block ground = w.getBlockAt(x, y, z);
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);
        if (!ground.getType().isSolid() || UNSAFE.contains(ground.getType())) return false;
        if (!feet.isPassable() || feet.isLiquid() || UNSAFE.contains(feet.getType())) return false;
        if (!head.isPassable() || head.isLiquid()) return false;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 0; dy <= 1; dy++)
                    if (w.getBlockAt(x + dx, y + dy, z + dz).getType() == Material.LAVA) return false;
        return true;
    }
}
