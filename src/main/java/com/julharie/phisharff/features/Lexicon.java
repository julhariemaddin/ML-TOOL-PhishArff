package com.julharie.phisharff.features;

import java.util.List;
import java.util.Set;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * Lexicon: the plain word lists the extractor looks things up in. Data only, no logic.
 * Edit a list, then rebuild the ARFF file and retrain: the extractor and the model must always agree.
 */
final class Lexicon {
    private Lexicon() {}

    /** Domain endings made of two parts, so the real domain needs 3 labels (deped.gov.ph). */
    static final Set<String> TWO_PART_SUFFIX = Set.of(
            "com.ph", "gov.ph", "edu.ph", "org.ph", "net.ph", "co.uk", "com.au", "co.jp");

    /** Cheap domain endings often used for throwaway scam sites. */
    static final Set<String> SUSPICIOUS_TLDS = Set.of(
            "xyz", "top", "tk", "ml", "ga", "cf", "gq", "click", "link", "work", "support", "country", "zip");

    /** Link shorteners hide the real destination. */
    static final Set<String> SHORTENERS = Set.of(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly", "rb.gy", "shorturl.at");

    /** Words scammers like to put in links. */
    static final List<String> SUSPICIOUS_WORDS = List.of(
            "login", "signin", "verify", "secure", "update", "account", "confirm", "password",
            "wallet", "claim", "promo", "bonus", "free", "invoice", "suspend");

    /** Brands that scammers pretend to be. */
    static final List<String> BRANDS = List.of(
            "paypal", "gcash", "paymaya", "maya", "bdo", "bpi", "metrobank", "landbank", "unionbank",
            "facebook", "google", "apple", "microsoft", "amazon", "netflix", "lazada", "shopee", "instagram");

    /**
     * Free hosting and tunnelling platforms that anybody can use to publish a page for nothing. They are legitimate
     * services, but heavily abused for phishing, so "is this host on one of them?" is a useful signal.
     */
    static final List<String> FREE_HOSTS = List.of(
            "duckdns.org", "000webhostapp.com", "000webhost.com", "workers.dev", "pages.dev", "r2.dev",
            "godaddysites.com", "weebly.com", "wixsite.com", "webflow.io", "netlify.app", "vercel.app",
            "github.io", "firebaseapp.com", "web.app", "herokuapp.com", "glitch.me", "onrender.com",
            "blogspot.com", "wordpress.com", "ngrok.io", "trycloudflare.com", "infinityfreeapp.com", "wcomhost.com");
}
