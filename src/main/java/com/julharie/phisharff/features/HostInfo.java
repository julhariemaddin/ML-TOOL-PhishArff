package com.julharie.phisharff.features;

import java.util.Arrays;
import java.util.regex.Pattern;

/*
 * PhishArff - URL feature extractor
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * HostInfo: what we learn from the host name: is it an IP, how many sub-domains, what is the "real" domain.
 *   paypal.com.account-verify.xyz -> subdomains=2, registrable="account-verify.xyz", tld="xyz"
 */
record HostInfo(boolean isIp, int subdomains, String registrable, String tld) {

    /** Matches a real IPv4 address such as 192.168.4.7. Each number must be 0-255, so 999.1.1.1 is NOT an IP. */
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    static HostInfo of(String host) {
        boolean isIp = IPV4.matcher(host).matches();
        String[] labels = host.isEmpty() ? new String[0] : host.split("\\.");

        // The real domain is normally the last 2 labels (example.com), or 3 for gov.ph / co.uk style endings.
        int realLen = 2;
        if (labels.length >= 3 && Lexicon.TWO_PART_SUFFIX.contains(labels[labels.length - 2] + "." + labels[labels.length - 1])) {
            realLen = 3;
        }
        // Labels in front of the real domain are sub-domains (phishers stack many of them).
        int subdomains = isIp ? 0 : Math.max(0, labels.length - realLen);
        String registrable = (isIp || labels.length <= realLen)
                ? host
                : String.join(".", Arrays.copyOfRange(labels, labels.length - realLen, labels.length));
        String tld = labels.length > 0 ? labels[labels.length - 1] : "";
        return new HostInfo(isIp, subdomains, registrable, tld);
    }
}
