package com.julharie.phisharff.ui;

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** About page: the author, the purpose, the workflow, the license. The app name is only in the window header. */
public final class AboutPage extends ScrollPane {

    public AboutPage(HostServices host) {
        FlowPane links = new FlowPane(14, 6,
                link("GitHub: julhariemaddin", "https://github.com/julhariemaddin", host),
                link("ORCID: 0009-0009-3366-1271", "https://orcid.org/0009-0009-3366-1271", host));
        Ui.Group author = new Ui.Group("Author",
                Ui.label("Julharie M. Maddin", "strong"),
                Ui.label("First-year BS Computer Science student at Jose Rizal Memorial State University, Siocon Campus, Philippines. "
                        + "Self-taught in programming since 2019 with a backend focus: Java, Spring Boot, PostgreSQL and Redis. "
                        + "Holds an NC II certification in Computer Systems Servicing.", ""),
                links);

        Ui.Group purpose = new Ui.Group("Purpose",
                Ui.label("This tool helps students and researchers build more accurate phishing detectors in Weka.", ""),
                Ui.label("A Weka model is only as good as the data behind it. Many public phishing datasets are biased, for example every "
                        + "legitimate link is a bare https://www.site.com while phishing links vary. A model can then score 99% by learning that "
                        + "shortcut instead of real phishing signals, and fail on real websites.", "muted"),
                Ui.label("It catches these problems before training: it verifies the column names, rates every row, warns about class bias, "
                        + "removes duplicates and conflicting labels, balances the classes, and records SHA-256 checksums so every ARFF file can be "
                        + "traced back to its source. It also exports the exact Java feature extractor used, so your website or app computes "
                        + "the same numbers as the training data.", "muted"),
                Ui.label("It also checks, before you export, whether this computer and Weka's Java heap can hold the file, and names the Weka "
                        + "algorithms that fit it.", "muted"));

        Ui.Group how = new Ui.Group("Workflow",
                Ui.label("Import", "strong"),
                Ui.label("Choose a link or email CSV. Required column names are checked and the file gets a checksum.", "muted"),
                Ui.label("Check data", "strong"),
                Ui.label("Every row is rated (is it a URL, is it a valid email). You review the result and confirm.", "muted"),
                Ui.label("Export", "strong"),
                Ui.label("Get the ARFF file for Weka and the Java feature extractor for your own project.", "muted"));

        Ui.Group legal = new Ui.Group("License and credits",
                Ui.label("Copyright (c) 2026 Julharie M. Maddin. Released under the MIT License, see the LICENSE file.", ""),
                Ui.label("Weka is developed by the University of Waikato. This is an independent tool and is not affiliated with the Weka project.", "muted"));

        VBox page = new VBox(10, author, purpose, how, legal);
        page.setPadding(new Insets(8));
        setContent(page);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
    }

    private static Hyperlink link(String text, String url, HostServices host) {
        Hyperlink h = new Hyperlink(text);
        h.setOnAction(e -> host.showDocument(url));
        return h;
    }
}
