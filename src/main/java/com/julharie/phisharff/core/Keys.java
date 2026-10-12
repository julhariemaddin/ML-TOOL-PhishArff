package com.julharie.phisharff.core;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Builds the "same row?" key used to find duplicates and conflicting labels. */
final class Keys {
    private Keys() {}

    static String of(DataKind kind, Entry e, boolean domainMode) {
        if (kind == DataKind.LINK) {
            String url = e.url() == null ? "" : e.url().trim().toLowerCase(Locale.ROOT);
            return domainMode ? com.julharie.phisharff.features.FeatureExtractor.toDomainForm(url) : url;
        }
        String text = (e.subject() == null ? "" : e.subject().trim()) + "\u0001"
                + (e.sender() == null ? "" : e.sender().trim()) + "\u0001"
                + (e.body() == null ? "" : e.body().trim());
        return UUID.nameUUIDFromBytes(text.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static Rating rate(DataKind kind, Entry e, boolean hasSubject, boolean hasSender) {
        return kind == DataKind.LINK
                ? UrlRater.rate(e.url())
                : EmailRater.rate(hasSubject ? e.subject() : null, hasSender ? e.sender() : null, e.body());
    }
}
