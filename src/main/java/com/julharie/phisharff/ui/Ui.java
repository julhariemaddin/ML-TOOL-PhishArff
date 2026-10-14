package com.julharie.phisharff.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Locale;

/** Small UI helpers: boxed groups (like Weka's titled borders), flags, notices and the status line. */
final class Ui {
    private Ui() {}

    /** Text of the status bar at the bottom of the window. */
    static final StringProperty STATUS = new SimpleStringProperty("OK");

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

    /** A small square flag. kind: ok, warn, bad, neutral. */
    static Label pill(String text, String kind) {
        Label l = new Label(text);
        l.getStyleClass().addAll("pill", "pill-" + kind);
        return l;
    }

    /** A boxed message. kind: info, warn, bad, ok. */
    static Label notice(String text, String kind) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().addAll("notice", "notice-" + kind);
        return l;
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

    // ------------------------------------------------------------------ no animation: a tool, not a toy
    static void fadeIn(Node n) { }

    static void fadeIn(Node n, int ms) { }

    static void countUp(Label label, double target) { label.setText(String.format(Locale.US, "%.0f", target)); }

    // ------------------------------------------------------------------ boxed group
    /** A rectangle with a title sitting on its top border, the way Weka's panels look. Disabled instead of dimmed. */
    static class Group extends StackPane {
        protected final VBox body = new VBox(8);

        Group(String title, Node... content) {
            getStyleClass().add("group");
            body.getStyleClass().add("group-box");
            body.setPadding(new Insets(16, 10, 10, 10));
            body.getChildren().addAll(content);
            Label t = new Label(title);
            t.getStyleClass().add("group-title");
            StackPane.setAlignment(t, Pos.TOP_LEFT);
            StackPane.setMargin(t, new Insets(0, 0, 0, 8));
            StackPane.setMargin(body, new Insets(8, 0, 0, 0));
            getChildren().addAll(body, t);
        }
    }

    /** A group with a one-line explanation under the title, and a lock (disabled) until the step before it is done. */
    static final class Card extends Group {
        Card(String title, String subtitle, Node... content) {
            super(title);
            if (subtitle != null && !subtitle.isEmpty()) body.getChildren().add(label(subtitle, "muted"));
            body.getChildren().addAll(content);
        }

        void setLocked(boolean lock) { setDisable(lock); }
    }
}
