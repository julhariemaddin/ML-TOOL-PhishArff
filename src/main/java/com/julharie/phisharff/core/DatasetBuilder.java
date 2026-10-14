package com.julharie.phisharff.core;

import com.julharie.phisharff.features.EmailFeatureExtractor;
import com.julharie.phisharff.features.FeatureConfig;
import com.julharie.phisharff.features.FeatureExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * The "Export" step, part 1: decide WHICH rows go into the dataset. THE FLOW:
 *   1. drop rows with an unknown label (and, if asked, rows rated Invalid)
 *   2. remove duplicates, remove rows whose key has BOTH labels (conflicts)
 *   3. optionally balance the classes (same number of phishing and legitimate rows)
 *   4. shuffle (fixed seed 42, so the same input gives the same file)
 *
 * Turning rows into numbers and files is done by the classes next to this one:
 *   ArffWriter    writes the ARFF file (dense, or sparse when n-gram columns are on)
 *   ExportReport  writes the .info.txt report next to it
 * writeArff and writeInfo below simply call them, so existing callers keep working.
 */
public final class DatasetBuilder {
    public static final String VERSION = "1.3.0";
    private static final long SEED = 42L;

    private DatasetBuilder() {}

    /**
     * ngramBuckets: 0 = no n-gram columns (dense ARFF). Above 0 (links only) = that many hashed character
     * n-gram columns are added and the ARFF is written in Weka's SPARSE format.
     */
    public record Options(DataKind kind, boolean domainMode, boolean balance, boolean dropInvalid,
                          boolean hasSubject, boolean hasSender, int ngramBuckets) {

        /** The old six-value form: no n-grams. */
        public Options(DataKind kind, boolean domainMode, boolean balance, boolean dropInvalid,
                       boolean hasSubject, boolean hasSender) {
            this(kind, domainMode, balance, dropInvalid, hasSubject, hasSender, 0);
        }

        public boolean ngrams() { return kind == DataKind.LINK && ngramBuckets > 0; }

        /** The feature settings for links, as one object the extractor understands. */
        public FeatureConfig featureConfig() { return new FeatureConfig(domainMode, ngrams() ? ngramBuckets : 0); }
    }

    public record Stats(int rowsIn, int unknownLabel, int invalidDropped, int duplicates, int conflicts,
                        int legitBefore, int phishingBefore, int rowsOut, int legitOut, int phishingOut,
                        List<String> featureNames) {}

    /** One cleaned row: the original entry plus its normalised label. */
    public record Row(Entry entry, boolean phishing) {}

    public record Built(List<Row> rows, Stats stats, Options options) {}

    // ---------------------------------------------------------------- steps 1-4
    public static Built build(List<Entry> entries, Options o) {
        int unknown = 0, invalidDropped = 0, dup = 0;
        Map<String, Row> unique = new LinkedHashMap<>();
        Set<String> conflicts = new HashSet<>();

        for (Entry e : entries) {
            String label = LabelNormalizer.normalize(e.label());
            if (label == null) { unknown++; continue; }
            Rating r = Keys.rate(o.kind(), e, o.hasSubject(), o.hasSender());
            if (r.level() == Rating.Level.INVALID && (o.dropInvalid() || r.score() == 0)) { invalidDropped++; continue; }

            boolean phishing = label.equals(LabelNormalizer.PHISHING);
            String key = Keys.of(o.kind(), e, o.domainMode());
            Row previous = unique.get(key);
            if (previous == null) unique.put(key, new Row(e, phishing));
            else if (previous.phishing() != phishing) conflicts.add(key);
            else dup++;
        }
        for (String key : conflicts) unique.remove(key);

        List<Row> legit = new ArrayList<>(), phish = new ArrayList<>();
        for (Row r : unique.values()) (r.phishing() ? phish : legit).add(r);
        int legitBefore = legit.size(), phishBefore = phish.size();

        Random rnd = new Random(SEED);
        Collections.shuffle(legit, rnd);
        Collections.shuffle(phish, rnd);
        if (o.balance()) {
            int n = Math.min(legit.size(), phish.size());
            legit = new ArrayList<>(legit.subList(0, n));
            phish = new ArrayList<>(phish.subList(0, n));
        }
        List<Row> all = new ArrayList<>(legit);
        all.addAll(phish);
        Collections.shuffle(all, rnd);

        List<String> names = o.kind() == DataKind.LINK
                ? FeatureExtractor.FEATURE_NAMES
                : EmailFeatureExtractor.featureNames(o.hasSubject(), o.hasSender());
        Stats stats = new Stats(entries.size(), unknown, invalidDropped, dup, conflicts.size(),
                legitBefore, phishBefore, all.size(), legit.size(), phish.size(), names);
        return new Built(all, stats, o);
    }

    // ---------------------------------------------------------------- step 5 (delegates)
    /** Writes the ARFF file and returns its SHA-256. */
    public static String writeArff(Built built, Path out, String sourceName, String sourceSha, Consumer<String> status)
            throws IOException {
        return ArffWriter.write(built, out, sourceName, sourceSha, status);
    }

    /** A plain-text report written next to the ARFF file, so the result can be reproduced and cited. */
    public static void writeInfo(Built built, Path infoFile, String sourceName, String sourceSha, String arffSha)
            throws IOException {
        ExportReport.write(built, infoFile, sourceName, sourceSha, arffSha, SEED);
    }
}
