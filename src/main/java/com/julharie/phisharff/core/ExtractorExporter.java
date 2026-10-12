package com.julharie.phisharff.core;

import com.julharie.phisharff.features.EmailFeatureExtractor;
import com.julharie.phisharff.features.FeatureExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Exports the Java feature extractor(s) used to build the ARFF file, so a website or app can compute
 * the same numbers. The exported source is the SAME file PhishArff runs, with only the package line changed.
 */
public final class ExtractorExporter {
    private static final Pattern PACKAGE_NAME = Pattern.compile("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*");
    private static final String PACKAGE_LINE = "package com.julharie.phisharff.features;";

    private ExtractorExporter() {}

    /** Returns the files written. pkg may be blank (default package). */
    public static List<Path> export(DataKind kind, Path folder, String pkg, boolean domainMode,
                                    boolean hasSubject, boolean hasSender) throws IOException {
        String p = pkg == null ? "" : pkg.trim();
        if (!p.isEmpty() && !PACKAGE_NAME.matcher(p).matches()) {
            throw new IOException("Package name must be lowercase like com.example.features");
        }
        Files.createDirectories(folder);
        List<Path> written = new ArrayList<>();
        written.add(copy("FeatureExtractor", folder, p));
        if (kind == DataKind.EMAIL) written.add(copy("EmailFeatureExtractor", folder, p));

        Path usage = folder.resolve("PhishArff-usage.txt");
        Files.writeString(usage, usage(kind, p, domainMode, hasSubject, hasSender), StandardCharsets.UTF_8);
        written.add(usage);
        return written;
    }

    private static Path copy(String className, Path folder, String pkg) throws IOException {
        String resource = "/com/julharie/phisharff/features/" + className + ".java";
        String source;
        try (InputStream in = ExtractorExporter.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Source file not found inside the app (" + resource
                    + "). Run PhishArff with run.bat or Maven so the sources are bundled.");
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        source = source.replace(PACKAGE_LINE, pkg.isEmpty() ? "" : "package " + pkg + ";");
        Path out = folder.resolve(className + ".java");
        Files.writeString(out, source, StandardCharsets.UTF_8);
        return out;
    }

    private static String usage(DataKind kind, String pkg, boolean domainMode, boolean hasSubject, boolean hasSender) {
        StringBuilder sb = new StringBuilder("PhishArff - how to use the exported feature extractor\n");
        sb.append("Copy the .java file(s) into your project").append(pkg.isEmpty() ? "" : " under the package " + pkg).append(".\n\n");
        if (kind == DataKind.LINK) {
            sb.append("Your ARFF file was built in ").append(domainMode ? "DOMAIN" : "FULL URL").append(" mode, so call:\n\n");
            sb.append(domainMode
                    ? "    double[] f = FeatureExtractor.extractDomainLevel(url);\n"
                    : "    double[] f = FeatureExtractor.extract(url);\n");
            sb.append("\nAttribute order (20 numbers, then the class):\n");
            for (String n : FeatureExtractor.FEATURE_NAMES) sb.append("    ").append(n).append('\n');
        } else {
            sb.append("Your ARFF file used these columns: body").append(hasSubject ? ", subject" : "").append(hasSender ? ", sender" : "").append(".\nSo call:\n\n");
            sb.append("    double[] f = EmailFeatureExtractor.extract(")
                    .append(hasSubject ? "subject" : "null").append(", ")
                    .append(hasSender ? "sender" : "null").append(", body);\n");
            sb.append("\nAttribute order (then the class):\n");
            for (String n : EmailFeatureExtractor.featureNames(hasSubject, hasSender)) sb.append("    ").append(n).append('\n');
        }
        sb.append("\nIn Weka code, put the numbers in a DenseInstance in this order, with the class value last.\n");
        sb.append("If you change a feature, rebuild the ARFF file and retrain the model.\n");
        return sb.toString();
    }
}
