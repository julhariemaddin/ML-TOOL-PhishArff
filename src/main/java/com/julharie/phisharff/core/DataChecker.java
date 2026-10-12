package com.julharie.phisharff.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The "Check data" step: rates every row, counts labels and duplicates, and looks for class bias in links. */
public final class DataChecker {
    private static final int MAX_PROBLEMS = 200;

    private DataChecker() {}

    public record Problem(int row, String preview, int score, String reason) {}

    /** One bias line, e.g. "Starts with https://www." legit 100% vs phishing 2%. */
    public record BiasLine(String what, int legitPercent, int phishingPercent) {
        public boolean strong() { return Math.abs(legitPercent - phishingPercent) >= 40; }
    }

    public record CheckReport(boolean skipped, int total, int good, int odd, int invalid, int unknownLabel,
                              int duplicates, double averageScore, int legit, int phishing,
                              List<Problem> problems, List<BiasLine> bias) {

        public String verdict() {
            if (skipped) return "Skipped";
            if (averageScore >= 90) return "Excellent";
            if (averageScore >= 75) return "Good";
            if (averageScore >= 50) return "Needs attention";
            return "Poor";
        }

        public boolean hasStrongBias() { return bias.stream().anyMatch(BiasLine::strong); }
    }

    public static CheckReport skipped(int total) {
        return new CheckReport(true, total, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    public static CheckReport check(DataKind kind, List<Entry> entries, boolean hasSubject, boolean hasSender) {
        int good = 0, odd = 0, invalid = 0, unknown = 0, dup = 0, legit = 0, phish = 0;
        long scoreSum = 0;
        List<Problem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // bias counters for links: index 0 = legit, 1 = phishing -> [rows, https, www, path]
        int[][] b = new int[2][4];

        for (Entry e : entries) {
            Rating r = Keys.rate(kind, e, hasSubject, hasSender);
            scoreSum += r.score();
            switch (r.level()) {
                case GOOD -> good++;
                case ODD -> odd++;
                case INVALID -> invalid++;
            }
            String label = LabelNormalizer.normalize(e.label());
            if (label == null) {
                unknown++;
                addProblem(problems, e, kind, 0, "Unknown label '" + clip(e.label(), 30) + "'");
            } else {
                boolean isPhish = label.equals(LabelNormalizer.PHISHING);
                if (isPhish) phish++; else legit++;
                if (kind == DataKind.LINK && r.level() != Rating.Level.INVALID) countBias(b[isPhish ? 1 : 0], e.url());
            }
            if (r.level() != Rating.Level.GOOD) addProblem(problems, e, kind, r.score(), r.reason());
            if (!seen.add(Keys.of(kind, e, false))) dup++;
        }

        List<BiasLine> bias = new ArrayList<>();
        if (kind == DataKind.LINK && b[0][0] > 0 && b[1][0] > 0) {
            bias.add(new BiasLine("Uses https://", pct(b[0][1], b[0][0]), pct(b[1][1], b[1][0])));
            bias.add(new BiasLine("Host starts with www.", pct(b[0][2], b[0][0]), pct(b[1][2], b[1][0])));
            bias.add(new BiasLine("Has a path after the domain", pct(b[0][3], b[0][0]), pct(b[1][3], b[1][0])));
        }
        double avg = entries.isEmpty() ? 0 : (double) scoreSum / entries.size();
        return new CheckReport(false, entries.size(), good, odd, invalid, unknown, dup, avg, legit, phish, problems, bias);
    }

    private static void addProblem(List<Problem> list, Entry e, DataKind kind, int score, String reason) {
        if (list.size() >= MAX_PROBLEMS) return;
        String value = kind == DataKind.LINK ? e.url() : (e.subject() != null && !e.subject().isBlank() ? e.subject() : e.body());
        String shown = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        list.add(new Problem(e.row(), shown.isEmpty() ? "(empty)" : clip(shown, 70), score, reason));
    }

    private static void countBias(int[] c, String url) {
        if (url == null) return;
        String s = url.trim().toLowerCase(Locale.ROOT);
        c[0]++;
        if (s.startsWith("https://")) c[1]++;
        String rest = s.replaceFirst("^[a-z][a-z0-9+.\\-]*://", "");
        if (rest.startsWith("www.")) c[2]++;
        int cut = -1;
        for (int i = 0; i < rest.length(); i++) if ("/?#".indexOf(rest.charAt(i)) >= 0) { cut = i; break; }
        if (cut >= 0 && rest.length() - cut > 1) c[3]++;
    }

    private static int pct(int part, int whole) { return whole == 0 ? 0 : (int) Math.round(100.0 * part / whole); }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }
}
