package com.julharie.phisharff.ui;

import com.julharie.phisharff.core.DataKind;
import com.julharie.phisharff.db.Database;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Home page: a short intro and two tabs, Link and Email. */
public final class HomePage extends VBox {
    private final ConvertPane linkPane;
    private final ConvertPane emailPane;

    public HomePage(Database db, HostServices host) {
        super(10);
        setPadding(new Insets(22, 28, 0, 28));

        Label title = Ui.label("Turn CSV files into Weka-ready ARFF", "hero-title");
        Label sub = Ui.label("Pick Link or Email, import your CSV, check the data, then export. "
                + "You also get the Java code that computes the same features for your website.", "hero-sub");

        linkPane = new ConvertPane(DataKind.LINK, db, host);
        emailPane = new ConvertPane(DataKind.EMAIL, db, host);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab link = new Tab("Link to ARFF", linkPane);
        Tab email = new Tab("Email to ARFF", emailPane);
        tabs.getTabs().addAll(link, email);
        // a gentle fade when switching tabs
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> Ui.fadeIn(b.getContent(), 220));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(title, sub, tabs);
    }

    public ConvertPane linkPane() { return linkPane; }
    public ConvertPane emailPane() { return emailPane; }
}
