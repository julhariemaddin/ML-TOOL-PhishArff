package com.julharie.phisharff.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Imports a CSV: computes the checksum, checks the required column names, and reads the rows
 * (only the columns we need, so big files stay small in memory).
 */
public final class DataLoader {
    private static final int MAX_BODY_CHARS = 20_000;

    private DataLoader() {}

    /** Status of one expected column. foundAs = the real header text, or null when missing. */
    public record ColumnStatus(String name, boolean required, String foundAs) {
        public boolean found() { return foundAs != null; }
    }

    public record LoadResult(Path path, String fileName, long sizeBytes, String sha256, char delimiter,
                             List<String> header, List<ColumnStatus> columns, List<Entry> entries,
                             int totalRows, int raggedRows) {

        /** True when every required column was found. */
        public boolean ok() {
            return columns.stream().noneMatch(c -> c.required() && !c.found());
        }

        public boolean has(String column) {
            return columns.stream().anyMatch(c -> c.name().equals(column) && c.found());
        }

        public List<String> missingRequired() {
            List<String> out = new ArrayList<>();
            for (ColumnStatus c : columns) if (c.required() && !c.found()) out.add(c.name());
            return out;
        }
    }

    public static LoadResult load(Path file, DataKind kind, Consumer<String> status) throws IOException {
        status.accept("Calculating checksum...");
        String sha = Checksum.sha256(file);
        long size = Files.size(file);

        try (CsvReader csv = CsvReader.open(file)) {
            // 1. header
            String[] headerRow = csv.next();
            if (headerRow == null) throw new IOException("The file is empty.");
            List<String> header = new ArrayList<>();
            for (int i = 0; i < headerRow.length; i++) {
                String h = headerRow[i];
                if (i == 0 && h.startsWith("\uFEFF")) h = h.substring(1);   // remove BOM
                header.add(h.trim());
            }

            // 2. match expected columns by name
            int[] index = new int[kind.columns().size()];
            List<ColumnStatus> statuses = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            for (int k = 0; k < index.length; k++) {
                DataKind.ColumnSpec spec = kind.columns().get(k);
                index[k] = -1;
                outer:
                for (String alias : spec.aliases()) {
                    for (int i = 0; i < header.size(); i++) {
                        if (!used.contains(i) && normalize(header.get(i)).equals(alias)) {
                            index[k] = i;
                            used.add(i);
                            break outer;
                        }
                    }
                }
                statuses.add(new ColumnStatus(spec.name(), spec.required(), index[k] < 0 ? null : header.get(index[k])));
            }

            boolean ok = statuses.stream().noneMatch(c -> c.required() && !c.found());
            if (!ok) {
                return new LoadResult(file, file.getFileName().toString(), size, sha, csv.delimiter(),
                        header, statuses, List.of(), 0, 0);
            }

            // 3. rows
            int urlIdx = indexOf(kind, statuses, index, "url"), bodyIdx = indexOf(kind, statuses, index, "body");
            int labelIdx = indexOf(kind, statuses, index, "label"), subjectIdx = indexOf(kind, statuses, index, "subject");
            int senderIdx = indexOf(kind, statuses, index, "sender");
            List<Entry> entries = new ArrayList<>();
            int total = 0, ragged = 0;
            String[] row;
            while ((row = csv.next()) != null) {
                total++;
                if (row.length != header.size()) ragged++;
                String body = cell(row, bodyIdx);
                if (body != null && body.length() > MAX_BODY_CHARS) body = body.substring(0, MAX_BODY_CHARS);
                entries.add(new Entry(total, cell(row, urlIdx), cell(row, subjectIdx), cell(row, senderIdx),
                        body, cell(row, labelIdx)));
                if (total % 5000 == 0) status.accept("Reading rows... " + total);
            }
            return new LoadResult(file, file.getFileName().toString(), size, sha, csv.delimiter(),
                    header, statuses, entries, total, ragged);
        }
    }

    private static int indexOf(DataKind kind, List<ColumnStatus> statuses, int[] index, String name) {
        for (int k = 0; k < kind.columns().size(); k++) if (kind.columns().get(k).name().equals(name)) return index[k];
        return -1;
    }

    /** Value of a column, "" if the row is too short, null if the column does not exist. */
    private static String cell(String[] row, int idx) {
        if (idx < 0) return null;
        return idx < row.length ? row[idx] : "";
    }

    /** "Email Body", "email-body", " EMAIL_BODY " -> "email_body". */
    static String normalize(String header) {
        return header.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
