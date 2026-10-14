package com.julharie.phisharff.ui;

import com.julharie.phisharff.core.DataKind;
import com.julharie.phisharff.db.Database;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Home page: two tabs, Link and Email. */
public final class HomePage extends VBox {
    private final ConvertPane linkPane;
    private final ConvertPane emailPane;

    public HomePage(Database db, HostServices host) {
        super(0);
        setPadding(new Insets(8, 8, 0, 8));

        linkPane = new ConvertPane(DataKind.LINK, db, host);
        emailPane = new ConvertPane(DataKind.EMAIL, db, host);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Link to ARFF", linkPane), new Tab("Email to ARFF", emailPane));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().add(tabs);
    }

    public ConvertPane linkPane() { return linkPane; }
    public ConvertPane emailPane() { return emailPane; }
}
