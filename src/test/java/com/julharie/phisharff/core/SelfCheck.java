package com.julharie.phisharff.core;

import com.julharie.phisharff.features.FeatureConfig;
import com.julharie.phisharff.features.FeatureExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * SelfCheck: a small test program with no outside libraries (plain Java).
 *
 * Run it after every change to the feature code:
 *   mvn compile
 *   java -cp target/classes src/test/java/com/julharie/phisharff/core/SelfCheck.java
 * (compile-and-run in one go works with JDK 17+; if your JDK complains, compile it first with javac -cp target/classes)
 *
 * It prints PASS or FAIL for every check and exits with code 1 if anything failed.
 */
public final class SelfCheck {
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // 1. The worked example from the README: the first 20 numbers must never change (old models depend on them).
        double[] f = FeatureExtractor.extract("http://paypal.com.account-verify.xyz:8080/login?id=1&x=2");
        check("26 base features", f.length == 26);
        check("first 20 values unchanged", Arrays.equals(Arrays.copyOf(f, 20), new double[]{
                56, 29, 6, 3, 1, 6, 6.0 / 56, 4, 2, 0, 0, 0, 1, 2, 1, 1, 1, 0, 1, 0}));

        // 2. Domain form
        check("domain form", FeatureExtractor.toDomainForm("https://www.google.com/search?q=1").equals("http://google.com"));
        check("host of", FeatureExtractor.hostOf("https://user@a.b.com:8080/x").equals("a.b.com"));

        // 3. www and scheme must not matter in Domain only mode (this is what removes the bias)
        check("domain mode ignores scheme, www and path", Arrays.equals(
                FeatureExtractor.extractAll("https://www.site.com/login?id=1", new FeatureConfig(true, 64)),
                FeatureExtractor.extractAll("site.com", new FeatureConfig(true, 64))));

        // 4. n-gram hashing is deterministic and counts every piece once
        TreeMap<Integer, Integer> a = FeatureExtractor.ngramCounts("http://a.b", 1024);
        int pieces = a.values().stream().mapToInt(Integer::intValue).sum();
        check("n-gram piece count for '^a.b$' (5+4+3+2+1)", pieces == 15);
        check("n-gram same in both modes", a.equals(FeatureExtractor.ngramCounts("https://www.a.b/x?y=1", 1024)));
        check("no n-gram columns when buckets = 0", FeatureExtractor.ngramCounts("a.b", 0).isEmpty());

        // 5. FeatureConfig
        check("column count", new FeatureConfig(true, 100).columnCount() == 126);
        boolean threw = false;
        try { new FeatureConfig(true, -1); } catch (IllegalArgumentException e) { threw = true; }
        check("negative buckets rejected", threw);

        // 6. A sparse ARFF row must decode back to exactly what the extractor returns (this is the training/prediction contract)
        for (String url : List.of("https://www.google.com", "http://192.168.4.7/verify/account",
                "http://paypal.com.account-verify.xyz:8080/login?id=1&x=2", "x8k2q9fj.top")) {
            FeatureConfig cfg = new FeatureConfig(true, 512);
            double[] expected = FeatureExtractor.extractAll(url, cfg);
            double[] base = FeatureExtractor.extractDomainLevel(url);
            String row = ArffWriter.sparseRow(base, FeatureExtractor.ngramCounts(url, 512), 512, "phishing");
            // The ARFF stores 6 decimals, so compare with that tolerance.
            check("sparse row round trip: " + url, close(decodeSparse(row, expected.length), expected)
                    && row.trim().endsWith((expected.length) + " phishing}"));
        }

        // 7. End to end: labels, conflicts, duplicates, balancing, dense and sparse files
        List<Entry> entries = List.of(
                new Entry(1, "https://www.google.com", null, null, null, "0"),
                new Entry(2, "https://github.com/openjdk/jdk", null, null, null, "benign"),
                new Entry(3, "http://paypa1-secure.xyz/login?id=9", null, null, null, "1"),
                new Entry(4, "http://192.168.4.7/verify/account", null, null, null, "phishing"),
                new Entry(5, "http://paypal.com.account-verify.xyz:8080/login?id=1&x=2", null, null, null, "phish"),
                new Entry(6, "http://paypa1-secure.xyz/other", null, null, null, "1"),      // same domain -> duplicate in Domain only mode
                new Entry(7, "http://conflict.example/a", null, null, null, "0"),
                new Entry(8, "http://conflict.example/b", null, null, null, "1"),            // same domain, other label -> conflict
                new Entry(9, "not a url at all", null, null, null, "0"),                    // invalid
                new Entry(10, "http://x.test", null, null, null, "unknown"));                // unknown label
        DatasetBuilder.Built dense = DatasetBuilder.build(entries,
                new DatasetBuilder.Options(DataKind.LINK, true, false, true, false, false));
        DatasetBuilder.Stats s = dense.stats();
        check("unknown label dropped", s.unknownLabel() == 1);
        check("invalid row dropped", s.invalidDropped() == 1);
        check("duplicate removed", s.duplicates() == 1);
        check("conflict removed", s.conflicts() == 1);
        check("rows out = 2 legitimate + 3 phishing", s.legitOut() == 2 && s.phishingOut() == 3 && s.rowsOut() == 5);

        DatasetBuilder.Built balanced = DatasetBuilder.build(entries,
                new DatasetBuilder.Options(DataKind.LINK, true, true, true, false, false, 256));
        check("balancing keeps 2 + 2", balanced.stats().legitOut() == 2 && balanced.stats().phishingOut() == 2);

        Path tmp = Files.createTempDirectory("phisharff-selfcheck");
        Path denseFile = tmp.resolve("dense.arff"), sparseFile = tmp.resolve("sparse.arff");
        DatasetBuilder.writeArff(dense, denseFile, "t.csv", "sha", x -> {});
        DatasetBuilder.writeArff(balanced, sparseFile, "t.csv", "sha", x -> {});
        DatasetBuilder.writeInfo(balanced, tmp.resolve("sparse.info.txt"), "t.csv", "sha", "sha2");
        List<String> dl = Files.readAllLines(denseFile), sl = Files.readAllLines(sparseFile);
        check("dense file: 26 attributes + class", dl.stream().filter(l -> l.startsWith("@attribute")).count() == 27);
        check("dense file: 5 data rows", dl.size() - dl.indexOf("@data") - 1 == 5);
        check("sparse file: 26 + 256 attributes + class", sl.stream().filter(l -> l.startsWith("@attribute")).count() == 283);
        check("sparse file: 4 data rows, all start with {", sl.subList(sl.indexOf("@data") + 1, sl.size()).stream()
                .allMatch(l -> l.startsWith("{")) && sl.size() - sl.indexOf("@data") - 1 == 4);
        check("info report mentions n-grams", Files.readString(tmp.resolve("sparse.info.txt")).contains("ng_0..ng_255"));

        // 8. Weka memory check: the numbers and the warnings
        check("heap text 1024m", Environment.parseMegabytes("1024m") == 1024);
        check("heap text 8g", Environment.parseMegabytes("8G") == 8192);
        check("heap text plain number is MB", Environment.parseMegabytes("512") == 512);
        check("heap text junk", Environment.parseMegabytes("lots") == -1 && Environment.parseMegabytes("") == -1);
        Path ini = tmp.resolve("RunWeka.ini");
        Files.writeString(ini, "# maxheap=1m\nmaxheap=4096m\nfoo=bar\n");
        check("RunWeka.ini maxheap read, comment ignored", Environment.readMaxHeap(ini) == 4096);
        check("missing RunWeka.ini is unknown", Environment.readMaxHeap(tmp.resolve("nope.ini")) == -1);

        long GB = Environment.GB, MB = Environment.MB;
        WekaNeeds.Estimate e4k = WekaNeeds.estimate(286_370, 4096), e64k = WekaNeeds.estimate(286_370, 65536);
        check("4096 columns dense is about 9 GB (the OutOfMemory case)", e4k.denseBytes() > 8 * GB && e4k.denseBytes() < 10 * GB);
        check("65536 columns dense is over 100 GB", e64k.denseBytes() > 100 * GB);
        check("65536 columns sparse needs under 2 GB", e64k.requiredBytes() < 2 * GB);
        check("n-grams on: sparse-safe algorithms only", e64k.algorithms().contains("SGD") && !e64k.avoid().isEmpty());
        check("n-grams off: any algorithm", WekaNeeds.estimate(286_370, 0).algorithms().startsWith("any"));
        check("required heap is rounded up to 512 MB", e64k.requiredHeapMb() % 512 == 0 && e64k.requiredHeapMb() * MB >= e64k.requiredBytes());

        WekaNeeds.Estimate e16k = WekaNeeds.estimate(286_370, 16384);
        check("no warning when RAM, Weka heap and tool heap are enough", WekaNeeds.warnings(e16k, 32 * GB, 20 * GB, 10240, "x", 8 * GB, "y", 50 * MB).isEmpty());
        check("warning when free RAM is below the requirement", WekaNeeds.warnings(e16k, 32 * GB, 512 * MB, 10240, "x", 8 * GB, "y", 0).stream().anyMatch(w -> w.contains("free")));
        check("warning when total RAM is below the requirement", WekaNeeds.warnings(e16k, 512 * MB, 256 * MB, 10240, "x", 8 * GB, "y", 0).stream().anyMatch(w -> w.contains("in total")));
        check("warning when Weka's heap is too small", WekaNeeds.warnings(e16k, 32 * GB, 20 * GB, 512, "x", 8 * GB, "y", 0).stream().anyMatch(w -> w.contains("maxheap=")));
        check("warning when Weka's heap is unknown and 1 GB is not enough", WekaNeeds.warnings(WekaNeeds.estimate(2_000_000, 16384), 64 * GB, 40 * GB, -1, "", 8 * GB, "y", 0).stream().anyMatch(w -> w.contains("unknown")));
        check("warning when Weka's heap is about the size of the RAM", WekaNeeds.warnings(e16k, 8 * GB, 6 * GB, 8192, "x", 8 * GB, "y", 0).stream().anyMatch(w -> w.contains("swapping")));
        check("warning when this tool's heap is too small for the file", WekaNeeds.warnings(e16k, 32 * GB, 20 * GB, 10240, "x", 128 * MB, "y", 200 * MB).stream().anyMatch(w -> w.contains("This tool")));
        check("no RAM warning before a file is open", WekaNeeds.warnings(WekaNeeds.estimate(0, 65536), 1 * GB, 1 * GB, 64, "x", 8 * GB, "y", 0).isEmpty());

        System.out.println(failed == 0 ? "\nALL CHECKS PASSED" : "\n" + failed + " CHECK(S) FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Turns "{0 56,3 1,42 phishing}" back into a dense array (class column dropped). */
    private static double[] decodeSparse(String row, int columns) {
        double[] out = new double[columns];
        String body = row.trim();
        body = body.substring(1, body.length() - 1);
        for (String part : body.split(",")) {
            String[] kv = part.split(" ");
            int idx = Integer.parseInt(kv[0]);
            if (idx < columns) out[idx] = Double.parseDouble(kv[1]);
        }
        return out;
    }

    private static boolean close(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 5e-7) return false;
        return true;
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) failed++;
    }
}
