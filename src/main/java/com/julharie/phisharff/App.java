package com.julharie.phisharff;

import com.julharie.phisharff.core.DatasetBuilder;
import com.julharie.phisharff.db.Database;
import com.julharie.phisharff.ui.AboutPage;
import com.julharie.phisharff.ui.HomePage;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * PhishArff - turn link and email CSV files into Weka-ready ARFF files.
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * MIT License.
 *
 * Plain, boxed layout in the spirit of the Weka Explorer: a header with the title and two pages (Home, About),
 * the page, and a status bar. The application name appears only in the header.
 */
public class App extends Application {

    private final StackPane content = new StackPane();
    private HomePage home;
    private AboutPage about;

    @Override
    public void start(Stage stage) {
        Database db = new Database(Database.defaultFile());
        home = new HomePage(db, getHostServices());
        about = new AboutPage(getHostServices());

        // ---- header: icon and title on the left, pages on the right
        ImageView icon = new ImageView(new Image(App.class.getResourceAsStream("/icon-64.png")));
        icon.setFitHeight(20);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        Label title = new Label("PhishArff");
        title.getStyleClass().add("header-title");

        ToggleGroup group = new ToggleGroup();
        ToggleButton homeBtn = navButton("Home", group);
        ToggleButton aboutBtn = navButton("About", group);
        homeBtn.setSelected(true);
        homeBtn.setOnAction(e -> show(home));
        aboutBtn.setOnAction(e -> show(about));
        group.selectedToggleProperty().addListener((o, a, b) -> { if (b == null) a.setSelected(true); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, icon, title, spacer, homeBtn, aboutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        // ---- status bar
        Label status = new Label();
        status.textProperty().bind(javafx.beans.binding.Bindings.concat("Status: ", com.julharie.phisharff.ui.StatusBar.text()));
        status.getStyleClass().add("status-text");
        Label version = new Label("Version " + DatasetBuilder.VERSION + "  |  MIT License  |  (c) 2026 Julharie M. Maddin");
        version.getStyleClass().add("status-text");
        HBox footer = new HBox(status, grow(), version);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("footer");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(content);
        root.setBottom(footer);
        content.getChildren().add(home);

        Scene scene = new Scene(root, 1060, 780);
        scene.getStylesheets().add(App.class.getResource("/app.css").toExternalForm());
        stage.getIcons().add(new Image(App.class.getResourceAsStream("/icon.png")));
        stage.setTitle("PhishArff");
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    private static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static ToggleButton navButton(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.getStyleClass().add("nav-btn");
        return b;
    }

    private void show(Node page) {
        if (content.getChildren().contains(page)) return;
        content.getChildren().setAll(page);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
