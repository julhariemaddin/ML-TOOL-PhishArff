package com.julharie.phisharff.features;

import java.util.Locale;
import java.util.regex.Pattern;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * UrlParts: cuts one URL into the pieces the features need. Pure text handling, no judgement.
 *
 *   http://paypal.com.account-verify.xyz:8080/login?id=1&x=2
 *   -> https=false, host="paypal.com.account-verify.xyz", hasPort=true, path="/login", query="id=1&x=2"
 */
record UrlParts(boolean https, String host, boolean hasPort, String path, String query) {

    /** Matches "scheme://" at the start, e.g. "https://" or "ftp://". The scheme may be empty ("://x.com"). */
    private static final Pattern SCHEME = Pattern.compile("^([a-z][a-z0-9+.\\-]*)?://");

    /** Parses a URL. Lower-cases and trims it first, so callers can pass raw text. */
    static UrlParts parse(String rawUrl) {
        String lower = clean(rawUrl);

        boolean https = lower.startsWith("https://");                      // a. https or not
        String rest = SCHEME.matcher(lower).replaceFirst("");              // b. remove the scheme

        int cut = firstIndexOf(rest, "/?#");                               // c. the authority ends at / ? or #
        String authority = cut < 0 ? rest : rest.substring(0, cut);
        String tail = cut < 0 ? "" : rest.substring(cut);

        int at = authority.lastIndexOf('@');                               // d. drop a fake username (a@evil.com)
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;

        String host = hostPort;                                            // e. separate the port
        boolean hasPort = false;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0) {
            host = hostPort.substring(0, colon);
            String port = hostPort.substring(colon + 1);
            hasPort = !port.isEmpty() && !port.equals("80") && !port.equals("443");   // 80/443 are normal
        }

        String path = tail;                                                // f. separate path and query
        String query = "";
        int q = tail.indexOf('?');
        int hash = tail.indexOf('#');
        if (q >= 0) {
            path = tail.substring(0, q);
            query = tail.substring(q + 1);
            int hashInQuery = query.indexOf('#');
            if (hashInQuery >= 0) query = query.substring(0, hashInQuery);
        } else if (hash >= 0) {
            path = tail.substring(0, hash);
        }
        return new UrlParts(https, host, hasPort, path, query);
    }

    /**
     * Keeps only the host part: drops scheme, "www.", path and query.
     *   https://www.google.com/search?q=1  ->  http://google.com
     */
    static String domainForm(String rawUrl) {
        String s = SCHEME.matcher(clean(rawUrl)).replaceFirst("");
        int cut = firstIndexOf(s, "/?#");
        String authority = cut < 0 ? s : s.substring(0, cut);
        if (authority.startsWith("www.")) authority = authority.substring(4);
        return "http://" + authority;
    }

    /** null becomes "", spaces are removed, everything lower-case (URLs are case-insensitive for us). */
    static String clean(String rawUrl) {
        return rawUrl == null ? "" : rawUrl.trim().toLowerCase(Locale.ROOT);
    }

    private static int firstIndexOf(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }
}
