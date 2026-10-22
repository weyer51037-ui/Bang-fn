package dev.vibe.plus;

import dev.vibe.economy.Crates;
import dev.vibe.economy.Travel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /open crates           - browse every crate from anywhere
 * /open crates <crate>   - open a crate from anywhere using a key from your inventory
 * /set opencrates [on|off] - mods switch /open crates on or off for everyone
 */
public final class OpenCommands implements CommandExecutor, TabCompleter {
    private final VibeEconomyPlus pl;
    private final Bridge br;

    public OpenCommands(VibeEconomyPlus pl, Bridge br) { this.pl = pl; this.br = br; }

    private boolean enabled() { return pl.getConfig().getBoolean("set.opencrates", false); }

    private Map<?, ?> crateMap() { return (Map<?, ?>) Bridge.field(br.crates(), "crates"); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("set")) return set(s, a);
        return open(s, a);
    }

    // ---------- /set ----------
    private boolean set(CommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("opencrates")) {
            s.sendMessage(Bridge.c("&cUsage: /set opencrates [on|off]"));
            return true;
        }
        boolean v = a.length > 1 ? (a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("true")) : !enabled();
        pl.getConfig().set("set.opencrates", v);
        pl.saveConfig();
        s.sendMessage(Bridge.c("&7/open crates for everyone: " + (v ? "&aON" : "&cOFF")));
        pl.getServer().broadcastMessage(Bridge.c(v
                ? "&5[Crates] &7Staff enabled &f/open crates&7 - open crates from anywhere!"
                : "&5[Crates] &7/open crates is now disabled."));
        return true;
    }

    // ---------- /open ----------
    private boolean open(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (a.length == 0 || !a[0].equalsIgnoreCase("crates")) { br.msg(p, "&cUsage: /open crates [crate]"); return true; }
        if (!enabled() && !p.hasPermission("vibeeconomy.mod")) {
            br.msg(p, "&c/open crates is currently disabled by staff.");
            return true;
        }
        Crates crates = (Crates) br.crates();
        if (a.length == 1) { crates.openList(p); return true; }

        if (((Travel) br.travel()).inCombat(p)) { br.msg(p, "&cYou can't do that while in combat."); return true; }
        Object crate = crateMap().get(a[1].toLowerCase());
        if (crate == null) { br.msg(p, "&cUnknown crate. Try: &f" + names()); return true; }
        String keyId = (String) Bridge.field(crate, "key");

        PlayerInventory inv = p.getInventory();
        int slot = -1;
        ItemStack[] c = inv.getStorageContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] != null && keyId.equals(crates.keyType(c[i]))) { slot = i; break; }
        }
        if (slot < 0) { br.msg(p, "&cYou need a " + crates.keyName(keyId) + " &cfor that crate."); return true; }
        if (inv.firstEmpty() == -1 && c[slot].getAmount() > 1) { br.msg(p, "&cMake some inventory room first."); return true; }

        ItemStack key = c[slot];
        int before = key.getAmount();
        Bridge.call(crates, "open", p, crate, key);

        // If the crate code did not consume the key itself, take one.
        ItemStack now = inv.getItem(slot);
        boolean consumed = now == null || now.getType().isAir() || now.getAmount() < before
                || !keyId.equals(crates.keyType(now));
        if (!consumed) {
            if (before <= 1) inv.setItem(slot, null); else now.setAmount(before - 1);
        }
        return true;
    }

    private String names() {
        List<String> l = new ArrayList<>();
        for (Object k : crateMap().keySet()) l.add(String.valueOf(k));
        return String.join(", ", l);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("set")) {
            if (a.length == 1 && "opencrates".startsWith(a[0].toLowerCase())) out.add("opencrates");
            if (a.length == 2) { out.add("on"); out.add("off"); }
        } else {
            if (a.length == 1) out.add("crates");
            if (a.length == 2) for (Object k : crateMap().keySet()) if (String.valueOf(k).startsWith(a[1].toLowerCase())) out.add(String.valueOf(k));
        }
        return out;
    }
}
