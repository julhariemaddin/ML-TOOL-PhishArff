package com.julharie.phisharff.features;

import java.util.TreeMap;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * NgramHasher: counts the character n-grams of a host name and drops each one into a fixed number of columns.
 *
 *   host "evil-login.xyz" is padded to "^evil-login.xyz$" so the start and the ending are visible, then cut into
 *   every piece of 1 to 6 characters: "^", "e", ..., "^e", "ev", ..., "^evil-", ... Each piece adds 1 to the column
 *   its hash points at (FNV-1a, a fixed formula).
 *
 * Why a hash? There is nothing to train or store: the same host always gives the same columns in PhishArff and in
 * your website, and no label is used, so it cannot leak the answer into cross-validation. Two different pieces can
 * share a column (a collision); more columns means fewer collisions.
 *
 * Only the HOST is used (never the path or scheme), so Domain only mode stays free of that bias.
 */
final class NgramHasher {
    private NgramHasher() {}

    /** Piece sizes counted. Chosen by testing 2-4, 2-5, 1-6 and 2-6 on 286,000 domains; 1-6 was best. */
    static final int MIN = 1;
    static final int MAX = 6;

    /** Name of column i in the ARFF file: ng_0, ng_1, ... */
    static String name(int i) {
        return "ng_" + i;
    }

    /** Non-zero columns only (column -> count), sorted by column. host must already be lower-case and without "www.". */
    static TreeMap<Integer, Integer> counts(String host, int buckets) {
        return counts(host, buckets, MIN, MAX);
    }

    /** Same with explicit piece sizes (used by the self-check to compare against older versions). */
    static TreeMap<Integer, Integer> counts(String host, int buckets, int min, int max) {
        TreeMap<Integer, Integer> out = new TreeMap<>();
        if (buckets <= 0) return out;
        String t = "^" + host + "$";
        for (int n = min; n <= max; n++) {
            for (int i = 0; i + n <= t.length(); i++) {
                int h = 0x811c9dc5;                                  // FNV-1a offset basis
                for (int j = i; j < i + n; j++) { h ^= t.charAt(j); h *= 0x01000193; }
                out.merge(Integer.remainderUnsigned(h, buckets), 1, Integer::sum);
            }
        }
        return out;
    }
}
