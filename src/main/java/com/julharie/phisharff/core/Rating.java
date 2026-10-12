package com.julharie.phisharff.core;

/** Data-quality rating of ONE row. This says "is it well-formed content", never "is it phishing". */
public record Rating(int score, Level level, String reason) {

    public enum Level { GOOD, ODD, INVALID }

    public static Rating of(int score, String reason) {
        int s = Math.max(0, Math.min(100, score));
        Level level = s >= 80 ? Level.GOOD : (s >= 50 ? Level.ODD : Level.INVALID);
        return new Rating(s, level, reason);
    }
}
