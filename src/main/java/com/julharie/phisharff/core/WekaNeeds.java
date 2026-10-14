package com.julharie.phisharff.core;

import java.util.ArrayList;
import java.util.List;

/*
 * PhishArff - Weka ARFF preparation tool
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * WekaNeeds: how much memory Weka needs for an export, which Weka algorithms are safe for it, and the warnings to
 * show when this computer or Weka's Java heap is too small.
 *
 * The numbers are estimates, on purpose a little generous:
 *   dense  ARFF (no n-grams)  one double per column per row
 *   sparse ARFF (n-grams on)  only the non-zero columns are stored (12 bytes each: value + index)
 *   Weka keeps a working copy while it trains and filters, so the required heap is 2 x the data size.
 * An algorithm or filter that is not sparse-aware expands every row to ALL columns. That is the dense number, and
 * it is why a 65,536-column file can fit in 1 GB of sparse data and still need 150 GB in the wrong algorithm.
 */
public final class WekaNeeds {
    private WekaNeeds() {}

    public static final int BASE_COLUMNS = 26;
    /** Weka copies the data while training and filtering. */
    static final double WORKING_COPY = 2.0;

    public static final String SPARSE_SAFE = "functions.SGD (-F 1), functions.SMO, bayes.NaiveBayesMultinomial";
    public static final String NOT_SAFE = "trees.RandomForest, trees.J48, functions.Logistic, filters that centre the data (Standardize)";

    public record Estimate(long rows, int buckets, long sparseBytes, long denseBytes, long requiredBytes) {
        public boolean ngrams() { return buckets > 0; }

        /** The Weka algorithms that fit this export. */
        public String algorithms() {
            return ngrams() ? SPARSE_SAFE : "any algorithm (small dense file)";
        }

        public String avoid() {
            return ngrams() ? NOT_SAFE : "";
        }

        /** Smallest Weka heap (MB, rounded up to 512) that holds the required bytes. */
        public long requiredHeapMb() {
            long mb = (requiredBytes + Environment.MB - 1) / Environment.MB;
            return ((mb + 511) / 512) * 512;
        }
    }

    /** Typical number of non-zero columns in one sparse row: 26 base features plus the distinct hashed pieces of a host. */
    static int nonZeroPerRow(int buckets) {
        return 20 + Math.min(buckets, 110);
    }

    public static Estimate estimate(long rows, int buckets) {
        long r = Math.max(0, rows);
        long sparse = r * (72L + 12L * nonZeroPerRow(buckets));
        long dense = r * (40L + 8L * (BASE_COLUMNS + (long) buckets + 1));
        long needed = (long) ((buckets > 0 ? sparse : dense) * WORKING_COPY);
        return new Estimate(r, buckets, sparse, dense, needed);
    }

    /** What this tool itself needs to read a CSV of this size and write the ARFF (rough). */
    public static long appRequiredBytes(long fileBytes) {
        return fileBytes * 4 + 256L * Environment.MB;
    }

    /**
     * Warnings, most serious first. Empty list = nothing to worry about.
     * wekaHeapMb: Weka's -Xmx in MB, or -1 when unknown.
     */
    public static List<String> warnings(Estimate e, long totalRam, long freeRam, long wekaHeapMb, String wekaHeapSource,
                                        long appMaxHeap, String appHeapSource, long fileBytes) {
        List<String> w = new ArrayList<>();
        long need = e.requiredBytes();
        String needText = Environment.size(need);

        if (e.rows() > 0) {
            if (totalRam > 0 && need > totalRam) {
                w.add("This computer has " + Environment.size(totalRam) + " of RAM in total. Weka needs about " + needText
                        + " for this export" + (e.ngrams() ? " even with a sparse-safe algorithm" : "")
                        + ". Choose fewer n-gram columns or fewer rows.");
            } else if (freeRam > 0 && need > freeRam) {
                w.add("Only " + Environment.size(freeRam) + " of RAM is free right now. Weka needs about " + needText
                        + " for this export. Close other programs or choose fewer n-gram columns.");
            }

            if (wekaHeapMb > 0) {
                if (wekaHeapMb * Environment.MB < need) {
                    w.add("Weka's Java heap is " + wekaHeapMb + " MB (" + wekaHeapSource + "). This export needs about "
                            + needText + ". Set maxheap=" + e.requiredHeapMb() + "m in RunWeka.ini and restart Weka.");
                }
                if (totalRam > 0 && wekaHeapMb * Environment.MB > totalRam * 0.85) {
                    w.add("Weka's Java heap (" + wekaHeapMb + " MB) is almost as large as this computer's RAM ("
                            + Environment.size(totalRam) + "). Windows will start swapping and Weka will crawl.");
                }
            } else if (need > WEKA_DEFAULT_BYTES) {
                w.add("Weka's Java heap is unknown here. Weka's default is " + Environment.WEKA_DEFAULT_HEAP_MB
                        + " MB and this export needs about " + needText + ". Enter your Weka heap below, or set maxheap="
                        + e.requiredHeapMb() + "m in RunWeka.ini.");
            }
        }

        long appNeed = appRequiredBytes(fileBytes);
        if (fileBytes > 0 && appMaxHeap > 0 && appMaxHeap < appNeed) {
            w.add("This tool's Java heap is " + Environment.size(appMaxHeap) + " (" + appHeapSource + "). Reading and exporting this file needs about "
                    + Environment.size(appNeed) + ". Start it with run.bat, or raise -Xmx.");
        }
        return w;
    }

    private static final long WEKA_DEFAULT_BYTES = Environment.WEKA_DEFAULT_HEAP_MB * Environment.MB;
}
