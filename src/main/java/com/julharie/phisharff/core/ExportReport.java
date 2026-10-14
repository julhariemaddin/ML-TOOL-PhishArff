package com.julharie.phisharff.core;

import com.julharie.phisharff.core.DatasetBuilder.Built;
import com.julharie.phisharff.core.DatasetBuilder.Options;
import com.julharie.phisharff.core.DatasetBuilder.Stats;
import com.julharie.phisharff.features.FeatureExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The "Export" step, part 3: the plain-text .info.txt report written next to the ARFF file. */
final class ExportReport {
    private ExportReport() {}

    static void write(Built built, Path infoFile, String sourceName, String sourceSha, String arffSha, long seed)
            throws IOException {
        Stats s = built.stats();
        Options o = built.options();
        StringBuilder sb = new StringBuilder();
        sb.append("PhishArff ").append(DatasetBuilder.VERSION).append(" - export report\n");
        sb.append("Author: Julharie M. Maddin (ORCID 0009-0009-3366-1271)\n");
        sb.append("Created: ").append(ArffWriter.now()).append("\n\n");
        sb.append("Data type : ").append(o.kind().title()).append("\n");
        if (o.kind() == DataKind.LINK) sb.append("Mode      : ").append(o.domainMode() ? "domain (host only)" : "full URL").append("\n");
        else sb.append("Columns   : body").append(o.hasSubject() ? ", subject" : "").append(o.hasSender() ? ", sender" : "").append("\n");
        sb.append("Source    : ").append(sourceName).append("\nSource SHA-256: ").append(sourceSha).append("\n");
        sb.append("ARFF SHA-256  : ").append(arffSha).append("\n\n");
        sb.append("Rows read: ").append(s.rowsIn()).append("\n");
        sb.append("  unknown label dropped : ").append(s.unknownLabel()).append("\n");
        sb.append("  invalid rows dropped  : ").append(s.invalidDropped()).append("\n");
        sb.append("  duplicates removed    : ").append(s.duplicates()).append("\n");
        sb.append("  conflicting keys removed (both labels): ").append(s.conflicts()).append("\n");
        sb.append("  before balancing: legitimate ").append(s.legitBefore()).append(", phishing ").append(s.phishingBefore()).append("\n");
        sb.append("Rows written: ").append(s.rowsOut()).append(" (legitimate ").append(s.legitOut())
                .append(", phishing ").append(s.phishingOut()).append(")  balanced=").append(o.balance()).append("\n");
        sb.append("Shuffle seed: ").append(seed).append("\n\n");
        sb.append("Label mapping: benign/legit/good/safe/ham/0 = legitimate; phishing/malicious/bad/spam/scam/1 = phishing\n\n");
        if (o.ngrams()) {
            sb.append("Character n-grams: ").append(o.ngramBuckets()).append(" hashed columns ng_0..ng_").append(o.ngramBuckets() - 1)
                    .append(" (host only, piece sizes ").append(FeatureExtractor.NGRAM_MIN).append("-").append(FeatureExtractor.NGRAM_MAX)
                    .append("). The ARFF file is SPARSE.\n")
                    .append("  Suggested start in Weka: classifier functions.SGD with -F 1 (log loss), 10-fold cross-validation.\n")
                    .append("  Also fine: functions.SMO, bayes.NaiveBayesMultinomial. trees.RandomForest is slow on thousands of sparse columns.\n\n");
        }
        sb.append("Features (column order):\n");
        int i = 1;
        for (String n : s.featureNames()) sb.append(String.format("  %2d. %s%n", i++, n));
        Files.writeString(infoFile, sb.toString(), StandardCharsets.UTF_8);
    }
}
