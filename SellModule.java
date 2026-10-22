package dev.vibe.plus;

import dev.vibe.economy.Crates;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /sell, /sell hand, /sell all that also pays for custom items and crate keys. */
public final class SellModule implements Listener {
    private final VibeEconomyPlus pl;
    private final Bridge br;

    static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public SellModule(VibeEconomyPlus pl, Bridge br) { this.pl = pl; this.br = br; }

    // ---------- pricing ----------
    public double value(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        int amt = it.getAmount();

        String keyId = ((Crates) br.crates()).keyType(it);
        if (keyId != null) return pl.getConfig().getDouble("sell.keys." + keyId, 0) * amt;

        ItemMeta m = it.getItemMeta();
        String name = m != null && m.hasDisplayName() ? ChatColor.stripColor(m.getDisplayName()).toLowerCase() : "";
        if (!name.isEmpty()) {
            for (String b : pl.getConfig().getStringList("sell.blocked-name-contains")) {
                if (name.contains(b.toLowerCase())) return 0;
            }
        }

        double base = br.sellPrice(it.getType());
        boolean custom = m != null && (m.hasDisplayName() || m.hasLore() || m.hasEnchants()
                || (m instanceof EnchantmentStorageMeta es && es.hasStoredEnchants()));
        double unit = base;
        if (custom && pl.getConfig().getBoolean("sell.custom-items.enabled", true)) {
            int levels = 0;
            Map<Enchantment, Integer> ench = m instanceof EnchantmentStorageMeta es ? es.getStoredEnchants() : m.getEnchants();
            for (int lv : ench.values()) levels += lv;
            unit = base * pl.getConfig().getDouble("sell.custom-items.multiplier", 1.0)
                    + levels * pl.getConfig().getDouble("sell.custom-items.per-enchant-level", 150);
        } else if (custom) {
            return 0;
        }
        int maxDur = it.getType().getMaxDurability();
        if (m instanceof Damageable d && maxDur > 0 && unit > 0) {
            double frac = Math.min(1.0, d.getDamage() / (double) maxDur);
            unit *= 1.0 - frac * pl.getConfig().getDouble("sell.custom-items.damage-penalty", 0.5);
        }
        return Math.round(unit * amt * 100.0) / 100.0;
    }

    private void pay(Player p, double total, int stacks) {
        if (total <= 0) { br.msg(p, "&cYou have nothing sellable."); return; }
        br.eco.deposit(p.getUniqueId(), total);
        br.record(p.getUniqueId(), "Sold " + stacks + " stack(s)", total);
        br.msg(p, "&aSold &f" + stacks + " &astack(s) for &f" + br.money(total) + "&a.");
    }

    // ---------- commands ----------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!pl.getConfig().getBoolean("sell.override-command", true)) return;
        String[] parts = e.getMessage().substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!label.equals("sell")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String sub = parts.length > 1 ? parts[1].toLowerCase() : "";
        switch (sub) {
            case "hand" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                double v = value(hand);
                if (v <= 0) { br.msg(p, "&cYou can't sell that item."); return; }
                p.getInventory().setItemInMainHand(null);
                pay(p, v, 1);
            }
            case "all" -> {
                PlayerInventory inv = p.getInventory();
                double total = 0;
                int stacks = 0;
                ItemStack[] c = inv.getStorageContents();
                for (int i = 0; i < c.length; i++) {
                    double v = value(c[i]);
                    if (v > 0) { total += v; stacks++; c[i] = null; }
                }
                inv.setStorageContents(c);
                pay(p, total, stacks);
            }
            default -> open(p);
        }
    }

    // ---------- GUI ----------
    private static final int CONFIRM = 49, CANCEL = 45;

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Bridge.c(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(Bridge.c(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Bridge.c("&5&lSell Items"));
        for (int i = 45; i < 54; i++) h.inv.setItem(i, named(Material.GRAY_STAINED_GLASS_PANE, " "));
        h.inv.setItem(CANCEL, named(Material.BARRIER, "&cCancel", "&7Items are returned to you."));
        refresh(h.inv);
        p.openInventory(h.inv);
    }

    private double total(Inventory inv) {
        double t = 0;
        for (int i = 0; i < 45; i++) t += value(inv.getItem(i));
        return t;
    }

    private void refresh(Inventory inv) {
        inv.setItem(CONFIRM, named(Material.EMERALD, "&a&lSell for " + br.money(total(inv)),
                "&7Custom items and keys included.", "&7Unsellable items are returned."));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == e.getView().getTopInventory() && e.getRawSlot() >= 45) {
            e.setCancelled(true);
            if (e.getRawSlot() == CANCEL) p.closeInventory();
            if (e.getRawSlot() == CONFIRM) confirm(p, h);
            return;
        }
        Bukkit.getScheduler().runTask(pl, () -> refresh(h.inv));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        for (int s : e.getRawSlots()) if (s >= 45 && s < 54) { e.setCancelled(true); return; }
        Bukkit.getScheduler().runTask(pl, () -> refresh(h.inv));
    }

    private void confirm(Player p, Holder h) {
        double total = 0;
        int stacks = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack it = h.inv.getItem(i);
            double v = value(it);
            if (v > 0) { total += v; stacks++; h.inv.setItem(i, null); }
        }
        pay(p, total, stacks);
        p.closeInventory(); // onClose gives back anything we did not buy
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < 45; i++) {
            ItemStack it = h.inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            h.inv.setItem(i, null);
            for (ItemStack left : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
    }
}
