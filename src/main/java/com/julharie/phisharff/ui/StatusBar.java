package com.julharie.phisharff.ui;

import javafx.beans.property.StringProperty;

/** Gives App access to the status text that the pages update (Ui is package-private). */
public final class StatusBar {
    private StatusBar() {}

    public static StringProperty text() { return Ui.STATUS; }
}
