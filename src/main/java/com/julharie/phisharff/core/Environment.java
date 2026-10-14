package com.julharie.phisharff.core;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * PhishArff - Weka ARFF preparation tool
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * Released under the MIT License. Keep this notice when you copy or modify this file.
 */

/**
 * Environment: what this computer and this Java process can offer. Used to warn before an export is made that
 * Weka cannot open.
 *
 *   physical RAM          total and free (the JVM's own view of the operating system)
 *   this tool's heap      Runtime.maxMemory(), and where the limit comes from (-Xmx, MaxRAMPercentage, an
 *                         environment variable, or the JVM default of 25% of RAM)
 *   Weka's heap           read from RunWeka.ini (maxheap=...) when Weka is installed in the usual place; the user
 *                         can always type the value, because Weka started from a command line has its own -Xmx.
 */
public final class Environment {
    private Environment() {}

    public static final long MB = 1024L * 1024L;
    public static final long GB = 1024L * MB;
    /** Weka's own default when RunWeka.ini does not say otherwise. */
    public static final long WEKA_DEFAULT_HEAP_MB = 1024;

    /** mb is -1 when unknown. */
    public record WekaHeap(long mb, String source) {
        public boolean known() { return mb > 0; }
    }

    // ---------------------------------------------------------------- RAM
    public static long totalRam() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean x) return x.getTotalMemorySize();
        return -1;
    }

    public static long freeRam() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean x) return x.getFreeMemorySize();
        return -1;
    }

    // ---------------------------------------------------------------- this tool's heap
    public static long appMaxHeap() { return Runtime.getRuntime().maxMemory(); }

    /** Where this process's heap limit comes from, in plain words. */
    public static String appHeapSource() {
        for (String name : new String[]{"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS"}) {
            String v = System.getenv(name);
            if (v != null && (v.contains("-Xmx") || v.contains("MaxRAMPercentage"))) return "environment variable " + name;
        }
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (a.startsWith("-Xmx")) return "-Xmx option";
            if (a.startsWith("-XX:MaxRAMPercentage")) return a.substring(4);
        }
        return "Java default (25% of RAM)";
    }

    // ---------------------------------------------------------------- Weka's heap
    /** Reads maxheap from RunWeka.ini. Returns unknown (-1) when Weka or the setting is not found. */
    public static WekaHeap detectWekaHeap() {
        for (Path ini : wekaIniCandidates()) {
            long mb = readMaxHeap(ini);
            if (mb > 0) return new WekaHeap(mb, "read from " + ini);
        }
        return new WekaHeap(-1, "not found");
    }

    static List<Path> wekaIniCandidates() {
        List<Path> out = new ArrayList<>();
        String home = System.getenv("WEKA_HOME");
        if (home != null && !home.isBlank()) out.add(Path.of(home, "RunWeka.ini"));
        for (String var : new String[]{"ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
            String root = System.getenv(var);
            if (root == null || root.isBlank()) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(root), "[Ww]eka*")) {
                for (Path dir : ds) out.add(dir.resolve("RunWeka.ini"));
            } catch (IOException | RuntimeException ignored) { /* folder missing: fine */ }
        }
        return out;
    }

    /** Public for tests. Looks for a line maxheap=1024m (a leading # means comment). */
    public static long readMaxHeap(Path ini) {
        try {
            if (!Files.isRegularFile(ini)) return -1;
            for (String line : Files.readAllLines(ini)) {
                String t = line.trim();
                if (t.startsWith("#") || !t.toLowerCase(Locale.ROOT).startsWith("maxheap")) continue;
                int eq = t.indexOf('=');
                if (eq > 0) return parseMegabytes(t.substring(eq + 1));
            }
        } catch (IOException | RuntimeException ignored) { /* unreadable: treat as unknown */ }
        return -1;
    }

    /** "1024m" -> 1024, "8g" -> 8192, "512" -> 512 (plain numbers are megabytes), junk -> -1. */
    public static long parseMegabytes(String text) {
        if (text == null) return -1;
        String s = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.endsWith("b")) s = s.substring(0, s.length() - 1);
        long factor = 1;
        if (s.endsWith("g")) { factor = 1024; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("m")) { s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("k")) { return -1; }
        try {
            long n = Long.parseLong(s);
            return n > 0 ? n * factor : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- text
    /** 1536 MB -> "1.5 GB", 800 KB -> "0.8 MB". */
    public static String size(long bytes) {
        if (bytes < 0) return "unknown";
        if (bytes >= GB) return String.format(Locale.US, "%.1f GB", bytes / (double) GB);
        return String.format(Locale.US, "%.0f MB", Math.max(1, bytes / (double) MB));
    }
}
