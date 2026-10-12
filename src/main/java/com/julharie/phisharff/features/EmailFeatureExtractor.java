package com.julharie.phisharff.features;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * PhishArff - Email feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * EmailFeatureExtractor
 * =====================
 * PURPOSE: turn an email (body, and optionally subject and sender) into numbers a Weka model can read.
 *
 * CONDITIONAL COLUMNS: the features come in three groups. The body group is always used. The subject group
 * and the sender group are used only if your CSV had a subject / sender column. Pass null for a group you
 * did not train with, and it is skipped. The order is always: body, then subject, then sender.
 *
 * USAGE (website or app):
 *   double[] row = EmailFeatureExtractor.extract(subject, sender, body);   // null = group not used
 *   List<String> names = EmailFeatureExtractor.featureNames(subject != null, sender != null);
 *
 * This class reuses FeatureExtractor (same package) to analyse every link found inside the email.
 *
 * GOLDEN RULE: use exactly the same groups as the ARFF file you trained on, in the same order.
 */
public final class EmailFeatureExtractor {

    // =====================================================================================
    // SECTION 1 - FEATURE NAMES (three groups)
    // =====================================================================================

    public static final List<String> BODY_FEATURES = List.of(
            "body_length", "word_count", "num_links", "num_unique_link_domains", "num_suspicious_links",
            "has_ip_link", "has_html", "num_exclamations", "uppercase_ratio", "digit_ratio",
            "urgent_words", "money_words", "credential_words", "mentions_attachment");

    public static final List<String> SUBJECT_FEATURES = List.of(
            "subject_length", "subject_uppercase_ratio", "subject_exclamations",
            "subject_urgent_words", "subject_fake_reply");

    public static final List<String> SENDER_FEATURES = List.of(
            "sender_domain_length", "sender_domain_has_digits", "sender_free_provider",
            "sender_suspicious_tld", "sender_brand_mismatch", "sender_link_mismatch");

    // =====================================================================================
    // SECTION 2 - LOOKUP LISTS
    // =====================================================================================

    private static final List<String> URGENT = List.of(
            "urgent", "immediately", "act now", "within 24", "final notice", "last warning", "suspended",
            "limited time", "expire", "verify your", "confirm your", "right away", "as soon as possible", "deadline");

    private static final List<String> MONEY = List.of(
            "prize", "winner", "lottery", "claim", "refund", "free gift", "bonus", "cash", "inheritance",
            "invoice", "payment", "wire transfer", "bitcoin", "gift card");

    private static final List<String> CREDENTIAL = List.of(
            "password", "log in", "login", "sign in", "username", "account number", "otp", "one-time",
            "pin code", "ssn", "card number", "cvv", "bank account", "security code");

    private static final List<String> ATTACHMENT = List.of(
            "attach", ".exe", ".zip", ".scr", ".docm", ".iso", "enable macros", "enable content");

    private static final Set<String> FREE_PROVIDERS = Set.of(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "live.com", "aol.com",
            "proton.me", "protonmail.com", "icloud.com", "mail.com", "gmx.com", "yandex.com");

    private static final Pattern LINK = Pattern.compile("(?i)(?:https?://|www\\.)[^\\s<>\"')\\]]+");
    private static final Pattern ADDRESS = Pattern.compile("<([^<>]+)>");
    private static final int MAX_LINKS = 200;

    private EmailFeatureExtractor() {}

    // =====================================================================================
    // SECTION 3 - PUBLIC METHODS
    // =====================================================================================

    /** Column names for the groups in use. Order: body, subject, sender. */
    public static List<String> featureNames(boolean hasSubject, boolean hasSender) {
        List<String> names = new ArrayList<>(BODY_FEATURES);
        if (hasSubject) names.addAll(SUBJECT_FEATURES);
        if (hasSender) names.addAll(SENDER_FEATURES);
        return names;
    }

    /** The method the model uses. null subject or sender = that group is not used. */
    public static double[] extract(String subject, String sender, String body) {
        Map<String, Double> values = extractMap(subject, sender, body);
        double[] out = new double[values.size()];
        int i = 0;
        for (double v : values.values()) out[i++] = v;
        return out;
    }

    /** Does the real work. Returns every feature by name (insertion order = column order). */
    public static Map<String, Double> extractMap(String subject, String sender, String body) {

        // ---- STEP 1: clean the input ----------------------------------------------------
        String text = body == null ? "" : body;
        String lower = text.toLowerCase(Locale.ROOT);

        // ---- STEP 2: find every link in the body and analyse each one -------------------
        List<String> links = findLinks(text);
        Set<String> linkDomains = new HashSet<>();
        int suspiciousLinks = 0;
        boolean ipLink = false;
        for (String link : links) {
            Map<String, Double> f = FeatureExtractor.extractMap(link);          // reuse the URL features
            linkDomains.add(FeatureExtractor.registrableDomain(FeatureExtractor.hostOf(link)));
            double score = f.get("has_ip") + f.get("has_at") + f.get("has_suspicious_word")
                    + f.get("suspicious_tld") + f.get("uses_shortener") + f.get("brand_mismatch");
            if (score >= 2) suspiciousLinks++;                                  // 2 or more red flags
            if (f.get("has_ip") == 1.0) ipLink = true;
        }

        // ---- STEP 3: body group (always) ------------------------------------------------
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("body_length", (double) text.length());
        f.put("word_count", (double) countWords(text));
        f.put("num_links", (double) links.size());
        f.put("num_unique_link_domains", (double) linkDomains.size());
        f.put("num_suspicious_links", (double) suspiciousLinks);
        f.put("has_ip_link", flag(ipLink));
        f.put("has_html", flag(looksLikeHtml(lower)));
        f.put("num_exclamations", (double) count(text, '!'));
        f.put("uppercase_ratio", upperRatio(text));
        f.put("digit_ratio", text.isEmpty() ? 0.0 : (double) countDigits(text) / text.length());
        f.put("urgent_words", (double) countAny(lower, URGENT));
        f.put("money_words", (double) countAny(lower, MONEY));
        f.put("credential_words", (double) countAny(lower, CREDENTIAL));
        f.put("mentions_attachment", flag(countAny(lower, ATTACHMENT) > 0));

        // ---- STEP 4: subject group (only if the CSV had a subject column) ---------------
        if (subject != null) {
            String s = subject.trim();
            String sl = s.toLowerCase(Locale.ROOT);
            f.put("subject_length", (double) s.length());
            f.put("subject_uppercase_ratio", upperRatio(s));
            f.put("subject_exclamations", (double) count(s, '!'));
            f.put("subject_urgent_words", (double) countAny(sl, URGENT));
            f.put("subject_fake_reply", flag(sl.startsWith("re:") || sl.startsWith("fw:") || sl.startsWith("fwd:")));
        }

        // ---- STEP 5: sender group (only if the CSV had a sender column) -----------------
        if (sender != null) {
            String address = senderAddress(sender);
            int at = address.lastIndexOf('@');
            String domain = at >= 0 ? address.substring(at + 1).toLowerCase(Locale.ROOT) : "";
            String real = FeatureExtractor.registrableDomain(domain);
            String tld = domain.contains(".") ? domain.substring(domain.lastIndexOf('.') + 1) : "";
            String senderLower = sender.toLowerCase(Locale.ROOT);
            boolean brandMismatch = FeatureExtractor.BRANDS.stream()
                    .anyMatch(b -> senderLower.contains(b) && !real.contains(b));
            f.put("sender_domain_length", (double) domain.length());
            f.put("sender_domain_has_digits", flag(countDigits(real) > 0));
            f.put("sender_free_provider", flag(FREE_PROVIDERS.contains(real)));
            f.put("sender_suspicious_tld", flag(FeatureExtractor.SUSPICIOUS_TLDS.contains(tld)));
            f.put("sender_brand_mismatch", flag(brandMismatch));
            f.put("sender_link_mismatch", flag(!links.isEmpty() && !linkDomains.contains(real)));
        }
        return f;
    }

    /** "Bank Support <help@bank.com>" -> "help@bank.com". Plain "help@bank.com" is returned as it is. */
    public static String senderAddress(String sender) {
        if (sender == null) return "";
        String s = sender.trim();
        Matcher m = ADDRESS.matcher(s);
        if (m.find()) s = m.group(1).trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s;
    }

    /** All http(s):// and www. links in a text (at most 200). */
    public static List<String> findLinks(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = LINK.matcher(text == null ? "" : text);
        while (m.find() && found.size() < MAX_LINKS) {
            String link = m.group();
            while (!link.isEmpty() && ".,;:!?".indexOf(link.charAt(link.length() - 1)) >= 0) {
                link = link.substring(0, link.length() - 1);              // drop trailing punctuation
            }
            if (!link.isEmpty()) found.add(link);
        }
        return found;
    }

    // =====================================================================================
    // SECTION 4 - TINY UTILITIES
    // =====================================================================================

    private static double flag(boolean b) { return b ? 1.0 : 0.0; }

    private static boolean looksLikeHtml(String lower) {
        return lower.contains("<html") || lower.contains("<a ") || lower.contains("<br")
                || lower.contains("<div") || lower.contains("<table") || lower.contains("</");
    }

    private static int countWords(String s) {
        int n = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            boolean space = Character.isWhitespace(s.charAt(i));
            if (!space && !inWord) n++;
            inWord = !space;
        }
        return n;
    }

    private static double upperRatio(String s) {
        int upper = 0, letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        return letters == 0 ? 0.0 : (double) upper / letters;
    }

    private static int countDigits(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) n++;
        return n;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /** Total number of times any phrase of the list appears in the text. */
    private static int countAny(String lower, List<String> phrases) {
        int n = 0;
        for (String p : phrases) {
            int from = 0;
            while ((from = lower.indexOf(p, from)) >= 0) {
                n++;
                from += p.length();
            }
        }
        return n;
    }
}
