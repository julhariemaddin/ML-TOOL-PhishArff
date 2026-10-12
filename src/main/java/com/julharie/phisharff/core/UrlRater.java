package com.julharie.phisharff.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides whether a value is a URL and rates how well-formed it is (0-100).
 * 80+ = Good, 50-79 = Odd (usable but unusual), below 50 = Invalid (not a URL).
 */
public final class UrlRater {
    private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*)://");
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    private static final Pattern LABEL = Pattern.compile("^[a-z0-9-]+$");
    private static final int MAX_LENGTH = 2048;

    private UrlRater() {}

    public static Rating rate(String raw) {
        if (raw == null || raw.isBlank()) return Rating.of(0, "Empty value");
        String s = raw.trim();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i)) || Character.isISOControl(s.charAt(i))) {
                return Rating.of(5, "Contains spaces or control characters, not a single URL");
            }
        }
        int score = 100;
        List<String> notes = new ArrayList<>();
        if (s.length() > MAX_LENGTH) { score -= 30; notes.add("longer than " + MAX_LENGTH + " characters"); }

        // 1. scheme
        String rest = s;
        Matcher m = SCHEME.matcher(s);
        if (m.find()) {
            String scheme = m.group(1).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") && !scheme.equals("ftp")) {
                return Rating.of(10, "Scheme '" + scheme + "' is not http, https or ftp");
            }
            rest = s.substring(m.end());
        } else {
            score -= 10;
            notes.add("no scheme (http:// assumed)");
        }

        // 2. authority = everything before the first / ? #
        int cut = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            if ("/?#".indexOf(rest.charAt(i)) >= 0) { cut = i; break; }
        }
        String authority = rest.substring(0, cut);
        int at = authority.lastIndexOf('@');
        if (at >= 0) { score -= 10; notes.add("contains @ (user info)"); authority = authority.substring(at + 1); }

        // 3. port
        String host = authority;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0 && !authority.endsWith("]")) {
            String port = authority.substring(colon + 1);
            host = authority.substring(0, colon);
            if (!port.matches("\\d{1,5}") || Integer.parseInt(port) > 65535) return Rating.of(15, "Invalid port '" + port + "'");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return Rating.of(0, "No host name");

        // 4. host
        if (host.startsWith("[")) {
            score -= 10; notes.add("IPv6 address");
        } else if (!IPV4.matcher(host).matches()) {
            String[] labels = host.split("\\.", -1);
            if (labels.length < 2) return Rating.of(15, "Host has no dot, expected something like example.com");
            for (String label : labels) {
                if (label.isEmpty()) return Rating.of(10, "Host has an empty part (two dots in a row)");
                if (label.length() > 63) { score -= 30; notes.add("host part longer than 63 characters"); }
                if (!LABEL.matcher(label).matches()) { score -= 30; notes.add("unusual characters in host"); break; }
                if (label.startsWith("-") || label.endsWith("-")) { score -= 25; notes.add("host part starts or ends with a hyphen"); break; }
            }
            String tld = labels[labels.length - 1];
            if (tld.matches("\\d+")) return Rating.of(15, "Host looks like a broken IP address");
            if (!tld.startsWith("xn--") && (!tld.matches("[a-z]+") || tld.length() < 2 || tld.length() > 24)) {
                score -= 40; notes.add("unusual domain ending '." + tld + "'");
            }
        }
        return Rating.of(score, notes.isEmpty() ? "Valid URL" : String.join("; ", notes));
    }
}
