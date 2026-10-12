package com.julharie.phisharff.features;

import java.util.*;
import java.util.regex.Pattern;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * FeatureExtractor
 * ================
 * PURPOSE: turn a URL (text) into 20 numbers that a machine learning model (for example in Weka) can read.
 *
 * This is the SAME code PhishArff used to build your ARFF file. Use it in your website or app so that
 * training and prediction see every URL in exactly the same way.
 *
 * USAGE:
 *   double[] full   = FeatureExtractor.extract(url);             // if you exported with "Full URL" mode
 *   double[] domain = FeatureExtractor.extractDomainLevel(url);  // if you exported with "Domain only" mode
 *   FeatureExtractor.FEATURE_NAMES                                // the column order of the ARFF file
 *
 * GOLDEN RULES:
 *   - Use the same mode as the ARFF file. A mismatch gives wrong answers with no error.
 *   - If you add, remove, reorder or change a feature, rebuild the ARFF file and retrain the model.
 *
 * THE FLOW (follow the "STEP n" comments inside extractMap):
 *   1 clean > 2 split URL > 3 analyse domain > 4 count characters > 5 keyword/brand checks > 6 fill 20 features
 */
public final class FeatureExtractor {

    // =====================================================================================
    // SECTION 1 - THE 20 FEATURE NAMES
    // =====================================================================================

    /** Column order. The model is trained on exactly this order, so NEVER reorder it. */
    public static final List<String> FEATURE_NAMES = List.of(
            "url_length", "hostname_length", "path_length",        // how long is it?
            "num_dots", "num_hyphens", "num_digits", "digit_ratio", // character counts
            "num_special_chars", "num_query_params",
            "has_at", "has_ip", "uses_https", "has_port",           // yes/no tricks (1 = yes, 0 = no)
            "num_subdomains", "host_has_hyphen",                    // domain shape
            "has_suspicious_word", "suspicious_tld", "uses_shortener",
            "brand_mismatch", "double_slash_in_path");

    // =====================================================================================
    // SECTION 2 - LOOKUP LISTS
    // =====================================================================================

    /** Matches "scheme://" at the start, e.g. "https://" or "ftp://". The scheme may be empty ("://x.com"). */
    private static final Pattern SCHEME = Pattern.compile("^([a-z][a-z0-9+.\\-]*)?://");

    /** Matches a real IPv4 address such as 192.168.4.7. Each number must be 0-255, so 999.1.1.1 is NOT an IP. */
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    /** Domain endings made of two parts, so the real domain needs 3 labels (deped.gov.ph). */
    static final Set<String> TWO_PART_SUFFIX = Set.of(
            "com.ph", "gov.ph", "edu.ph", "org.ph", "net.ph", "co.uk", "com.au", "co.jp");

    /** Cheap domain endings often used for throwaway scam sites. */
    static final Set<String> SUSPICIOUS_TLDS = Set.of(
            "xyz", "top", "tk", "ml", "ga", "cf", "gq", "click", "link", "work", "support", "country", "zip");

    /** Link shorteners hide the real destination. */
    static final Set<String> SHORTENERS = Set.of(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly", "rb.gy", "shorturl.at");

    /** Words scammers like to put in links. */
    static final List<String> SUSPICIOUS_WORDS = List.of(
            "login", "signin", "verify", "secure", "update", "account", "confirm", "password",
            "wallet", "claim", "promo", "bonus", "free", "invoice", "suspend");

    /** Brands that scammers pretend to be. */
    static final List<String> BRANDS = List.of(
            "paypal", "gcash", "paymaya", "maya", "bdo", "bpi", "metrobank", "landbank", "unionbank",
            "facebook", "google", "apple", "microsoft", "amazon", "netflix", "lazada", "shopee", "instagram");

    /** Nobody should create a FeatureExtractor object: every method is static. */
    private FeatureExtractor() {}

    // =====================================================================================
    // SECTION 3 - PUBLIC METHODS
    // =====================================================================================

    /**
     * FLOW STEP 2 and 5: the method the model uses.
     * Returns the 20 values in the same order as FEATURE_NAMES.
     */
    public static double[] extract(String rawUrl) {
        Map<String, Double> values = extractMap(rawUrl);              // 1. get every feature by name
        double[] ordered = new double[FEATURE_NAMES.size()];          // 2. empty array with 20 slots
        for (int i = 0; i < ordered.length; i++) {                    // 3. for each slot, in order...
            ordered[i] = values.get(FEATURE_NAMES.get(i));            //    ...look up the value by its name
        }
        return ordered;                                               // 4. same order every time = no mix-ups
    }

    /**
     * FLOW STEP 3 and 4: does the real work. Returns each feature by name.
     * Worked example used in the comments:
     *     http://paypal.com.account-verify.xyz:8080/login?id=1&x=2
     */
    public static Map<String, Double> extractMap(String rawUrl) {

        // ---- STEP 1: clean the input ----------------------------------------------------
        String url = rawUrl == null ? "" : rawUrl.trim();             // null becomes "", spaces removed
        String lower = url.toLowerCase(Locale.ROOT);                  // URLs are case-insensitive for us

        // ---- STEP 2: split the URL into its parts ---------------------------------------
        Parts p = splitUrl(lower);                                    // host, port flag, path, query, https flag

        // ---- STEP 3: analyse the domain -------------------------------------------------
        Domain d = analyseDomain(p.host());                           // IP? subdomains? real domain? ending?

        // ---- STEP 4: count characters in the whole URL ----------------------------------
        int digits = 0;                                               // how many 0-9
        int special = 0;                                              // how many of  ? & = % _ ~ # $ ! * + ;
        for (char c : url.toCharArray()) {                            // look at every character once
            if (Character.isDigit(c)) digits++;
            if ("?&=%_~#$!*+;".indexOf(c) >= 0) special++;
        }

        // ---- STEP 5: keyword and brand checks -------------------------------------------
        // Does the URL contain any scam word (login, verify, ...)?
        boolean suspiciousWord = SUSPICIOUS_WORDS.stream().anyMatch(lower::contains);
        // Brand pretender: brand name is in the host, but is NOT the real domain.
        //   paypal.com.account-verify.xyz -> host has "paypal", real domain is account-verify.xyz -> TRUE
        //   www.paypal.com                -> real domain is paypal.com                            -> FALSE
        boolean brandMismatch = BRANDS.stream()
                .anyMatch(b -> p.host().contains(b) && !d.registrable().contains(b));

        // ---- STEP 6: fill in the 20 features (values for the example in brackets) -------
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("url_length", (double) url.length());                              // total length          [56]
        f.put("hostname_length", (double) p.host().length());                    // length of the host    [29]
        f.put("path_length", (double) p.path().length());                        // length of /login      [6]
        f.put("num_dots", (double) count(url, '.'));                             // dots in the URL       [3]
        f.put("num_hyphens", (double) count(url, '-'));                          // hyphens in the URL    [1]
        f.put("num_digits", (double) digits);                                    // digits                [6]
        f.put("digit_ratio", url.isEmpty() ? 0.0 : (double) digits / url.length()); // digits / length    [0.11]
        f.put("num_special_chars", (double) special);                            // symbols  ? = & =      [4]
        f.put("num_query_params", (double) countParams(p.query()));              // id=1&x=2 -> 2 params  [2]
        f.put("has_at", flag(url.contains("@")));                                // fake username trick   [0]
        f.put("has_ip", flag(d.isIp()));                                         // IP instead of name    [0]
        f.put("uses_https", flag(p.https()));                                    // secure scheme         [0]
        f.put("has_port", flag(p.hasPort()));                                    // unusual port 8080     [1]
        f.put("num_subdomains", (double) d.subdomains());                        // paypal, com           [2]
        f.put("host_has_hyphen", flag(p.host().contains("-")));                  // hyphen in host        [1]
        f.put("has_suspicious_word", flag(suspiciousWord));                      // login/verify/account  [1]
        f.put("suspicious_tld", flag(SUSPICIOUS_TLDS.contains(d.tld())));        // .xyz                  [1]
        f.put("uses_shortener", flag(SHORTENERS.contains(d.registrable())));     // bit.ly etc.           [0]
        f.put("brand_mismatch", flag(brandMismatch));                            // fake brand            [1]
        f.put("double_slash_in_path", flag(p.path().contains("//")));            // // inside the path     [0]
        return f;
    }

    // =====================================================================================
    // SECTION 4 - DOMAIN FORM (used when the training data is biased, see README)
    // =====================================================================================

    /**
     * Keeps only the host part. Drops scheme, "www.", path and query.
     *   https://www.google.com/search?q=1  ->  http://google.com
     * Training and the website must both use this if mode.txt says "domain".
     */
    public static String toDomainForm(String rawUrl) {
        String s = rawUrl == null ? "" : rawUrl.trim().toLowerCase(Locale.ROOT); // 1. clean and lowercase
        s = SCHEME.matcher(s).replaceFirst("");                                  // 2. remove "https://"
        int cut = firstIndexOf(s, "/?#");                                        // 3. where does the host end?
        String authority = cut < 0 ? s : s.substring(0, cut);                    // 4. keep only the host part
        if (authority.startsWith("www.")) authority = authority.substring(4);    // 5. drop "www."
        return "http://" + authority;                                            // 6. same neutral shape every time
    }

    /** Features computed from the domain form only. */
    public static double[] extractDomainLevel(String rawUrl) {
        return extract(toDomainForm(rawUrl));
    }


    /** Host name of a URL, lowercase, without port or user info. "https://a.b.com:8080/x" -> "a.b.com". */
    public static String hostOf(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim().toLowerCase(Locale.ROOT);
        return splitUrl(url).host();
    }

    /** The "real" domain of a host name. "paypal.com.account-verify.xyz" -> "account-verify.xyz". */
    public static String registrableDomain(String host) {
        return analyseDomain(host == null ? "" : host.trim().toLowerCase(Locale.ROOT)).registrable();
    }

    // =====================================================================================
    // SECTION 5 - STEP HELPERS (used by extractMap)
    // =====================================================================================

    /** The pieces of a URL we need. A record is a small read-only bundle of values. */
    private record Parts(boolean https, String host, boolean hasPort, String path, String query) {}

    /** What we learn from the host name. */
    private record Domain(boolean isIp, int subdomains, String registrable, String tld) {}

    /**
     * STEP 2 helper: cut the URL into pieces.
     * Example: http://paypal.com.account-verify.xyz:8080/login?id=1&x=2
     */
    private static Parts splitUrl(String lower) {
        // 2a. https or not
        boolean https = lower.startsWith("https://");

        // 2b. remove the scheme:  "paypal.com.account-verify.xyz:8080/login?id=1&x=2"
        String rest = SCHEME.matcher(lower).replaceFirst("");

        // 2c. the authority ends at the first / ? or #
        int cut = firstIndexOf(rest, "/?#");
        String authority = cut < 0 ? rest : rest.substring(0, cut);   // "paypal.com.account-verify.xyz:8080"
        String tail = cut < 0 ? "" : rest.substring(cut);             // "/login?id=1&x=2"

        // 2d. drop a fake username. In "http://paypal.com@evil.com" the real host is what follows the @
        int at = authority.lastIndexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;

        // 2e. separate the port from the host
        String host = hostPort;                                        // default: no port
        boolean hasPort = false;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0) {
            host = hostPort.substring(0, colon);                       // "paypal.com.account-verify.xyz"
            String port = hostPort.substring(colon + 1);               // "8080"
            hasPort = !port.isEmpty() && !port.equals("80") && !port.equals("443"); // 80/443 are normal
        }

        // 2f. separate the path from the query
        String path = tail;                                            // default: everything is path
        String query = "";
        int q = tail.indexOf('?');
        int hash = tail.indexOf('#');
        if (q >= 0) {                                                  // there is a query
            path = tail.substring(0, q);                               // "/login"
            query = tail.substring(q + 1);                             // "id=1&x=2"
            int hashInQuery = query.indexOf('#');                      // remove a #fragment from the query
            if (hashInQuery >= 0) query = query.substring(0, hashInQuery);
        } else if (hash >= 0) {                                        // no query, but a #fragment
            path = tail.substring(0, hash);
        }
        return new Parts(https, host, hasPort, path, query);
    }

    /**
     * STEP 3 helper: learn about the host name.
     * Example host: paypal.com.account-verify.xyz
     */
    private static Domain analyseDomain(String host) {
        // 3a. is the host an IP address?
        boolean isIp = IPV4.matcher(host).matches();

        // 3b. split at dots:  [paypal, com, account-verify, xyz]
        String[] labels = host.isEmpty() ? new String[0] : host.split("\\.");

        // 3c. how many labels make the REAL domain? Normally 2 (example.com), 3 for gov.ph / co.uk
        int realLen = 2;
        if (labels.length >= 3 && TWO_PART_SUFFIX.contains(labels[labels.length - 2] + "." + labels[labels.length - 1])) {
            realLen = 3;
        }

        // 3d. labels in front of the real domain are subdomains (phishers stack many of them)
        int subdomains = isIp ? 0 : Math.max(0, labels.length - realLen);   // example: 4 - 2 = 2

        // 3e. the real domain = the last realLen labels:  "account-verify.xyz"
        String registrable = (isIp || labels.length <= realLen)
                ? host
                : String.join(".", Arrays.copyOfRange(labels, labels.length - realLen, labels.length));

        // 3f. the ending (top-level domain): "xyz"
        String tld = labels.length > 0 ? labels[labels.length - 1] : "";
        return new Domain(isIp, subdomains, registrable, tld);
    }

    // =====================================================================================
    // SECTION 6 - TINY UTILITIES
    // =====================================================================================

    /** true -> 1.0, false -> 0.0 (the model only understands numbers). */
    private static double flag(boolean b) {
        return b ? 1.0 : 0.0;
    }

    /** Number of real parameters in a query string. Empty pieces ("a=1&&b=2", "&") are not counted. */
    private static int countParams(String query) {
        int n = 0;
        for (String piece : query.split("&")) {      // "id=1&x=2" -> ["id=1", "x=2"]
            if (!piece.isEmpty()) n++;               // skip empty pieces
        }
        return n;
    }

    /** How many times does character c appear in s? */
    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    /** Position of the first character of s that is in `chars`, or -1 if none. */
    private static int firstIndexOf(String s, String chars) {
        for (int i = 0; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }
}
