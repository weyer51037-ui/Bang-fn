package dev.vibe.plus;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rewrites "/pay Bob 1.5m" into "/pay Bob 1500000" before VibeEconomy sees it. */
public final class AbbrevListener implements Listener {
    private final Amounts amounts;
    private final Set<String> commands = new HashSet<>();

    public AbbrevListener(Amounts amounts, List<String> cmds) {
        this.amounts = amounts;
        for (String c : cmds) commands.add(c.toLowerCase());
    }

    private String rewrite(String line, boolean slash) {
        String body = slash ? line.substring(1) : line;
        String[] parts = body.split(" ", -1);
        String label = parts[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!commands.contains(label)) return null;
        boolean changed = false;
        for (int i = 1; i < parts.length; i++) {
            if (Bukkit.getPlayerExact(parts[i]) != null) continue; // a player literally named "5m"
            String x = amounts.expand(parts[i]);
            if (x != null) { parts[i] = x; changed = true; }
        }
        return changed ? (slash ? "/" : "") + String.join(" ", parts) : null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayer(PlayerCommandPreprocessEvent e) {
        String r = rewrite(e.getMessage(), true);
        if (r != null) e.setMessage(r);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsole(ServerCommandEvent e) {
        String r = rewrite(e.getCommand(), false);
        if (r != null) e.setCommand(r);
    }
}
