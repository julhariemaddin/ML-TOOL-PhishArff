package com.julharie.phisharff.features;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * FeatureConfig: the two choices that decide what a feature row looks like. The ARFF file, the exported extractor and
 * your website must all use the SAME FeatureConfig, otherwise the model sees different numbers than it was trained on.
 *
 * @param domainMode   true = look at the host only (scheme, www, path and query are ignored)
 * @param ngramBuckets 0 = no n-gram columns; above 0 = that many hashed character n-gram columns are appended
 */
public record FeatureConfig(boolean domainMode, int ngramBuckets) {

    public FeatureConfig {
        if (ngramBuckets < 0) throw new IllegalArgumentException("ngramBuckets must be 0 or more");
    }

    public boolean ngrams() {
        return ngramBuckets > 0;
    }

    /** Number of numeric columns in a row (the class column is not counted). */
    public int columnCount() {
        return FeatureExtractor.FEATURE_NAMES.size() + ngramBuckets;
    }
}
