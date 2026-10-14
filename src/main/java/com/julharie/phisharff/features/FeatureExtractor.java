package com.julharie.phisharff.features;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * FeatureExtractor
 * ================
 * PURPOSE: turn a URL (text) into 26 numbers, plus optionally hashed character n-gram counts of the host, that a
 * machine learning model (for example in Weka) can read.
 *
 * This is the SAME code PhishArff used to build your ARFF file. Use it in your website or app so that training and
 * prediction see every URL in exactly the same way.
 *
 * USAGE (pick the line that matches how you exported):
 *   double[] full   = FeatureExtractor.extract(url);                             // Full URL, no n-grams
 *   double[] domain = FeatureExtractor.extractDomainLevel(url);                  // Domain only, no n-grams
 *   double[] all    = FeatureExtractor.extractAll(url, new FeatureConfig(true, 16384)); // with n-gram columns
 *   FeatureExtractor.FEATURE_NAMES                                               // the first 26 column names
 *
 * GOLDEN RULES:
 *   - Use the same FeatureConfig as the ARFF file. A mismatch gives wrong answers with no error.
 *   - If you add, remove, reorder or change a feature, rebuild the ARFF file and retrain the model.
 *
 * HOW THE CODE IS ORGANISED (all files are in this package and are exported together):
 *   FeatureExtractor  this class: the public entry point, puts the numbers in order
 *   UrlParts          cuts a URL into scheme / host / port / path / query
 *   HostInfo          is the host an IP? how many sub-domains? what is the real domain?
 *   HostStats         measurements of the host text (entropy, digit runs, vowels, ...)
 *   NgramHasher       hashed character n-gram columns of the host
 *   Lexicon           the word lists (suspicious words, brands, free hosts, ...)
 *   FeatureConfig     Domain only on/off + number of n-gram columns
 */
public final class FeatureExtractor {

    /** Column order of the base features. The model is trained on exactly this order, so NEVER reorder it. */
    public static final List<String> FEATURE_NAMES = List.of(
            "url_length", "hostname_length", "path_length",        // how long is it?
            "num_dots", "num_hyphens", "num_digits", "digit_ratio", // character counts
            "num_special_chars", "num_query_params",
            "has_at", "has_ip", "uses_https", "has_port",           // yes/no tricks (1 = yes, 0 = no)
            "num_subdomains", "host_has_hyphen",                    // domain shape
            "has_suspicious_word", "suspicious_tld", "uses_shortener",
            "brand_mismatch", "double_slash_in_path",
            // six host features, appended at the END so the first 20 columns keep their order
            "host_entropy", "max_digit_run", "vowel_ratio", "free_hosting", "longest_label", "tld_length");

    /** Piece sizes of the character n-grams (see NgramHasher). */
    public static final int NGRAM_MIN = NgramHasher.MIN;
    public static final int NGRAM_MAX = NgramHasher.MAX;

    /** Nobody should create a FeatureExtractor object: every method is static. */
    private FeatureExtractor() {}

    // =====================================================================================
    // PUBLIC API
    // =====================================================================================

    /** The 26 base values for a URL, in the order of FEATURE_NAMES (Full URL mode). */
    public static double[] extract(String rawUrl) {
        Map<String, Double> values = extractMap(rawUrl);
        double[] ordered = new double[FEATURE_NAMES.size()];
        for (int i = 0; i < ordered.length; i++) ordered[i] = values.get(FEATURE_NAMES.get(i));
        return ordered;
    }

    /** The 26 base values computed from the domain form only (Domain only mode). */
    public static double[] extractDomainLevel(String rawUrl) {
        return extract(toDomainForm(rawUrl));
    }

    /** One URL as a full row: base values, then config.ngramBuckets() n-gram columns (zeros where a column is empty). */
    public static double[] extractAll(String rawUrl, FeatureConfig config) {
        double[] base = config.domainMode() ? extractDomainLevel(rawUrl) : extract(rawUrl);
        double[] all = Arrays.copyOf(base, base.length + config.ngramBuckets());
        for (Map.Entry<Integer, Integer> e : ngramCounts(rawUrl, config.ngramBuckets()).entrySet()) {
            all[base.length + e.getKey()] = e.getValue();
        }
        return all;
    }

    /** Convenience form of extractAll(url, new FeatureConfig(domainMode, buckets)). */
    public static double[] extractAll(String rawUrl, boolean domainMode, int buckets) {
        return extractAll(rawUrl, new FeatureConfig(domainMode, buckets));
    }

    /** Non-zero n-gram columns of the URL's host (column -> count). Same result in Full URL and Domain only mode. */
    public static TreeMap<Integer, Integer> ngramCounts(String rawUrl, int buckets) {
        return NgramHasher.counts(HostStats.stripWww(hostOf(rawUrl)), buckets);
    }

    /** Name of n-gram column i in the ARFF file: ng_0, ng_1, ... */
    public static String ngramName(int i) {
        return NgramHasher.name(i);
    }

    /**
     * Keeps only the host part. Drops scheme, "www.", path and query.
     *   https://www.google.com/search?q=1  ->  http://google.com
     * Training and the website must both use this if the ARFF file was built in Domain only mode.
     */
    public static String toDomainForm(String rawUrl) {
        return UrlParts.domainForm(rawUrl);
    }

    /** Host name of a URL, lowercase, without port or user info. "https://a.b.com:8080/x" -> "a.b.com". */
    public static String hostOf(String rawUrl) {
        return UrlParts.parse(rawUrl).host();
    }

    /** The "real" domain of a host name. "paypal.com.account-verify.xyz" -> "account-verify.xyz". */
    public static String registrableDomain(String host) {
        return HostInfo.of(host == null ? "" : host.trim().toLowerCase(java.util.Locale.ROOT)).registrable();
    }

    /**
     * Every base feature by name. Worked example used in the comments:
     *     http://paypal.com.account-verify.xyz:8080/login?id=1&x=2
     */
    public static Map<String, Double> extractMap(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();                 // 1. clean the input
        String lower = UrlParts.clean(url);
        UrlParts p = UrlParts.parse(lower);                               // 2. split the URL into parts
        HostInfo d = HostInfo.of(p.host());                               // 3. analyse the host

        int digits = 0;                                                   // 4. count characters in the whole URL
        int special = 0;                                                  //    ? & = % _ ~ # $ ! * + ;
        for (char c : url.toCharArray()) {
            if (Character.isDigit(c)) digits++;
            if ("?&=%_~#$!*+;".indexOf(c) >= 0) special++;
        }

        // 5. keyword and brand checks.
        boolean suspiciousWord = Lexicon.SUSPICIOUS_WORDS.stream().anyMatch(lower::contains);
        // Brand pretender: the brand is in the host but is NOT the real domain.
        //   paypal.com.account-verify.xyz -> TRUE        www.paypal.com -> FALSE
        boolean brandMismatch = Lexicon.BRANDS.stream()
                .anyMatch(b -> p.host().contains(b) && !d.registrable().contains(b));

        String core = HostStats.stripWww(p.host());                       // 6. host text without a leading "www."

        Map<String, Double> f = new LinkedHashMap<>();                    // 7. fill in the features (example values in [])
        f.put("url_length", (double) url.length());                              // [56]
        f.put("hostname_length", (double) p.host().length());                    // [29]
        f.put("path_length", (double) p.path().length());                        // [6]
        f.put("num_dots", (double) count(url, '.'));                             // [3]
        f.put("num_hyphens", (double) count(url, '-'));                          // [1]
        f.put("num_digits", (double) digits);                                    // [6]
        f.put("digit_ratio", url.isEmpty() ? 0.0 : (double) digits / url.length()); // [0.11]
        f.put("num_special_chars", (double) special);                            // [4]
        f.put("num_query_params", (double) countParams(p.query()));              // [2]
        f.put("has_at", flag(url.contains("@")));                                // [0]
        f.put("has_ip", flag(d.isIp()));                                         // [0]
        f.put("uses_https", flag(p.https()));                                    // [0]
        f.put("has_port", flag(p.hasPort()));                                    // [1]
        f.put("num_subdomains", (double) d.subdomains());                        // [2]
        f.put("host_has_hyphen", flag(p.host().contains("-")));                  // [1]
        f.put("has_suspicious_word", flag(suspiciousWord));                      // [1]
        f.put("suspicious_tld", flag(Lexicon.SUSPICIOUS_TLDS.contains(d.tld()))); // [1]
        f.put("uses_shortener", flag(Lexicon.SHORTENERS.contains(d.registrable()))); // [0]
        f.put("brand_mismatch", flag(brandMismatch));                            // [1]
        f.put("double_slash_in_path", flag(p.path().contains("//")));            // [0]
        f.put("host_entropy", HostStats.entropy(core));                          // how random it looks
        f.put("max_digit_run", (double) HostStats.maxDigitRun(core));            // longest 0-9 run
        f.put("vowel_ratio", HostStats.vowelRatio(core));                        // vowels / length
        f.put("free_hosting", flag(HostStats.onFreeHost(core)));                 // free hosting platform
        f.put("longest_label", (double) HostStats.longestLabel(core));           // longest dotted part
        f.put("tld_length", (double) d.tld().length());                          // "xyz" -> 3
        return f;
    }

    // =====================================================================================
    // TINY UTILITIES
    // =====================================================================================

    /** true -> 1.0, false -> 0.0 (the model only understands numbers). */
    private static double flag(boolean b) {
        return b ? 1.0 : 0.0;
    }

    /** Number of real parameters in a query string. Empty pieces ("a=1&&b=2", "&") are not counted. */
    private static int countParams(String query) {
        int n = 0;
        for (String piece : query.split("&")) {
            if (!piece.isEmpty()) n++;
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
}
