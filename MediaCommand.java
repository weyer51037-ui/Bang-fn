package dev.vibe.plus;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** /media - shows the requirements for the MEDIA rank (all text comes from config.yml). */
public final class MediaCommand implements CommandExecutor {
    private final VibeEconomyPlus pl;

    public MediaCommand(VibeEconomyPlus pl) { this.pl = pl; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        var cfg = pl.getConfig();
        s.sendMessage(Bridge.c(cfg.getString("media.title", "&c&lMEDIA &7Rank")));
        s.sendMessage(Bridge.c(cfg.getString("media.intro", "&7Meet &fany one &7of these to apply:")));
        for (String r : cfg.getStringList("media.rules")) s.sendMessage(Bridge.c("&8 - " + r));
        String apply = cfg.getString("media.apply", "");
        if (!apply.isEmpty()) s.sendMessage(Bridge.c(apply));
        return true;
    }
}
