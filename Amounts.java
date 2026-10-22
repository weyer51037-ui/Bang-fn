package dev.vibe.plus;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns 1.5m / 2b / 500k / 3s into plain numbers. */
public final class Amounts {
    private static final Pattern P = Pattern.compile("^(\\d+(?:[.,]\\d+)?|\\.\\d+)([a-zA-Z]{1,2})$");
    private final Map<String, BigDecimal> suffix = new HashMap<>();

    public Amounts(ConfigurationSection s) {
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try { suffix.put(k.toLowerCase(), new BigDecimal(s.getString(k, "0"))); }
            catch (NumberFormatException ignored) { }
        }
    }

    /** "1.5m" -> "1500000"; null when the text is not an abbreviated amount. */
    public String expand(String arg) {
        Matcher m = P.matcher(arg);
        if (!m.matches()) return null;
        BigDecimal mul = suffix.get(m.group(2).toLowerCase());
        if (mul == null) return null;
        BigDecimal v = new BigDecimal(m.group(1).replace(',', '.')).multiply(mul);
        return v.stripTrailingZeros().toPlainString();
    }
}
