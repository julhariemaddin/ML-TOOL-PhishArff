package com.julharie.phisharff.core;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Small streaming CSV reader. Handles quoted fields, "" escapes, line breaks inside quotes,
 * a UTF-8 BOM, and the delimiters comma, semicolon and tab (detected from the first line).
 */
public final class CsvReader implements Closeable {
    private static final int MAX_FIELD = 5_000_000;

    private final BufferedReader in;
    private final char delimiter;

    private CsvReader(BufferedReader in, char delimiter) {
        this.in = in;
        this.delimiter = delimiter;
    }

    public static CsvReader open(Path file) throws IOException {
        char delimiter = detectDelimiter(file);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8), 1 << 16);
        return new CsvReader(reader, delimiter);
    }

    public char delimiter() { return delimiter; }

    /** Next record, or null at the end of the file. Blank lines are skipped. */
    public String[] next() throws IOException {
        while (true) {
            String[] row = readRecord();
            if (row == null) return null;
            if (row.length == 1 && row[0].isEmpty()) continue;   // blank line
            return row;
        }
    }

    private String[] readRecord() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false, any = false;
        int c;
        while ((c = in.read()) != -1) {
            any = true;
            char ch = (char) c;
            if (inQuotes) {
                if (ch == '"') {
                    in.mark(1);
                    int n = in.read();
                    if (n == '"') sb.append('"');                 // "" inside quotes = one quote
                    else { inQuotes = false; if (n != -1) in.reset(); }
                } else {
                    sb.append(ch);
                    if (sb.length() > MAX_FIELD) throw new IOException("A field is larger than 5 million characters. Check for a missing closing quote.");
                }
            } else if (ch == '"' && sb.length() == 0) {
                inQuotes = true;
            } else if (ch == delimiter) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else if (ch == '\n') {
                fields.add(sb.toString());
                return fields.toArray(new String[0]);
            } else if (ch != '\r') {
                sb.append(ch);
            }
        }
        if (!any) return null;
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    /** Looks at the first line and picks the delimiter that appears most (outside quotes). */
    private static char detectDelimiter(Path file) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line = r.readLine();
            if (line == null) return ',';
            int comma = 0, semi = 0, tab = 0;
            boolean q = false;
            for (char ch : line.toCharArray()) {
                if (ch == '"') q = !q;
                else if (!q) {
                    if (ch == ',') comma++;
                    else if (ch == ';') semi++;
                    else if (ch == '\t') tab++;
                }
            }
            if (tab > comma && tab >= semi) return '\t';
            if (semi > comma) return ';';
            return ',';
        }
    }

    @Override public void close() throws IOException { in.close(); }
}
