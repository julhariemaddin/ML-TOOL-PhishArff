package com.julharie.phisharff;

import com.julharie.phisharff.db.Database;
import com.julharie.phisharff.ui.AboutPage;
import com.julharie.phisharff.ui.HomePage;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * PhishArff - turn link and email CSV files into Weka-ready ARFF files.
 * Copyright (c) 2026 Julharie M. Maddin (https://github.com/julhariemaddin, ORCID 0009-0009-3366-1271)
 * MIT License.
 *
 * The window has a header with two pages (Home, About) and a footer.
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

        // ---- header: logo on the left, navigation on the right
        ImageView logo = new ImageView(new Image(App.class.getResourceAsStream("/logo-horizontal.png")));
        logo.setFitHeight(42);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        ToggleGroup group = new ToggleGroup();
        ToggleButton homeBtn = navButton("Home", group);
        ToggleButton aboutBtn = navButton("About", group);
        homeBtn.setSelected(true);
        homeBtn.setOnAction(e -> show(home));
        aboutBtn.setOnAction(e -> show(about));
        // never allow "nothing selected"
        group.selectedToggleProperty().addListener((o, a, b) -> { if (b == null) a.setSelected(true); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox header = new HBox(10, logo, spacer, homeBtn, aboutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        Label footerText = new Label("PhishArff " + com.julharie.phisharff.core.DatasetBuilder.VERSION
                + "   |   MIT License   |   Copyright (c) 2026 Julharie M. Maddin");
        HBox footer = new HBox(footerText);
        footer.getStyleClass().add("footer");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(content);
        root.setBottom(footer);
        content.getChildren().add(home);

        Scene scene = new Scene(root, 1120, 800);
        scene.getStylesheets().add(App.class.getResource("/app.css").toExternalForm());
        stage.getIcons().add(new Image(App.class.getResourceAsStream("/icon.png")));
        stage.setTitle("PhishArff");
        stage.setMinWidth(940);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();
        animateIn(home);
    }

    private static ToggleButton navButton(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.getStyleClass().add("nav-btn");
        return b;
    }

    /** Swap the page with a short fade and slide. */
    private void show(Node page) {
        if (content.getChildren().contains(page)) return;
        content.getChildren().setAll(page);
        animateIn(page);
    }

    private static void animateIn(Node page) {
        page.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(220), page);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), page);
        slide.setFromY(10);
        slide.setToY(0);
        fade.play();
        slide.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
