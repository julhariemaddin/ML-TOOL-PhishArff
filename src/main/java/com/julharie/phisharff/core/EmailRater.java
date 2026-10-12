package com.julharie.phisharff.core;

import com.julharie.phisharff.features.EmailFeatureExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Rates how well-formed an email row is (0-100). Same levels as UrlRater. */
public final class EmailRater {
    private static final Pattern ADDRESS = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private EmailRater() {}

    /** subject / sender are null when the CSV has no such column. */
    public static Rating rate(String subject, String sender, String body) {
        if (body == null || body.isBlank()) return Rating.of(0, "Empty body");
        int score = 100;
        List<String> notes = new ArrayList<>();

        int letters = 0, odd = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isLetter(c)) letters++;
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') odd++;
        }
        if (odd > body.length() / 5) { score -= 60; notes.add("body looks like binary data"); }
        if (letters < 3) { score -= 50; notes.add("body has almost no text"); }
        else if (body.trim().length() < 10) { score -= 40; notes.add("very short body"); }

        if (sender != null) {
            if (sender.isBlank()) { score -= 15; notes.add("sender is empty"); }
            else if (!ADDRESS.matcher(EmailFeatureExtractor.senderAddress(sender)).matches()) {
                score -= 35; notes.add("sender is not a valid email address");
            }
        }
        if (subject != null && subject.isBlank()) { score -= 10; notes.add("subject is empty"); }
        return Rating.of(score, notes.isEmpty() ? "Valid email" : String.join("; ", notes));
    }
}
