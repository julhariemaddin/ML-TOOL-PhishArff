package com.julharie.phisharff.ui;

import com.julharie.phisharff.core.DatasetBuilder;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** About page: who built it, why it exists, how it works, license. */
public final class AboutPage extends ScrollPane {

    public AboutPage(HostServices host) {
        ImageView logo = new ImageView(new Image(AboutPage.class.getResourceAsStream("/logo.png")));
        logo.setFitWidth(230);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        Label tagline = Ui.label("From a raw CSV to a Weka-ready ARFF file, with checks you can trust.", "hero-title");
        tagline.setStyle("-fx-font-size: 22px;");
        Label version = Ui.label("Version " + DatasetBuilder.VERSION + "   |   MIT License   |   Java and JavaFX", "muted");
        VBox heroText = new VBox(8, tagline, version);
        heroText.setAlignment(Pos.CENTER_LEFT);
        HBox hero = new HBox(30, logo, heroText);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("card");

        // ---- about the author
        FlowPane links = new FlowPane(14, 6,
                link("GitHub: julhariemaddin", "https://github.com/julhariemaddin", host),
                link("ORCID: 0009-0009-3366-1271", "https://orcid.org/0009-0009-3366-1271", host));
        VBox author = section("About the author",
                Ui.label("Julharie M. Maddin", "card-title"),
                Ui.label("First-year BS Computer Science student at Jose Rizal Memorial State University, Siocon Campus, Philippines. "
                        + "Self-taught in programming since 2019 with a backend focus: Java, Spring Boot, PostgreSQL and Redis. "
                        + "Holds an NC II certification in Computer Systems Servicing.", ""),
                links);

        // ---- purpose
        VBox purpose = section("Purpose",
                Ui.label("PhishArff exists to help students and researchers build more accurate phishing detectors in Weka.", ""),
                Ui.label("A Weka model is only as good as the data behind it. Many public phishing datasets are biased, for example every "
                        + "legitimate link is a bare https://www.site.com while phishing links vary. A model can then score 99% by learning that "
                        + "shortcut instead of real phishing signals, and fail on real websites.", "muted"),
                Ui.label("PhishArff catches these problems before training: it verifies the column names, rates every row, warns about class bias, "
                        + "removes duplicates and conflicting labels, balances the classes, and records SHA-256 checksums so every ARFF file can be "
                        + "traced back to its source. It also exports the exact Java feature extractor used, so your website or app computes "
                        + "the same numbers as the training data.", "muted"));

        // ---- how it works
        VBox how = section("How it works",
                step("Import", "Choose a link or email CSV. Required column names are checked and the file gets a checksum."),
                step("Check", "Every row is rated (is it a URL, is it a valid email). You review the result and confirm."),
                step("Export", "Get the ARFF file for Weka and the Java feature extractor for your own project."));

        // ---- license and credits
        VBox legal = section("License and credits",
                Ui.label("Copyright (c) 2026 Julharie M. Maddin. Released under the MIT License, see the LICENSE file.", ""),
                Ui.label("Weka is developed by the University of Waikato. PhishArff is an independent tool and is not affiliated with the Weka project.", "muted"));

        VBox page = new VBox(18, hero, author, purpose, how, legal);
        page.setPadding(new Insets(22, 28, 28, 28));
        setContent(page);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
    }

    private static VBox section(String title, javafx.scene.Node... content) {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        box.getChildren().add(Ui.label(title, "section-title"));
        box.getChildren().addAll(content);
        return box;
    }

    private static HBox step(String title, String text) {
        VBox texts = new VBox(2, Ui.label(title, "card-title"), Ui.label(text, "muted"));
        HBox row = new HBox(12, Ui.accentBar(40), texts);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Hyperlink link(String text, String url, HostServices host) {
        Hyperlink h = new Hyperlink(text);
        h.setOnAction(e -> host.showDocument(url));
        return h;
    }
}
