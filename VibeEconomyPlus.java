package dev.vibe.plus;

import dev.vibe.economy.VibeEconomy;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class VibeEconomyPlus extends JavaPlugin {
    private Bridge bridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Plugin p = getServer().getPluginManager().getPlugin("VibeEconomy");
        if (!(p instanceof VibeEconomy eco)) {
            getLogger().severe("VibeEconomy not found - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        bridge = new Bridge(eco);

        Amounts amounts = new Amounts(getConfig().getConfigurationSection("amounts.suffixes"));
        getServer().getPluginManager().registerEvents(
                new AbbrevListener(amounts, getConfig().getStringList("amounts.commands")), this);

        getServer().getPluginManager().registerEvents(new ShopQuantity(this, bridge), this);
        getServer().getPluginManager().registerEvents(new SellModule(this, bridge), this);
        getServer().getPluginManager().registerEvents(new RtpModule(this, bridge), this);

        Toggles toggles = new Toggles(this);
        getServer().getPluginManager().registerEvents(toggles, this);
        var cmd = getCommand("vtoggle");
        if (cmd != null) { cmd.setExecutor(toggles); cmd.setTabCompleter(toggles); }

        OpenCommands oc = new OpenCommands(this, bridge);
        for (String name : new String[]{"open", "set"}) {
            var c = getCommand(name);
            if (c != null) { c.setExecutor(oc); c.setTabCompleter(oc); }
        }

        var media = getCommand("media");
        if (media != null) media.setExecutor(new MediaCommand(this));

        getLogger().info("VibeEconomyPlus enabled.");
    }
}
