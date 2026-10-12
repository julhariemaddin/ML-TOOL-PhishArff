package com.julharie.phisharff.core;

import java.util.Locale;
import java.util.Set;

/** Turns the many ways people write a label into exactly two classes. Unknown text returns null. */
public final class LabelNormalizer {
    public static final String LEGIT = "legitimate";
    public static final String PHISHING = "phishing";

    private static final Set<String> LEGIT_WORDS = Set.of(
            "legitimate", "legit", "benign", "good", "safe", "ham", "normal", "genuine", "0", "no", "false", "not phishing");
    private static final Set<String> PHISH_WORDS = Set.of(
            "phishing", "phish", "malicious", "bad", "spam", "scam", "fraud", "1", "yes", "true",
            "phishing email", "phishing url", "phishing link");

    private LabelNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (LEGIT_WORDS.contains(s)) return LEGIT;
        if (PHISH_WORDS.contains(s)) return PHISHING;
        return null;
    }
}
