package com.julharie.phisharff.ui;

import com.julharie.phisharff.core.*;
import com.julharie.phisharff.core.DataChecker.BiasLine;
import com.julharie.phisharff.core.DataChecker.CheckReport;
import com.julharie.phisharff.core.DataChecker.Problem;
import com.julharie.phisharff.core.DataLoader.ColumnStatus;
import com.julharie.phisharff.core.DataLoader.LoadResult;
import com.julharie.phisharff.db.Database;
import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * One complete workflow for one data type (links or emails), as three boxed groups:
 *   Import      - choose a CSV, see its checksum, check the required column names
 *   Check data  - rate every row (is it a URL / a valid email?), confirm the data
 *   Export      - write the ARFF file, or export the Java feature extractor
 * Check data and Export stay disabled (greyed) until the step before them is done.
 * For links, the Export group also shows the Weka memory check: the RAM and the Java heap Weka needs for the chosen
 * n-gram size, the Weka algorithms that fit, and a yellow warning when this computer or Weka's heap is too small.
 */
public final class ConvertPane extends ScrollPane {

    private final DataKind kind;
    private final Database db;
    private final HostServices host;

    private LoadResult loaded;
    private CheckReport report;
    private boolean busy;

    // ---- card 1 (import)
    private final HBox dropZone = new HBox(8);
    private final Label dropTitle;
    private final ProgressBar loadBar = new ProgressBar();
    private final Label loadStatus = Ui.label("", "muted");
    private final GridPane infoGrid = new GridPane();
    private final TextField shaField = new TextField();
    private final VBox columnsBox = new VBox(6);
    private final VBox noticeBox1 = new VBox(8);

    // ---- card 2 (check)
    private final CheckBox checkToggle = new CheckBox("Check that every row is valid content (recommended)");
    private final CheckBox dropInvalid = new CheckBox("Remove rows rated Invalid when exporting");
    private final Button runCheck = new Button("Run check");
    private final ProgressBar checkBar = new ProgressBar();
    private final Label checkStatus = Ui.label("", "muted");
    private final VBox resultBox = new VBox(12);
    private final CheckBox confirm = new CheckBox("I reviewed the results and confirm this data is correct");

    // ---- card 3 (export)
    private final ComboBox<String> modeBox = new ComboBox<>();
    private final ComboBox<String> ngramBox = new ComboBox<>();
    private final CheckBox balance = new CheckBox("Balance classes (same number of phishing and legitimate rows)");
    private final TextField packageField = new TextField();
    private final Button exportArffBtn = new Button("Export ARFF file");
    private final Button exportJavaBtn = new Button("Export Java feature extractor");
    private final ProgressBar exportBar = new ProgressBar();
    private final Label exportStatus = Ui.label("", "muted");
    private final VBox exportResult = new VBox(10);
    private final VBox memBox = new VBox(6);
    private final Label algoLabel = new Label();
    private final TextField wekaHeapField = new TextField();
    private String wekaHeapSource = "entered by you";

    private final Ui.Card card1, card2, card3;
    private final VBox historyBox = new VBox(6);

    public ConvertPane(DataKind kind, Database db, HostServices host) {
        this.kind = kind;
        this.db = db;
        this.host = host;

        // ============================================================ CARD 1
        dropTitle = new Label("No file selected. Open a " + kind.title().toLowerCase() + " CSV file, or drop it here.");
        dropTitle.getStyleClass().add("file-field");
        dropTitle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dropTitle, Priority.ALWAYS);
        String hint = "Required columns: " + kind.requiredNames()
                + (kind == DataKind.EMAIL ? "     Optional: subject, sender (they add more features)" : "");
        Button choose = new Button("Open file...");
        choose.setId("choose-" + kind.name());
        choose.getStyleClass().add("btn-primary");
        choose.setOnAction(e -> chooseFile());
        dropZone.setAlignment(Pos.CENTER_LEFT);
        dropZone.getChildren().addAll(choose, dropTitle);
        dropZone.setOnDragOver(this::onDragOver);
        dropZone.setOnDragExited(e -> dropTitle.getStyleClass().remove("file-field-active"));
        dropZone.setOnDragDropped(this::onDrop);

        loadBar.setMaxWidth(Double.MAX_VALUE);
        Ui.show(loadBar, false);
        Ui.show(loadStatus, false);
        Ui.show(infoGrid, false);
        Ui.show(columnsBox, false);

        infoGrid.setHgap(14);
        infoGrid.setVgap(6);
        shaField.setEditable(false);
        shaField.getStyleClass().add("mono");
        HBox.setHgrow(shaField, Priority.ALWAYS);

        card1 = new Ui.Card("Import", "Choose a CSV file. The column names are checked and a checksum is computed.",
                dropZone, Ui.label(hint, "muted"), loadBar, loadStatus, infoGrid, columnsBox, noticeBox1);

        // ============================================================ CARD 2
        checkToggle.setSelected(true);
        dropInvalid.setSelected(true);
        checkToggle.selectedProperty().addListener((o, a, b) -> {
            runCheck.setText(b ? "Run check" : "Skip check");
            updateState();
        });
        runCheck.getStyleClass().add("btn-primary");
        runCheck.setId("runCheck-" + kind.name());
        runCheck.setOnAction(e -> runCheck());
        checkBar.setMaxWidth(Double.MAX_VALUE);
        Ui.show(checkBar, false);
        Ui.show(checkStatus, false);
        Ui.show(resultBox, false);
        confirm.setId("confirm-" + kind.name());
        confirm.setDisable(true);
        confirm.selectedProperty().addListener((o, a, b) -> updateState());
        Label rateNote = Ui.label(kind == DataKind.LINK
                ? "Each value is tested: is it a URL, and how well-formed is it (0-100)? This rates the DATA, it does not say whether a link is phishing."
                : "Each row is tested: does it have a body, a valid sender address, a subject (0-100)? This rates the DATA, it does not say whether an email is phishing.", "muted");
        card2 = new Ui.Card("Check data", "Confirm the content is what it should be before anything is exported.",
                rateNote, checkToggle, dropInvalid, new HBox(12, runCheck), checkBar, checkStatus, resultBox, confirm);

        // ============================================================ CARD 3
        balance.setSelected(true);
        packageField.setPromptText("Package name, e.g. com.example.features (optional)");
        packageField.setText(db.getSetting("package_name", "com.example.features"));
        exportArffBtn.getStyleClass().add("btn-primary");
        exportArffBtn.setId("exportArff-" + kind.name());
        exportArffBtn.setOnAction(e -> onExportArff());
        exportJavaBtn.getStyleClass().add("btn-secondary");
        exportJavaBtn.setId("exportJava-" + kind.name());
        exportJavaBtn.setOnAction(e -> onExportJava());
        exportBar.setMaxWidth(Double.MAX_VALUE);
        Ui.show(exportBar, false);
        Ui.show(exportStatus, false);
        Ui.show(exportResult, false);

        Node modeRow;
        if (kind == DataKind.LINK) {
            modeBox.getItems().addAll("Domain only (recommended when the data is biased)", "Full URL");
            modeBox.getSelectionModel().select(1);
            modeBox.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(modeBox, Priority.ALWAYS);
            ngramBox.getItems().addAll("Off (26 columns, dense ARFF)", "1024 n-gram columns", "4096 n-gram columns",
                    "16384 n-gram columns", "65536 n-gram columns");
            ngramBox.getSelectionModel().select(0);
            ngramBox.setPrefWidth(230);
            ngramBox.setMinWidth(230);
            algoLabel.setWrapText(true);
            algoLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(algoLabel, Priority.ALWAYS);
            HBox ngramRow = new HBox(8, ngramBox, algoLabel);
            ngramRow.setAlignment(Pos.TOP_LEFT);

            String savedHeap = db.getSetting("weka_heap_mb", "");
            if (!savedHeap.isBlank()) {
                wekaHeapField.setText(savedHeap);
            } else {
                Environment.WekaHeap detected = Environment.detectWekaHeap();
                if (detected.known()) { wekaHeapField.setText(String.valueOf(detected.mb())); wekaHeapSource = detected.source(); }
            }
            wekaHeapField.setPromptText("unknown");
            wekaHeapField.setPrefWidth(90);
            wekaHeapField.setMaxWidth(90);
            wekaHeapField.textProperty().addListener((o, a, b) -> {
                wekaHeapSource = "entered by you";
                db.setSetting("weka_heap_mb", b.trim());
                refreshMemory();
            });
            ngramBox.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> refreshMemory());
            balance.selectedProperty().addListener((o, a, b) -> refreshMemory());

            modeRow = new VBox(6, Ui.label("Feature mode", "section-title"), modeBox, Ui.label(
                    "Domain only ignores scheme, www, path and query. Use it when one class has a different URL shape than the other. "
                            + "The website must use the same mode.", "muted"),
                    Ui.label("Character n-grams (optional)", "section-title"), ngramRow, Ui.label(
                    "Adds hashed counts of 1-6 letter pieces of the host name. They raise accuracy a lot. The ARFF is sparse, so only the "
                            + "Weka algorithms shown beside the list can open it without running out of memory. "
                            + "The website must use the same number of columns.", "muted"),
                    new Ui.Group("Memory check for Weka", memBox));
        } else {
            modeRow = Ui.label("Feature groups: body (always), plus subject and sender when those columns exist in your file.", "muted");
        }
        VBox javaBox = new VBox(8, Ui.label("Feature extractor for your website or app", "section-title"),
                Ui.label("Exports the exact Java code used to build the ARFF file, so predictions use the same numbers.", "muted"),
                new HBox(10, packageField, exportJavaBtn));
        HBox.setHgrow(packageField, Priority.ALWAYS);
        card3 = new Ui.Card("Export", "Create the ARFF file for Weka, and the Java code to use the same features elsewhere.",
                modeRow, balance, new HBox(12, exportArffBtn), exportBar, exportStatus, exportResult,
                new Separator(), javaBox);

        // ============================================================ PAGE
        VBox page = new VBox(10, card1, card2, card3, historyBox);
        page.setPadding(new Insets(4, 4, 12, 4));
        setContent(page);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);

        card2.setLocked(true);
        card3.setLocked(true);
        refreshHistory();
        updateState();
        refreshMemory();
    }

    /** Number of hashed n-gram columns chosen in the Export card (0 = off; always 0 for emails). */
    private int ngramBuckets() {
        if (kind != DataKind.LINK) return 0;
        return switch (ngramBox.getSelectionModel().getSelectedIndex()) {
            case 1 -> 1024;
            case 2 -> 4096;
            case 3 -> 16384;
            case 4 -> 65536;
            default -> 0;
        };
    }

    // ================================================================== Weka memory check (links)
    /** Rows that will probably be exported: balanced or not, from the check report when there is one. */
    private long estimateRows() {
        if (loaded == null || !loaded.ok()) return 0;
        if (report != null && !report.skipped()) {
            long l = report.legit(), p = report.phishing();
            if (l + p > 0) return balance.isSelected() ? 2 * Math.min(l, p) : l + p;
        }
        return loaded.totalRows();
    }

    /**
     * Rebuilds the memory box for the chosen n-gram size: what Weka needs, what this computer and Weka's heap have, the
     * Weka algorithms that fit, and yellow warnings. The n-gram list and the algorithm box turn yellow with them.
     */
    private void refreshMemory() {
        if (kind != DataKind.LINK) return;
        int buckets = ngramBuckets();
        long rows = estimateRows();
        WekaNeeds.Estimate est = WekaNeeds.estimate(rows, buckets);
        long total = Environment.totalRam(), free = Environment.freeRam();
        long wekaMb = Environment.parseMegabytes(wekaHeapField.getText());
        long appHeap = Environment.appMaxHeap();
        String appSource = Environment.appHeapSource();
        List<String> warnings = WekaNeeds.warnings(est, total, free, wekaMb, wekaHeapSource, appHeap, appSource,
                loaded == null ? 0 : loaded.sizeBytes());
        boolean warn = !warnings.isEmpty();

        // algorithm beside the n-gram list
        algoLabel.setText("Weka algorithm: " + est.algorithms() + (est.ngrams() ? "\nDo not use: " + est.avoid() : ""));
        algoLabel.getStyleClass().removeAll("algo-box", "algo-box-warn");
        algoLabel.getStyleClass().add(warn ? "algo-box-warn" : "algo-box");
        ngramBox.getStyleClass().remove("combo-warn");
        if (warn) ngramBox.getStyleClass().add("combo-warn");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(4);
        int row = 0;
        addMem(g, row++, "Rows to export (estimate)", rows > 0 ? Ui.num(rows) : "open a file to estimate");
        if (rows > 0) {
            addMem(g, row++, est.ngrams() ? "Weka needs (sparse-safe algorithm)" : "Weka needs", Environment.size(est.requiredBytes()));
            if (est.ngrams()) addMem(g, row++, "Weka needs (any other algorithm)", Environment.size(est.denseBytes()));
        }
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("btn-small");
        refresh.setOnAction(e -> refreshMemory());
        g.add(Ui.label("RAM of this computer", "section-title"), 0, row);
        g.add(new HBox(10, new Label(Environment.size(total) + " total, " + Environment.size(free) + " free"), refresh), 1, row++);
        HBox wekaRow = new HBox(8, wekaHeapField, new Label("MB"), Ui.label("(" + (wekaHeapField.getText().isBlank()
                ? "type it, or find maxheap in RunWeka.ini; Weka's default is 1024" : wekaHeapSource) + ")", "muted"));
        wekaRow.setAlignment(Pos.CENTER_LEFT);
        g.add(Ui.label("Weka Java heap (-Xmx)", "section-title"), 0, row);
        g.add(wekaRow, 1, row++);
        addMem(g, row, "Java heap of this tool", Environment.size(appHeap) + "  (" + appSource + ")");

        memBox.getChildren().setAll(g);
        for (String w : warnings) memBox.getChildren().add(Ui.notice(w, "warn"));
        if (warnings.isEmpty() && rows > 0 && est.ngrams()) {
            memBox.getChildren().add(Ui.notice("Memory is enough for the algorithms listed above. Any other algorithm expands the file to "
                    + Environment.size(est.denseBytes()) + ".", "info"));
        }
    }

    private static void addMem(GridPane g, int row, String name, String value) {
        g.add(Ui.label(name, "section-title"), 0, row);
        g.add(new Label(value), 1, row);
    }

    // ================================================================== state
    public DataKind kind() { return kind; }
    public boolean isBusy() { return busy; }
    public LoadResult loaded() { return loaded; }
    public CheckReport report() { return report; }

    private void updateState() {
        boolean loadedOk = loaded != null && loaded.ok();
        card2.setLocked(!loadedOk);
        confirm.setDisable(report == null);
        card3.setLocked(!(loadedOk && report != null && confirm.isSelected()));
        runCheck.setText(checkToggle.isSelected() ? "Run check" : "Skip check");
    }

    // ================================================================== card 1: import
    private void onDragOver(DragEvent e) {
        if (e.getDragboard().hasFiles() && !busy) {
            e.acceptTransferModes(TransferMode.COPY);
            if (!dropTitle.getStyleClass().contains("file-field-active")) dropTitle.getStyleClass().add("file-field-active");
        }
        e.consume();
    }

    private void onDrop(DragEvent e) {
        List<File> files = e.getDragboard().getFiles();
        boolean ok = files != null && !files.isEmpty();
        if (ok) loadFile(files.get(0).toPath());
        e.setDropCompleted(ok);
        dropTitle.getStyleClass().remove("file-field-active");
        e.consume();
    }

    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a " + kind.title().toLowerCase() + " CSV file");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV files", "*.csv", "*.txt", "*.tsv"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File dir = lastDir();
        if (dir != null) fc.setInitialDirectory(dir);
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f != null) loadFile(f.toPath());
    }

    /** Public so it can be driven from tests and from drag and drop. */
    public void loadFile(Path file) {
        if (busy) return;
        if (!Files.isRegularFile(file)) { setNotice(noticeBox1, "That is not a file.", "bad"); return; }
        db.setSetting("last_dir", file.toAbsolutePath().getParent().toString());
        loaded = null;
        report = null;
        confirm.setSelected(false);
        Ui.show(resultBox, false);
        Ui.show(exportResult, false);
        Ui.show(infoGrid, false);
        Ui.show(columnsBox, false);
        noticeBox1.getChildren().clear();
        updateState();
        refreshMemory();

        Task<LoadResult> task = new Task<>() {
            @Override protected LoadResult call() throws Exception {
                return DataLoader.load(file, kind, this::updateMessage);
            }
        };
        runTask(task, loadBar, loadStatus, this::showLoaded, ex -> setNotice(noticeBox1, "Could not read the file: " + ex.getMessage(), "bad"));
    }

    private void showLoaded(LoadResult r) {
        loaded = r;
        dropTitle.setText(r.fileName());

        // file facts + checksum
        infoGrid.getChildren().clear();
        addInfo(0, "File", r.fileName());
        addInfo(1, "Size", Ui.bytes(r.sizeBytes()));
        addInfo(2, "Rows", r.ok() ? Ui.num(r.totalRows()) : "not read (fix the columns first)");
        addInfo(3, "Delimiter", r.delimiter() == '\t' ? "tab" : "'" + r.delimiter() + "'");
        shaField.setText(r.sha256());
        Button copy = new Button("Copy");
        copy.getStyleClass().add("btn-small");
        copy.setOnAction(e -> Ui.copy(r.sha256()));
        HBox shaRow = new HBox(8, shaField, copy);
        shaRow.setAlignment(Pos.CENTER_LEFT);
        infoGrid.add(Ui.label("SHA-256", "section-title"), 0, 4);
        infoGrid.add(shaRow, 1, 4);
        GridPane.setHgrow(shaRow, Priority.ALWAYS);
        Ui.show(infoGrid, true);
        Ui.fadeIn(infoGrid);

        // column check
        columnsBox.getChildren().clear();
        columnsBox.getChildren().add(Ui.label("Column check", "section-title"));
        for (ColumnStatus c : r.columns()) columnsBox.getChildren().add(columnRow(c));
        Ui.show(columnsBox, true);
        Ui.fadeIn(columnsBox);

        noticeBox1.getChildren().clear();
        if (!r.ok()) {
            DataKind.ColumnSpec spec = kind.columns().stream().filter(s -> r.missingRequired().contains(s.name())).findFirst().orElseThrow();
            setNotice(noticeBox1, "Missing required column(s): " + String.join(", ", r.missingRequired())
                    + ".\nYour file has: " + String.join(", ", r.header())
                    + ".\nRename a column in your CSV and import again. Accepted names for '" + spec.name() + "': "
                    + String.join(", ", spec.aliases()) + ".", "bad");
        } else {
            setNotice(noticeBox1, Ui.num(r.totalRows()) + " rows read. All required columns found.", "ok");
            if (r.raggedRows() > 0) {
                noticeBox1.getChildren().add(Ui.notice(Ui.num(r.raggedRows())
                        + " rows have a different number of columns than the header. Check for a stray delimiter.", "warn"));
            }
            Database.HistoryItem before = db.findBySha(kind.name(), r.sha256());
            if (before != null) {
                noticeBox1.getChildren().add(Ui.notice("This exact file (same checksum) was already exported on "
                        + before.createdAt() + " as " + Path.of(before.outputPath()).getFileName() + ".", "info"));
            }
            if (r.totalRows() == 0) setNotice(noticeBox1, "The file has a header but no rows.", "bad");
        }
        updateState();
        refreshMemory();
    }

    private void addInfo(int row, String name, String value) {
        infoGrid.add(Ui.label(name, "section-title"), 0, row);
        infoGrid.add(new Label(value), 1, row);
    }

    private Node columnRow(ColumnStatus c) {
        Label pill;
        String text;
        if (c.found()) {
            pill = Ui.pill("Found", "ok");
            text = c.foundAs().equalsIgnoreCase(c.name()) ? "" : "  (your column '" + c.foundAs() + "')";
            if (!c.required()) text += "  adds " + c.name() + " features";
        } else if (c.required()) {
            pill = Ui.pill("Missing", "bad");
            text = "";
        } else {
            pill = Ui.pill("Not in file", "neutral");
            text = "  optional, " + c.name() + " features are skipped";
        }
        pill.setMinWidth(82);
        pill.setAlignment(Pos.CENTER);
        Label name = new Label(c.name() + (c.required() ? " *" : ""));
        name.setStyle("-fx-font-weight: bold;");
        HBox row = new HBox(10, pill, name, Ui.label(text, "muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ================================================================== card 2: check
    /** Runs the check (or skips it when the toggle is off). */
    public void runCheck() {
        if (busy || loaded == null || !loaded.ok()) return;
        confirm.setSelected(false);
        Ui.show(exportResult, false);
        if (!checkToggle.isSelected()) {
            report = DataChecker.skipped(loaded.totalRows());
            showReport(report);
            return;
        }
        final LoadResult data = loaded;
        Task<CheckReport> task = new Task<>() {
            @Override protected CheckReport call() {
                updateMessage("Checking " + Ui.num(data.totalRows()) + " rows...");
                return DataChecker.check(kind, data.entries(), data.has("subject"), data.has("sender"));
            }
        };
        runTask(task, checkBar, checkStatus, r -> { report = r; showReport(r); },
                ex -> setNotice(resultBox, "Check failed: " + ex.getMessage(), "bad"));
    }

    private void showReport(CheckReport r) {
        resultBox.getChildren().clear();
        if (r.skipped()) {
            resultBox.getChildren().add(Ui.notice("Check skipped. Rows with an empty value or an unknown label are still removed on export.", "warn"));
        } else {
            // score + verdict
            Label score = Ui.label("0", "score");
            Ui.countUp(score, r.averageScore());
            String level = r.averageScore() >= 75 ? "ok" : (r.averageScore() >= 50 ? "warn" : "bad");
            VBox scoreText = new VBox(4, new HBox(8, Ui.label("/ 100", "muted"), Ui.pill(r.verdict(), level)),
                    Ui.label("Average data quality of " + Ui.num(r.total()) + " rows", "muted"));
            HBox scoreRow = new HBox(14, score, scoreText);
            scoreRow.setAlignment(Pos.CENTER_LEFT);

            FlowPane pills = new FlowPane(8, 8,
                    Ui.pill("Good " + Ui.num(r.good()), "ok"),
                    Ui.pill("Odd " + Ui.num(r.odd()), "warn"),
                    Ui.pill("Invalid " + Ui.num(r.invalid()), r.invalid() > 0 ? "bad" : "neutral"),
                    Ui.pill("Unknown label " + Ui.num(r.unknownLabel()), r.unknownLabel() > 0 ? "bad" : "neutral"),
                    Ui.pill("Duplicates " + Ui.num(r.duplicates()), "neutral"));
            resultBox.getChildren().addAll(scoreRow, pills, classBar(r));

            if (kind == DataKind.LINK && !r.bias().isEmpty()) resultBox.getChildren().add(biasBox(r));
            if (!r.problems().isEmpty()) resultBox.getChildren().add(problemTable(r.problems(), r));
        }
        Ui.show(resultBox, true);
        Ui.fadeIn(resultBox);

        // sensible default for links: domain mode when the data is biased
        if (kind == DataKind.LINK) modeBox.getSelectionModel().select(r.hasStrongBias() ? 0 : 1);
        updateState();
        refreshMemory();
    }

    private Node classBar(CheckReport r) {
        int total = r.legit() + r.phishing();
        if (total == 0) return Ui.notice("No row has a recognised label (legitimate or phishing).", "bad");
        double legitShare = (double) r.legit() / total;
        Region legit = new Region(), phish = new Region();
        legit.getStyleClass().add("bar-legit");
        phish.getStyleClass().add("bar-phish");
        legit.setMinHeight(10);
        phish.setMinHeight(10);
        HBox bar = new HBox(0, legit, phish);
        legit.prefWidthProperty().bind(bar.widthProperty().multiply(legitShare));
        phish.prefWidthProperty().bind(bar.widthProperty().multiply(1 - legitShare));
        HBox caption = new HBox(Ui.label("Legitimate " + Ui.num(r.legit()), "muted"), Ui.grow(), Ui.label("Phishing " + Ui.num(r.phishing()), "muted"));
        return new VBox(4, bar, caption);
    }

    private Node biasBox(CheckReport r) {
        VBox box = new VBox(4);
        String head = r.hasStrongBias()
                ? "Class bias detected. One class has a different URL shape than the other, so a model may learn that shortcut instead of real phishing signals. Use Domain only mode."
                : "No strong class bias detected in scheme, www or path.";
        box.getChildren().add(Ui.notice(head, r.hasStrongBias() ? "warn" : "ok"));
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(4);
        grid.setPadding(new Insets(4, 0, 0, 4));
        grid.add(Ui.label("", "section-title"), 0, 0);
        grid.add(Ui.label("Legitimate", "section-title"), 1, 0);
        grid.add(Ui.label("Phishing", "section-title"), 2, 0);
        int row = 1;
        for (BiasLine b : r.bias()) {
            Label what = new Label(b.what());
            Label legit = new Label(b.legitPercent() + "%");
            Label phish = new Label(b.phishingPercent() + "%");
            if (b.strong()) for (Label l : List.of(what, legit, phish)) l.setStyle("-fx-text-fill: #A12A2A; -fx-font-weight: bold;");
            grid.add(what, 0, row);
            grid.add(legit, 1, row);
            grid.add(phish, 2, row++);
        }
        box.getChildren().add(grid);
        return box;
    }

    private Node problemTable(List<Problem> problems, CheckReport r) {
        TableView<Problem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<Problem, Number> c1 = new TableColumn<>("Row");
        c1.setMinWidth(60); c1.setPrefWidth(70); c1.setMaxWidth(90);
        c1.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().row()));
        TableColumn<Problem, String> c2 = new TableColumn<>("Value");
        c2.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().preview()));
        c2.setMinWidth(220); c2.setPrefWidth(380);
        TableColumn<Problem, Number> c3 = new TableColumn<>("Score");
        c3.setMinWidth(60); c3.setPrefWidth(70); c3.setMaxWidth(90);
        c3.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().score()));
        TableColumn<Problem, String> c4 = new TableColumn<>("Why");
        c4.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().reason()));
        c4.setMinWidth(220);
        table.getColumns().addAll(List.of(c1, c2, c3, c4));
        table.getItems().addAll(problems);
        table.setPrefHeight(Math.min(230, 34 + problems.size() * 30));
        table.setPlaceholder(new Label("No problems found"));
        int issues = r.odd() + r.invalid() + r.unknownLabel();
        String caption = issues > problems.size()
                ? "Showing the first " + problems.size() + " of " + Ui.num(issues) + " rows that need attention"
                : "Rows that need attention";
        return new VBox(6, Ui.label(caption, "section-title"), table);
    }

    // ================================================================== card 3: export
    private void onExportArff() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save ARFF file");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ARFF file", "*.arff"));
        fc.setInitialFileName(baseName(loaded.fileName()) + "_" + modeSuffix() + ".arff");
        File dir = lastDir();
        if (dir != null) fc.setInitialDirectory(dir);
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        Path p = f.toPath();
        if (!p.getFileName().toString().toLowerCase().endsWith(".arff")) p = p.resolveSibling(p.getFileName() + ".arff");
        exportArff(p);
    }

    /** Public so it can be driven from tests. */
    public void exportArff(Path out) {
        if (busy || loaded == null || report == null) return;
        db.setSetting("last_dir", out.toAbsolutePath().getParent().toString());
        final LoadResult data = loaded;
        final boolean domain = kind == DataKind.LINK && modeBox.getSelectionModel().getSelectedIndex() == 0;
        final DatasetBuilder.Options options = new DatasetBuilder.Options(kind, domain, balance.isSelected(),
                dropInvalid.isSelected() && !report.skipped(), data.has("subject"), data.has("sender"), ngramBuckets());
        final Path target = out;

        Task<ExportSummary> task = new Task<>() {
            @Override protected ExportSummary call() throws Exception {
                updateMessage("Preparing rows...");
                DatasetBuilder.Built built = DatasetBuilder.build(data.entries(), options);
                DatasetBuilder.Stats s = built.stats();
                if (s.rowsOut() == 0 || s.legitOut() == 0 || s.phishingOut() == 0) {
                    throw new IllegalStateException("Nothing to export: after cleaning, one class has no rows (legitimate "
                            + s.legitOut() + ", phishing " + s.phishingOut() + "). Check the labels and the rows rated Invalid.");
                }
                String sha = DatasetBuilder.writeArff(built, target, data.fileName(), data.sha256(), this::updateMessage);
                Path info = target.resolveSibling(baseName(target.getFileName().toString()) + ".info.txt");
                DatasetBuilder.writeInfo(built, info, data.fileName(), data.sha256(), sha);
                return new ExportSummary(s, sha, target, info);
            }
        };
        runTask(task, exportBar, exportStatus, summary -> showExported(summary, data, options),
                ex -> setNotice(exportResult, "Export failed: " + ex.getMessage(), "bad"));
    }

    private record ExportSummary(DatasetBuilder.Stats stats, String sha256, Path arff, Path info) {}

    private void showExported(ExportSummary x, LoadResult data, DatasetBuilder.Options o) {
        DatasetBuilder.Stats s = x.stats();
        exportResult.getChildren().clear();
        exportResult.getChildren().add(Ui.notice("Saved " + Ui.num(s.rowsOut()) + " rows (" + Ui.num(s.legitOut()) + " legitimate, "
                + Ui.num(s.phishingOut()) + " phishing) to " + x.arff(), "ok"));
        exportResult.getChildren().add(Ui.label("Removed: " + Ui.num(s.unknownLabel()) + " unknown label, "
                + Ui.num(s.invalidDropped()) + " invalid, " + Ui.num(s.duplicates()) + " duplicates, "
                + Ui.num(s.conflicts()) + " conflicting (both labels).", "muted"));
        TextField sha = new TextField(x.sha256());
        sha.setEditable(false);
        sha.getStyleClass().add("mono");
        HBox.setHgrow(sha, Priority.ALWAYS);
        Button copy = new Button("Copy");
        copy.getStyleClass().add("btn-small");
        copy.setOnAction(e -> Ui.copy(x.sha256()));
        Button folder = new Button("Show folder");
        folder.getStyleClass().add("btn-secondary");
        folder.setOnAction(e -> openFolder(x.arff().getParent()));
        HBox shaRow = new HBox(8, Ui.label("ARFF SHA-256", "section-title"), sha, copy);
        shaRow.setAlignment(Pos.CENTER_LEFT);
        exportResult.getChildren().addAll(shaRow, Ui.label("A report with the checksums, counts and feature list was saved next to it: "
                + x.info().getFileName(), "muted"), new HBox(folder));
        Ui.show(exportResult, true);
        Ui.fadeIn(exportResult);

        String mode = kind == DataKind.LINK ? (o.domainMode() ? "domain" : "full") : "email";
        db.addHistory(new Database.HistoryItem("", kind.name(), data.fileName(), data.sha256(), s.rowsIn(), s.rowsOut(),
                mode, x.arff().toString(), x.sha256()));
        refreshHistory();
    }

    private void onExportJava() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose a folder for the Java files");
        File dir = lastDir();
        if (dir != null) dc.setInitialDirectory(dir);
        File f = dc.showDialog(getScene().getWindow());
        if (f == null) return;
        exportJava(f.toPath());
    }

    /** Public so it can be driven from tests. */
    public void exportJava(Path folder) {
        if (loaded == null) return;
        boolean domain = kind == DataKind.LINK && modeBox.getSelectionModel().getSelectedIndex() == 0;
        try {
            List<Path> files = ExtractorExporter.export(kind, folder, packageField.getText(), domain,
                    loaded.has("subject"), loaded.has("sender"), ngramBuckets());
            db.setSetting("package_name", packageField.getText().trim());
            db.setSetting("last_dir", folder.toAbsolutePath().toString());
            StringBuilder names = new StringBuilder();
            for (Path p : files) names.append(names.length() == 0 ? "" : ", ").append(p.getFileName());
            Button open = new Button("Show folder");
            open.getStyleClass().add("btn-secondary");
            open.setOnAction(e -> openFolder(folder));
            exportResult.getChildren().clear();
            exportResult.getChildren().addAll(Ui.notice("Saved " + names + " to " + folder, "ok"), new HBox(open));
        } catch (Exception ex) {
            exportResult.getChildren().clear();
            exportResult.getChildren().add(Ui.notice("Could not export the Java code: " + ex.getMessage(), "bad"));
        }
        Ui.show(exportResult, true);
        Ui.fadeIn(exportResult);
    }

    // ================================================================== history (SQLite)
    private void refreshHistory() {
        historyBox.getChildren().clear();
        if (!db.available()) return;
        List<Database.HistoryItem> items = db.recent(kind.name(), 5);
        if (items.isEmpty()) return;
        historyBox.getChildren().add(Ui.label("Recent " + kind.title().toLowerCase() + " exports", "section-title"));
        for (Database.HistoryItem h : items) {
            String text = h.createdAt() + "   " + h.sourceName() + "  >  " + Ui.num(h.rowsOut()) + " rows ("
                    + h.mode() + ")   " + h.outputSha256().substring(0, 12) + "...";
            Label l = Ui.label(text, "muted");
            l.setWrapText(false);
            historyBox.getChildren().add(l);
        }
        historyBox.setPadding(new Insets(0, 6, 0, 6));
    }

    // ================================================================== helpers
    private <T> void runTask(Task<T> task, ProgressBar bar, Label status, Consumer<T> onOk, Consumer<Throwable> onFail) {
        busy = true;
        Ui.show(bar, true);
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        Ui.show(status, true);
        status.textProperty().bind(task.messageProperty());
        task.messageProperty().addListener((o, a, b) -> { if (b != null && !b.isEmpty()) Ui.STATUS.set(b); });
        Runnable finish = () -> {
            busy = false;
            Ui.STATUS.set("OK");
            status.textProperty().unbind();
            Ui.show(bar, false);
            Ui.show(status, false);
        };
        task.setOnSucceeded(e -> { finish.run(); onOk.accept(task.getValue()); });
        task.setOnFailed(e -> {
            finish.run();
            Throwable ex = task.getException();
            Ui.STATUS.set("Failed");
            onFail.accept(ex);
            if (ex instanceof OutOfMemoryError) System.gc();
        });
        Thread t = new Thread(task, "phisharff-task");
        t.setDaemon(true);
        t.start();
    }

    private void setNotice(Pane target, String text, String level) {
        target.getChildren().setAll(Ui.notice(text, level));
        Ui.show(target, true);
        Ui.fadeIn(target);
    }

    private void openFolder(Path folder) {
        if (folder != null && host != null) host.showDocument(folder.toUri().toString());
    }

    private File lastDir() {
        File f = new File(db.getSetting("last_dir", System.getProperty("user.home")));
        return f.isDirectory() ? f : null;
    }

    private String modeSuffix() {
        return kind == DataKind.LINK ? (modeBox.getSelectionModel().getSelectedIndex() == 0 ? "domain" : "full") : "features";
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
