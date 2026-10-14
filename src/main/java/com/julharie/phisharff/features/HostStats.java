package com.julharie.phisharff.features;

import java.util.HashMap;
import java.util.Map;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * HostStats: small measurements of the host name text ("how random does it look?"). Used by the six host features.
 * Every method takes the host WITHOUT a leading "www." (see stripWww) so www.site.com and site.com agree.
 */
final class HostStats {
    private HostStats() {}

    /** Removes one leading "www.". */
    static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /** Shannon entropy in bits of the characters. Random-looking names (x8k2q9fj.top) score higher than words. */
    static double entropy(String s) {
        if (s.isEmpty()) return 0.0;
        Map<Character, Integer> counts = new HashMap<>();
        for (int i = 0; i < s.length(); i++) counts.merge(s.charAt(i), 1, Integer::sum);
        double n = s.length(), h = 0.0;
        for (int c : counts.values()) {
            double pr = c / n;
            h -= pr * (Math.log(pr) / Math.log(2));
        }
        return h;
    }

    /** Length of the longest run of digits, e.g. "abc12345.com" -> 5. */
    static int maxDigitRun(String s) {
        int best = 0, run = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= '0' && s.charAt(i) <= '9') { run++; best = Math.max(best, run); } else run = 0;
        }
        return best;
    }

    /** Share of the characters that are a, e, i, o or u. Pronounceable names have a "normal" share. */
    static double vowelRatio(String s) {
        if (s.isEmpty()) return 0.0;
        int v = 0;
        for (int i = 0; i < s.length(); i++) if ("aeiou".indexOf(s.charAt(i)) >= 0) v++;
        return (double) v / s.length();
    }

    /** Length of the longest dot-separated part. */
    static int longestLabel(String host) {
        int best = 0;
        for (String label : host.split("\\.")) best = Math.max(best, label.length());
        return best;
    }

    /** true when the host IS, or is a sub-domain of, one of Lexicon.FREE_HOSTS. */
    static boolean onFreeHost(String host) {
        for (String h : Lexicon.FREE_HOSTS) if (host.equals(h) || host.endsWith("." + h)) return true;
        return false;
    }
}
