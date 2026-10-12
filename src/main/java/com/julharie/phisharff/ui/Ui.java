package com.julharie.phisharff.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.Locale;

/** Small UI helpers: cards, pills, and the few gentle animations the app uses. */
final class Ui {
    private Ui() {}

    // ------------------------------------------------------------------ text helpers
    static String num(long n) { return NumberFormat.getIntegerInstance(Locale.US).format(n); }

    static String bytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / 1024.0 / 1024.0);
    }

    static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        l.setWrapText(true);
        return l;
    }

    /** kind: ok, warn, bad, neutral. */
    static Label pill(String text, String kind) {
        Label l = new Label(text);
        l.getStyleClass().addAll("pill", "pill-" + kind);
        return l;
    }

    /** kind: info, warn, bad, ok. */
    static Label notice(String text, String kind) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().addAll("notice", "notice-" + kind);
        return l;
    }

    /** A thin blue vertical bar used to mark titles. */
    static Region accentBar(double height) {
        Region bar = new Region();
        bar.getStyleClass().add("accent-bar");
        bar.setMinWidth(4);
        bar.setPrefWidth(4);
        bar.setMaxWidth(4);
        bar.setMinHeight(height);
        return bar;
    }

    static void copy(String text) {
        ClipboardContent c = new ClipboardContent();
        c.putString(text);
        Clipboard.getSystemClipboard().setContent(c);
    }

    static void show(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ------------------------------------------------------------------ animations (all short and subtle)
    static void fadeIn(Node n) { fadeIn(n, 260); }

    static void fadeIn(Node n, int ms) {
        n.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(ms), n);
        t.setFromY(8);
        t.setToY(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        f.play();
        t.play();
    }

    /** Counts a label up from 0 to the target in about half a second. */
    static void countUp(Label label, double target) {
        SimpleDoubleProperty v = new SimpleDoubleProperty(0);
        v.addListener((o, a, b) -> label.setText(String.format(Locale.US, "%.0f", b.doubleValue())));
        new Timeline(new KeyFrame(Duration.millis(550), new KeyValue(v, target, Interpolator.EASE_OUT))).play();
    }

    // ------------------------------------------------------------------ card
    /** A white rounded card with a numbered header. Can be locked (dimmed) until the previous step is done. */
    static final class Card extends VBox {
        private boolean locked;

        Card(String title, String subtitle, Node... content) {
            super(14);
            getStyleClass().add("card");
            VBox titles = new VBox(2, label(title, "card-title"), label(subtitle, "muted"));
            HBox head = new HBox(12, accentBar(38), titles);
            head.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(head);
            getChildren().addAll(content);
            setPadding(new Insets(20, 24, 20, 24));
        }

        /** Locked cards are dimmed and cannot be used. Changing the state fades smoothly. */
        void setLocked(boolean lock) {
            if (lock == locked && getProperties().containsKey("init")) return;
            getProperties().put("init", true);
            locked = lock;
            setDisable(lock);
            FadeTransition f = new FadeTransition(Duration.millis(260), this);
            f.setToValue(lock ? 0.45 : 1.0);
            f.play();
        }
    }
}
