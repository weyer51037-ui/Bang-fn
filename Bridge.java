package dev.vibe.plus;

import dev.vibe.economy.VibeEconomy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/** Reaches the package-private parts of VibeEconomy through reflection. */
public final class Bridge {
    public final VibeEconomy eco;

    public Bridge(VibeEconomy eco) { this.eco = eco; }

    public static String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        return Map.of(int.class, Integer.class, long.class, Long.class, double.class, Double.class,
                boolean.class, Boolean.class, float.class, Float.class).getOrDefault(c, c);
    }

    public static Object call(Object target, String name, Object... args) {
        try {
            for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    boolean ok = true;
                    for (int i = 0; i < pt.length; i++) {
                        if (args[i] != null && !box(pt[i]).isInstance(args[i])) { ok = false; break; }
                    }
                    if (!ok) continue;
                    m.setAccessible(true);
                    return m.invoke(target, args);
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("VibeEconomy call failed: " + name, ex);
        }
        throw new IllegalStateException("VibeEconomy has no method " + name);
    }

    public static Object field(Object target, String name) {
        try {
            for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
                try {
                    Field f = k.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) { }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        return null;
    }

    // package-private accessors on the main class
    public Object shopGui() { return call(eco, "shopGui"); }
    public Object crates() { return call(eco, "crates"); }
    public Object travel() { return call(eco, "travel"); }
    public double sellPrice(Material m) { return (Double) call(eco, "sellPrice", m); }
    public void record(UUID id, String why, double amt) { call(eco, "record", id, why, amt); }
    public void recordShards(UUID id, String why, long amt) { call(eco, "recordShards", id, why, amt); }
    public void giveItem(Player p, ItemStack it) { call(eco, "giveItem", p, it); }
    public void msg(CommandSender s, String text) { call(eco, "msg", s, text); }
    public String money(double v) { return (String) call(eco, "money", v); }
}
