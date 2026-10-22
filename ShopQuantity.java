package dev.vibe.plus;

import dev.vibe.economy.ItemSpec;
import dev.vibe.economy.ShopGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Right-click a stackable item in any shop category to open a quantity menu.
 * Starts at 1, buttons add/remove 1, 8 or 32, capped at one full stack.
 * Left click keeps the normal buy-one behaviour.
 */
public final class ShopQuantity implements Listener {
    private static final String SHOP_HOLDER = "dev.vibe.economy.ShopGui$Holder";
    private final VibeEconomyPlus pl;
    private final Bridge br;

    static final class Qty implements InventoryHolder {
        Inventory inv;
        ItemSpec spec;
        String cat;
        String currency;
        int qty = 1;
        int max;
        @Override public Inventory getInventory() { return inv; }
    }

    public ShopQuantity(VibeEconomyPlus pl, Bridge br) {
        this.pl = pl;
        this.br = br;
        // Take over ShopGui's own click listener; we hand every event back to it except right-clicks we handle.
        HandlerList hl = InventoryClickEvent.getHandlerList();
        for (RegisteredListener rl : hl.getRegisteredListeners()) {
            if (rl.getListener().getClass().getName().equals("dev.vibe.economy.ShopGui")) hl.unregister(rl);
        }
    }

    private ShopGui shop() { return (ShopGui) br.shopGui(); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder top = e.getView().getTopInventory().getHolder();
        if (top instanceof Qty q) { qtyClick(e, q); return; }

        boolean shopHolder = top != null && top.getClass().getName().equals(SHOP_HOLDER);
        if (shopHolder && pl.getConfig().getBoolean("shop.quantity-menu", true)
                && e.getClick() == ClickType.RIGHT
                && e.getClickedInventory() == e.getView().getTopInventory()
                && e.getWhoClicked() instanceof Player p) {
            String cat = (String) Bridge.field(top, "category");
            ItemSpec spec = specAt(top, cat, e.getRawSlot());
            if (spec != null && eligible(spec)) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(pl, () -> open(p, spec, cat));
                return;
            }
        }
        shop().onClick(e); // normal ShopGui behaviour (it ignores inventories that are not its own)
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Qty) e.setCancelled(true);
    }

    private ItemSpec specAt(InventoryHolder holder, String cat, int slot) {
        Object o = ((Map<?, ?>) Bridge.field(holder, "slots")).get(slot);
        if (o instanceof ItemSpec s) return s;
        Object catObj = ((Map<?, ?>) Bridge.field(shop(), "cats")).get(cat);
        if (catObj == null) return null;
        List<?> items = (List<?>) Bridge.field(catObj, "items");
        if (o instanceof Integer i && i >= 0 && i < items.size() && items.get(i) instanceof ItemSpec s) return s;
        if (o instanceof String id) {
            for (Object it : items) if (it instanceof ItemSpec s && id.equals(s.id)) return s;
        }
        return null;
    }

    private boolean eligible(ItemSpec s) {
        return s.givesItem()
                && (s.action == null || s.action.isEmpty())
                && (s.commands == null || s.commands.isEmpty())
                && s.price > 0
                && s.material != null && s.material.getMaxStackSize() > 1;
    }

    private String currencyOf(ItemSpec s, String cat) {
        if (s.currency != null && !s.currency.isEmpty()) return s.currency;
        Object catObj = ((Map<?, ?>) Bridge.field(shop(), "cats")).get(cat);
        Object cur = catObj == null ? null : Bridge.field(catObj, "currency");
        return cur == null ? "money" : cur.toString();
    }

    private void open(Player p, ItemSpec spec, String cat) {
        Qty q = new Qty();
        q.spec = spec;
        q.cat = cat;
        q.currency = currencyOf(spec, cat);
        q.max = spec.material.getMaxStackSize();
        String name = label(spec);
        if (name.length() > 20) name = name.substring(0, 20);
        q.inv = Bukkit.createInventory(q, 27, Bridge.c("&5&lBuy &8| &f" + name));
        render(q);
        p.openInventory(q.inv);
    }

    private String label(ItemSpec s) {
        ItemStack b = s.build();
        ItemMeta m = b.getItemMeta();
        if (m != null && m.hasDisplayName()) return org.bukkit.ChatColor.stripColor(m.getDisplayName());
        return s.material.name().toLowerCase().replace('_', ' ');
    }

    private double unit(ItemSpec s) { return s.price / Math.max(1, s.amount); }

    private boolean shards(Qty q) { return q.currency.equalsIgnoreCase("shards"); }

    private String priceText(Qty q, int n) {
        double total = unit(q.spec) * n;
        return shards(q) ? (long) Math.ceil(total) + " Vanta Shards" : br.money(Math.round(total * 100.0) / 100.0);
    }

    private ItemStack button(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Bridge.c(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(Bridge.c(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private void render(Qty q) {
        ItemStack prev = q.spec.build();
        prev.setAmount(Math.max(1, Math.min(q.qty, q.max)));
        ItemMeta m = prev.getItemMeta();
        List<String> lore = m.hasLore() && m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Bridge.c("&7Quantity: &f" + q.qty + "&7/" + q.max));
        lore.add(Bridge.c("&7Each: &f" + priceText(q, 1)));
        lore.add(Bridge.c("&7Total: &a" + priceText(q, q.qty)));
        m.setLore(lore);
        prev.setItemMeta(m);

        Inventory inv = q.inv;
        inv.clear();
        ItemStack fill = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);
        inv.setItem(10, button(Material.RED_STAINED_GLASS_PANE, "&c-32"));
        inv.setItem(11, button(Material.RED_STAINED_GLASS_PANE, "&c-8"));
        inv.setItem(12, button(Material.RED_STAINED_GLASS_PANE, "&c-1"));
        inv.setItem(13, prev);
        inv.setItem(14, button(Material.LIME_STAINED_GLASS_PANE, "&a+1"));
        inv.setItem(15, button(Material.LIME_STAINED_GLASS_PANE, "&a+8"));
        inv.setItem(16, button(Material.LIME_STAINED_GLASS_PANE, "&a+32", "&7Capped at one stack (" + q.max + ")"));
        inv.setItem(18, button(Material.ARROW, "&7Back"));
        inv.setItem(20, button(Material.RED_DYE, "&cReset to 1"));
        inv.setItem(22, button(Material.EMERALD, "&a&lConfirm purchase", "&7Buy &f" + q.qty + " &7for &a" + priceText(q, q.qty)));
        inv.setItem(24, button(Material.CHEST, "&eFull stack", "&7Set to " + q.max));
    }

    private void qtyClick(InventoryClickEvent e, Qty q) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        switch (e.getRawSlot()) {
            case 10 -> q.qty -= 32;
            case 11 -> q.qty -= 8;
            case 12 -> q.qty -= 1;
            case 14 -> q.qty += 1;
            case 15 -> q.qty += 8;
            case 16 -> q.qty += 32;
            case 20 -> q.qty = 1;
            case 24 -> q.qty = q.max;
            case 18 -> { shop().openCategory(p, q.cat); return; }
            case 22 -> { buy(p, q); return; }
            default -> { return; }
        }
        q.qty = Math.max(1, Math.min(q.qty, q.max));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        render(q);
    }

    private void buy(Player p, Qty q) {
        double total = Math.round(unit(q.spec) * q.qty * 100.0) / 100.0;
        String why = "Shop: " + label(q.spec) + " x" + q.qty;
        if (shards(q)) {
            long cost = (long) Math.ceil(unit(q.spec) * q.qty);
            if (!br.eco.takeShards(p.getUniqueId(), cost)) { br.msg(p, "&cYou don't have enough Vanta Shards."); return; }
            br.recordShards(p.getUniqueId(), why, -cost);
        } else {
            if (!br.eco.withdraw(p.getUniqueId(), total)) { br.msg(p, "&cYou can't afford that."); return; }
            br.record(p.getUniqueId(), why, -total);
        }
        ItemStack it = q.spec.build();
        it.setAmount(q.qty);
        br.giveItem(p, it);
        br.msg(p, "&aBought &f" + q.qty + "x " + label(q.spec) + " &afor &f" + priceText(q, q.qty) + "&a.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        render(q);
    }
}
